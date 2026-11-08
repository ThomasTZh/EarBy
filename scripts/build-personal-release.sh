#!/usr/bin/env bash
# Builds the signed standalone APK. Signing inputs stay outside the repository.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
cd "$EAR_ROOT"
: "${EARBY_UPLOAD_STORE:?Set EARBY_UPLOAD_STORE to the upload keystore path}"
: "${EARBY_UPLOAD_STORE_PASSWORD:?Set EARBY_UPLOAD_STORE_PASSWORD}"
: "${EARBY_UPLOAD_KEY_ALIAS:?Set EARBY_UPLOAD_KEY_ALIAS}"
: "${EARBY_UPLOAD_KEY_PASSWORD:?Set EARBY_UPLOAD_KEY_PASSWORD}"
: "${EARBY_UPLOAD_CERT_SHA256:?Set EARBY_UPLOAD_CERT_SHA256 to the upload certificate SHA-256}"
mkdir -p dist/release
./gradlew --dependency-verification strict :app:assembleRelease
apk=app/build/outputs/apk/release/app-release.apk
out=dist/release/EarBy-1.0.0.apk
cp "$apk" "$out"
apksigner="$ANDROID_HOME/build-tools/36.0.0/apksigner"
"$apksigner" verify --print-certs "$out"
scripts/check-release.sh "$out"
sha256sum "$out"
