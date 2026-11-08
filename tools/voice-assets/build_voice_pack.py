#!/usr/bin/env python3
"""Assemble the bundled voice pack from reviewed candidates (M4.4/M5, catalog-driven).

Every catalog entry marked `bundled` must have a reviewed candidate under `candidates/`. The
script copies new clips into the app asset directory and writes the schema v1 manifest with
hashes, frame counts and per-clip provenance. Entries already present in the manifest (the
thirteen frozen interval clips) are kept byte-for-byte and never regenerated.

Review status is never invented: an entry without a recorded listening review is written with
`humanAccepted=false`.

This script changes assets; it is not a routine check.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import sys
import wave
from pathlib import Path

SCHEMA_VERSION = 1
PACK_ID = "english-answers"
PACK_VERSION = "2.0.0"
LANGUAGE = "en"
VOICE = "piper-mixed-english"
POSTPROCESSING = (
    "ffmpeg silenceremove + 80 ms leading and 120 ms trailing pad, atempo=0.85 rate control, "
    "two-pass loudnorm I=-20 LUFS TP=-3 dBTP, mono 48 kHz PCM16"
)

ROOT = Path(__file__).resolve().parents[2]
VOCABULARY_PATH = ROOT / "tools" / "voice-assets" / "vocabulary.json"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def load_bundled_catalog() -> list[dict]:
    data = json.loads(VOCABULARY_PATH.read_text(encoding="utf-8"))
    bundled = [entry for entry in data.get("entries", []) if entry.get("bundled")]
    if not bundled:
        raise SystemExit("the catalog declares no bundled entries")
    return bundled


def load_summaries(paths: list[Path]) -> dict[str, dict]:
    merged: dict[str, dict] = {}
    for path in paths:
        if not path.is_file():
            raise SystemExit(f"missing generation summary: {path}")
        data = json.loads(path.read_text(encoding="utf-8"))
        for record in data.get("phrases", []):
            merged[record["voiceId"]] = record
    return merged


def load_reviews(paths: list[Path]) -> dict[str, dict]:
    merged: dict[str, dict] = {}
    for path in paths:
        if not path.is_file():
            continue
        data = json.loads(path.read_text(encoding="utf-8"))
        for entry in data.get("entries", []):
            merged[entry["id"]] = entry
    return merged


def load_existing(manifest_path: Path) -> dict[str, dict]:
    if not manifest_path.is_file():
        return {}
    data = json.loads(manifest_path.read_text(encoding="utf-8"))
    return {entry["id"]: entry for entry in data.get("entries", [])}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Assemble the bundled voice pack")
    parser.add_argument("--candidates", default="candidates")
    parser.add_argument(
        "--summaries",
        nargs="+",
        default=[
            "candidates/generation-summary-piper-alba-full-v6.json",
            "candidates/generation-summary-piper-alba-m9a-v1.json",
            "candidates/generation-summary-letters-list-alba-v1.json",
            "candidates/generation-summary-letter-e-cori-v1.json",
        ],
        help="generation summary files merged by voice id",
    )
    parser.add_argument("--asset-dir", default="../../app/src/main/assets/voice")
    parser.add_argument(
        "--reviews",
        nargs="+",
        default=[
            "reviews.json",
            "reviews-intervals.json",
            "reviews-vocabulary.json",
            "reviews-m9a.json",
            "reviews-note-letters.json",
        ],
        help="review record files merged by voice id",
    )
    parser.add_argument(
        "--refresh",
        nargs="*",
        default=None,
        help="bundled ids to rebuild from the candidates even though they are already packaged",
    )
    args = parser.parse_args(argv)
    refresh_ids = set(args.refresh or [])

    candidates = Path(args.candidates)
    asset_dir = Path(args.asset_dir)
    summaries = load_summaries([Path(path) for path in args.summaries])
    reviews = load_reviews([Path(path) for path in args.reviews])
    manifest_path = asset_dir / "voice-pack.json"
    existing = load_existing(manifest_path)
    wav_dir = asset_dir / "wav"
    wav_dir.mkdir(parents=True, exist_ok=True)

    entries = []
    for catalog_entry in load_bundled_catalog():
        voice_id = catalog_entry["id"]
        frozen = existing.get(voice_id)
        if frozen is not None and voice_id not in refresh_ids:
            # Already packaged (the frozen interval clips): keep the entry and its file as is.
            packaged = asset_dir / frozen["file"]
            if not packaged.is_file():
                print(f"FAIL: packaged {packaged} is missing", file=sys.stderr)
                return 1
            entries.append(frozen)
            continue

        source_file = candidates / f"{voice_id}.wav"
        if not source_file.is_file():
            print(f"FAIL: candidate {source_file} is missing", file=sys.stderr)
            return 1
        record = summaries.get(voice_id)
        if record is None:
            print(f"FAIL: no generation summary for {voice_id}", file=sys.stderr)
            return 1
        file_name = voice_id.replace(".", "_") + ".wav"
        target_file = wav_dir / file_name
        shutil.copyfile(source_file, target_file)
        with wave.open(str(target_file), "rb") as handle:
            frame_count = handle.getnframes()
            sample_rate = handle.getframerate()
            channels = handle.getnchannels()
            bit_depth = handle.getsampwidth() * 8
        review = reviews.get(voice_id, {})
        source = {
            "kind": "generated",
            "reference": f"Piper voice {record.get('voice', 'unknown')}, local CPU generation",
            "license": record.get("datasetLicense", "unknown"),
            "model": record.get("voice", "piper"),
            "modelRevision": record.get("modelRevision", "unknown"),
            "seed": "model default sampling",
            "postprocessing": POSTPROCESSING,
        }
        if isinstance(record.get("generatedAt"), (int, float)):
            source["generatedAt"] = record["generatedAt"]
        entries.append(
            {
                "id": voice_id,
                "file": f"wav/{file_name}",
                "text": catalog_entry["spokenText"],
                "sampleRate": sample_rate,
                "channels": channels,
                "bitDepth": bit_depth,
                "frameCount": frame_count,
                "sha256": sha256(target_file),
                "source": source,
                "review": {
                    "humanAccepted": bool(review.get("humanAccepted", False)),
                    "reviewedAt": review.get("reviewedAt"),
                    "reviewer": review.get("reviewer"),
                    "notes": review.get("reason", "pending listening review"),
                },
            }
        )

    manifest = {
        "schemaVersion": SCHEMA_VERSION,
        "packId": PACK_ID,
        "packVersion": PACK_VERSION,
        "language": LANGUAGE,
        "voice": VOICE,
        "entries": entries,
    }
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    pending = sum(1 for entry in entries if not entry["review"]["humanAccepted"])
    print(
        f"Wrote {manifest_path} with {len(entries)} entries "
        f"({pending} awaiting listening review)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
