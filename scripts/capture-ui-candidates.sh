#!/usr/bin/env bash
# Generate review candidates; never approve or write to the baseline directory.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
cd "$EAR_ROOT"
OUT="${1:-.local/verification/project-review-2026-10-11/candidates}"
OUT="$(realpath -m "$OUT")"
BASELINE="$(realpath app/src/test/screenshots)"
if [[ "$OUT" == "$BASELINE" || "$OUT" == "$BASELINE/"* ]]; then
    echo 'Refusing to generate candidates in the approved baseline directory' >&2
    exit 2
fi
mkdir -p "$OUT"
./gradlew --dependency-verification strict :app:recordRoborazziDebug \
    "-Pear.screenshots.dir=$OUT" -Pear.ui.fresh=true
python3 scripts/screenshot-contract.py "$OUT"
