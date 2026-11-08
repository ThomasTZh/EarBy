#!/usr/bin/env bash
# Verifies the tracked generator SoundFont and the packaged EARP1 pack named by the descriptor.
#
# The SoundFont is a generated generator input under synthNotes/ and is not packaged. If it is
# missing, this script reports how to restore it instead of fetching anything.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
cd "$EAR_ROOT"

DESCRIPTOR="app/src/main/assets/soundfont/piano.json"
python3 - "$DESCRIPTOR" <<'PY'
import hashlib
import json
import pathlib
import sys

descriptor_path = pathlib.Path(sys.argv[1])
descriptor = json.loads(descriptor_path.read_text(encoding="utf-8"))
block = descriptor["samplePack"]
repo_root = descriptor_path.resolve().parents[5]
source = (repo_root / block["sourceSf2Path"]).resolve()
pack = (descriptor_path.parent / block["file"]).resolve()

def digest(path: pathlib.Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            value.update(chunk)
    return value.hexdigest()

problems = []
for path, expected_bytes, expected_sha, label in (
    (source, block["sourceSf2Bytes"], block["sourceSf2Sha256"], "generator SoundFont"),
    (pack, block["bytes"], block["sha256"], "EARP1 pack"),
):
    if not path.is_file():
        problems.append(f"{label} missing at {path}")
        continue
    actual_bytes = path.stat().st_size
    actual_sha = digest(path)
    if actual_bytes != expected_bytes or actual_sha != expected_sha:
        problems.append(
            f"{label} at {path} is {actual_bytes} bytes / {actual_sha}, "
            f"expected {expected_bytes} bytes / {expected_sha}"
        )
    else:
        print(f"OK: {label} verified at {path} ({actual_bytes} bytes)")
if problems:
    print(
        "FAIL: restore the tracked generator input from synthNotes/ or regenerate the pack with "
        "synthNotes/tools/build_sample_pack.py; nothing is fetched from a URL.",
        file=sys.stderr,
    )
    for problem in problems:
        print(f"  {problem}", file=sys.stderr)
    raise SystemExit(1)
PY
