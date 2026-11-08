#!/usr/bin/env python3
"""Development-only voice audition: synthesize sample phrases with Piper and Kokoro.

Produces the same phrases in several British female voices, postprocessed to mono 48 kHz PCM16
with the pack's loudness targets, plus a summary JSON. This tool is never used at runtime; the
app ships the chosen WAVs only.
"""
from __future__ import annotations

import json
import re
import subprocess
import sys
import wave
from pathlib import Path

MODELS = Path(__file__).resolve().parents[2] / ".local" / "tts-audition" / "models"
OUT_ROOT = Path(__file__).resolve().parents[2] / ".local" / "tts-audition"

PHRASES = [
    "Minor third",
    "Major seventh",
    "or",
    "sus two",
    "C sharp",
    "first inversion",
]

PIPER_VOICES = {
    "piper-alba": "en_GB-alba-medium",
    "piper-jenny": "en_GB-jenny_dioco-medium",
    "piper-cori": "en_GB-cori-high",
}

KOKORO_VOICES = {
    "kokoro-emma": "bf_emma",
    "kokoro-isabella": "bf_isabella",
}


def slug(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")


def postprocess(raw: Path, target: Path) -> dict:
    target.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [
            "ffmpeg", "-y", "-v", "error", "-i", str(raw),
            "-af", "loudnorm=I=-20:TP=-3:LRA=11",
            "-ar", "48000", "-ac", "1", "-c:a", "pcm_s16le", str(target),
        ],
        check=True,
    )
    with wave.open(str(target), "rb") as handle:
        seconds = handle.getnframes() / handle.getframerate()
    return {"seconds": round(seconds, 4)}


def synthesize_piper() -> list[dict]:
    from piper import PiperVoice

    records = []
    for voice_name, model_name in PIPER_VOICES.items():
        model = MODELS / f"{model_name}.onnx"
        if not model.is_file():
            print(f"FAIL: missing {model}", file=sys.stderr)
            continue
        voice = PiperVoice.load(str(model))
        for phrase in PHRASES:
            raw = OUT_ROOT / "raw" / voice_name / f"{slug(phrase)}.wav"
            raw.parent.mkdir(parents=True, exist_ok=True)
            with wave.open(str(raw), "wb") as handle:
                voice.synthesize_wav(phrase, handle)
            target = OUT_ROOT / "out" / voice_name / f"{slug(phrase)}.wav"
            records.append(
                {
                    "voice": voice_name,
                    "engine": "piper",
                    "model": model_name,
                    "text": phrase,
                    "file": str(target.relative_to(OUT_ROOT)),
                    **postprocess(raw, target),
                }
            )
            print("piper", voice_name, phrase, records[-1]["seconds"])
    return records


def synthesize_kokoro() -> list[dict]:
    import soundfile as sf
    from kokoro_onnx import Kokoro

    kokoro = Kokoro(str(MODELS / "kokoro-v1.0.onnx"), str(MODELS / "voices-v1.0.bin"))
    records = []
    for voice_name, voice in KOKORO_VOICES.items():
        for phrase in PHRASES:
            samples, rate = kokoro.create(phrase, voice=voice, speed=0.9, lang="en-gb")
            raw = OUT_ROOT / "raw" / voice_name / f"{slug(phrase)}.wav"
            raw.parent.mkdir(parents=True, exist_ok=True)
            sf.write(str(raw), samples, rate, subtype="PCM_16")
            target = OUT_ROOT / "out" / voice_name / f"{slug(phrase)}.wav"
            records.append(
                {
                    "voice": voice_name,
                    "engine": "kokoro",
                    "model": "kokoro-v1.0",
                    "text": phrase,
                    "file": str(target.relative_to(OUT_ROOT)),
                    **postprocess(raw, target),
                }
            )
            print("kokoro", voice_name, phrase, records[-1]["seconds"])
    return records


def main() -> int:
    OUT_ROOT.mkdir(parents=True, exist_ok=True)
    records = synthesize_piper() + synthesize_kokoro()
    (OUT_ROOT / "audition-summary.json").write_text(json.dumps(records, indent=2) + "\n")
    print(f"wrote {len(records)} clips to {OUT_ROOT / 'out'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
