#!/usr/bin/env python3
"""One-off probe: candidate takes for the letter E.

Owner verdict 2026-10-14: neither the bundled E (alba, respelled "ee" at 1.3) nor the list-split
E satisfied; a better E is needed. This probe records several voices and spellings, plus the
middle segment of an "D. E. F." list take, so the owner can pick. It writes candidates only.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
import wave
from pathlib import Path

from generate_piper_voice_pack import MODEL_FILES, MODELS_DIR, VOICE_PROVENANCE
from generate_voice_pack import postprocess

EXTRA_MODELS = {
    "southern": ("en_GB-southern_english_female-low.onnx", "en_GB-southern_english_female-low"),
}
SILENCE_PATTERN = re.compile(r"silence_(start|end): ([0-9.]+)")

# label -> (voice key, synthesis text, length scale)
CANDIDATES = {
    "alba-E": ("alba", "E", 1.0),
    "alba-E-period": ("alba", "E.", 1.0),
    "alba-ee": ("alba", "ee", 1.3),
    "jenny-E": ("jenny", "E", 1.0),
    "jenny-ee": ("jenny", "ee", 1.2),
    "cori-E": ("cori", "E", 1.0),
    "amy-E": ("amy", "E", 1.0),
    "southern-E": ("southern", "E", 1.0),
}


def synthesize(model: Path, text: str, length_scale: float, target: Path) -> None:
    from piper import PiperVoice
    from piper.config import SynthesisConfig

    voice = PiperVoice.load(str(model))
    target.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(target), "wb") as handle:
        voice.synthesize_wav(text, handle, syn_config=SynthesisConfig(length_scale=length_scale))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=Path("candidates-letter-e"))
    parser.add_argument("--raw", type=Path, default=Path("raw/letter-e"))
    args = parser.parse_args(argv)
    out_dir, raw_dir = args.out, args.raw
    out_dir.mkdir(parents=True, exist_ok=True)
    raw_dir.mkdir(parents=True, exist_ok=True)

    records = []
    for label, (voice_key, text, length_scale) in CANDIDATES.items():
        if voice_key in MODEL_FILES:
            model = MODELS_DIR / MODEL_FILES[voice_key]
            provenance = VOICE_PROVENANCE[voice_key]
        else:
            file_name, voice_name = EXTRA_MODELS[voice_key]
            model = MODELS_DIR / file_name
            provenance = {
                "voice": voice_name,
                "modelRevision": f"rhasspy/piper-voices {voice_name}",
                "datasetLicense": "see rhasspy/piper-voices",
            }
        if not model.is_file():
            print(f"SKIP {label}: missing model {model}", file=sys.stderr)
            continue
        raw = raw_dir / f"{label}.wav"
        synthesize(model, text, length_scale, raw)
        target = out_dir / f"{label}.wav"
        detail = postprocess(raw, target)
        records.append(
            {
                "label": label,
                "voice": provenance["voice"],
                "text": text,
                "lengthScale": length_scale,
                "sha256": hashlib.sha256(target.read_bytes()).hexdigest(),
                **detail,
            }
        )
        print(f"{label:20s} {provenance['voice']:36s} {detail['seconds']:.3f} s")

    # The middle segment of an "D. E. F." list take, for a list-context E.
    alba = MODELS_DIR / MODEL_FILES["alba"]
    list_raw = raw_dir / "alba-D-E-F.wav"
    synthesize(alba, "D. E. F.", 1.0, list_raw)
    boundaries = detect_silences(list_raw)
    if len(boundaries) >= 2:
        strongest = sorted(boundaries, key=lambda entry: -entry[1])[:2]
        points = sorted(midpoint for midpoint, _ in strongest)
        piece = raw_dir / "alba-list-e.wav"
        subprocess.run(
            [
                "ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-i", str(list_raw),
                "-af",
                f"atrim=start={points[0]:.6f}:end={points[1]:.6f},asetpts=PTS-STARTPTS",
                "-ac", "1", "-ar", "48000", "-c:a", "pcm_s16le", str(piece),
            ],
            check=True,
        )
        target = out_dir / "alba-list-middle.wav"
        detail = postprocess(piece, target)
        records.append(
            {
                "label": "alba-list-middle",
                "voice": VOICE_PROVENANCE["alba"]["voice"],
                "text": "D. E. F. -> middle segment",
                "lengthScale": 1.0,
                "sha256": hashlib.sha256(target.read_bytes()).hexdigest(),
                **detail,
            }
        )
        print(f"{'alba-list-middle':20s} {'en_GB-alba-medium':36s} {detail['seconds']:.3f} s")

    (out_dir / "probe-summary.json").write_text(
        json.dumps({"candidates": records}, indent=2), encoding="utf-8"
    )
    return 0


def detect_silences(raw: Path) -> list[tuple[float, float]]:
    result = subprocess.run(
        [
            "ffmpeg", "-hide_banner", "-i", str(raw),
            "-af", "silencedetect=noise=-40dB:d=0.12", "-f", "null", "-",
        ],
        capture_output=True,
        text=True,
    )
    starts: list[float] = []
    ends: list[float] = []
    for kind, value in SILENCE_PATTERN.findall(result.stderr):
        if kind == "start":
            starts.append(float(value))
        else:
            ends.append(float(value))
    return [((start + end) / 2, end - start) for start, end in zip(starts, ends)]


if __name__ == "__main__":
    raise SystemExit(main())
