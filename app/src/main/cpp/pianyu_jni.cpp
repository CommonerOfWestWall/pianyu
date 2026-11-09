#include "spektralab/spk_engine.h"
#include <android/bitmap.h>
#include <dlfcn.h>
#include <jni.h>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

namespace {
struct Api {
    decltype(&spk_engine_create) create;
    decltype(&spk_open) open;
    decltype(&spk_render) render;
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
        open = symbol<decltype(open)>(lib, "spk_open");
        render = symbol<decltype(render)>(lib, "spk_render");
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
void fail(JNIEnv* env, const std::exception& e) {
    if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), e.what());
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
        Utf json(env, params);
        spk_image image{rgb.data(), info.width, info.height, 3};
        char* reply = nullptr;
        auto* raw = api().open(engine, &image, json.chars, &reply);
        if (reply) api().free_string(reply);
        if (!raw) throw std::runtime_error(engine_error(engine, "Could not open image session"));
        std::unique_ptr<spk_session, decltype(api().release)> session(raw, api().release);
        spk_result result{};
        struct ResultGuard { spk_result* value; ~ResultGuard() { api().free_result(value); } } guard{&result};
        if (api().render(session.get(), "full", &result) != SPK_OK)
            throw std::runtime_error(engine_error(engine, "Could not render image"));
        if (!result.rgba16 || !result.width || !result.height) throw std::runtime_error("Empty engine result");

        jclass bitmap_class = env->FindClass("android/graphics/Bitmap");
        jclass config_class = env->FindClass("android/graphics/Bitmap$Config");
        auto config = env->GetStaticObjectField(config_class,
            env->GetStaticFieldID(config_class, "ARGB_8888", "Landroid/graphics/Bitmap$Config;"));
        auto factory = env->GetStaticMethodID(bitmap_class, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
        jobject output = env->CallStaticObjectMethod(bitmap_class, factory, jint(result.width), jint(result.height), config);
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
    } catch (const std::exception& e) { fail(env, e); return nullptr; }
}
