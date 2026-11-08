#!/usr/bin/env python3
"""Split a spoken letter list into the seven note-name clips.

Owner request 2026-10-14: the isolated A..G letter clips are usable but not fully satisfying;
record the list "A, B, C, D, E, F, G" in one take and split it into seven files, so every
letter keeps list prosody. Synthesis and postprocessing are identical to the pack generator.

This script writes candidates; it is not a routine check and never touches bundled assets.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import wave
from pathlib import Path

from generate_piper_voice_pack import MODEL_FILES, MODELS_DIR, VOICE_PROVENANCE
from generate_voice_pack import postprocess

LETTERS = "ABCDEFG"
DEFAULT_TEXT = "A, B, C, D, E, F, G."
SILENCE_PATTERN = re.compile(r"silence_(start|end): ([0-9.]+)")


def synthesize(model: Path, text: str, length_scale: float, target: Path) -> None:
    """Writes one raw take with Piper, exactly like the pack generator."""
    from piper import PiperVoice
    from piper.config import SynthesisConfig

    voice = PiperVoice.load(str(model))
    target.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(target), "wb") as handle:
        voice.synthesize_wav(text, handle, syn_config=SynthesisConfig(length_scale=length_scale))


def detect_silences(raw: Path) -> list[tuple[float, float]]:
    """Detected silences as (midpoint, duration) in seconds, in order."""
    result = subprocess.run(
        [
            "ffmpeg", "-hide_banner", "-i", str(raw),
            "-af", "silencedetect=noise=-40dB:d=0.12", "-f", "null", "-",
        ],
        capture_output=True,
        text=True,
    )
    starts: list[float] = []
    ends: list[float] = []
    for kind, value in SILENCE_PATTERN.findall(result.stderr):
        if kind == "start":
            starts.append(float(value))
        else:
            ends.append(float(value))
    return [((start + end) / 2, end - start) for start, end in zip(starts, ends)]


def split(raw: Path, boundaries: list[float], out_dir: Path, letters: str) -> list[dict]:
    """Cuts [raw] at [boundaries], postprocesses every piece and reports each file."""
    duration = _duration(raw)
    points = [0.0] + boundaries + [duration]
    records = []
    for index, letter in enumerate(letters):
        start, end = points[index], points[index + 1]
        piece = out_dir / f"{letter}.split.wav"
        subprocess.run(
            [
                "ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-i", str(raw),
                "-af", f"atrim=start={start:.6f}:end={end:.6f},asetpts=PTS-STARTPTS",
                "-ac", "1", "-ar", "48000", "-c:a", "pcm_s16le", str(piece),
            ],
            check=True,
        )
        target = out_dir / f"note.{letter.lower()}.wav"
        detail = postprocess(piece, target)
        piece.unlink()
        records.append(
            {
                "id": f"note.{letter.lower()}",
                "letter": letter,
                "startSeconds": round(start, 6),
                "endSeconds": round(end, 6),
                **detail,
            }
        )
    return records


def _duration(path: Path) -> float:
    with wave.open(str(path), "rb") as handle:
        return handle.getnframes() / handle.getframerate()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--text", default=DEFAULT_TEXT)
    parser.add_argument("--voice", default="alba", choices=sorted(MODEL_FILES))
    parser.add_argument("--length-scale", type=float, default=1.0)
    parser.add_argument("--out", type=Path, default=None)
    parser.add_argument("--raw", type=Path, default=None)
    args = parser.parse_args(argv)

    model = MODELS_DIR / MODEL_FILES[args.voice]
    if not model.is_file():
        raise SystemExit(f"missing Piper model: {model}")
    out_dir = args.out or Path("candidates-letters") / args.voice
    raw = args.raw or Path("raw/piper-letters") / f"{args.voice}.wav"
    out_dir.mkdir(parents=True, exist_ok=True)

    synthesize(model, args.text, args.length_scale, raw)
    silences = detect_silences(raw)
    if len(silences) < len(LETTERS) - 1:
        print(
            f"FAIL: found {len(silences)} silence gaps for {len(LETTERS)} letters; "
            "use a stronger separator in --text",
            file=sys.stderr,
        )
        return 1
    strongest = sorted(silences, key=lambda entry: -entry[1])[: len(LETTERS) - 1]
    boundaries = sorted(midpoint for midpoint, _ in strongest)
    records = split(raw, boundaries, out_dir, LETTERS)
    for record in records:
        if not 0.15 <= record["seconds"] <= 2.5:
            print(f"FAIL: {record['id']} is {record['seconds']:.3f} s", file=sys.stderr)
            return 1

    provenance = VOICE_PROVENANCE[args.voice]
    summary = {
        "generator": "piper",
        "voice": provenance["voice"],
        "modelRevision": provenance["modelRevision"],
        "datasetLicense": provenance["datasetLicense"],
        "text": args.text,
        "lengthScale": args.length_scale,
        "rawDurationSeconds": round(_duration(raw), 3),
        "silences": len(boundaries),
        "phrases": records,
    }
    (out_dir / "letter-split-summary.json").write_text(
        json.dumps(summary, indent=2), encoding="utf-8"
    )
    print(json.dumps({"voice": args.voice, "out": str(out_dir), "letters": len(records)}, indent=2))
    for record in records:
        print(f"  {record['letter']}  {record['seconds']:.3f} s  {record['id']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
