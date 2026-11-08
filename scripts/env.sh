#!/usr/bin/env bash
# Source this file to select project tools without changing system Java.
export EAR_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$EAR_ROOT/.local/gradle-home}"
# Keep uv's cache and managed Python inside the project so automated checks never need
# writes outside the workspace (sandboxed/read-only home caches otherwise break them).
export UV_CACHE_DIR="${UV_CACHE_DIR:-$EAR_ROOT/.local/uv-cache}"
export UV_PYTHON_INSTALL_DIR="${UV_PYTHON_INSTALL_DIR:-$EAR_ROOT/.local/uv-python}"
# The system compilers are ccache wrappers; without these the cache/config writes fail in a
# read-only home and every native compile stops. XDG_DATA_HOME keeps the Kotlin daemon's
# client marker inside the project too.
export CCACHE_DIR="${CCACHE_DIR:-$EAR_ROOT/.local/ccache}"
export CCACHE_CONFIGPATH="${CCACHE_CONFIGPATH:-$CCACHE_DIR/ccache.conf}"
export XDG_DATA_HOME="${XDG_DATA_HOME:-$EAR_ROOT/.local/xdg-data}"
mkdir -p "$CCACHE_DIR" "$XDG_DATA_HOME"
if [[ -x "$EAR_ROOT/.local/jdk-17/bin/javac" ]]; then
    export JAVA_HOME="$EAR_ROOT/.local/jdk-17"
fi
export ANDROID_HOME="${ANDROID_HOME:-$EAR_ROOT/.local/android-sdk}"
if [[ -n "${JAVA_HOME:-}" ]]; then
    export PATH="$JAVA_HOME/bin:$PATH"
fi
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/19.0/bin:$ANDROID_HOME/emulator:$PATH"
