#!/usr/bin/env python3
"""Validate the separator clip's format, level, hash and recorded provenance.

Read-only: it opens the shipped WAV, measures it, and reads the provenance documents. It never
rewrites an asset, a document or a hash.

A pass proves the file's container, sample rate, channel count, frame count, duration, peak, DC
offset and SHA-256, and that the provenance record names the source, the licence and the upstream
acknowledgements. It does not prove that the cue sounds right or that the record is truthful;
those are human review items, and `--require-reviewed` turns a missing listening record into a
failure instead of pretending the machine closed it.

This file is a prototype for `tools/audio-analysis/validate_separator.py` and is also used to
check the replacement candidate with `--wav ... --sha256 ... --no-provenance`.
"""
from __future__ import annotations

import argparse
import hashlib
import math
import re
import sys
from pathlib import Path

import numpy as np
import soundfile as sf

EXPECTED_RELATIVE_CLIP = "app/src/main/assets/separator/original-separator.wav"
EXPECTED_SHA256 = "ed935306de57c8466d7d4a096817624d945ac0858771251adbc2d741c8ac96a6"
EXPECTED_BYTES = 30_798
EXPECTED_FRAMES = 15_360
EXPECTED_RATE = 48_000
MIN_DURATION_S = 0.20
MAX_DURATION_S = 0.60
PEAK_CEILING_DBFS = -1.0
PEAK_FLOOR_DBFS = -60.0
MAX_ABS_DC = 1.0e-3

UPSTREAM_URL = (
    "https://ftp.osuosl.org/pub/musescore/soundfont/MuseScore_General/MuseScore_General.sf2"
)
UPSTREAM_SHA256 = "ee51d2c4b1525e70f19a45909c4fd7a2e26d91d115fa89dbf5a6bc413d8b9bf3"
LICENCE_SHA256 = "5ad8d737e13c7f01f5b9674872a82a92b4ba253603e8ed14b9db12293550b4b9"
REQUIRED_ACKNOWLEDGEMENTS = ("Frank Wen", "Michael Cowgill", "S. Christian Collins")
LISTENING_RECORD = re.compile(r"^Separator listening: accepted by the owner on \d{4}-\d{2}-\d{2}$", re.M)


def find_root(start: Path) -> Path:
    for candidate in (start, *start.parents):
        if (candidate / EXPECTED_RELATIVE_CLIP).is_file():
            return candidate
    raise SystemExit(f"cannot find {EXPECTED_RELATIVE_CLIP} above {start}")


def measure(wav: Path) -> dict:
    info = sf.info(wav)
    samples, rate = sf.read(wav, dtype="int16")
    floats = samples.astype(np.float64) / 32768.0
    return {
        "info": info,
        "rate": rate,
        "channels": 1 if samples.ndim == 1 else samples.shape[1],
        "frames": int(samples.size if samples.ndim == 1 else samples.shape[0]),
        "peak_dbfs": 20.0 * math.log10(float(np.max(np.abs(floats)))),
        "peak_sample": int(np.max(np.abs(samples.astype(np.int32)))),
        "rms_dbfs": 20.0 * math.log10(float(np.sqrt(np.mean(floats * floats)))),
        "dc": float(np.mean(floats)),
        "bytes": wav.stat().st_size,
        "sha256": hashlib.sha256(wav.read_bytes()).hexdigest(),
    }


def check_audio(wav: Path, expected_sha256: str, expected_bytes: int | None) -> list[str]:
    problems: list[str] = []
    if not wav.is_file():
        return [f"missing clip: {wav}"]
    m = measure(wav)
    info = m["info"]
    if info.samplerate != EXPECTED_RATE:
        problems.append(f"sample rate {info.samplerate} != {EXPECTED_RATE}")
    if m["channels"] != 1:
        problems.append(f"channels {m['channels']} != 1")
    if info.subtype != "PCM_16":
        problems.append(f"subtype {info.subtype} != PCM_16")
    if m["frames"] != EXPECTED_FRAMES:
        problems.append(f"frames {m['frames']} != {EXPECTED_FRAMES}")
    duration = m["frames"] / info.samplerate
    if not MIN_DURATION_S <= duration <= MAX_DURATION_S:
        problems.append(f"duration {duration:.3f} s outside {MIN_DURATION_S}-{MAX_DURATION_S} s")
    if m["peak_dbfs"] > PEAK_CEILING_DBFS:
        problems.append(f"peak {m['peak_dbfs']:.2f} dBFS above {PEAK_CEILING_DBFS} dBFS")
    if m["peak_dbfs"] < PEAK_FLOOR_DBFS:
        problems.append(f"peak {m['peak_dbfs']:.2f} dBFS below {PEAK_FLOOR_DBFS} dBFS (silent?)")
    if m["peak_sample"] >= 32_767:
        problems.append(f"clipped sample {m['peak_sample']}")
    if abs(m["dc"]) > MAX_ABS_DC:
        problems.append(f"DC offset {m['dc']:.2e} above {MAX_ABS_DC:.0e}")
    if m["sha256"] != expected_sha256:
        problems.append(f"SHA-256 {m['sha256']} != {expected_sha256}")
    if expected_bytes is not None and m["bytes"] != expected_bytes:
        problems.append(f"size {m['bytes']} != {expected_bytes}")
    print(
        f"  {wav.name}: {m['frames']} frames, {duration:.3f} s, "
        f"{m['rate']} Hz, {m['channels']} ch {info.subtype}, "
        f"peak {m['peak_dbfs']:.2f} dBFS, RMS {m['rms_dbfs']:.2f} dBFS, DC {m['dc']:.2e}, "
        f"SHA-256 {m['sha256']}"
    )
    return problems


def check_provenance(root: Path, require_listening: bool) -> list[str]:
    problems: list[str] = []
    asset_sources = (root / "docs/legal/ASSET_SOURCES.md").read_text()
    assets = (root / "docs/audio/ASSETS.md").read_text()
    notices = (root / "THIRD_PARTY_NOTICES.md").read_text()
    licence = root / "LICENSES/MuseScore_General-MIT.txt"
    if EXPECTED_SHA256 not in asset_sources:
        problems.append("docs/legal/ASSET_SOURCES.md does not record the clip SHA-256")
    for needle, label in (
        (UPSTREAM_URL, "upstream SoundFont URL"),
        (UPSTREAM_SHA256, "upstream SoundFont SHA-256"),
        ("MIT", "MIT licence"),
        ("separator_render.cpp", "rendering harness"),
    ):
        if needle not in asset_sources:
            problems.append(f"docs/legal/ASSET_SOURCES.md does not record the {label}")
    if EXPECTED_SHA256 not in assets:
        problems.append("docs/audio/ASSETS.md does not record the clip SHA-256")
    for needle, label in (
        ("MuseScore General", "source SoundFont"),
        ("MIT", "MIT licence"),
        ("MuseScore_General-MIT.txt", "licence text path"),
    ):
        if needle not in notices:
            problems.append(f"THIRD_PARTY_NOTICES.md does not record the {label}")
    for name in REQUIRED_ACKNOWLEDGEMENTS:
        if name not in notices:
            problems.append(f"THIRD_PARTY_NOTICES.md is missing the acknowledgement for {name}")
    if not licence.is_file():
        problems.append("LICENSES/MuseScore_General-MIT.txt is missing")
    else:
        text = licence.read_text()
        if "must be included in any derivative work" not in text:
            problems.append("licence text does not carry the derivative-work acknowledgement clause")
        if hashlib.sha256(licence.read_bytes()).hexdigest() != LICENCE_SHA256:
            problems.append("licence text SHA-256 does not match the recorded upstream hash")
    if require_listening and not LISTENING_RECORD.search(assets):
        problems.append(
            "no owner listening record; add 'Separator listening: accepted by the owner on "
            "YYYY-MM-DD' to docs/audio/ASSETS.md after the human review"
        )
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=None, help="repository root")
    parser.add_argument("--wav", type=Path, default=None, help="clip to check (default: shipped)")
    parser.add_argument("--sha256", default=EXPECTED_SHA256, help="expected clip SHA-256")
    parser.add_argument("--bytes", type=int, default=EXPECTED_BYTES, help="expected byte size")
    parser.add_argument("--no-provenance", action="store_true", help="skip the provenance record")
    parser.add_argument("--require-reviewed", action="store_true", help="require the listening record")
    args = parser.parse_args()

    root = args.root or find_root(Path(__file__).resolve().parent)
    wav = args.wav or root / EXPECTED_RELATIVE_CLIP
    expected_bytes = None if args.no_provenance else args.bytes
    problems = check_audio(wav, args.sha256, expected_bytes)
    if not args.no_provenance:
        problems += check_provenance(root, args.require_reviewed)

    if problems:
        for problem in problems:
            print(f"FAIL: {problem}", file=sys.stderr)
        return 1
    scope = "format, level and hash" if args.no_provenance else "format, level, hash and provenance record"
    print(f"PASS: separator clip {scope}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
