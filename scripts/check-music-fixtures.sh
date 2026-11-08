#!/usr/bin/env bash
# Regenerate the music21 fixtures into a temp directory and compare with the committed files.
# Read-only: the committed fixtures are never rewritten by an ordinary check.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
cd "$EAR_ROOT"
if [[ $# != 0 ]]; then
    echo 'Usage: scripts/check-music-fixtures.sh (no arguments; generation writes to a temp dir)' >&2
    exit 2
fi
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
(
    cd tools/music-fixtures
    uv run python generate.py --out "$OUT"
)
for fixture in chords.json scales.json; do
    if ! diff -u "core/music/src/test/resources/music-fixtures/$fixture" "$OUT/$fixture"; then
        echo "FAIL: $fixture differs from the committed fixture; regenerate explicitly and review" >&2
        exit 1
    fi
done
echo 'PASS: music21 fixtures match the committed files'
