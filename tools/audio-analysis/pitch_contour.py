#!/usr/bin/env python3
"""Pitch-contour and rate metrics for a voice clip.

Used to check delivery objectively before a listening review: a flat statement has a small
pitch range and a non-rising end, whereas a question-like contour rises at the end. Only the
human review decides acceptance; these numbers explain what changed.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import soundfile as sf


@dataclass(frozen=True)
class ContourMetrics:
    seconds: float
    voicedFrames: int
    medianHz: float
    rangeSemitones: float
    endSlopeSemitones: float
    voicedFraction: float

    def as_dict(self) -> dict:
        return {
            "seconds": round(self.seconds, 4),
            "voicedFrames": self.voicedFrames,
            "medianHz": round(self.medianHz, 2),
            "rangeSemitones": round(self.rangeSemitones, 3),
            "endSlopeSemitones": round(self.endSlopeSemitones, 3),
            "voicedFraction": round(self.voicedFraction, 4),
        }


def _frame_pitch(signal: np.ndarray, rate: int, frame: int, hop: int, fmin: float, fmax: float) -> list[float]:
    """Autocorrelation pitch per frame; 0.0 marks an unvoiced frame."""
    pitches: list[float] = []
    min_lag = int(rate / fmax)
    max_lag = int(rate / fmin)
    for start in range(0, max(signal.size - frame, 1), hop):
        window = signal[start:start + frame]
        if window.size < frame:
            break
        window = window - float(np.mean(window))
        energy = float(np.sqrt(np.mean(window ** 2)))
        if energy < 1e-4:
            pitches.append(0.0)
            continue
        corr = np.correlate(window, window, mode="full")[frame - 1:]
        if corr[0] <= 0:
            pitches.append(0.0)
            continue
        segment = corr[min_lag:max_lag]
        if segment.size == 0:
            pitches.append(0.0)
            continue
        lag = int(np.argmax(segment)) + min_lag
        if corr[lag] / corr[0] < 0.3:
            pitches.append(0.0)
            continue
        pitches.append(float(rate / lag))
    return pitches


def analyse(path: str) -> ContourMetrics:
    samples, rate = sf.read(path, dtype="float64", always_2d=True)
    signal = samples.mean(axis=1)
    frame = max(int(rate * 0.04), 64)
    hop = max(int(rate * 0.01), 16)
    pitches = np.array(_frame_pitch(signal, rate, frame, hop, 70.0, 400.0))
    voiced = pitches[pitches > 0]
    if voiced.size < 3:
        raise ValueError(f"{path}: not enough voiced frames for a pitch measurement")
    semitones = 12.0 * np.log2(voiced / 440.0)
    # Slope over the final third of the voiced part: positive means a rising, question-like end.
    tail = semitones[int(len(semitones) * 2 / 3):]
    if tail.size >= 2:
        slope = float(np.polyfit(np.arange(tail.size), tail, 1)[0]) * (tail.size - 1)
    else:
        slope = 0.0
    return ContourMetrics(
        seconds=float(signal.size / rate),
        voicedFrames=int(voiced.size),
        medianHz=float(np.median(voiced)),
        rangeSemitones=float(np.percentile(semitones, 95) - np.percentile(semitones, 5)),
        endSlopeSemitones=slope,
        voicedFraction=float(voiced.size / max(pitches.size, 1)),
    )


if __name__ == "__main__":
    import argparse
    import json

    parser = argparse.ArgumentParser(description="Report pitch-contour metrics")
    parser.add_argument("files", nargs="+")
    args = parser.parse_args()
    for name in args.files:
        print(f"{name}: {json.dumps(analyse(name).as_dict())}")
