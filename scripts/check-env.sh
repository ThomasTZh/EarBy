#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
mode="${1:---all}"
[[ "$mode" == --all || "$mode" == --build ]] || { echo 'Usage: check-env.sh [--all|--build]' >&2; exit 2; }
missing=0
require_command() {
    if command -v "$1" >/dev/null; then
        printf 'OK      %s: %s\n' "$1" "$(command -v "$1")"
    else
        printf 'MISSING %s — %s\n' "$1" "$2"
        missing=1
    fi
}
require_path() {
    if [[ -e "$1" ]]; then printf 'OK      %s\n' "$1"; else printf 'MISSING %s — %s\n' "$1" "$2"; missing=1; fi
}
for command in git curl unzip python3; do require_command "$command" 'See docs/development/ENVIRONMENT.md'; done
require_command java 'Install/select JDK 17'
require_command javac 'Install/select a full JDK 17, not just a JRE'
if command -v java >/dev/null && command -v javac >/dev/null; then
    if ! java -version 2>&1 | head -1 | grep -Eq 'version "17\.' || ! javac -version 2>&1 | grep -Eq '^javac 17\.'; then
        echo 'MISSING consistent Java/Javac 17 — source scripts/env.sh after installing JDK 17'
        missing=1
    fi
fi
require_path "$ANDROID_HOME/platforms/android-36/android.jar" 'Run scripts/setup-android.sh'
require_path "$ANDROID_HOME/build-tools/36.0.0/aapt2" 'Run scripts/setup-android.sh'
require_path "$EAR_ROOT/gradle/wrapper/gradle-wrapper.jar" 'Run scripts/bootstrap-gradle.sh'
if [[ "$mode" == --all ]]; then
    for command in adb sdkmanager ffmpeg ffprobe cmake ninja gcc g++ pkg-config; do require_command "$command" 'See the environment guide'; done
    require_path "$ANDROID_HOME/ndk/28.2.13676358/build/cmake/android.toolchain.cmake" 'Run scripts/setup-android.sh'
    require_path "$ANDROID_HOME/cmake/3.22.1/bin/cmake" 'Run scripts/setup-android.sh'
    if [[ -r /dev/kvm && -w /dev/kvm ]]; then echo 'OK      KVM access'; else echo 'PENDING KVM inaccessible here; check access on the host'; missing=1; fi
    if command -v adb >/dev/null; then
        if devices="$(adb devices)" && grep -Eq $'\tdevice$' <<< "$devices"; then
            printf '%s\n' "$devices"
        else echo 'PENDING authorized ADB device'; missing=1; fi
    fi
    if git -C "$EAR_ROOT" rev-parse --show-toplevel >/dev/null 2>&1; then echo 'OK      Git repository'; else echo 'PENDING initialize Git on the host; do not overwrite protected metadata'; missing=1; fi
    echo 'MANUAL  Confirm Android Studio installation and matching Gradle JDK 17.'
fi
exit "$missing"
