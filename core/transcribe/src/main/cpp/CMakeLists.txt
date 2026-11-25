# Copyright (c) 2026 WebWalker3D. Licensed under CPAL-1.0; see LICENSE.
# Builds whisper.cpp (MIT, ggml-org) from a pinned, hash-checked release, plus Kairoify's small JNI bridge.
cmake_minimum_required(VERSION 3.22)
project(kairoify_whisper C CXX)

include(FetchContent)
FetchContent_Declare(
    whisper
    URL https://github.com/ggml-org/whisper.cpp/archive/refs/tags/v1.9.5.tar.gz
    URL_HASH SHA256=ff1a9053feb509ff9d7729703355541ae9690073a6b1c40eb692c962e0dc1720
)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(whisper)

add_library(kairoify_whisper SHARED whisper_jni.cpp)
target_link_libraries(kairoify_whisper PRIVATE whisper log)
if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_options(kairoify_whisper PRIVATE -O3)
endif ()
target_link_options(kairoify_whisper PRIVATE -Wl,-z,max-page-size=16384)
