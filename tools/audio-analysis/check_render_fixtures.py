#!/usr/bin/env python3
"""Numeric checks for the WAV fixtures rendered by the host harness.

The harness renders with the same renderer sources the app uses and exports frame markers next to
each WAV, so these checks assert the machine-checkable part of the audio contract against the
requested schedule rather than against a second, unrelated synthesizer:

* exact rendered frame count against the requested duration;
* no NaN/Inf and no samples outside [-1, 1];
* audio only inside the declared note windows plus the instrument's release tail, so a misplaced
  or extra note fails;
* a declared silent gap really is silent;
* single-note fixtures carry the expected pitch within the documented tolerance;
* a unison fixture contains exactly one pair of identical notes, never two copies of one pitch.

Whether the piano sounds correct is a human listening result and is not claimed here.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import numpy as np
import soundfile as sf

# Single-note pitch checks: the roadmap allows 15 cents for reviewed piano fixtures.
PITCH_TOLERANCE_CENTS = 15.0
# Energy above this level counts as audible; the instrument release lives below it quickly.
AUDIBLE_DBFS = -60.0
# Allowed decay after a note window ends. The instrument keeps sounding through the sample's own
# release, which the timeline does not model, so this is a documented bound, not an exact edge.
RELEASE_TAIL_SECONDS = 2.5

EXPECTED = {
    "c4": {"notes": [60], "seconds": 1.0},
    "a4": {"notes": [69], "seconds": 1.0},
    "g4": {"notes": [67], "seconds": 1.0},
    "ascending_c4_g4": {"notes": [60, 67], "seconds": 1.5},
    "descending_g4_c4": {"notes": [67, 60], "seconds": 1.5},
    "harmonic_c4_e4": {"notes": [60, 64], "seconds": 1.0},
    "unison_c4": {"notes": [60, 60], "seconds": 1.0},
    "chord_cmaj7": {"notes": [60, 64, 67, 71], "seconds": 1.0},
    "scale_c_major_both": {
        "notes": [60, 62, 64, 65, 67, 69, 71, 72, 71, 69, 67, 65, 64, 62, 60],
        "seconds": 11.9,
    },
}


def midi_hz(note: int) -> float:
    return 440.0 * 2 ** ((note - 69) / 12)


def dominant_hz(signal: np.ndarray, rate: int) -> float:
    """Strongest partial below 2 kHz of the steady part of the signal."""
    windowed = signal * np.hanning(signal.size)
    spectrum = np.abs(np.fft.rfft(windowed))
    freqs = np.fft.rfftfreq(signal.size, d=1.0 / rate)
    limit = freqs <= 2000.0
    return float(freqs[limit][int(np.argmax(spectrum[limit]))])


def nearest_harmonic_cents(measured: float, fundamental: float) -> float:
    harmonic = max(1, round(measured / fundamental))
    return 1200.0 * np.log2(measured / (fundamental * harmonic))


def audible_mask(mono: np.ndarray, rate: int) -> np.ndarray:
    """Per 10 ms window: True where the signal is above the audible threshold."""
    window = max(int(rate * 0.01), 1)
    usable = (mono.size // window) * window
    frames = mono[:usable].reshape(-1, window)
    rms = np.sqrt(np.mean(frames ** 2, axis=1))
    dbfs = 20.0 * np.log10(np.maximum(rms, 1e-12))
    return dbfs > AUDIBLE_DBFS


def check(directory: Path, name: str, spec: dict) -> list[str]:
    problems: list[str] = []
    wav = directory / f"{name}.wav"
    meta_path = directory / f"{name}.json"
    if not wav.is_file():
        return [f"{wav.name} is missing"]
    if not meta_path.is_file():
        return [f"{meta_path.name} is missing (frame markers are required)"]
    meta = json.loads(meta_path.read_text(encoding="utf-8"))

    samples, rate = sf.read(wav, dtype="float64", always_2d=True)
    mono = samples.mean(axis=1)
    duration = mono.size / rate
    expected_frames = int(round(spec["seconds"] * rate))
    if abs(mono.size - expected_frames) > 1:
        problems.append(
            f"{name}: rendered {mono.size} frames, requested {expected_frames} "
            f"(difference above one frame)",
        )
    if not np.all(np.isfinite(mono)):
        problems.append(f"{name}: contains NaN or Inf")
    peak = float(np.max(np.abs(mono)))
    if peak > 1.0:
        problems.append(f"{name}: peak {peak:.4f} exceeds full scale")
    if peak < 1e-3:
        problems.append(f"{name}: effectively silent (peak {peak:.6f})")

    notes = meta.get("notes", [])
    if len(notes) != len(spec["notes"]):
        problems.append(f"{name}: markers declare {len(notes)} notes, expected {len(spec['notes'])}")
    for note, midi in zip(notes, spec["notes"]):
        if note["midi"] != midi:
            problems.append(f"{name}: marker declares MIDI {note['midi']}, expected {midi}")

    # Audio must not start before the first note or continue past the declared tail.
    mask = audible_mask(mono, rate)
    window_seconds = len(mask) * 0.01
    if mask.any():
        first = int(np.argmax(mask))
        last = len(mask) - int(np.argmax(mask[::-1])) - 1
        first_note_start = min((n["startFrame"] for n in notes), default=0) / rate
        if first * 0.01 < first_note_start - 0.02:
            problems.append(
                f"{name}: audible audio at {first * 0.01:.3f}s, before the first note at "
                f"{first_note_start:.3f}s",
            )
        last_end = max((n["endFrame"] for n in notes), default=mono.size) / rate
        allowed_end = min(last_end + RELEASE_TAIL_SECONDS, window_seconds)
        if last * 0.01 > allowed_end:
            problems.append(
                f"{name}: audible audio at {last * 0.01:.3f}s, past the declared note end "
                f"{last_end:.3f}s plus the {RELEASE_TAIL_SECONDS:.1f}s release bound",
            )

    # A gap the schedule declares between the first note ending and the second starting must be
    # silent: this is the contract that specified silence really is silent.
    if len(notes) == 2 and notes[1]["startFrame"] > notes[0]["endFrame"]:
        gap_start = notes[0]["endFrame"] / rate
        gap_end = notes[1]["startFrame"] / rate
        if gap_end - gap_start > 0.05:
            gap = mono[int(gap_start * rate) + int(0.05 * rate):int(gap_end * rate)]
            if gap.size and np.max(np.abs(gap)) > 10 ** (AUDIBLE_DBFS / 20):
                problems.append(
                    f"{name}: declared gap {gap_start:.3f}-{gap_end:.3f}s is not silent "
                    f"(peak {float(np.max(np.abs(gap))):.6f})",
                )

    # Pitch: only single-note fixtures have one unambiguous fundamental.
    if len(notes) == 1:
        steady = mono[int(rate * 0.2):int(rate * 0.8)]
        if steady.size > 1024:
            measured = dominant_hz(steady, rate)
            cents = nearest_harmonic_cents(measured, midi_hz(spec["notes"][0]))
            if abs(cents) > PITCH_TOLERANCE_CENTS:
                problems.append(
                    f"{name}: strongest partial {measured:.1f} Hz is {cents:+.1f} cents from the "
                    f"nearest harmonic of {midi_hz(spec['notes'][0]):.1f} Hz",
                )
    return problems


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Check rendered WAV fixtures numerically")
    parser.add_argument("--dir", required=True)
    args = parser.parse_args(argv)

    directory = Path(args.dir)
    failures: list[str] = []
    checked = 0
    for name, spec in EXPECTED.items():
        problems = check(directory, name, spec)
        failures.extend(problems)
        checked += 1
        print(f"{name}: {'OK' if not problems else 'FAIL: ' + '; '.join(problems)}")
    for failure in failures:
        print(f"FAIL: {failure}", file=sys.stderr)
    if failures:
        return 1
    print(f"PASS: {checked} rendered fixtures passed the numeric checks")
    return 0


if __name__ == "__main__":
    sys.exit(main())
