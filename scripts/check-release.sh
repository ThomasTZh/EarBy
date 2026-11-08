#!/usr/bin/env bash
# Verifies a signed APK or AAB against the public release identity.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
cd "$EAR_ROOT"
[[ $# == 1 ]] || { echo 'Usage: scripts/check-release.sh <apk-or-aab>' >&2; exit 2; }
file="$1"
[[ -f "$file" ]] || { echo "FAIL: missing $file" >&2; exit 2; }
: "${EARBY_UPLOAD_CERT_SHA256:?Set EARBY_UPLOAD_CERT_SHA256 to the upload certificate SHA-256}"
build_tools="$ANDROID_HOME/build-tools/36.0.0"
expected_cert="$(printf '%s' "$EARBY_UPLOAD_CERT_SHA256" | tr -d ':' | tr '[:upper:]' '[:lower:]')"
require_upload_certificate() {
    local actual
    actual="$(printf '%s' "$1" | tr -d ':' | tr '[:upper:]' '[:lower:]')"
    if [[ -z "$actual" ]]; then
        echo 'FAIL: could not read the signing certificate' >&2
        return 1
    fi
    if [[ "$actual" != "$expected_cert" ]]; then
        echo "FAIL: signing certificate $actual does not match the upload key" >&2
        return 1
    fi
    echo 'certificate: upload key fingerprint verified'
}
case "$file" in
  *.apk)
    scripts/check-apk.sh "$file"
    cert="$("$build_tools/apksigner" verify --print-certs "$file" |
        sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -1)"
    require_upload_certificate "$cert"
    "$build_tools/aapt2" dump badging "$file" | grep -q "package: name='com.earby.app' versionCode='100' versionName='1.0.0'"
    ;;
  *.aab)
    verify_file="$(mktemp)"
    trap 'rm -f "$verify_file"' EXIT
    jarsigner -verify -certs "$file" | tee "$verify_file"
    grep -q "jar verified" "$verify_file"
    cert="$(keytool -printcert -jarfile "$file" |
        sed -n 's/^[[:space:]]*SHA256: //p' | head -1)"
    require_upload_certificate "$cert"
    unzip -l "$file" | grep -q 'base/manifest/AndroidManifest.xml'
    ;;
  *) echo 'FAIL: expected .apk or .aab' >&2; exit 2 ;;
esac
echo "PASS: $file has the EarBy 1.0.0 release identity"
