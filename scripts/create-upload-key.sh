#!/usr/bin/env bash
# Creates the owner's upload key outside the repository. Run interactively; no secret is printed.
set -euo pipefail
store="${EARBY_UPLOAD_STORE:-$HOME/.config/earby/signing/upload.jks}"
alias_name="${EARBY_UPLOAD_KEY_ALIAS:-upload}"
if [[ -e "$store" ]]; then
  echo "Refusing to overwrite existing key: $store" >&2
  exit 1
fi
mkdir -p "$(dirname "$store")"
echo "Creating $store with alias $alias_name."
echo "You will be asked twice for a password (store, then key); use the same strong password"
echo "for both and store it in a password manager. The certificate identity below is fixed;"
echo "those fields have no functional effect on Play App Signing."
keytool -genkeypair -v \
  -keystore "$store" \
  -alias "$alias_name" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storetype PKCS12 \
  -dname "${EARBY_UPLOAD_DNAME:-CN=EarBy Upload Key, OU=EarBy, O=EarBy, C=GB}"
echo "Created $store. Back it up securely; never commit it."
echo "Record the upload certificate SHA-256 for EARBY_UPLOAD_CERT_SHA256:"
echo "  keytool -list -v -keystore \"$store\" -alias \"$alias_name\" | sed -n 's/^[[:space:]]*SHA256: //p' | head -1"
