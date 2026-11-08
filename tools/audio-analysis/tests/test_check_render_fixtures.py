#!/usr/bin/env python3
"""Tests for the rendered-fixture analysis.

Each malformed condition the analysis is supposed to catch gets a synthetic fixture here, so the
checks are proven to fail on bad audio instead of only passing on good audio.
"""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

import numpy as np
import soundfile as sf

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import check_render_fixtures as checker  # noqa: E402

RATE = 48_000


def write_fixture(directory: Path, name: str, notes: list[tuple[int, int, int]], seconds: float,
                  *, tone: bool = True, extra_gap_audio: bool = False, nan: bool = False,
                  amplitude: float = 0.3) -> None:
    frames = int(round(seconds * RATE))
    mono = np.zeros(frames, dtype=np.float64)
    for midi, start, end in notes:
        if not tone:
            continue
        t = np.arange(end - start) / RATE
        mono[start:end] = amplitude * np.sin(2 * np.pi * checker.midi_hz(midi) * t)
    if extra_gap_audio and len(notes) == 2:
        gap_start, gap_end = notes[0][2] + int(0.05 * RATE), notes[1][1]
        mono[gap_start:gap_end] = 0.2
    if nan:
        mono[100] = float("nan")
    sf.write(directory / f"{name}.wav", mono, RATE, subtype="FLOAT")
    (directory / f"{name}.json").write_text(
        json.dumps({
            "fixture": name,
            "sampleRate": RATE,
            "channels": 1,
            "totalFrames": frames,
            "velocity": 96,
            "notes": [{"midi": m, "startFrame": s, "endFrame": e} for m, s, e in notes],
        }),
        encoding="utf-8",
    )


class RenderFixtureCheckTest(unittest.TestCase):

    def _check(self, name: str, spec: dict, build) -> list[str]:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            build(root)
            return checker.check(root, name, spec)

    def test_a_well_formed_fixture_passes(self) -> None:
        spec = {"notes": [60], "seconds": 1.0}
        problems = self._check(
            "c4", spec,
            lambda root: write_fixture(root, "c4", [(60, 0, RATE)], 1.0),
        )
        self.assertEqual([], problems)

    def test_a_silent_fixture_fails(self) -> None:
        spec = {"notes": [60], "seconds": 1.0}
        problems = self._check(
            "c4", spec,
            lambda root: write_fixture(root, "c4", [(60, 0, RATE)], 1.0, tone=False),
        )
        self.assertTrue(any("silent" in problem for problem in problems), problems)

    def test_wrong_frame_count_fails(self) -> None:
        # The fixture carries a full second of audio while the schedule asks for half a second.
        spec = {"notes": [60], "seconds": 0.5}
        problems = self._check(
            "c4", spec,
            lambda root: write_fixture(root, "c4", [(60, 0, RATE)], 1.0),
        )
        self.assertTrue(any("frames" in problem for problem in problems), problems)

    def test_audio_inside_a_declared_gap_fails(self) -> None:
        spec = {"notes": [60, 67], "seconds": 1.5}
        problems = self._check(
            "ascending_c4_g4", spec,
            lambda root: write_fixture(
                root, "ascending_c4_g4",
                [(60, 0, int(0.7 * RATE)), (67, int(0.8 * RATE), int(1.5 * RATE))],
                1.5, extra_gap_audio=True,
            ),
        )
        self.assertTrue(any("gap" in problem for problem in problems), problems)

    def test_audio_before_the_first_note_fails(self) -> None:
        spec = {"notes": [60], "seconds": 1.0}

        def build(root: Path) -> None:
            frames = RATE
            t_axis = np.arange(frames) / RATE
            mono = np.zeros(frames, dtype=np.float64)
            # Sound starts at 0.3 s while the schedule declares the note at 0.5 s.
            onset = int(0.3 * RATE)
            mono[onset:] = 0.3 * np.sin(2 * np.pi * checker.midi_hz(60) * t_axis[:frames - onset])
            sf.write(root / "c4.wav", mono, RATE, subtype="FLOAT")
            (root / "c4.json").write_text(
                json.dumps({"fixture": "c4", "sampleRate": RATE, "channels": 1, "totalFrames": frames,
                            "velocity": 96,
                            "notes": [{"midi": 60, "startFrame": int(0.5 * RATE), "endFrame": frames}]}),
                encoding="utf-8",
            )

        problems = self._check("c4", spec, build)
        self.assertTrue(any("before the first note" in problem for problem in problems), problems)

    def test_non_finite_audio_fails(self) -> None:
        spec = {"notes": [60], "seconds": 1.0}
        problems = self._check(
            "c4", spec,
            lambda root: write_fixture(root, "c4", [(60, 0, RATE)], 1.0, nan=True),
        )
        self.assertTrue(any("NaN" in problem for problem in problems), problems)

    def test_missing_marker_sidecar_fails(self) -> None:
        spec = {"notes": [60], "seconds": 1.0}
        problems = self._check(
            "c4", spec,
            lambda root: sf.write(root / "c4.wav", np.zeros(RATE), RATE, subtype="FLOAT"),
        )
        self.assertTrue(any("marker" in problem for problem in problems), problems)

    def test_wrong_marker_pitch_fails(self) -> None:
        spec = {"notes": [60], "seconds": 1.0}
        problems = self._check(
            "c4", spec,
            lambda root: write_fixture(root, "c4", [(67, 0, RATE)], 1.0),
        )
        self.assertTrue(any("marker declares MIDI 67" in problem for problem in problems), problems)

    def test_detuned_audio_fails_the_pitch_check(self) -> None:
        spec = {"notes": [60], "seconds": 1.0}

        def build(root: Path) -> None:
            frames = RATE
            t = np.arange(frames) / RATE
            # 60 cents sharp: outside the 15 cent tolerance for piano fixtures.
            mono = 0.3 * np.sin(2 * np.pi * checker.midi_hz(60) * 1.035 * t)
            sf.write(root / "c4.wav", mono, RATE, subtype="FLOAT")
            (root / "c4.json").write_text(
                json.dumps({"fixture": "c4", "sampleRate": RATE, "channels": 1, "totalFrames": frames,
                            "velocity": 96, "notes": [{"midi": 60, "startFrame": 0, "endFrame": frames}]}),
                encoding="utf-8",
            )

        problems = self._check("c4", spec, build)
        self.assertTrue(any("cents" in problem for problem in problems), problems)


if __name__ == "__main__":
    unittest.main()
