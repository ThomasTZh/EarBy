#!/usr/bin/env python3
"""Cut single syllables out of natural carrier phrases using piper alignments.

The voices colour a word-final /ɑː/ or the standalone "dough" vowel, so the fix is to take
the syllable from *inside* a phrase, where it is pronounced cleanly. Piper returns exact
per-phoneme sample offsets when the model is loaded with include_alignments=True.
Read-only; writes under .local.
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
    "cori": "en_GB-cori-high.onnx",
}

# name, voice, carrier phrase, phoneme to stop before (empty = stop before the word gap),
# length scale (match the stretched syllables; 1.0 for the letter A)
TASKS = [
    ("a-cut-alba", "alba", "eigh flat", " ", 1.0),
    ("do-cut-alba", "alba", "doughy", "\u026a", 1.25),
    ("fa-cut-alba", "alba", "fah lah", " ", 1.25),
    ("fa-cut-jenny", "jenny", "fah lah", " ", 1.25),
    ("fa-cut-cori", "cori", "fah lah", " ", 1.25),
    ("la-cut-alba", "alba", "lah fah", " ", 1.25),
    ("la-cut-jenny", "jenny", "lah fah", " ", 1.25),
    ("la-cut-cori", "cori", "lah fah", " ", 1.25),
]

MARGIN_SAMPLES = 400


def main() -> int:
    from piper import PiperVoice

    voices = {
        name: PiperVoice.load(str(MODELS / f), include_alignments=True)
        for name, f in MODEL_FILES.items()
    }
    raw_dir = OUT / "raw"
    out_dir = OUT / "out"
    raw_dir.mkdir(parents=True, exist_ok=True)
    out_dir.mkdir(parents=True, exist_ok=True)
    extra = []
    from piper.config import SynthesisConfig

    for name, voice_name, phrase, stop_before, length_scale in TASKS:
        voice = voices[voice_name]
        raw = raw_dir / f"{name}.full.wav"
        with wave.open(str(raw), "wb") as handle:
            alignments = voice.synthesize_wav(
                phrase, handle, syn_config=SynthesisConfig(length_scale=length_scale), include_alignments=True
            )
        sample_rate = voice.config.sample_rate
        with wave.open(str(raw), "rb") as handle:
            frames = np.frombuffer(handle.readframes(handle.getnframes()), dtype=np.int16)
        # Skip the model's long low-level pad before the first phoneme; it is not silence
        # for ffmpeg's trimmer and would otherwise stay in the clip.
        start = 0
        end = 0
        seen = 0
        for entry in alignments:
            if entry.phoneme == "^":
                start = end + entry.num_samples
                end = start
                continue
            if entry.phoneme == "$":
                end += entry.num_samples
                continue
            if stop_before and entry.phoneme == stop_before:
                break
            seen += 1
            end += entry.num_samples
        if seen == 0:
            raise SystemExit(f"no phonemes matched for {name}")
        cut = frames[start : min(len(frames), end + MARGIN_SAMPLES)].astype(np.float32)
        fade = min(len(cut), int(sample_rate * 0.008))
        if fade:
            cut[-fade:] *= np.linspace(1.0, 0.0, fade, dtype=np.float32)
        cut = np.clip(cut, -32768, 32767).astype(np.int16)
        cut_path = raw_dir / f"{name}.wav"
        with wave.open(str(cut_path), "wb") as handle:
            handle.setnchannels(1)
            handle.setsampwidth(2)
            handle.setframerate(sample_rate)
            handle.writeframes(cut.tobytes())
        target = out_dir / f"{name}.wav"
        detail = postprocess(cut_path, target)
        extra.append(
            {
                "variant": name,
                "voice": voice_name,
                "phonemes": f"cut from {phrase!r}",
                "file": f"out/{name}.wav",
                **detail,
            }
        )
        print(name, detail["seconds"])
    summary_path = OUT / "variant-summary.json"
    summary = json.loads(summary_path.read_text())
    by_name = {v["variant"]: v for v in summary}
    for record in extra:
        by_name[record["variant"]] = record
    summary_path.write_text(json.dumps(list(by_name.values()), indent=2) + "\n")
    print(f"recorded {len(extra)} cut takes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
