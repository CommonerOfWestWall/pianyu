cmake_minimum_required(VERSION 3.22)
project(pianyu_jni LANGUAGES CXX)
set(SPEKTRALAB_ENGINE_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../engine")
set(SPEKTRALAB_DEPS_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../build/deps" CACHE PATH
    "Pinned native dependency directory")
set(SPEKTRALAB_LIBRAW_SOURCE "${SPEKTRALAB_DEPS_DIR}/LibRaw-0.22.2" CACHE PATH
    "Pinned LibRaw 0.22.2 source tree")
include("${SPEKTRALAB_ENGINE_ROOT}/cmake/LibRaw.cmake")

add_library(pianyu_jni SHARED pianyu_jni.cpp
    "${SPEKTRALAB_ENGINE_ROOT}/src/io/raw_decoder.cpp"
    "${SPEKTRALAB_ENGINE_ROOT}/src/io/raw_headroom.cpp")
target_compile_features(pianyu_jni PRIVATE cxx_std_20)
target_include_directories(pianyu_jni PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/include"
    "${SPEKTRALAB_ENGINE_ROOT}/src")
target_link_libraries(pianyu_jni PRIVATE android jnigraphics dl log spk_libraw)
target_link_options(pianyu_jni PRIVATE "-Wl,-z,max-page-size=16384")
