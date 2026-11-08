#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
cd "$EAR_ROOT"
scripts/check-env.sh --build
python3 scripts/check-verification.py
command -v adb >/dev/null || { echo 'ADB missing; run scripts/setup-android.sh' >&2; exit 2; }
adb get-state >/dev/null
./gradlew --dependency-verification strict :app:assembleDebug
scripts/check-apk.sh app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n com.earby.app.debug/com.thomas.passiveeartraining.MainActivity
