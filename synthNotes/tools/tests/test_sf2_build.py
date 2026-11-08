"""Unit tests for the SoundFont writer and validator (standard library only)."""

from __future__ import annotations

import math
import struct
import sys
import tempfile
import unittest
import wave
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from build_sf2 import (  # noqa: E402
    SoundFontBuildError,
    build_sf2,
    read_note_wav,
)
from validate_sf2 import Sf2Error, parse_sf2, validate  # noqa: E402

TEST_FRAMES = 4800
TEST_RATE = 48000
TEST_TONE_HZ = 480.0


def write_test_wav(path: Path, frames: int = TEST_FRAMES, sample_rate: int = TEST_RATE) -> None:
    """Write a whole number of 480 Hz cycles at RMS 0.120."""
    amplitude = 0.12 * math.sqrt(2.0) * 32768.0
    values = [
        int(round(amplitude * math.sin(2.0 * math.pi * TEST_TONE_HZ * index / sample_rate)))
        for index in range(frames)
    ]
    with wave.open(str(path), "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(sample_rate)
        handle.writeframes(struct.pack(f"<{frames}h", *values))


def make_entries(directory: Path, midi_values: list[int], split_midi: int) -> list[dict]:
    entries = []
    for midi in midi_values:
        path = directory / f"{midi:03d}.wav"
        write_test_wav(path)
        pcm, rms = read_note_wav(
            path, expected_frames=TEST_FRAMES, expected_rate=TEST_RATE
        )
        entries.append(
            {
                "midi": midi,
                "pcm": pcm,
                "rms": rms,
                "source": "bass" if midi <= split_midi else "guitar",
            }
        )
    return entries


class BuildSf2Test(unittest.TestCase):
    def test_read_note_wav_accepts_contract(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "060.wav"
            write_test_wav(path)
            pcm, rms = read_note_wav(
                path, expected_frames=TEST_FRAMES, expected_rate=TEST_RATE
            )
            self.assertEqual(len(pcm), 2 * TEST_FRAMES)
            self.assertAlmostEqual(rms, 0.12, delta=0.002)

    def test_read_note_wav_rejects_wrong_rate(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "060.wav"
            write_test_wav(path, sample_rate=44100)
            with self.assertRaisesRegex(SoundFontBuildError, "expected 48000 Hz"):
                read_note_wav(path, expected_frames=TEST_FRAMES)

    def test_read_note_wav_rejects_wrong_rms(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "060.wav"
            write_test_wav(path)
            with self.assertRaisesRegex(SoundFontBuildError, "RMS"):
                read_note_wav(path, expected_frames=TEST_FRAMES, expected_rms=0.02)

    def test_build_rejects_bad_split_mapping(self) -> None:
        entries = [
            {"midi": 60, "pcm": bytes(200), "rms": 0.06, "source": "guitar"},
            {"midi": 61, "pcm": bytes(200), "rms": 0.06, "source": "bass"},
        ]
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(SoundFontBuildError, "expected bass"):
                build_sf2(entries, Path(directory) / "x.sf2", "T", 60)

    def test_build_and_validate_round_trip(self) -> None:
        midi_values = [60, 61, 62]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            entries = make_entries(root, midi_values, split_midi=61)
            out = root / "test.sf2"
            manifest = build_sf2(entries, out, "Test preset", 61)
            self.assertEqual(manifest["sampleCount"], 3)
            self.assertEqual(manifest["preset"]["bank"], 0)
            parsed = parse_sf2(out)
            self.assertEqual(len(parsed["presets"]), 1)
            self.assertEqual(len(parsed["instruments"]), 1)
            summary = validate(
                out, expected_midis=midi_values, expected_frames=TEST_FRAMES
            )
            self.assertEqual(summary["sampleCount"], 3)
            self.assertEqual(summary["keyRange"], [60, 62])
            self.assertEqual(summary["frames"], TEST_FRAMES)

    def test_validate_compares_embedded_pcm_with_wavs(self) -> None:
        midi_values = [60, 61, 62]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            entries = make_entries(root, midi_values, split_midi=61)
            out = root / "test.sf2"
            build_sf2(entries, out, "Test preset", 61)
            summary = validate(
                out,
                wav_dir=root,
                expected_midis=midi_values,
                expected_frames=TEST_FRAMES,
            )
            self.assertEqual(summary["sampleCount"], 3)

            write_test_wav(root / "061.wav", frames=TEST_FRAMES)
            with wave.open(str(root / "061.wav"), "rb") as handle:
                frames = handle.getnframes()
                payload = handle.readframes(frames)
            corrected = payload[:2] + struct.pack("<h", 1234) + payload[4:]
            with wave.open(str(root / "061.wav"), "wb") as handle:
                handle.setnchannels(1)
                handle.setsampwidth(2)
                handle.setframerate(TEST_RATE)
                handle.writeframes(corrected)
            with self.assertRaisesRegex(Sf2Error, "does not match the WAV"):
                validate(
                    out,
                    wav_dir=root,
                    expected_midis=midi_values,
                    expected_frames=TEST_FRAMES,
                )
