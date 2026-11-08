#!/usr/bin/env python3
"""Generate voice clips for the semantic catalog with Chatterbox on CPU.

Pipeline per phrase: generate at the model's own sample rate, then postprocess with FFmpeg to
mono 48 kHz PCM16, trim excessive outer silence while keeping consonants and a small pad, and
apply measured two-pass loudness normalisation. Very short clips whose integrated loudness
cannot be measured reliably use a documented bounded-gain fallback instead of an undefined
gain. Raw takes stay in tools/voice-assets/raw; accepted candidates land in
tools/voice-assets/candidates, and only build_voice_pack.py copies reviewed files into the app.

This script performs downloads and generates audio; it is not a routine check.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import resource
import shutil
import subprocess
import sys
import time
from pathlib import Path

# The phrases come from the declarative semantic catalog. Generation covers the whole catalog;
# only reviewed, bundled entries are ever copied into the app by build_voice_pack.py. Existing
# raw takes are reused, so a batch can be resumed after an interruption.
VOCABULARY_PATH = Path(__file__).resolve().parent / "vocabulary.json"


def load_catalog_phrases() -> tuple[tuple[str, str], ...]:
    """Every catalog phrase in canonical id order; --only selects a batch."""
    try:
        data = json.loads(VOCABULARY_PATH.read_text(encoding="utf-8"))
    except FileNotFoundError as failure:
        raise SystemExit(f"missing voice vocabulary: {VOCABULARY_PATH}") from failure
    entries = tuple(
        (entry["id"], entry["spokenText"]) for entry in data.get("entries", [])
    )
    if not entries:
        raise SystemExit("the voice vocabulary declares no phrases")
    return entries


PHRASES: tuple[tuple[str, str], ...] = load_catalog_phrases()

TARGET_RATE = 48_000
# Delivery settings agreed with the reviewer: a flatter contour (low exaggeration/CFG) and a
# slower speaking rate implemented as a pitch-preserving tempo change.
CFG_WEIGHT = 0.2
EXAGGERATION = 0.1
TEMPO_FACTOR = 0.85
TARGET_LUFS = -20.0
TARGET_TRUE_PEAK_DBTP = -3.0
LEADING_PAD_SECONDS = 0.080
TRAILING_PAD_SECONDS = 0.120
# Below this integrated loudness the FFmpeg measurement is not trustworthy for a short clip.
MIN_MEASURABLE_LUFS = -60.0
# Bounded-gain fallback: raise low-level speech without exceeding a sane amplification.
MAX_FALLBACK_GAIN_DB = 12.0


def run(command: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(command, check=True, capture_output=True, text=True)


def probe(path: Path) -> dict:
    result = run(
        [
            "ffprobe", "-v", "error", "-show_entries",
            "stream=sample_rate,channels,bits_per_sample,codec_name",
            "-show_entries", "format=duration",
            "-of", "json", str(path),
        ],
    )
    return json.loads(result.stdout)


def measure_loudness(path: Path) -> dict:
    """Runs the first loudnorm pass and returns the measured values."""
    result = subprocess.run(
        [
            "ffmpeg", "-hide_banner", "-nostats", "-i", str(path),
            "-af", f"loudnorm=I={TARGET_LUFS}:TP={TARGET_TRUE_PEAK_DBTP}:print_format=json",
            "-f", "null", "-",
        ],
        capture_output=True,
        text=True,
    )
    match = re.search(r"\{[^{}]*\"input_i\"[^{}]*\}", result.stderr, re.S)
    if not match:
        raise RuntimeError(f"loudnorm produced no measurement for {path.name}")
    return json.loads(match.group(0))


def postprocess(raw: Path, target: Path) -> dict:
    """Trims, converts and normalises one generated take."""
    target.parent.mkdir(parents=True, exist_ok=True)
    trimmed = target.with_name(target.stem + ".trimmed.wav")
    try:
        # Trim outer silence but keep a short pad so consonants are never clipped away.
        run(
            [
                "ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-i", str(raw),
                "-af",
                "silenceremove=start_periods=1:start_silence=0:start_threshold=-50dB:"
                "detection=peak,areverse,"
                "silenceremove=start_periods=1:start_silence=0:start_threshold=-50dB:"
                "detection=peak,areverse,"
                f"adelay={int(LEADING_PAD_SECONDS * 1000)}:all=1,"
                f"apad=pad_dur={TRAILING_PAD_SECONDS},"
                f"atempo={TEMPO_FACTOR}",
                "-ac", "1", "-ar", str(TARGET_RATE), "-c:a", "pcm_s16le", str(trimmed),
            ],
        )
        measurement = measure_loudness(trimmed)
        input_i = float(measurement["input_i"])
        input_tp = float(measurement["input_tp"])
        input_thresh = float(measurement["input_thresh"])

        if input_i <= MIN_MEASURABLE_LUFS or input_i != input_i:
            # Bounded-gain fallback for clips whose integrated loudness is not measurable.
            gain_db = min(MAX_FALLBACK_GAIN_DB, TARGET_TRUE_PEAK_DBTP - input_tp)
            gain_db = max(0.0, gain_db)
            mode = "bounded_gain"
            run(
                [
                    "ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-i", str(trimmed),
                    "-af", f"volume={gain_db}dB,alimiter=limit={10 ** (TARGET_TRUE_PEAK_DBTP / 20):.6f}",
                    "-ac", "1", "-ar", str(TARGET_RATE), "-c:a", "pcm_s16le", str(target),
                ],
            )
            loudness = {"mode": mode, "inputI": input_i, "gainDb": round(gain_db, 3)}
        else:
            mode = "two_pass_loudnorm"
            run(
                [
                    "ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-i", str(trimmed),
                    "-af",
                    "loudnorm="
                    f"I={TARGET_LUFS}:TP={TARGET_TRUE_PEAK_DBTP}:"
                    f"measured_I={input_i}:measured_TP={input_tp}:"
                    f"measured_LRA={measurement['input_lra']}:measured_thresh={input_thresh}:"
                    f"offset={measurement['target_offset']}:linear=true:print_format=summary",
                    "-ac", "1", "-ar", str(TARGET_RATE), "-c:a", "pcm_s16le", str(target),
                ],
            )
            loudness = {
                "mode": mode,
                "inputI": input_i,
                "inputTp": input_tp,
                "targetOffset": float(measurement["target_offset"]),
            }
    finally:
        trimmed.unlink(missing_ok=True)

    info = probe(target)
    stream = info["streams"][0]
    return {
        "sampleRate": int(stream["sample_rate"]),
        "channels": int(stream["channels"]),
        "bitDepth": int(stream["bits_per_sample"]),
        "seconds": float(info["format"]["duration"]),
        "sha256": hashlib.sha256(target.read_bytes()).hexdigest(),
        "loudness": loudness,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Generate the bundled English voice pack")
    parser.add_argument("--out", default="candidates", help="directory for accepted-format candidates")
    parser.add_argument("--raw", default="raw/takes", help="directory for raw model output")
    parser.add_argument("--device", default="cpu")
    parser.add_argument("--only", nargs="*", default=None, help="regenerate only these voice ids")
    args = parser.parse_args(argv)

    import torch
    import torchaudio

    from chatterbox.tts import ChatterboxTTS

    out_dir = Path(args.out)
    raw_dir = Path(args.raw)
    out_dir.mkdir(parents=True, exist_ok=True)
    raw_dir.mkdir(parents=True, exist_ok=True)

    selected = [(voice_id, text) for voice_id, text in PHRASES if not args.only or voice_id in args.only]
    started = time.monotonic()
    model = ChatterboxTTS.from_pretrained(device=args.device)
    load_seconds = time.monotonic() - started

    records = []
    for voice_id, text in selected:
        raw_path = raw_dir / f"{voice_id}.wav"
        if not raw_path.is_file():
            wav = model.generate(text, cfg_weight=CFG_WEIGHT, exaggeration=EXAGGERATION)
            torchaudio.save(str(raw_path), wav, model.sr)
        target = out_dir / f"{voice_id}.wav"
        item_started = time.monotonic()
        detail = postprocess(raw_path, target)
        record = {
            "voiceId": voice_id,
            "text": text,
            "postprocessSeconds": round(time.monotonic() - item_started, 2),
            "peakRssMiB": round(resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024, 1),
            **detail,
        }
        records.append(record)
        print(json.dumps(record), flush=True)
        if detail["seconds"] > 8.0:
            print(f"FAIL: {voice_id} is {detail['seconds']:.3f} s, above the 8 s limit", file=sys.stderr)
            return 1

    summary = {
        "generator": "chatterbox-tts",
        "device": args.device,
        "modelSampleRate": model.sr,
        "modelLoadSeconds": round(load_seconds, 2),
        "torchVersion": torch.__version__,
        "torchThreads": torch.get_num_threads(),
        "phrases": records,
        "peakRssMiB": round(resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024, 1),
    }
    (out_dir / "generation-summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps({"generated": len(records), "peakRssMiB": summary["peakRssMiB"]}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
