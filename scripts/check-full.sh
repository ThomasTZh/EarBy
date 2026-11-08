#!/usr/bin/env bash
# Full integration defaults to the isolated, automated-only physical-phone workflow.
# `--fresh` forces the Gradle test/build tasks to rerun instead of being UP-TO-DATE and is
# forwarded to check-fast.sh; it exists for the M11 final execution claim. The default stays
# incremental.
# `--skip-endurance` excludes only the real 30-minute screen-off endurance stage; the remaining
# isolated-phone suites still run and the driver records the exclusion in its report. It exists
# for scripts/qa.sh, which cites the recorded M11 endurance evidence instead of repeating it.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
cd "$EAR_ROOT"
fresh=()
skip_endurance=false
for arg in "$@"; do
    case "$arg" in
        --automated-only) ;;
        --fresh) fresh=(--rerun-tasks) ;;
        --skip-endurance) skip_endurance=true ;;
        *)
            echo 'Usage: check-full.sh [--automated-only] [--fresh] [--skip-endurance]' >&2
            exit 2
            ;;
    esac
done
if [[ ${#fresh[@]} -gt 0 ]]; then
    scripts/check-fast.sh --fresh
else
    scripts/check-fast.sh
fi
scripts/check-assets.sh
scripts/check-native-tests.sh
# Screenshots: the strict owner gate is scripts/check-ui.sh; the automated full run generates
# candidates and reports declared PENDING_VISUAL_APPROVAL cases without approving anything.
UI_CANDIDATES="${EAR_UI_CANDIDATES_DIR:-.local/verification/ui-candidates}"
scripts/capture-ui-candidates.sh "$UI_CANDIDATES"
python3 scripts/screenshot-report.py --candidates "$UI_CANDIDATES" --report-only
./gradlew "${fresh[@]}" --dependency-verification strict :app:assembleDebug :app:assembleRelease :app:lintRelease :app:lintDebug
./gradlew "${fresh[@]}" --dependency-verification strict -Pear.device.variant=review \
    :app:assembleReview :app:assembleReviewAndroidTest \
    :core:data:assembleDebugAndroidTest :core:audio:assembleDebugAndroidTest :app:lintReview
scripts/check-apk.sh \
    app/build/outputs/apk/debug/app-debug.apk \
    app/build/outputs/apk/release/app-release-unsigned.apk \
    app/build/outputs/apk/review/app-review.apk
scripts/check-native-toolchain.sh
# The driver's --smoke-only mode is the same bounded isolated-phone suite without the endurance
# stage, so the exclusion below is explicit in the device report.
if [[ "$skip_endurance" == true ]]; then
    scripts/device-smoke.sh --smoke-only --skip-build \
        --out "${EAR_REVIEW_OUT:-.local/verification/project-review-2026-10-11/device}"
else
    scripts/device-smoke.sh --automated-only --skip-build \
        --out "${EAR_REVIEW_OUT:-.local/verification/project-review-2026-10-11/device}"
fi
