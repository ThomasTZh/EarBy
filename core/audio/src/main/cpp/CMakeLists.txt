cmake_minimum_required(VERSION 3.22.1)
project(passiveeartraining_audio C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

if(NOT DEFINED MINIAUDIO_SOURCE_DIR OR NOT IS_DIRECTORY "${MINIAUDIO_SOURCE_DIR}")
    message(FATAL_ERROR
        "MINIAUDIO_SOURCE_DIR is not a directory (${MINIAUDIO_SOURCE_DIR}). "
        "Run scripts/fetch-native-sources.sh first; the build never downloads sources.")
endif()

# miniaudio supplies WAV decoding, format conversion and WAV encoding only. Device I/O is
# disabled because AudioTrack is the single owner of device output.
# miniaudio is a single-header library: one translation unit defines MA_IMPLEMENTATION and the
# rest of the native code links against it. The header is exposed as an include directory.
add_library(miniaudio INTERFACE)
target_include_directories(miniaudio INTERFACE "${MINIAUDIO_SOURCE_DIR}")

# The only renderer is the in-project EARP1 sample player. It reads the pack written by
# synthNotes/tools/build_sample_pack.py and keeps the ear_renderer_* contract used by JNI.
add_library(ear_audio STATIC
    error.cpp
    sample_player.cpp
    wav_io.cpp)
target_include_directories(ear_audio PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}")
target_link_libraries(ear_audio PUBLIC miniaudio)
if(ANDROID)
    target_link_libraries(ear_audio PUBLIC log)
endif()

# Optional host harness: renders WAV fixtures with the same renderer sources for offline
# numeric analysis. Enabled only by scripts/check-native-tests.sh, never by the Android build.
option(EAR_AUDIO_HOST_HARNESS "Build the host WAV render harness" OFF)
if(EAR_AUDIO_HOST_HARNESS AND NOT ANDROID)
    enable_testing()
    get_filename_component(EAR_PROJECT_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../.." ABSOLUTE)
    set(EAR_PACK "${EAR_PROJECT_ROOT}/app/src/main/assets/soundfont/earby-string-pack.bin")

    add_executable(ear_wav_contract_test wav_contract_test.cpp)
    target_link_libraries(ear_wav_contract_test PRIVATE ear_audio)
    add_test(NAME wav_contract COMMAND ear_wav_contract_test)

    add_executable(ear_renderer_reset_test renderer_reset_test.cpp)
    target_link_libraries(ear_renderer_reset_test PRIVATE ear_audio)
    add_test(NAME renderer_reset COMMAND ear_renderer_reset_test "${EAR_PACK}")

    add_executable(ear_render_harness host_render_main.cpp)
    target_link_libraries(ear_render_harness PRIVATE ear_audio)

    add_executable(ear_sample_player_test sample_player_test.cpp)
    target_link_libraries(ear_sample_player_test PRIVATE ear_audio)
    add_test(NAME sample_player COMMAND ear_sample_player_test "${EAR_PACK}")

    add_executable(ear_sample_player_bench sample_player_bench.cpp)
    target_link_libraries(ear_sample_player_bench PRIVATE ear_audio)
endif()

# One shared library owns the JNI surface. No JNI access happens from Compose, and the Kotlin
# owner wrapper serializes create/load/render/reset/close through a single worker.
if(ANDROID)
    add_library(ear_audio_jni SHARED
        jni_library.cpp)
    target_link_libraries(ear_audio_jni PRIVATE ear_audio)
    set_target_properties(ear_audio_jni PROPERTIES OUTPUT_NAME "ear_audio")
endif()
