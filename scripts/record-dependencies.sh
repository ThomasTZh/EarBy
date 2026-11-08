#!/usr/bin/env bash
# Deliberately mutating bootstrap, never part of routine verification.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
cd "$EAR_ROOT"
scripts/check-env.sh --build
# Cover every module's real configurations. New modules must be added here so their
# dependencies are recorded rather than discovered later as a verification failure.
./gradlew --write-verification-metadata sha256 \
    spotlessCheck :core:music:test :core:training:test :core:audio:test \
    :core:audio:assembleDebug :core:audio:assembleRelease :core:audio:assembleDebugAndroidTest \
    :core:media:test :core:data:test :core:data:assembleDebugAndroidTest \
    :app:test :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest \
    :app:lintDebug :app:lintRelease
python3 scripts/check-verification.py
printf '%s\n' 'Review gradle/verification-metadata.xml against trusted artifact sources before committing.' 'Then run scripts/check-full.sh in strict verification mode.'
