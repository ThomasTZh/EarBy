#!/usr/bin/env bash
# Downloads, verifies and stages the pinned native sources for core:audio.
# Explicit dependency operation: it records missing SHA-256 values and never runs during a build.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
cd "$EAR_ROOT"
python3 tools/native/fetch_native_sources.py --destination .local/native-sources --record "$@"
