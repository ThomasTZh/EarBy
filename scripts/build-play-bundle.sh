#!/usr/bin/env bash
# Builds the signed Play AAB. Signing inputs stay outside the repository.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
cd "$EAR_ROOT"
: "${EARBY_UPLOAD_STORE:?Set EARBY_UPLOAD_STORE to the upload keystore path}"
: "${EARBY_UPLOAD_STORE_PASSWORD:?Set EARBY_UPLOAD_STORE_PASSWORD}"
: "${EARBY_UPLOAD_KEY_ALIAS:?Set EARBY_UPLOAD_KEY_ALIAS}"
: "${EARBY_UPLOAD_KEY_PASSWORD:?Set EARBY_UPLOAD_KEY_PASSWORD}"
: "${EARBY_UPLOAD_CERT_SHA256:?Set EARBY_UPLOAD_CERT_SHA256 to the upload certificate SHA-256}"
mkdir -p dist/play
./gradlew --dependency-verification strict :app:bundleRelease
bundle=app/build/outputs/bundle/release/app-release.aab
out=dist/play/EarBy-1.0.0.aab
cp "$bundle" "$out"
verify_file="$(mktemp)"
trap 'rm -f "$verify_file"' EXIT
jarsigner -verify -certs "$out" | tee "$verify_file"
grep -q "jar verified" "$verify_file"
scripts/check-release.sh "$out"
sha256sum "$out"
