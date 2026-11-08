#!/usr/bin/env python3
"""Rank voices by how cleanly they render the isolated syllables "fah", "lah" and "eigh".

For each voice we synthesize both the isolated syllable and a word that contains the same
vowel followed by a consonant (fah vs father, lah vs lava, eigh vs eight), locate the vowel
with piper alignments, and compare the two average spectra. A voice that adds an r-like tail
to the isolated syllable scores a large distance. Development only; writes under /tmp.
"""
from __future__ import annotations

import json
import tempfile
import wave
from pathlib import Path

import numpy as np

MODELS = Path(__file__).resolve().parents[2] / ".local" / "tts-audition" / "models"

VOICES = {
    "alba": ("en_GB-alba-medium.onnx", None),
    "jenny": ("en_GB-jenny_dioco-medium.onnx", None),
    "cori": ("en_GB-cori-high.onnx", None),
    "southern": ("en_GB-southern_english_female-low.onnx", None),
    "vctk-p225": ("en_GB-vctk-medium.onnx", 0),
    "vctk-p233": ("en_GB-vctk-medium.onnx", 1),
    "vctk-p248": ("en_GB-vctk-medium.onnx", 2),
    "vctk-p260": ("en_GB-vctk-medium.onnx", 3),
    "vctk-p282": ("en_GB-vctk-medium.onnx", 4),
    "lessac": ("en_US-lessac-medium.onnx", None),
    "amy": ("en_US-amy-medium.onnx", None),
}

# VCTK speaker ids are looked up by name in the model config.
VCTK_NAMES = ["p225", "p233", "p248", "p260", "p282"]

PAIRS = [
    ("fah", ["\u0251", "\u02d0"], "father", ["\u0251", "\u02d0"]),
    ("lah", ["\u0251", "\u02d0"], "lava", ["\u0251", "\u02d0"]),
    ("eigh", ["e", "\u026a"], "eight", ["e", "\u026a"]),
]


def synth_with_vowel(voice, text, target, speaker_id):
    """Synthesize text, return (vowel_samples, sample_rate) located by phoneme alignment."""
    from piper.config import SynthesisConfig
    config = SynthesisConfig(speaker_id=speaker_id) if speaker_id is not None else None
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        path = Path(tmp.name)
    with wave.open(str(path), "wb") as handle:
        alignments = voice.synthesize_wav(text, handle, syn_config=config, include_alignments=True)
    with wave.open(str(path), "rb") as handle:
        frames = np.frombuffer(handle.readframes(handle.getnframes()), dtype=np.int16)
    sr = voice.config.sample_rate
    offset = 0
    start = end = None
    match_at = 0
    for entry in alignments:
        if entry.phoneme == "^":
            offset += entry.num_samples
            continue
        if match_at < len(target) and entry.phoneme == target[match_at]:
            if match_at == 0:
                start = offset
            match_at += 1
            if match_at == len(target):
                end = offset + entry.num_samples
                break
        elif entry.phoneme == target[0]:
            start = offset
            match_at = 1
        else:
            start = None
            match_at = 0
        offset += entry.num_samples
    path.unlink(missing_ok=True)
    if start is None or end is None:
        raise RuntimeError(f"vowel {target} not found in {text!r}")
    return frames[start:end], sr


def average_log_spectrum(samples, sr, grid_hz):
    win = int(0.030 * sr)
    if len(samples) < win:
        samples = np.pad(samples, (0, win - len(samples)))
    step = max(1, (len(samples) - win) // 7)
    spectra = []
    for start in range(0, len(samples) - win + 1, step):
        seg = samples[start : start + win].astype(np.float64) * np.hanning(win)
        spec = np.abs(np.fft.rfft(seg, n=4096))
        spectra.append(20 * np.log10(spec + 1e-6))
    freq = np.fft.rfftfreq(4096, 1 / sr)
    mean = np.mean(spectra, axis=0)
    return np.interp(grid_hz, freq, mean)


def main() -> int:
    from piper import PiperVoice

    grid = np.linspace(80, 4000, 240)
    results = {}
    cache: dict[str, object] = {}
    for name, (file, vctk_index) in VOICES.items():
        speaker_id = None
        if vctk_index is not None:
            config = json.loads((MODELS / file.replace(".onnx", ".onnx.json")).read_text())
            speaker_id = config["speaker_id_map"][VCTK_NAMES[vctk_index]]
        key = file
        if key not in cache:
            cache[key] = PiperVoice.load(str(MODELS / file), include_alignments=True)
        voice = cache[key]
        row = {}
        for iso, iso_target, word, word_target in PAIRS:
            try:
                a, sr_a = synth_with_vowel(voice, iso, iso_target, speaker_id)
                b, _ = synth_with_vowel(voice, word, word_target, speaker_id)
                dist = float(np.mean(np.abs(average_log_spectrum(a, sr_a, grid) - average_log_spectrum(b, sr_a, grid))))
                row[iso] = round(dist, 2)
            except Exception as exc:  # noqa: BLE001 - report per-voice failure and continue
                row[iso] = f"ERR {exc}"
        results[name] = row
        print(name, row, flush=True)
    Path("/tmp/opencode/vowel-ranking.json").write_text(json.dumps(results, indent=2) + "\n")
    for item in ("fah", "lah", "eigh"):
        ranked = sorted(
            ((v[item], k) for k, v in results.items() if isinstance(v[item], float))
        )
        print(f"best for {item}:", ", ".join(f"{k}({d})" for d, k in ranked[:5]))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
