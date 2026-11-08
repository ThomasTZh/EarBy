#!/usr/bin/env bash
# Builds the host render harness from the same renderer sources and renders the standard WAV
# fixtures, then checks them numerically with the Python analysis tools.
#
# The harness needs the packaged EARP1 pack. If it is missing, this script reports BLOCKED
# instead of pretending the render path was verified.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
cd "$EAR_ROOT"

PACK="${EAR_SAMPLE_PACK:-app/src/main/assets/soundfont/earby-string-pack.bin}"
if [[ ! -f "$PACK" ]]; then
    echo "BLOCKED: no EARP1 pack at $PACK; the host render fixtures cannot run yet." >&2
    echo "         Run synthNotes/tools/build_sample_pack.py and see docs/audio/ASSETS.md." >&2
    exit 2
fi

# The pack is generated from the tracked, hash-pinned SoundFont. Refuse a stale pack and a
# descriptor that disagrees with it before compiling the player.
python3 synthNotes/tools/build_sample_pack.py --check

BUILD_DIR=".local/native-host-build"
cmake -S core/audio/src/main/cpp -B "$BUILD_DIR" -G Ninja -DCMAKE_BUILD_TYPE=Release \
    -DEAR_AUDIO_HOST_HARNESS=ON \
    -DMINIAUDIO_SOURCE_DIR="$EAR_ROOT/.local/native-sources/miniaudio" >/dev/null
cmake --build "$BUILD_DIR" --target ear_render_harness ear_wav_contract_test ear_renderer_reset_test \
    ear_sample_player_test ear_sample_player_bench >/dev/null

ctest --test-dir "$BUILD_DIR" -R "^(wav_contract|renderer_reset|sample_player)$" --no-tests=error --output-on-failure
# The sample player test also runs directly so its per-check lines are visible in the log.
"$BUILD_DIR/ear_sample_player_test" "$PACK"

OUT="${EAR_NATIVE_EVIDENCE_DIR:-.local/verification/project-review-2026-10-11/native-fixtures}"
mkdir -p "$OUT"
"$BUILD_DIR/ear_render_harness" "$PACK" "$OUT"
# The analysis tools live in a locked uv project (numpy/scipy/soundfile/jsonschema).
export UV_CACHE_DIR="${UV_CACHE_DIR:-$EAR_ROOT/.local/uv-cache}"
export UV_PYTHON_INSTALL_DIR="${UV_PYTHON_INSTALL_DIR:-$EAR_ROOT/.local/uv-python}"
export TMPDIR="${EAR_TMPDIR:-$EAR_ROOT/.local/tmp}"
mkdir -p "$UV_CACHE_DIR" "$UV_PYTHON_INSTALL_DIR" "$TMPDIR"
(cd tools/audio-analysis && uv run python check_render_fixtures.py --dir "../../$OUT")
