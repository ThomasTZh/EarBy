#!/usr/bin/env python3
"""Independent music21 oracle and fixture generator for the v0.2.x theory catalogs.

The generator owns its own copy of the documented chord/scale tables, builds the expected
spellings with music21 intervals and scale classes, and refuses to write fixtures when the two
disagree. Tables that cannot be spelled within -2..2 accidentals are recorded as expected
failures instead of being relabeled. Output is canonical JSON: sorted keys, no timestamps, no
absolute paths, so two clean generations are byte-identical.

Usage:
    uv run python generate.py --out ../../core/music/src/test/resources/music-fixtures
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from music21 import interval, pitch, scale

CHORD_QUALITIES = {
    "major": {"semitones": [0, 4, 7], "letters": [0, 2, 4], "intervals": ["P1", "M3", "P5"]},
    "minor": {"semitones": [0, 3, 7], "letters": [0, 2, 4], "intervals": ["P1", "m3", "P5"]},
    "diminished": {"semitones": [0, 3, 6], "letters": [0, 2, 4], "intervals": ["P1", "m3", "d5"]},
    "augmented": {"semitones": [0, 4, 8], "letters": [0, 2, 4], "intervals": ["P1", "M3", "A5"]},
    "sus2": {"semitones": [0, 2, 7], "letters": [0, 1, 4], "intervals": ["P1", "M2", "P5"]},
    "sus4": {"semitones": [0, 5, 7], "letters": [0, 3, 4], "intervals": ["P1", "P4", "P5"]},
    "major_seventh": {
        "semitones": [0, 4, 7, 11],
        "letters": [0, 2, 4, 6],
        "intervals": ["P1", "M3", "P5", "M7"],
    },
    "minor_seventh": {
        "semitones": [0, 3, 7, 10],
        "letters": [0, 2, 4, 6],
        "intervals": ["P1", "m3", "P5", "m7"],
    },
    "dominant_seventh": {
        "semitones": [0, 4, 7, 10],
        "letters": [0, 2, 4, 6],
        "intervals": ["P1", "M3", "P5", "m7"],
    },
    "half_diminished": {
        "semitones": [0, 3, 6, 10],
        "letters": [0, 2, 4, 6],
        "intervals": ["P1", "m3", "d5", "m7"],
    },
    "diminished_seventh": {
        "semitones": [0, 3, 6, 9],
        "letters": [0, 2, 4, 6],
        "intervals": ["P1", "m3", "d5", "d7"],
    },
}

SCALES = {
    "major": {
        "semitones": [0, 2, 4, 5, 7, 9, 11],
        "letters": [0, 1, 2, 3, 4, 5, 6],
        "music21": "MajorScale",
    },
    "natural_minor": {
        "semitones": [0, 2, 3, 5, 7, 8, 10],
        "letters": [0, 1, 2, 3, 4, 5, 6],
        "music21": "MinorScale",
    },
    "harmonic_minor": {
        "semitones": [0, 2, 3, 5, 7, 8, 11],
        "letters": [0, 1, 2, 3, 4, 5, 6],
        "music21": "HarmonicMinorScale",
    },
    "jazz_melodic_minor": {
        "semitones": [0, 2, 3, 5, 7, 9, 11],
        "letters": [0, 1, 2, 3, 4, 5, 6],
        "music21": "MelodicMinorScale",
        "convention": "same collection ascending and descending; the library's descending form "
                      "is natural minor and is not used",
    },
    "dorian": {
        "semitones": [0, 2, 3, 5, 7, 9, 10],
        "letters": [0, 1, 2, 3, 4, 5, 6],
        "music21": "DorianScale",
    },
    "phrygian": {
        "semitones": [0, 1, 3, 5, 7, 8, 10],
        "letters": [0, 1, 2, 3, 4, 5, 6],
        "music21": "PhrygianScale",
    },
    "lydian": {
        "semitones": [0, 2, 4, 6, 7, 9, 11],
        "letters": [0, 1, 2, 3, 4, 5, 6],
        "music21": "LydianScale",
    },
    "mixolydian": {
        "semitones": [0, 2, 4, 5, 7, 9, 10],
        "letters": [0, 1, 2, 3, 4, 5, 6],
        "music21": "MixolydianScale",
    },
    "locrian": {
        "semitones": [0, 1, 3, 5, 6, 8, 10],
        "letters": [0, 1, 2, 3, 4, 5, 6],
        "music21": "LocrianScale",
    },
    "major_pentatonic": {
        "semitones": [0, 2, 4, 7, 9],
        "letters": [0, 1, 2, 4, 5],
        "intervals": ["P1", "M2", "M3", "P5", "M6"],
    },
    "minor_pentatonic": {
        "semitones": [0, 3, 5, 7, 10],
        "letters": [0, 2, 3, 4, 6],
        "intervals": ["P1", "m3", "P4", "P5", "m7"],
    },
    "minor_blues": {
        "semitones": [0, 3, 5, 6, 7, 10],
        "letters": [0, 2, 3, 4, 4, 6],
        "intervals": ["P1", "m3", "P4", "d5", "P5", "m7"],
        "convention": "the altered fifth (b5) and natural fifth share the fourth letter with "
                      "different accidentals",
    },
    "whole_tone": {
        "semitones": [0, 2, 4, 6, 8, 10],
        "letters": [0, 1, 2, 3, 4, 5],
        "intervals": ["P1", "M2", "M3", "A4", "A5", "A6"],
        "convention": "explicit six-letter pattern 1,2,3,#4,#5,#6; the library's default "
                      "whole-tone spelling is not used",
    },
}

LETTERS = ["C", "D", "E", "F", "G", "A", "B"]

SHARP_ROOTS = [
    "C4", "C#4", "D4", "D#4", "E4", "F4", "F#4", "G4", "G#4", "A4", "A#4", "B4",
]
FLAT_ROOTS = [
    "C4", "Db4", "D4", "Eb4", "E4", "F4", "Gb4", "G4", "Ab4", "A4", "Bb4", "B4",
]


def as_parts(value: pitch.Pitch) -> dict:
    """A library-neutral spelling: letter, accidental -2..2, written octave and MIDI value."""
    accidental = int(value.accidental.alter) if value.accidental else 0
    return {
        "letter": value.step,
        "accidental": accidental,
        "octave": value.octave,
        "midi": int(value.midi),
    }


def parts_midi(letter: str, accidental: int, octave: int) -> int:
    marks = "#" * accidental if accidental > 0 else "-" * -accidental
    return int(pitch.Pitch(f"{letter}{marks}{octave}").midi)


def spelled_member(root_name: str, letter_step: int, target_midi: int) -> dict:
    """The theoretical spelling of one member from the root letter and its sounding value."""
    root = pitch.Pitch(root_name)
    letter = LETTERS[(LETTERS.index(root.step) + letter_step) % 7]
    for octave in range(
        min(root.octave, target_midi // 12 - 1) - 2, max(root.octave, target_midi // 12 + 1) + 3
    ):
        for accidental in range(-2, 3):
            if parts_midi(letter, accidental, octave) == target_midi:
                return {
                    "letter": letter,
                    "accidental": accidental,
                    "octave": octave,
                    "midi": target_midi,
                }
    raise SystemExit(f"cannot spell {target_midi} as letter {letter}")


def parts_only(value: dict) -> dict:
    return {key: value[key] for key in ("letter", "accidental", "octave")}


def generate_chords() -> dict:
    fixtures = []
    unrepresentable = []
    for root_name in SHARP_ROOTS + FLAT_ROOTS:
        for quality_id, quality in CHORD_QUALITIES.items():
            try:
                fixtures.append(chord_fixture(root_name, quality_id, quality))
            except SystemExit as failure:
                unrepresentable.append(
                    {"root": root_name, "quality": quality_id, "reason": str(failure)}
                )
    return {"chords": fixtures, "unrepresentable": unrepresentable}


def chord_fixture(root_name: str, quality_id: str, quality: dict) -> dict:
    root = pitch.Pitch(root_name)
    root_midi = int(root.midi)
    expected = [
        as_parts(root.transpose(interval.Interval(name))) for name in quality["intervals"]
    ]
    computed = [as_parts(root)]
    for index, semitone in enumerate(quality["semitones"]):
        if index == 0:
            continue
        computed.append(spelled_member(root_name, quality["letters"][index], root_midi + semitone))
    if [parts_only(value) for value in expected] != [parts_only(value) for value in computed]:
        raise SystemExit(
            f"{root_name} {quality_id}: music21 spells {expected}, table spells {computed}"
        )
    base_notes = [value["midi"] for value in expected]
    inversions = []
    for inversion in range(len(base_notes)):
        sounding = sorted(
            [note + 12 if index < inversion else note for index, note in enumerate(base_notes)]
        )
        if any(not 0 <= note <= 127 for note in sounding):
            continue
        inversions.append(
            {
                "inversion": inversion,
                "sounding": sounding,
                "bassMidi": sounding[0],
            }
        )
    return {
        "root": root_name,
        "rootMidi": root_midi,
        "quality": quality_id,
        "members": expected,
        "memberMidi": base_notes,
        "inversions": inversions,
    }


def generate_scales() -> dict:
    fixtures = []
    unrepresentable = []
    for root_name in SHARP_ROOTS + FLAT_ROOTS:
        for kind_id, kind in SCALES.items():
            try:
                fixtures.append(scale_fixture(root_name, kind_id, kind))
            except SystemExit as failure:
                unrepresentable.append(
                    {"root": root_name, "kind": kind_id, "reason": str(failure)}
                )
    return {"scales": fixtures, "unrepresentable": unrepresentable}


def scale_fixture(root_name: str, kind_id: str, kind: dict) -> dict:
    root = pitch.Pitch(root_name)
    root_midi = int(root.midi)
    computed = [as_parts(root)]
    for index, letter_step in enumerate(kind["letters"]):
        if index == 0:
            continue
        computed.append(
            spelled_member(root_name, letter_step, root_midi + kind["semitones"][index])
        )
    computed.append(as_parts(root.transpose(interval.Interval("P8"))))

    if "intervals" in kind:
        expected = [
            as_parts(root.transpose(interval.Interval(name))) for name in kind["intervals"]
        ]
        expected.append(as_parts(root.transpose(interval.Interval("P8"))))
        if [parts_only(value) for value in expected] != [parts_only(value) for value in computed]:
            raise SystemExit(
                f"{root_name} {kind_id}: music21 spells {expected}, table spells {computed}"
            )
    else:
        class_name = kind["music21"]
        oracle = getattr(scale, class_name)(root_name)
        top = str(root.transpose(interval.Interval("P8")))
        oracle_names = [str(value) for value in oracle.getPitches(root_name, top)]
        oracle_collection = sorted({int(pitch.Pitch(name).midi) % 12 for name in oracle_names})
        table_collection = sorted({value["midi"] % 12 for value in computed[:-1]})
        if oracle_collection != table_collection and "convention" not in kind:
            raise SystemExit(
                f"{root_name} {kind_id}: music21 collection {oracle_collection} does not match "
                f"the table {table_collection}"
            )
        expected = computed

    descending = list(reversed(expected))
    return {
        "root": root_name,
        "rootMidi": root_midi,
        "kind": kind_id,
        "ascending": expected,
        "ascendingMidi": [value["midi"] for value in expected],
        "descending": descending,
        "descendingMidi": [value["midi"] for value in descending],
        "both": expected + descending[1:],
        "bothMidi": [value["midi"] for value in expected + descending[1:]],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=None)
    args = parser.parse_args()
    out = args.out or Path(__file__).resolve().parent / "fixtures"
    out.mkdir(parents=True, exist_ok=True)
    (out / "chords.json").write_text(
        json.dumps(generate_chords(), indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    (out / "scales.json").write_text(
        json.dumps(generate_scales(), indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    print(f"wrote fixtures to {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
