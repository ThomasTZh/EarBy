#!/usr/bin/env python3
"""Audition words for the stubborn syllables on additional voices.

Plain full-sentence synthesis only (the owner approved that approach); no cuts or raw
phonemes. Development only; writes under .local/tts-audition/word-audition.
"""
from __future__ import annotations

import json
import sys
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MODELS = ROOT / ".local" / "tts-audition" / "models"
OUT = ROOT / ".local" / "tts-audition" / "word-audition"

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "voice-assets"))
from generate_voice_pack import postprocess  # noqa: E402

# name, engine, model file, voice-specific id, text, length scale
TAKES = [
    ("fah-amy", "piper", "en_US-amy-medium.onnx", None, "fah", 1.25),
    ("fah-cori", "piper", "en_GB-cori-high.onnx", None, "fah", 1.25),
    ("fah-southern", "piper", "en_GB-southern_english_female-low.onnx", None, "fah", 1.25),
    ("fah-vctk-p248", "piper", "en_GB-vctk-medium.onnx", "p248", "fah", 1.25),
    ("lah-amy", "piper", "en_US-amy-medium.onnx", None, "lah", 1.25),
    ("lah-jenny", "piper", "en_GB-jenny_dioco-medium.onnx", None, "lah", 1.25),
    ("lah-southern", "piper", "en_GB-southern_english_female-low.onnx", None, "lah", 1.25),
    ("eigh-amy", "piper", "en_US-amy-medium.onnx", None, "eigh", 1.0),
    ("eigh-cori", "piper", "en_GB-cori-high.onnx", None, "eigh", 1.0),
    ("eigh-jenny", "piper", "en_GB-jenny_dioco-medium.onnx", None, "eigh", 1.0),
    ("eigh-southern", "piper", "en_GB-southern_english_female-low.onnx", None, "eigh", 1.0),
    ("fah-kokoro-emma", "kokoro", "kokoro-v1.0.onnx", "bf_emma", "fah", None),
    ("lah-kokoro-emma", "kokoro", "kokoro-v1.0.onnx", "bf_emma", "lah", None),
    ("eigh-kokoro-emma", "kokoro", "kokoro-v1.0.onnx", "bf_emma", "eigh", None),
]


def main() -> int:
    from piper import PiperVoice
    from piper.config import SynthesisConfig

    raw_dir = OUT / "raw"
    out_dir = OUT / "out"
    raw_dir.mkdir(parents=True, exist_ok=True)
    out_dir.mkdir(parents=True, exist_ok=True)
    voices: dict[str, object] = {}
    kokoro = None
    records = []
    for name, engine, model_file, voice_id, text, length_scale in TAKES:
        raw = raw_dir / f"{name}.wav"
        if engine == "piper":
            if model_file not in voices:
                voices[model_file] = PiperVoice.load(str(MODELS / model_file))
            voice = voices[model_file]
            speaker = None
            if voice_id is not None:
                model_config = json.loads((MODELS / f"{model_file}.json").read_text())
                speaker = model_config["speaker_id_map"][voice_id]
            config = SynthesisConfig(
                speaker_id=speaker,
                length_scale=length_scale if length_scale is not None else 1.0,
            )
            with wave.open(str(raw), "wb") as handle:
                voice.synthesize_wav(text, handle, syn_config=config)
        else:
            if kokoro is None:
                from kokoro_onnx import Kokoro

                kokoro = Kokoro(str(MODELS / "kokoro-v1.0.onnx"), str(MODELS / "voices-v1.0.bin"))
            import soundfile as sf

            samples, rate = kokoro.create(text, voice=voice_id, speed=0.9, lang="en-gb")
            sf.write(str(raw), samples, rate, subtype="PCM_16")
        target = out_dir / f"{name}.wav"
        detail = postprocess(raw, target)
        records.append(
            {
                "take": name,
                "engine": engine,
                "model": model_file,
                "voiceId": voice_id,
                "text": text,
                "file": f"out/{name}.wav",
                **detail,
            }
        )
        print(name, detail["seconds"], flush=True)
    (OUT / "word-audition-summary.json").write_text(json.dumps(records, indent=2) + "\n")
    print(f"wrote {len(records)} takes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
