#!/usr/bin/env bash
# Verifies the bundled voice pack, the SoundFont descriptor and the separator clip.
#
# Read-only and deterministic: it never rewrites an asset, a manifest or a hash. A machine pass
# does not mean the audio sounds right; human listening review stays an explicit open item and
# `--require-reviewed` turns a pending review into a failure.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
cd "$EAR_ROOT"

MANIFEST="app/src/main/assets/voice/voice-pack.json"
SOUNDFONT_MANIFEST="app/src/main/assets/soundfont/piano.json"
SEPARATOR_CLIP="app/src/main/assets/separator/original-separator.wav"

# --require-reviewed applies to every asset group: a pending listening review fails the check.
REVIEW_ARGS=()
for arg in "$@"; do
    if [[ "$arg" == "--require-reviewed" ]]; then
        REVIEW_ARGS+=("$arg")
    fi
done

export UV_CACHE_DIR="${UV_CACHE_DIR:-$EAR_ROOT/.local/uv-cache}"
export UV_PYTHON_INSTALL_DIR="${UV_PYTHON_INSTALL_DIR:-$EAR_ROOT/.local/uv-python}"
export TMPDIR="${EAR_TMPDIR:-$EAR_ROOT/.local/tmp}"
mkdir -p "$UV_CACHE_DIR" "$UV_PYTHON_INSTALL_DIR" "$TMPDIR"
run_validator() {
    (cd "$EAR_ROOT/tools/audio-analysis" && uv run python "$@")
}

fail=0

if [[ ! -f "$MANIFEST" ]]; then
    echo "BLOCKED: $MANIFEST does not exist yet; the reviewed voice pack has not been produced." >&2
    echo "         Run tools/voice-assets/generate_voice_pack.py once a voice route is accepted." >&2
    fail=1
else
    run_validator validate_assets.py "../../$MANIFEST" ${REVIEW_ARGS[@]+"${REVIEW_ARGS[@]}"} || fail=1
fi

if [[ -f "$SOUNDFONT_MANIFEST" ]]; then
    run_validator validate_soundfont.py "../../$SOUNDFONT_MANIFEST" ${REVIEW_ARGS[@]+"${REVIEW_ARGS[@]}"} || fail=1
else
    echo "BLOCKED: $SOUNDFONT_MANIFEST does not exist yet; no reviewed piano SoundFont is bundled." >&2
    echo "         See docs/audio/ASSETS.md for the preparation and provenance steps." >&2
    fail=1
fi

if [[ -f "$SEPARATOR_CLIP" ]]; then
    run_validator validate_separator.py ${REVIEW_ARGS[@]+"${REVIEW_ARGS[@]}"} || fail=1
else
    echo "BLOCKED: $SEPARATOR_CLIP does not exist yet; the separator cue has not been produced." >&2
    echo "         See docs/legal/ASSET_SOURCES.md for the provenance record." >&2
    fail=1
fi

exit "$fail"
