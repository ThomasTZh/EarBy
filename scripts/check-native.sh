#!/usr/bin/env bash
# Verifies the Android native build of core:audio.
#
# It compiles the same renderer sources for both shipped ABIs with strict dependency
# verification, then inspects the packaged shared libraries: every library in the AAR must be
# one of ours, present for each supported ABI, and page-aligned for 16 KiB page-size devices.
#
# The host GoogleTest renderer suite is built by scripts/check-native-tests.sh; this script
# checks packaging and ABI/alignment, which a compiler probe alone does not.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
cd "$EAR_ROOT"

ABIS=(arm64-v8a x86_64)
fail=0

echo 'Building core:audio native library for every shipped ABI'
./gradlew --dependency-verification strict --offline :core:audio:assembleDebug

AAR='core/audio/build/outputs/aar/audio-debug.aar'
if [[ ! -f "$AAR" ]]; then
    echo "FAIL: $AAR was not produced" >&2
    exit 1
fi

mapfile -t packaged < <(unzip -Z1 "$AAR" | grep -E '^jni/.*\.so$' | sort)
if [[ ${#packaged[@]} -eq 0 ]]; then
    echo "FAIL: the AAR contains no shared library" >&2
    exit 1
fi

for entry in "${packaged[@]}"; do
    if [[ "$entry" != 'jni/'*'/libear_audio.so' ]]; then
        echo "FAIL: unexpected native library in the AAR: $entry" >&2
        fail=1
    fi
done
for abi in "${ABIS[@]}"; do
    if ! printf '%s\n' "${packaged[@]}" | grep -qx "jni/$abi/libear_audio.so"; then
        echo "FAIL: missing libear_audio.so for $abi" >&2
        fail=1
    fi
done

workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT
unzip -q -o "$AAR" 'jni/*' -d "$workdir"

READELF="$ANDROID_HOME/ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
if [[ ! -x "$READELF" ]]; then
    echo "FAIL: NDK llvm-readelf not found at $READELF" >&2
    exit 1
fi

for abi in "${ABIS[@]}"; do
    library="$workdir/jni/$abi/libear_audio.so"
    echo "--- $abi"
    # Every shared dependency must resolve inside the APK or the platform, never /usr/lib.
    if "$READELF" -d "$library" | grep -E 'NEEDED' | grep -qvE 'liblog\.so|libc\.so|libm\.so|libdl\.so|libstdc\+\+\.so|libc\+\+_shared\.so'; then
        echo "FAIL: $abi has an unexpected shared dependency:" >&2
        "$READELF" -d "$library" | grep -E 'NEEDED' >&2
        fail=1
    fi
    # 16 KiB page-size compatibility requires 2**14 alignment on every LOAD segment.
    while read -r align; do
        if [[ "$align" -lt 16384 ]]; then
            echo "FAIL: $abi LOAD segment alignment $align is below 16384" >&2
            fail=1
        fi
    done < <("$READELF" -l "$library" | awk '/LOAD/ {print $NF}' | sort -u)
    "$READELF" -l "$library" | awk '/LOAD/ {print "  LOAD align " $NF}' | sort -u
done

ZIPALIGN="$ANDROID_HOME/build-tools/36.0.0/zipalign"
if [[ -x "$ZIPALIGN" ]]; then
    "$ZIPALIGN" -c -P 16 -v 4 "$AAR" >/dev/null 2>&1 && echo 'zipalign: AAR is 16 KiB aligned' || {
        echo 'FAIL: zipalign reported a 16 KiB alignment problem in the AAR' >&2
        fail=1
    }
else
    echo 'BLOCKED: SDK zipalign is unavailable; APK alignment is checked in check-full' >&2
fi

exit "$fail"
