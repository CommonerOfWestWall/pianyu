#include "spektralab/spk_engine.h"
#include "io/raw_decoder.hpp"
#include <android/bitmap.h>
#include <android/log.h>
#include <dlfcn.h>
#include <filesystem>
#include <algorithm>
#include <jni.h>
#include <memory>
#include <new>
#include <stdexcept>
#include <string>
#include <vector>
#include <cmath>

namespace {
struct Api {
    decltype(&spk_engine_create) create;
    decltype(&spk_engine_destroy) destroy;
    decltype(&spk_open) open;
    decltype(&spk_render) render;
    decltype(&spk_progress) progress;
    decltype(&spk_result_free) free_result;
    decltype(&spk_session_release) release;
    decltype(&spk_string_free) free_string;
    decltype(&spk_last_error) error;

    template<class T> static T symbol(void* lib, const char* name) {
        auto value = reinterpret_cast<T>(dlsym(lib, name));
        if (!value) throw std::runtime_error(std::string("Missing engine symbol: ") + name);
        return value;
    }
    Api() {
        // Keep the core independently replaceable while retaining its original C ABI/SONAME.
        void* lib = dlopen("libpianyu_core.so", RTLD_NOW | RTLD_LOCAL);
        if (!lib) throw std::runtime_error(dlerror());
        create = symbol<decltype(create)>(lib, "spk_engine_create");
        destroy = symbol<decltype(destroy)>(lib, "spk_engine_destroy");
        open = symbol<decltype(open)>(lib, "spk_open");
        render = symbol<decltype(render)>(lib, "spk_render");
        progress = symbol<decltype(progress)>(lib, "spk_progress");
        free_result = symbol<decltype(free_result)>(lib, "spk_result_free");
        release = symbol<decltype(release)>(lib, "spk_session_release");
        free_string = symbol<decltype(free_string)>(lib, "spk_string_free");
        error = symbol<decltype(error)>(lib, "spk_last_error");
    }
};
Api& api() { static Api value; return value; }
std::string engine_error(spk_engine* engine, const char* fallback) {
    const char* message = api().error(engine); // Borrowed from the engine; never freed by this caller.
    return message && *message ? message : fallback;
}
std::string export_params(const char* params) {
    std::string result(params ? params : "{}");
    const auto end = result.find_last_of('}');
    if (end == std::string::npos) return result;
    size_t content = end;
    while (content > 0 && (result[content - 1] == ' ' || result[content - 1] == '\n' ||
        result[content - 1] == '\r' || result[content - 1] == '\t')) --content;
    result.insert(end, content > 0 && result[content - 1] != '{'
        ? ",\"striped\":true,\"strip_rows\":256"
        : "\"striped\":true,\"strip_rows\":256");
    return result;
}
struct Utf {
    JNIEnv* env; jstring value; const char* chars;
    Utf(JNIEnv* e, jstring s) : env(e), value(s), chars(e->GetStringUTFChars(s, nullptr)) {
        if (!chars) throw std::runtime_error("Could not read string");
    }
    ~Utf() { env->ReleaseStringUTFChars(value, chars); }
};
struct Pixels {
    JNIEnv* env; jobject bitmap; void* data = nullptr;
    Pixels(JNIEnv* e, jobject b) : env(e), bitmap(b) {
        if (AndroidBitmap_lockPixels(env, bitmap, &data) != ANDROID_BITMAP_RESULT_SUCCESS)
            throw std::runtime_error("Could not access image pixels");
    }
    ~Pixels() { AndroidBitmap_unlockPixels(env, bitmap); }
};
struct RawImage {
    spk::io::DecodedRaw decoded;
};
void fail(JNIEnv* env, const std::exception& e) {
    if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), e.what());
}

jobject rgba_bitmap(JNIEnv* env, uint32_t width, uint32_t height) {
    jclass bitmap_class = env->FindClass("android/graphics/Bitmap");
    jclass config_class = env->FindClass("android/graphics/Bitmap$Config");
    auto config = env->GetStaticObjectField(config_class,
        env->GetStaticFieldID(config_class, "ARGB_8888", "Landroid/graphics/Bitmap$Config;"));
    auto factory = env->GetStaticMethodID(bitmap_class, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    return env->CallStaticObjectMethod(bitmap_class, factory, jint(width), jint(height), config);
}

std::unique_ptr<spk_session, decltype(api().release)> open_image(
        JNIEnv* env, spk_engine* engine, const spk_image& image, jstring params) {
    Utf json(env, params);
    const std::string render_params = export_params(json.chars);
    char* reply = nullptr;
    auto* session = api().open(engine, &image, render_params.c_str(), &reply);
    if (reply) api().free_string(reply);
    if (!session) throw std::runtime_error(engine_error(engine, "Could not open image session"));
    return {session, api().release};
}

jobject render_image(JNIEnv* env, spk_engine* engine, spk_session* session) {
    spk_result result{};
    struct ResultGuard { spk_result* value; ~ResultGuard() { api().free_result(value); } } guard{&result};
    if (api().render(session, "full", &result) != SPK_OK) {
        const std::string message = engine_error(engine, "Could not render image");
        char* progress = nullptr;
        if (api().progress(session, nullptr, &progress) == SPK_OK && progress) {
            __android_log_print(ANDROID_LOG_WARN, "PianyuCore", "failed_render_progress=%s", progress);
            api().free_string(progress);
        }
        throw std::runtime_error(message);
    }
    if (!result.rgba16 || !result.width || !result.height) throw std::runtime_error("Empty engine result");

    jobject output = rgba_bitmap(env, result.width, result.height);
    if (!output || env->ExceptionCheck()) return nullptr;
    AndroidBitmapInfo output_info{};
    if (AndroidBitmap_getInfo(env, output, &output_info) != ANDROID_BITMAP_RESULT_SUCCESS)
        throw std::runtime_error("Could not allocate output image");
    {
        Pixels pixels(env, output);
        for (uint32_t y = 0; y < result.height; ++y) {
            auto* row = static_cast<uint8_t*>(pixels.data) + size_t(y) * output_info.stride;
            const auto* from = result.rgba16 + size_t(y) * result.row_stride_px * 4;
            for (uint32_t x = 0; x < result.width; ++x) {
                for (unsigned c = 0; c < 3; ++c) row[x * 4 + c] = uint8_t((uint32_t(from[x * 4 + c]) + 128) / 257);
                row[x * 4 + 3] = 255;
            }
        }
    }
    return output;
}

void resize_raw(spk::io::DecodedRaw& raw, jint edge) {
    const uint32_t longest = std::max(raw.width, raw.height);
    if (edge <= 0 || uint32_t(edge) >= longest) return;
    const double scale = double(edge) / longest;
    const auto width = std::max<uint32_t>(1, uint32_t(std::lround(raw.width * scale)));
    const auto height = std::max<uint32_t>(1, uint32_t(std::lround(raw.height * scale)));
    std::vector<float> rgb(size_t(width) * height * 3);
    for (uint32_t y = 0; y < height; ++y) {
        const double sy = std::clamp((y + .5) * raw.height / height - .5, 0.0, double(raw.height - 1));
        const auto y0 = uint32_t(sy), y1 = std::min(y0 + 1, raw.height - 1);
        for (uint32_t x = 0; x < width; ++x) {
            const double sx = std::clamp((x + .5) * raw.width / width - .5, 0.0, double(raw.width - 1));
            const auto x0 = uint32_t(sx), x1 = std::min(x0 + 1, raw.width - 1);
            auto pixel = [&](uint32_t col, uint32_t row) { return raw.rgb.data() + (size_t(row) * raw.width + col) * 3; };
            const auto* a = pixel(x0, y0); const auto* b = pixel(x1, y0);
            const auto* c = pixel(x0, y1); const auto* d = pixel(x1, y1);
            for (unsigned channel = 0; channel < 3; ++channel) {
                const float dx = float(sx - x0), dy = float(sy - y0);
                rgb[(size_t(y) * width + x) * 3 + channel] = std::lerp(
                    std::lerp(a[channel], b[channel], dx), std::lerp(c[channel], d[channel], dx), dy);
            }
        }
    }
    raw.width = width;
    raw.height = height;
    raw.rgb = std::move(rgb);
}

}

extern "C" JNIEXPORT jlong JNICALL Java_studio_pianyu_app_NativeCore_create(
        JNIEnv* env, jobject, jstring path) {
    try {
        Utf root(env, path);
        auto* engine = api().create(root.chars, nullptr);
        if (!engine) throw std::runtime_error(engine_error(nullptr, "Could not initialize image engine"));
        return reinterpret_cast<jlong>(engine);
    } catch (const std::exception& e) { fail(env, e); return 0; }
}

extern "C" JNIEXPORT void JNICALL Java_studio_pianyu_app_NativeCore_destroy(
        JNIEnv* env, jobject, jlong handle) {
    try {
        if (handle) api().destroy(reinterpret_cast<spk_engine*>(handle));
    } catch (const std::exception& e) { fail(env, e); }
}

extern "C" JNIEXPORT jobject JNICALL Java_studio_pianyu_app_NativeCore_process(
        JNIEnv* env, jobject, jlong handle, jobject source, jstring params) {
    try {
        auto* engine = reinterpret_cast<spk_engine*>(handle);
        if (!engine) throw std::runtime_error("Engine is not initialized");
        AndroidBitmapInfo info{};
        if (AndroidBitmap_getInfo(env, source, &info) != ANDROID_BITMAP_RESULT_SUCCESS ||
            info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) throw std::runtime_error("Expected software sRGB RGBA image");
        std::vector<float> rgb(size_t(info.width) * info.height * 3);
        {
            Pixels input(env, source);
            for (uint32_t y = 0; y < info.height; ++y) {
                auto* row = static_cast<const uint8_t*>(input.data) + size_t(y) * info.stride;
                for (uint32_t x = 0; x < info.width; ++x)
                    for (unsigned c = 0; c < 3; ++c)
                        rgb[(size_t(y) * info.width + x) * 3 + c] = row[x * 4 + c] / 255.0f;
            }
        }
        const spk_image image{rgb.data(), info.width, info.height, 3};
        auto session = open_image(env, engine, image, params);
        // The engine only borrows pixels for spk_open.
        std::vector<float>().swap(rgb);
        return render_image(env, engine, session.get());
    } catch (const std::bad_alloc&) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),
            "Native image allocation failed (std::bad_alloc)");
        return nullptr;
    } catch (const std::exception& e) { fail(env, e); return nullptr; }
}

extern "C" JNIEXPORT jlong JNICALL Java_studio_pianyu_app_NativeCore_createRaw(
        JNIEnv* env, jobject, jstring path, jint edge) {
    try {
        Utf source(env, path);
        auto raw = std::make_unique<RawImage>();
        std::string error;
        const std::filesystem::path file(source.chars);
        // Headroom retains values outside display range for the engine. Some
        // DNGs are intentionally outside the narrow Bayer policy; the pinned
        // compatible decoder remains a safe fallback for those files.
        if (!spk::io::decode_raw_headroom(file, raw->decoded, error) &&
            !spk::io::decode_raw_compatible(file, raw->decoded, error))
            throw std::runtime_error(error.empty() ? "Could not decode RAW image" : error);
        resize_raw(raw->decoded, edge);
        return reinterpret_cast<jlong>(raw.release());
    } catch (const std::bad_alloc&) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "RAW image allocation failed");
        return 0;
    } catch (const std::exception& e) { fail(env, e); return 0; }
}

extern "C" JNIEXPORT void JNICALL Java_studio_pianyu_app_NativeCore_destroyRaw(
        JNIEnv* env, jobject, jlong handle) {
    try { delete reinterpret_cast<RawImage*>(handle); }
    catch (const std::exception& e) { fail(env, e); }
}

extern "C" JNIEXPORT jobject JNICALL Java_studio_pianyu_app_NativeCore_previewRaw(
        JNIEnv* env, jobject, jlong raw_handle) {
    try {
        auto* raw = reinterpret_cast<RawImage*>(raw_handle);
        if (!raw || raw->decoded.rgb.empty()) throw std::runtime_error("RAW session is not initialized");
        const auto& decoded = raw->decoded;
        const auto width = decoded.width, height = decoded.height;
        jobject output = rgba_bitmap(env, width, height);
        if (!output || env->ExceptionCheck()) return nullptr;
        AndroidBitmapInfo info{};
        if (AndroidBitmap_getInfo(env, output, &info) != ANDROID_BITMAP_RESULT_SUCCESS)
            throw std::runtime_error("Could not allocate RAW preview");
        Pixels pixels(env, output);
        // LibRaw's output is linear ProPhoto RGB. Convert to linear sRGB
        // before the display transfer; this path is display-only, while the
        // cached float ProPhoto values remain untouched for engine rendering.
        auto encode = [](float value) {
            value = std::max(0.0f, value);
            const float encoded = value <= 0.0031308f ? 12.92f * value
                : 1.055f * std::pow(value, 1.0f / 2.4f) - 0.055f;
            return uint8_t(std::lround(std::clamp(encoded, 0.0f, 1.0f) * 255.0f));
        };
        for (uint32_t y = 0; y < height; ++y) {
            auto* row = static_cast<uint8_t*>(pixels.data) + size_t(y) * info.stride;
            for (uint32_t x = 0; x < width; ++x) {
                const auto* from = decoded.rgb.data() + (size_t(y) * width + x) * 3;
                // D50 ProPhoto to D65 sRGB with Bradford adaptation.
                const float r = 2.034076f * from[0] - 0.727334f * from[1] - 0.306742f * from[2];
                const float g = -0.228813f * from[0] + 1.231730f * from[1] - 0.002917f * from[2];
                const float b = -0.008570f * from[0] - 0.153287f * from[1] + 1.161856f * from[2];
                row[x * 4] = encode(r); row[x * 4 + 1] = encode(g);
                row[x * 4 + 2] = encode(b); row[x * 4 + 3] = 255;
            }
        }
        return output;
    } catch (const std::exception& e) { fail(env, e); return nullptr; }
}

extern "C" JNIEXPORT jobject JNICALL Java_studio_pianyu_app_NativeCore_processRaw(
        JNIEnv* env, jobject, jlong handle, jlong raw_handle, jstring params) {
    try {
        auto* engine = reinterpret_cast<spk_engine*>(handle);
        auto* raw = reinterpret_cast<RawImage*>(raw_handle);
        if (!engine || !raw || raw->decoded.rgb.empty()) throw std::runtime_error("RAW session is not initialized");
        const auto& decoded = raw->decoded;
        const spk_image image{decoded.rgb.data(), decoded.width, decoded.height, 3};
        auto session = open_image(env, engine, image, params);
        return render_image(env, engine, session.get());
    } catch (const std::bad_alloc&) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "RAW render allocation failed");
        return nullptr;
    } catch (const std::exception& e) { fail(env, e); return nullptr; }
}
