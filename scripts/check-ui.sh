#!/usr/bin/env bash
# Read-only comparison against the complete owner-approved image set.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
cd "$EAR_ROOT"
if [[ $# != 0 ]]; then
    echo 'Usage: scripts/check-ui.sh (no recording tasks or overrides accepted)' >&2
    exit 2
fi
python3 scripts/screenshot-contract.py app/src/test/screenshots
./gradlew --dependency-verification strict :app:verifyRoborazziDebug -Pear.ui.fresh=true
