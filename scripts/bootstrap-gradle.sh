#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
version=9.6.0
if [[ -f "$EAR_ROOT/gradle/wrapper/gradle-wrapper.jar" ]]; then
    echo 'Wrapper already exists. Use the Gradle wrapper task for deliberate upgrades.'
    exit 0
fi
command -v javac >/dev/null || { echo 'Install JDK 17 first: scripts/setup-android.sh' >&2; exit 2; }
java -version 2>&1 | head -1 | grep -Eq 'version "17\.' || { echo 'Select JDK 17 first.' >&2; exit 2; }
staging="$(mktemp -d)"
trap 'rm -rf -- "$staging"' EXIT
url="https://services.gradle.org/distributions/gradle-$version-bin.zip"
curl --fail --location --retry 2 --connect-timeout 15 --max-time 30 "$url.sha256" -o "$staging/checksum"
checksum="$(tr -d '[:space:]' < "$staging/checksum")"
[[ "$checksum" =~ ^[0-9a-f]{64}$ ]] || { echo 'Invalid Gradle checksum response' >&2; exit 1; }
curl --fail --location --retry 2 --connect-timeout 15 "$url" -o "$staging/gradle.zip"
printf '%s  %s\n' "$checksum" "$staging/gradle.zip" | sha256sum --check -
unzip -q "$staging/gradle.zip" -d "$staging"
mkdir "$staging/wrapper-project"
printf '%s\n' 'rootProject.name = "wrapper-bootstrap"' > "$staging/wrapper-project/settings.gradle.kts"
"$staging/gradle-$version/bin/gradle" --no-daemon -p "$staging/wrapper-project" wrapper \
    --gradle-version "$version" --distribution-type bin --gradle-distribution-sha256-sum "$checksum"
cp "$staging/wrapper-project/gradlew" "$staging/wrapper-project/gradlew.bat" "$EAR_ROOT/"
cp "$staging/wrapper-project/gradle/wrapper/gradle-wrapper.jar" "$staging/wrapper-project/gradle/wrapper/gradle-wrapper.properties" "$EAR_ROOT/gradle/wrapper/"
chmod +x "$EAR_ROOT/gradlew"
echo 'Official wrapper generated. Review and commit scripts, JAR and checksum properties.'
