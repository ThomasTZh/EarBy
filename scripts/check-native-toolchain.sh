#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
cmake="$ANDROID_HOME/cmake/3.22.1/bin/cmake"
ninja="$ANDROID_HOME/cmake/3.22.1/bin/ninja"
ndk="$ANDROID_HOME/ndk/28.2.13676358"
[[ -x "$cmake" && -x "$ninja" && -f "$ndk/build/cmake/android.toolchain.cmake" ]] || { echo 'Install SDK CMake and NDK with scripts/setup-android.sh' >&2; exit 2; }
staging="$(mktemp -d)"
trap 'rm -rf -- "$staging"' EXIT
cat > "$staging/CMakeLists.txt" <<'CMAKE'
cmake_minimum_required(VERSION 3.22)
project(toolchain_probe LANGUAGES C CXX)
add_library(toolchain_probe SHARED probe.cpp)
CMAKE
printf '%s\n' 'extern "C" int toolchain_probe() { return 0; }' > "$staging/probe.cpp"
for abi in arm64-v8a x86_64; do
    "$cmake" -S "$staging" -B "$staging/$abi" -G Ninja \
        -DCMAKE_MAKE_PROGRAM="$ninja" \
        -DCMAKE_TOOLCHAIN_FILE="$ndk/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$abi" -DANDROID_PLATFORM=android-29
    "$cmake" --build "$staging/$abi"
done
echo 'PASS: NDK compilation for arm64-v8a and x86_64. No audio runtime has been tested.'
