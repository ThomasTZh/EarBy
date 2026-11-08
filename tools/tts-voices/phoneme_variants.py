#!/usr/bin/env python3
"""Synthesize fa/la variants straight from phonemes.

Both piper voices keep an r-coloured [ɑː] from their US-English base, so the remaining lever
for "fa"/"la" is the vowel itself. These variants ask the same voices for the front open [a]
(short and long) beside the normal back [ɑː]. Read-only; writes under .local.
"""
from __future__ import annotations

import json
import sys
import wave
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
MODELS = ROOT / ".local" / "tts-audition" / "models"
OUT = ROOT / ".local" / "tts-audition" / "variants"

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "voice-assets"))
from generate_voice_pack import postprocess  # noqa: E402

MODEL_FILES = {
    "alba": "en_GB-alba-medium.onnx",
    "jenny": "en_GB-jenny_dioco-medium.onnx",
}

VARIANTS = [
    ("fa-a-short", "alba", ["f", "\u02c8", "a"]),
    ("fa-a-long", "alba", ["f", "\u02c8", "a", "\u02d0"]),
    ("fa-jenny-a-long", "jenny", ["f", "\u02c8", "a", "\u02d0"]),
    ("fa-ah-short", "alba", ["f", "\u02c8", "\u0251"]),
    ("la-a-short", "alba", ["l", "\u02c8", "a"]),
    ("la-a-long", "alba", ["l", "\u02c8", "a", "\u02d0"]),
    ("la-jenny-a-long", "jenny", ["l", "\u02c8", "a", "\u02d0"]),
    ("la-ah-short", "alba", ["l", "\u02c8", "\u0251"]),
]


def main() -> int:
    from piper import PiperVoice

    voices = {name: PiperVoice.load(str(MODELS / f)) for name, f in MODEL_FILES.items()}
    raw_dir = OUT / "raw"
    out_dir = OUT / "out"
    raw_dir.mkdir(parents=True, exist_ok=True)
    out_dir.mkdir(parents=True, exist_ok=True)
    extra = []
    for name, voice_name, phonemes in VARIANTS:
        voice = voices[voice_name]
        pm = voice.config.phoneme_id_map
        ids = [pm["^"][0]]
        for phoneme in phonemes:
            ids.extend(pm[phoneme])
        ids.append(pm["$"][0])
        audio = voice.phoneme_ids_to_audio(ids)
        if audio.dtype != np.int16:
            audio = np.clip(audio, -1.0, 1.0)
            audio = (audio * 32767.0).astype(np.int16)
        raw = raw_dir / f"{name}.wav"
        with wave.open(str(raw), "wb") as handle:
            handle.setnchannels(1)
            handle.setsampwidth(2)
            handle.setframerate(voice.config.sample_rate)
            handle.writeframes(audio.tobytes())
        target = out_dir / f"{name}.wav"
        detail = postprocess(raw, target)
        extra.append({"variant": name, "voice": voice_name, "phonemes": " ".join(phonemes), "file": f"out/{name}.wav", **detail})
        print(name, detail["seconds"])
    summary_path = OUT / "variant-summary.json"
    summary = json.loads(summary_path.read_text())
    known = {v["variant"] for v in summary}
    summary.extend(v for v in extra if v["variant"] not in known)
    summary_path.write_text(json.dumps(summary, indent=2) + "\n")
    print(f"recorded {len(extra)} phoneme variants")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
