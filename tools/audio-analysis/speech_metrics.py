#!/usr/bin/env python3
"""Numeric speech-likeness metrics for a voice clip.

These metrics catch gross generator failures (silence, noise, clipping, wrong rate) before a
human listens. They are not a pronunciation or intelligibility test: only a person can accept
a phrase, which is why the manifest keeps an explicit human review field.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import soundfile as sf


@dataclass(frozen=True)
class SpeechMetrics:
    sampleRate: int
    channels: int
    seconds: float
    peak: float
    rms: float
    crestFactorDb: float
    clippedFraction: float
    silenceFraction: float
    activeFraction: float
    spectralCentroidHz: float
    voicedFractionBelow4k: float
    preSpeechRmsRelativeToPeak: float
    firstVoicedMs: float

    def as_dict(self) -> dict:
        return {
            "sampleRate": self.sampleRate,
            "channels": self.channels,
            "seconds": round(self.seconds, 6),
            "peak": round(self.peak, 6),
            "rms": round(self.rms, 6),
            "crestFactorDb": round(self.crestFactorDb, 3),
            "clippedFraction": round(self.clippedFraction, 6),
            "silenceFraction": round(self.silenceFraction, 6),
            "activeFraction": round(self.activeFraction, 6),
            "spectralCentroidHz": round(self.spectralCentroidHz, 2),
            "voicedFractionBelow4k": round(self.voicedFractionBelow4k, 6),
            "preSpeechRmsRelativeToPeak": round(self.preSpeechRmsRelativeToPeak, 6),
            "firstVoicedMs": round(self.firstVoicedMs, 3),
        }


def analyse(path: str) -> SpeechMetrics:
    """Computes speech-likeness metrics for one PCM WAV file."""
    samples, rate = sf.read(path, dtype="float64", always_2d=True)
    mono = samples.mean(axis=1)
    if mono.size == 0:
        raise ValueError(f"{path}: empty audio")
    peak = float(np.max(np.abs(mono)))
    rms = float(np.sqrt(np.mean(mono ** 2)))
    crest = 20.0 * np.log10(peak / rms) if rms > 0 else float("inf")

    # A 20 ms window envelope separates speech activity from silence robustly.
    window = max(int(rate * 0.02), 1)
    usable = (mono.size // window) * window
    frames = mono[:usable].reshape(-1, window)
    frame_rms = np.sqrt(np.mean(frames ** 2, axis=1)) if usable else np.array([rms])
    frame_db = 20.0 * np.log10(np.maximum(frame_rms, 1e-12))
    loudest = float(np.max(frame_db))
    active = frame_db > (loudest - 35.0)

    spectrum = np.abs(np.fft.rfft(mono * np.hanning(mono.size)))
    freqs = np.fft.rfftfreq(mono.size, d=1.0 / rate)
    total = float(np.sum(spectrum)) or 1.0
    centroid = float(np.sum(freqs * spectrum) / total)
    below4k = float(np.sum(spectrum[freqs < 4000.0]) / total)

    # Anything audible before the first speech frame reads as noise or a hum in front of the
    # words. It is measured relative to the peak so it is independent of the loudness target.
    active_indices = np.flatnonzero(active)
    first_active = int(active_indices[0]) if active_indices.size else 0
    pre_speech = frames[:first_active]
    pre_rms = float(np.sqrt(np.mean(pre_speech ** 2))) if pre_speech.size else 0.0
    pre_relative = pre_rms / peak if peak > 0 else 0.0

    return SpeechMetrics(
        sampleRate=int(rate),
        channels=int(samples.shape[1]),
        seconds=float(mono.size / rate),
        peak=peak,
        rms=rms,
        crestFactorDb=float(crest),
        clippedFraction=float(np.mean(np.abs(mono) >= 0.999)),
        silenceFraction=float(np.mean(frame_db <= (loudest - 35.0))),
        activeFraction=float(np.mean(active)),
        spectralCentroidHz=centroid,
        voicedFractionBelow4k=below4k,
        preSpeechRmsRelativeToPeak=pre_relative,
        firstVoicedMs=float(first_active * window / rate * 1000.0),
    )


if __name__ == "__main__":
    import argparse
    import json
    from pathlib import Path

    parser = argparse.ArgumentParser(description="Report speech-likeness metrics for WAV files")
    parser.add_argument("files", nargs="+")
    args = parser.parse_args()
    for name in args.files:
        metrics = analyse(name)
        print(f"{Path(name).name}: {json.dumps(metrics.as_dict())}")
