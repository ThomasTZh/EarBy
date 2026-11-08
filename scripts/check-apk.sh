#!/usr/bin/env bash
# Inspects a packaged APK: permissions, native libraries and the merged manifest's own claims.
#
# The product contract is that no installed variant may request INTERNET. A dependency can add it
# through a merged manifest, so this inspects the packaged artifact rather than the source.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"

if [[ $# -lt 1 || ! -f "$1" ]]; then
    echo 'Usage: check-apk.sh path/to/app.apk [more.apk ...]' >&2
    exit 2
fi

aapt="$ANDROID_HOME/build-tools/36.0.0/aapt2"
zipalign="$ANDROID_HOME/build-tools/36.0.0/zipalign"
[[ -x "$aapt" ]] || { echo 'Build Tools 36.0.0 are missing' >&2; exit 2; }

fail=0
for apk in "$@"; do
    echo "--- $apk"
    # The product is offline by contract. A network permission reaching the package - directly or
    # through a merged dependency manifest such as Media3's - fails the gate.
    permissions="$("$aapt" dump permissions "$apk")"
    if grep -Eq 'permission.(INTERNET|ACCESS_NETWORK_STATE)' <<< "$permissions"; then
        echo "FAIL: $apk declares a network permission" >&2
        grep -E 'permission.(INTERNET|ACCESS_NETWORK_STATE)' <<< "$permissions" >&2
        fail=1
    else
        echo 'permissions: no network permission'
    fi

    # The packaged label and launcher icon are part of the product identity. The release build
    # shortens resource paths, so the icon is checked as the packaged entry the badging reports,
    # not by its debug path name.
    badging="$("$aapt" dump badging "$apk")"
    case "$apk" in
        *review*) expected_label='EarBy (Review)' ;;
        *) expected_label='EarBy' ;;
    esac
    icon_path="$(sed -n "s/^application: label='[^']*' icon='\([^']*\)'$/\1/p" <<< "$badging" | head -1)"
    if grep -q "^application-label:'$expected_label'$" <<< "$badging" &&
        grep -q "^application: label='$expected_label' icon='" <<< "$badging" &&
        [[ -n "$icon_path" ]] && grep -qx "$icon_path" < <(unzip -Z1 "$apk"); then
        echo "label/icon: '$expected_label' with packaged icon $icon_path"
    else
        echo "FAIL: $apk does not carry label '$expected_label' and a packaged launcher icon" >&2
        fail=1
    fi

    # Backup contract: automatic backup is off, and both extraction rule files are packaged so a
    # device transfer is explicitly excluded and can be explained to the user.
    manifest_tree="$("$aapt" dump xmltree --file AndroidManifest.xml "$apk")"
    if grep -q 'allowBackup(0x01010280)=false' <<< "$manifest_tree" &&
        grep -q 'fullBackupContent' <<< "$manifest_tree" &&
        grep -q 'dataExtractionRules' <<< "$manifest_tree"; then
        echo 'backup: automatic backup off with explicit extraction rules'
    else
        echo "FAIL: $apk is missing allowBackup=false or an extraction rule" >&2
        fail=1
    fi

    # Every packaged shared library must be one we built; a stray library means an unexpected
    # dependency or an upstream artifact that slipped into the build.
    mapfile -t libs < <(unzip -Z1 "$apk" | grep -E '^lib/.*\.so$' | sort -u)
    for entry in "${libs[@]}"; do
        case "$entry" in
            lib/arm64-v8a/*|lib/x86_64/*) ;;
            *)
                echo "FAIL: unexpected native ABI in $apk: $entry" >&2
                fail=1
                ;;
        esac
        case "$entry" in
            */libear_audio.so|*/libandroidx.graphics.path.so) ;;
            *)
                echo "FAIL: unexpected native library in $apk: $entry" >&2
                fail=1
                ;;
        esac
    done
    echo "native: libear_audio.so packaged for $(printf '%s\n' "${libs[@]}" | grep -c 'libear_audio.so') ABI(s)"
    for abi in arm64-v8a x86_64; do
        if ! printf '%s\n' "${libs[@]}" | grep -q "lib/$abi/libear_audio.so"; then
            echo "FAIL: $apk is missing libear_audio.so for $abi" >&2
            fail=1
        fi
    done

    # 16 KiB page-size devices require 16 KiB-aligned uncompressed entries.
    if [[ -x "$zipalign" ]]; then
        if "$zipalign" -c -P 16 -v 4 "$apk" >/dev/null 2>&1; then
            echo 'zipalign: 16 KiB alignment verified'
        else
            echo "FAIL: zipalign reported an alignment problem in $apk" >&2
            fail=1
        fi
    fi

    # The bundled assets are product inputs; the EARP1 pack replaced the SoundFont, which is now
    # only a tracked generator input under synthNotes/. A stray SoundFont in the package fails.
    listing="$(unzip -Z1 "$apk")"
    for asset in assets/soundfont/earby-string-pack.bin assets/voice/voice-pack.json; do
        if grep -qx "$asset" <<< "$listing"; then
            echo "assets: $asset present"
        else
            echo "FAIL: $apk is missing $asset" >&2
            fail=1
        fi
    done
    if grep -E '^assets/.*\.(sf2|sf3)$' <<< "$listing" >/dev/null; then
        echo "FAIL: $apk contains a stray SoundFont asset:" >&2
        grep -E '^assets/.*\.(sf2|sf3)$' <<< "$listing" >&2
        fail=1
    else
        echo 'assets: no stray SoundFont packaged'
    fi

    # Read actual DEX entries; dexdump cannot inspect an APK as a DEX file.
    if [[ "$apk" == *release* ]]; then
        dex_strings="$(unzip -p "$apk" 'classes*.dex' | strings)"
        if grep -E 'DiagnosticsScreen|ReviewProbe|ReviewApplication' <<< "$dex_strings" > /dev/null; then
            echo "FAIL: $apk contains debug/review-only code" >&2
            fail=1
        else
            echo 'debug/review-only code: absent from release DEX entries'
        fi
        # JNI_OnLoad finds these classes by source name. R8 must keep both (proguard-rules.pro);
        # if it renames or merges them the native typed exceptions silently disappear.
        for jni_class in \
            com/thomas/passiveeartraining/audio/NativeAudioException \
            com/thomas/passiveeartraining/audio/RendererClosedException; do
            if grep -Fq "$jni_class" <<< "$dex_strings"; then
                echo "JNI class kept: $jni_class"
            else
                echo "FAIL: $apk DEX is missing JNI class $jni_class (check proguard-rules.pro)" >&2
                fail=1
            fi
        done
    elif [[ "$apk" == *debug* ]]; then
        # Debug legitimately contains the diagnostics screen, but never the isolated review hooks.
        if unzip -p "$apk" 'classes*.dex' | strings | grep -E 'ReviewProbe|ReviewApplication' > /dev/null; then
            echo "FAIL: $apk contains review-only code" >&2
            fail=1
        else
            echo 'review-only code: absent from debug DEX entries'
        fi
    fi
done

exit "$fail"
