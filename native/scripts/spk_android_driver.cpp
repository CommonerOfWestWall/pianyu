// Android command-line driver: the upstream C ABI fixture with a compact CLI.
#include "spektralab/spk_engine.h"
#include <cstdint>
#include <fstream>
#include <iostream>
#include <string>
#include <vector>
#include <chrono>
#include <stdexcept>

static std::vector<float> read_rgb(const char* path, size_t count) {
    std::ifstream f(path, std::ios::binary | std::ios::ate);
    if (!f || f.tellg() != std::streamoff(count * sizeof(float))) throw std::runtime_error("bad RGB input length");
    std::vector<float> v(count); f.seekg(0); f.read(reinterpret_cast<char*>(v.data()), std::streamsize(count * sizeof(float))); if (!f) throw std::runtime_error("cannot read RGB input"); return v;
}
static void write_rgba(const char* path, const spk_result& r) {
    std::ofstream f(path, std::ios::binary | std::ios::trunc);
    for (uint32_t y = 0; y < r.height; ++y) f.write(reinterpret_cast<const char*>(r.rgba16 + size_t(y) * r.row_stride_px * 4), std::streamsize(r.width * 4 * sizeof(uint16_t)));
    if (!f) throw std::runtime_error("cannot write RGBA16 output");
}
int main(int argc, char** argv) {
    if (argc != 8) { std::cerr << "usage: spk_android_driver <resources> <input.f32> <width> <height> <params.json> <output.rgba16> <report.txt>\n"; return 2; }
    const uint32_t w = std::stoul(argv[3]), h = std::stoul(argv[4]);
    try {
        auto pixels = read_rgb(argv[2], size_t(w) * h * 3);
        std::ifstream p(argv[5], std::ios::binary | std::ios::ate); if (!p || p.tellg() <= 0) throw std::runtime_error("cannot read params JSON"); std::string json(size_t(p.tellg()), '\0'); p.seekg(0); p.read(json.data(), std::streamsize(json.size())); if (!p) throw std::runtime_error("cannot read params JSON");
        auto* engine = spk_engine_create(argv[1], nullptr);
        if (!engine) throw std::runtime_error(spk_last_error(nullptr));
        std::cout << "build=" << spk_build_info() << "\ncapabilities=" << spk_capabilities(engine) << "\n";
        spk_image image{pixels.data(), w, h, 3}; char* reply = nullptr;
        auto started = std::chrono::steady_clock::now();
        auto* session = spk_open(engine, &image, json.c_str(), &reply);
        if (!session) throw std::runtime_error(spk_last_error(engine));
        std::cout << "open_ms=" << std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - started).count() << "\nopen_reply=" << (reply ? reply : "") << "\n";
        if (reply) spk_string_free(reply);
        spk_result result{}; started = std::chrono::steady_clock::now();
        const auto status = spk_render(session, "full", &result);
        const auto ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - started).count();
        std::cout << "render_status=" << status << " render_wall_ms=" << ms << " engine_ms=" << result.elapsed_ms << "\n";
        if (status != SPK_OK) throw std::runtime_error(spk_last_error(engine));
        std::cout << "output_width=" << result.width << " output_height=" << result.height << " output_stride_px=" << result.row_stride_px << "\n";
        write_rgba(argv[6], result);
        spk_result repeat{}; started = std::chrono::steady_clock::now(); const auto repeat_status = spk_render(session, "full", &repeat); const auto repeat_ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - started).count();
        if (repeat_status != SPK_OK) throw std::runtime_error(spk_last_error(engine));
        std::cout << "repeat_status=" << repeat_status << " repeat_wall_ms=" << repeat_ms << " repeat_engine_ms=" << repeat.elapsed_ms << "\n";
        std::ofstream report(argv[7], std::ios::trunc); if (!report) throw std::runtime_error("cannot open report"); report << "status=" << status << "\nwidth=" << result.width << "\nheight=" << result.height << "\nrow_stride_px=" << result.row_stride_px << "\nrender_wall_ms=" << ms << "\nengine_ms=" << result.elapsed_ms << "\nrepeat_status=" << repeat_status << "\nrepeat_wall_ms=" << repeat_ms << "\nrepeat_engine_ms=" << repeat.elapsed_ms << "\nbackend=" << spk_capabilities(engine) << "\n"; if (!report) throw std::runtime_error("cannot write report");
        spk_result_free(&repeat);
        spk_result_free(&result); spk_session_release(session); spk_engine_destroy(engine); return 0;
    } catch (const std::exception& e) { std::cerr << "error=" << e.what() << "\n"; return 1; }
}
