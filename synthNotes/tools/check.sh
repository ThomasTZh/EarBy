#!/usr/bin/env bash
# Focused checks for the synthNotes export. The MATLAB suite is separate:
#   runtests('tests')   in the MATLAB desktop.
set -euo pipefail
cd "$(dirname "$0")/.."

python3 -m unittest discover -s tools/tests
python3 tools/validate_sf2.py out/string_synth.sf2 \
    --wav-dir out/audio/notes --mapping out/mapping.json
