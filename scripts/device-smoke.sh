#!/usr/bin/env bash
# The isolated review application is the only app this workflow installs or stops.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
cd "$EAR_ROOT"
if [[ $# == 0 ]]; then
    echo 'Usage: device-smoke.sh --automated-only [--out DIR] | --smoke-only [--out DIR]' >&2
    exit 2
fi
exec python3 scripts/review-device.py "$@"
