#!/usr/bin/env bash
# Host fast checks. `--fresh` forces the Gradle test/build tasks to rerun instead of being
# UP-TO-DATE, which the M11 final execution claim needs; the default stays incremental.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
cd "$EAR_ROOT"
if [[ $# -gt 1 || ( $# == 1 && "$1" != --fresh ) ]]; then
    echo 'Usage: check-fast.sh [--fresh]' >&2
    exit 2
fi
fresh=()
if [[ "${1:-}" == "--fresh" ]]; then
    fresh=(--rerun-tasks)
fi
python3 -m unittest discover -s scripts/tests
scripts/check-audio-tools.sh
scripts/check-music-fixtures.sh
python3 scripts/check-docs.py
python3 scripts/check-voice-durations.py
python3 scripts/check-architecture.py
scripts/check-env.sh --build
python3 scripts/check-verification.py
./gradlew "${fresh[@]}" --dependency-verification strict spotlessCheck :core:music:test :core:training:test :core:audio:test :core:media:test :core:data:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
scripts/check-native.sh
