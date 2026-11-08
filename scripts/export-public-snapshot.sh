#!/usr/bin/env bash
# Builds a clean, distributable source snapshot in an ignored local directory.
#
# This script is the only supported export path. The published repository is a one-commit
# snapshot, not this development history. The export drops internal-only files and private icon
# drafts, replaces personal filesystem locations with generic phrases, then refuses to finish if a
# secret, a placeholder, a private icon draft or a home path remains.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
cd "$EAR_ROOT"
[[ $# == 1 ]] || { echo 'Usage: scripts/export-public-snapshot.sh <empty-directory>' >&2; exit 2; }
dest="$1"
if [[ -e "$dest" ]] && [[ -n "$(find "$dest" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
  echo "Refusing to overwrite non-empty directory: $dest" >&2
  exit 2
fi
mkdir -p "$dest"
git archive HEAD | tar -x -C "$dest"
rm -rf \
  "$dest/tools/voice-assets/candidates"* \
  "$dest/tools/voice-assets/raw" \
  "$dest/tools/audio-analysis/raw" \
  "$dest/synthNotes/out" \
  "$dest/AGENTS.md" \
  "$dest/planDraft.md" \
  "$dest/docs/testing/notes4human.txt" \
  "$dest/icons/EarByIconV1.png" \
  "$dest/icons/EarByIconV2.png" \
  "$dest/icons/EarByIconV3.png" \
  "$dest/icons/EarByIconV4.png"
# Candidate directories under tools are private review material, not release inputs.
find "$dest/tools" -type d -name 'candidates*' -prune -exec rm -rf {} +
find "$dest/tools" -type d -name raw -prune -exec rm -rf {} +

# Personal filesystem locations become generic phrases in the published text.
find "$dest" -type f \( -name '*.md' -o -name '*.txt' -o -name '*.html' \) -print0 |
  xargs -0 sed -i -E \
    -e 's#~/Documents/EarByBackups[^ `)]*#an external backup#g' \
    -e 's#~/Documents[^ `)]*#an external backup location#g' \
    -e 's#~/Downloads[^ `)]*#an archived build#g' \
    -e 's#/home/[A-Za-z0-9._-]+/[^ `)]*#<local path>#g'

if find "$dest" -type f \( -name '*.jks' -o -name '*.keystore' -o -name 'keystore.properties' \) | grep -q .; then
  echo 'FAIL: secret-like file in public snapshot' >&2
  exit 1
fi
if find "$dest/tools" -type f -name '*.wav' | grep -q .; then
  echo 'FAIL: candidate audio remains in public snapshot' >&2
  exit 1
fi
if find "$dest/icons" -maxdepth 1 -type f \
    \( -name 'EarByIconV1.png' -o -name 'EarByIconV2.png' \
       -o -name 'EarByIconV3.png' -o -name 'EarByIconV4.png' \) | grep -q .; then
  echo 'FAIL: private icon draft remains in the public snapshot' >&2
  exit 1
fi
for icon in EarByIconV5.png EarByIconV6.png; do
  if [[ ! -f "$dest/icons/$icon" ]]; then
    echo "FAIL: expected public icon $icon is missing" >&2
    exit 1
  fi
done
remaining="$(grep -RInE 'example\.invalid|~/Documents|~/Downloads|/home/[A-Za-z0-9._-]+/' "$dest" \
    --include='*.md' --include='*.txt' --include='*.html' || true)"
if [[ -n "$remaining" ]]; then
  printf '%s\n' "$remaining" >&2
  echo 'FAIL: placeholder or personal path remains in the public snapshot' >&2
  exit 1
fi
echo "Public snapshot written to $dest"
du -sh "$dest"
