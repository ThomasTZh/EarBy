#!/usr/bin/env bash
# Runs the Python asset/audio tooling tests in the locked analysis environment.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
export UV_CACHE_DIR="${UV_CACHE_DIR:-$EAR_ROOT/.local/uv-cache}"
export UV_PYTHON_INSTALL_DIR="${UV_PYTHON_INSTALL_DIR:-$EAR_ROOT/.local/uv-python}"
export TMPDIR="${EAR_TMPDIR:-$EAR_ROOT/.local/tmp}"
mkdir -p "$UV_CACHE_DIR" "$UV_PYTHON_INSTALL_DIR" "$TMPDIR"
# The synthNotes tools are standard-library only: the SoundFont writer/validator and the EARP1
# pack generator's refusal and determinism tests.
cd "$EAR_ROOT"
python3 -m unittest discover -s synthNotes/tools/tests
cd "$EAR_ROOT/tools/audio-analysis"
uv run python -m unittest discover -s tests
