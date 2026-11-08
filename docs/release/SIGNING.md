# Release signing

The upload key is deliberately outside this repository.

Recommended location: `~/.config/earby/signing/upload.jks`.

Generate a new 4096-bit RSA upload key once, interactively, and store its passwords in a password
manager:

```bash
mkdir -p ~/.config/earby/signing
keytool -genkeypair -v \
  -keystore ~/.config/earby/signing/upload.jks \
  -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

Then export the signing values for the build shell. `EARBY_UPLOAD_CERT_SHA256` is the upload
certificate fingerprint that `scripts/check-release.sh` requires:

```bash
export EARBY_UPLOAD_STORE="$HOME/.config/earby/signing/upload.jks"
export EARBY_UPLOAD_STORE_PASSWORD='...'
export EARBY_UPLOAD_KEY_ALIAS='upload'
export EARBY_UPLOAD_KEY_PASSWORD='...'
export EARBY_UPLOAD_CERT_SHA256="$(
  keytool -list -v -keystore "$EARBY_UPLOAD_STORE" -alias "$EARBY_UPLOAD_KEY_ALIAS" \
    -storepass "$EARBY_UPLOAD_STORE_PASSWORD" |
    sed -n 's/^[[:space:]]*SHA256: //p' | head -1
)"
```

The fingerprint may be entered with or without colons; the comparison is case-insensitive.

Build:

```bash
scripts/build-personal-release.sh
scripts/build-play-bundle.sh
```

Both build scripts call `scripts/check-release.sh` and fail if the artifact is not signed by the
fingerprint in `EARBY_UPLOAD_CERT_SHA256`. To check an existing artifact directly:

```bash
scripts/check-release.sh dist/release/EarBy-1.0.0.apk
scripts/check-release.sh dist/play/EarBy-1.0.0.aab
```

Enroll in Play App Signing on the first upload. Back up the upload key and its passwords in two
secure places. Losing the upload key can block uploads; Play App Signing recovery is not
guaranteed to preserve the same local signing identity.

A sideloaded APK signed with the upload key has a different signature from the Play-distributed
app. Do not switch installation sources for the same package without uninstalling.

Never commit the keystore, passwords or a populated `keystore.properties`.
