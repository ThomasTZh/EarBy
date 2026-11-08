#!/usr/bin/env python3
"""Generate labelled pronunciation variants for the words the owner flagged.

Words whose phonemes were already correct but whose delivery sounded wrong are rendered twice:
once with piper-alba slowed down, once with the second-choice voice piper-jenny for comparison.
Read-only with respect to the pack candidates; variants live under .local.
"""
from __future__ import annotations

import json
import sys
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MODELS = ROOT / ".local" / "tts-audition" / "models"
OUT = ROOT / ".local" / "tts-audition" / "variants"

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "voice-assets"))
from generate_voice_pack import postprocess  # noqa: E402

VARIANTS = [
    ("a-default", "alba", "A", 1.0, "letter A as today: 'ay' /eɪ/"),
    ("a-ah", "alba", "ah", 1.0, "letter A as 'ah' /ɑː/ (like the a in British 'car')"),
    ("a-uh", "alba", "uh", 1.0, "letter A as the article 'a' /ʌ/ (a car, a pen)"),
    ("augmented-alba-slow", "alba", "augmented", 1.15, "slower delivery"),
    ("augmented-jenny", "jenny", "augmented", 1.0, "second voice, default speed"),
    ("diminished-alba-slow", "alba", "diminished", 1.15, "slower delivery"),
    ("diminished-jenny", "jenny", "diminished", 1.0, "second voice"),
    ("half-diminished-alba-slow", "alba", "half diminished", 1.15, "slower delivery"),
    ("half-diminished-jenny", "jenny", "half diminished", 1.0, "second voice"),
    ("four-alba-slow", "alba", "four", 1.15, "slower delivery; aims for a weaker r"),
    ("four-jenny", "jenny", "four", 1.0, "second voice"),
    ("fa-alba-slow", "alba", "fah", 1.15, "slower delivery"),
    ("fa-jenny", "jenny", "fah", 1.0, "second voice"),
    ("whole-alba-slow", "alba", "whole", 1.15, "slower delivery"),
    ("whole-jenny", "jenny", "whole", 1.0, "second voice"),
    ("two-alba-slow", "alba", "two", 1.15, "slower delivery"),
    ("two-jenny", "jenny", "two", 1.0, "second voice"),
    ("three-alba-slow", "alba", "three", 1.2, "slower delivery; fixes the abrupt cutoff"),
    ("three-jenny", "jenny", "three", 1.0, "second voice"),
    ("b-alba-slow", "alba", "bee", 1.15, "slower delivery"),
    ("b-jenny", "jenny", "bee", 1.0, "second voice"),
    ("e-alba-slow", "alba", "ee", 1.3, "stretched vowel for the 'too short' letter E"),
    ("e-jenny-slow", "jenny", "ee", 1.3, "second voice, stretched vowel"),
]

MODEL_FILES = {
    "alba": "en_GB-alba-medium.onnx",
    "jenny": "en_GB-jenny_dioco-medium.onnx",
}


def main() -> int:
    from piper import PiperVoice
    from piper.config import SynthesisConfig

    voices = {
        name: PiperVoice.load(str(MODELS / file)) for name, file in MODEL_FILES.items()
    }
    raw_dir = OUT / "raw"
    out_dir = OUT / "out"
    raw_dir.mkdir(parents=True, exist_ok=True)
    out_dir.mkdir(parents=True, exist_ok=True)
    records = []
    for name, voice_name, text, length_scale, note in VARIANTS:
        raw = raw_dir / f"{name}.wav"
        with wave.open(str(raw), "wb") as handle:
            voices[voice_name].synthesize_wav(
                text, handle, syn_config=SynthesisConfig(length_scale=length_scale)
            )
        target = out_dir / f"{name}.wav"
        detail = postprocess(raw, target)
        records.append(
            {
                "variant": name,
                "voice": voice_name,
                "text": text,
                "lengthScale": length_scale,
                "note": note,
                "file": f"out/{name}.wav",
                **detail,
            }
        )
        print(name, detail["seconds"])
    (OUT / "variant-summary.json").write_text(json.dumps(records, indent=2) + "\n")
    print(f"wrote {len(records)} variants")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
