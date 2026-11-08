#!/usr/bin/env python3
"""Build the single bank-0/program-0 SF2 from the reviewed one-shot WAV set.

The builder is deliberately small and uses only the standard library. It
follows the chunk layout of the repository's existing SoundFont writer
(tools/audio-analysis/sf2_tool.py): one preset, one instrument, one key zone
per sample, sampleID last in each zone, RIFF chunk sizes padded to even bytes.

Usage:
    python3 tools/build_sf2.py --wav-dir out/audio/notes \
        --mapping out/mapping.json --out out/string_synth.sf2
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import struct
import sys
import wave
from pathlib import Path

MIDI_LOW = 21
MIDI_HIGH = 108
SAMPLE_RATE = 48000
FRAMES = 120000
RMS_TARGET = 0.120
RMS_TOLERANCE = 0.002

GEN_KEY_RANGE = 43
GEN_VEL_RANGE = 44
GEN_SAMPLE_ID = 53
GEN_SAMPLE_MODES = 54
GEN_OVERRIDING_ROOT_KEY = 58
GEN_INSTRUMENT = 41


class SoundFontBuildError(Exception):
    """Raised when the export inputs or the mapping are invalid."""


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with Path(path).open("rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def _name(text: str, length: int = 20) -> bytes:
    raw = text.encode("latin-1", "replace")[:length]
    return raw + b"\x00" * (length - len(raw))


def chunk(chunk_id: bytes, payload: bytes) -> bytes:
    """Serialize one RIFF chunk; pad to even length and include the pad in size."""
    if len(payload) % 2:
        payload += b"\x00"
    return chunk_id + struct.pack("<I", len(payload)) + payload


def read_note_wav(
    path: Path,
    *,
    expected_frames: int = FRAMES,
    expected_rate: int = SAMPLE_RATE,
    expected_rms: float = RMS_TARGET,
    rms_tolerance: float = RMS_TOLERANCE,
) -> tuple[bytes, float]:
    """Validate one export WAV and return its raw PCM bytes and RMS."""
    path = Path(path)
    if not path.is_file():
        raise SoundFontBuildError(f"missing note file: {path}")
    try:
        with wave.open(str(path), "rb") as handle:
            channels = handle.getnchannels()
            width = handle.getsampwidth()
            rate = handle.getframerate()
            frames = handle.getnframes()
            pcm = handle.readframes(frames)
    except wave.Error as error:
        raise SoundFontBuildError(f"not a readable PCM WAV: {path} ({error})") from error
    if channels != 1:
        raise SoundFontBuildError(f"{path.name}: expected mono, found {channels} channels")
    if width != 2:
        raise SoundFontBuildError(f"{path.name}: expected 16-bit PCM, found {8 * width} bits")
    if rate != expected_rate:
        raise SoundFontBuildError(f"{path.name}: expected {expected_rate} Hz, found {rate} Hz")
    if frames != expected_frames:
        raise SoundFontBuildError(
            f"{path.name}: expected {expected_frames} frames, found {frames}"
        )
    if len(pcm) != 2 * frames:
        raise SoundFontBuildError(f"{path.name}: truncated PCM payload")
    samples = struct.unpack(f"<{frames}h", pcm)
    rms = math.sqrt(sum(value * value for value in samples) / frames) / 32768.0
    if abs(rms - expected_rms) > rms_tolerance:
        raise SoundFontBuildError(
            f"{path.name}: RMS {rms:.4f} is outside {expected_rms:.3f} "
            f"+/- {rms_tolerance:.3f}"
        )
    return pcm, rms


def _zone_generators(midi: int, sample_index: int) -> bytes:
    """Five generators, sampleID last, matching the accepted bundled font order."""
    return b"".join(
        [
            struct.pack("<HH", GEN_KEY_RANGE, midi | (midi << 8)),
            struct.pack("<HH", GEN_VEL_RANGE, 0 | (127 << 8)),
            struct.pack("<HH", GEN_SAMPLE_MODES, 0),
            struct.pack("<HH", GEN_OVERRIDING_ROOT_KEY, midi),
            struct.pack("<HH", GEN_SAMPLE_ID, sample_index),
        ]
    )


def build_sf2(
    entries: list[dict],
    out_path: Path,
    preset_name: str,
    split_midi: int,
    *,
    sample_rate: int = SAMPLE_RATE,
    instrument_name: str | None = None,
) -> dict:
    """Write one preset with one instrument and one zone per entry.

    Each entry is a dict with the keys midi (int), pcm (bytes) and source
    ("bass" or "guitar"). Entries must be sorted by midi.
    """
    entries = sorted(entries, key=lambda entry: entry["midi"])
    if not entries:
        raise SoundFontBuildError("no notes to pack")
    midi_values = [entry["midi"] for entry in entries]
    if midi_values != list(range(midi_values[0], midi_values[-1] + 1)):
        raise SoundFontBuildError("note set must be contiguous")
    if not (MIDI_LOW <= midi_values[0] and midi_values[-1] <= MIDI_HIGH):
        raise SoundFontBuildError("note set must stay inside MIDI 21-108")
    for entry in entries:
        if entry["source"] not in ("bass", "guitar"):
            raise SoundFontBuildError(f"invalid source for MIDI {entry['midi']}")
        expected = "bass" if entry["midi"] <= split_midi else "guitar"
        if entry["source"] != expected:
            raise SoundFontBuildError(
                f"MIDI {entry['midi']} is mapped to {entry['source']}, expected {expected}"
            )

    smpl = bytearray()
    shdr = bytearray()
    igen = bytearray()
    ibag = bytearray()
    sample_records = []
    for index, entry in enumerate(entries):
        pcm = entry["pcm"]
        if len(pcm) % 2:
            raise SoundFontBuildError("PCM payload must be whole 16-bit frames")
        start = len(smpl) // 2
        smpl += pcm
        end = len(smpl) // 2
        shdr += _name(f"{entry['midi']:03d}")
        shdr += struct.pack(
            "<IIIIIBbHH",
            start,
            end,
            0,
            0,
            sample_rate,
            entry["midi"],
            0,
            0,
            1,
        )
        ibag += struct.pack("<HH", len(igen) // 4, 0)
        igen += _zone_generators(entry["midi"], index)
        sample_records.append(
            {
                "midi": entry["midi"],
                "source": entry["source"],
                "name": f"{entry['midi']:03d}",
                "start": start,
                "end": end,
                "frames": end - start,
                "rms": entry["rms"],
            }
        )
    ibag += struct.pack("<HH", len(igen) // 4, 0)
    shdr += _name("EOS") + struct.pack("<IIIIIBbHH", 0, 0, 0, 0, 0, 0, 0, 0, 0)

    instrument_name = instrument_name or f"Bass<={split_midi} / Guitar>{split_midi}"
    inst = (
        _name(instrument_name[:20])
        + struct.pack("<H", 0)
        + _name("EOI")
        + struct.pack("<H", len(entries))
    )
    phdr = (
        _name(preset_name)
        + struct.pack("<HHHIII", 0, 0, 0, 0, 0, 0)
        + _name("EOP")
        + struct.pack("<HHHIII", 0, 0, 1, 0, 0, 0)
    )
    pgen = struct.pack("<HH", GEN_INSTRUMENT, 0)
    pbag = struct.pack("<HH", 0, 0) + struct.pack("<HH", len(pgen) // 4, 0)
    pmod = bytes(10)
    imod = bytes(10)

    pdta = b"pdta" + b"".join(
        [
            chunk(b"phdr", phdr),
            chunk(b"pbag", pbag),
            chunk(b"pmod", pmod),
            chunk(b"pgen", pgen),
            chunk(b"inst", inst),
            chunk(b"ibag", ibag),
            chunk(b"imod", imod),
            chunk(b"igen", igen),
            chunk(b"shdr", shdr),
        ]
    )
    comment = (
        f"Synthetic plucked-string one-shots; bass <= MIDI {split_midi}, "
        f"guitar above; {sample_rate} Hz mono; RMS {RMS_TARGET:.3f}"
    )
    info = b"INFO" + b"".join(
        [
            chunk(b"ifil", struct.pack("<HH", 2, 1)),
            chunk(b"isng", b"EMU8000\x00"),
            chunk(b"INAM", preset_name.encode("latin-1", "replace") + b"\x00"),
            chunk(b"ICMT", comment.encode("latin-1", "replace") + b"\x00"),
        ]
    )
    smpl_list = b"sdta" + chunk(b"smpl", bytes(smpl))
    body = b"sfbk" + chunk(b"LIST", info) + chunk(b"LIST", smpl_list) + chunk(b"LIST", pdta)
    out_path = Path(out_path)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_bytes(b"RIFF" + struct.pack("<I", len(body)) + body)

    manifest = {
        "file": str(out_path),
        "bytes": out_path.stat().st_size,
        "sha256": sha256_file(out_path),
        "preset": {"name": preset_name, "bank": 0, "program": 0},
        "instrument": instrument_name,
        "splitMidi": split_midi,
        "sampleRate": sample_rate,
        "sampleCount": len(entries),
        "sampleDataFrames": len(smpl) // 2,
        "samples": sample_records,
    }
    return manifest


def build_from_directory(
    wav_dir: Path,
    out_path: Path,
    mapping_path: Path,
    *,
    expected_frames: int = FRAMES,
    sample_rate: int = SAMPLE_RATE,
) -> dict:
    wav_dir = Path(wav_dir)
    mapping_path = Path(mapping_path)
    try:
        mapping = json.loads(mapping_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise SoundFontBuildError(f"cannot read mapping {mapping_path}: {error}") from error
    split_midi = int(mapping["splitMidi"])
    if not (MIDI_LOW <= split_midi < MIDI_HIGH):
        raise SoundFontBuildError(f"splitMidi {split_midi} is outside 21-107")
    declared = {
        int(item["midi"]): item for item in mapping.get("files", []) if "midi" in item
    }

    entries = []
    for midi in range(MIDI_LOW, MIDI_HIGH + 1):
        path = wav_dir / f"{midi:03d}.wav"
        pcm, rms = read_note_wav(path, expected_frames=expected_frames, expected_rate=sample_rate)
        source = "bass" if midi <= split_midi else "guitar"
        if midi in declared:
            if declared[midi].get("source") != source:
                raise SoundFontBuildError(
                    f"MIDI {midi}: mapping source {declared[midi].get('source')} "
                    f"does not match split {split_midi}"
                )
            if declared[midi].get("sha256") not in (None, "", sha256_file(path)):
                raise SoundFontBuildError(f"MIDI {midi}: WAV sha256 does not match mapping")
        entries.append({"midi": midi, "pcm": pcm, "rms": rms, "source": source})

    preset_name = str(mapping.get("presetName", "String Synth A0-C8"))
    manifest = build_sf2(
        entries,
        out_path,
        preset_name,
        split_midi,
        sample_rate=sample_rate,
    )
    manifest["mapping"] = str(mapping_path)
    manifest["wavDir"] = str(wav_dir)
    (Path(out_path).with_suffix(".sf2.json")).write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
    )
    return manifest


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Build the single string-synth SF2 export")
    parser.add_argument("--wav-dir", type=Path, required=True)
    parser.add_argument("--mapping", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        manifest = build_from_directory(args.wav_dir, args.out, args.mapping)
    except SoundFontBuildError as error:
        print(f"build_sf2: {error}", file=sys.stderr)
        return 2
    print(json.dumps({k: manifest[k] for k in (
        "file", "bytes", "sha256", "preset", "splitMidi", "sampleCount")}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
