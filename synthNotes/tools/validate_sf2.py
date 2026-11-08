#!/usr/bin/env python3
"""Validate the exported string-synthesis SF2 independently of the writer.

Checks the RIFF/SF2 container, the single bank-0/program-0 preset, one
instrument, one key zone per sample, root key and correction, sample rate,
no-loop one-shot flags, contiguous MIDI 21-108 coverage, PCM equality with the
final WAV set, RMS, and the mapping JSON.

Usage:
    python3 tools/validate_sf2.py out/string_synth.sf2 \
        --wav-dir out/audio/notes --mapping out/mapping.json
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


class Sf2Error(Exception):
    """Raised when the file or its export contract is invalid."""


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with Path(path).open("rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def read_chunks(path: Path) -> tuple[bytes, dict[str, bytes]]:
    data = Path(path).read_bytes()
    if len(data) < 12 or data[:4] != b"RIFF" or data[8:12] != b"sfbk":
        raise Sf2Error("not a RIFF/sfbk file")
    riff_size = struct.unpack_from("<I", data, 4)[0]
    if riff_size + 8 != len(data):
        raise Sf2Error(f"RIFF size {riff_size} does not match file length {len(data) - 8}")
    chunks: dict[str, bytes] = {}
    position = 12
    while position + 8 <= len(data):
        chunk_id = data[position:position + 4]
        size = struct.unpack_from("<I", data, position + 4)[0]
        payload = data[position + 8:position + 8 + size]
        if len(payload) != size:
            raise Sf2Error(f"truncated chunk {chunk_id!r}")
        if chunk_id == b"LIST":
            if len(payload) < 4:
                raise Sf2Error("LIST chunk without a form type")
            subtype = payload[:4].decode("latin-1")
            inner = position + 12
            inner_end = position + 8 + size
            while inner + 8 <= inner_end:
                inner_id = data[inner:inner + 4]
                inner_size = struct.unpack_from("<I", data, inner + 4)[0]
                inner_payload = data[inner + 8:inner + 8 + inner_size]
                if len(inner_payload) != inner_size:
                    raise Sf2Error(f"truncated subchunk {inner_id!r}")
                chunks[f"{subtype}:{inner_id.decode('latin-1')}"] = inner_payload
                inner += 8 + inner_size + (inner_size & 1)
        else:
            chunks[chunk_id.decode("latin-1")] = payload
        position += 8 + size + (size & 1)
    return data, chunks


def _text(raw: bytes) -> str:
    return raw.split(b"\x00", 1)[0].decode("latin-1", "replace")


def _records(payload: bytes, size: int, label: str) -> list[bytes]:
    if len(payload) % size:
        raise Sf2Error(f"{label} length {len(payload)} is not a multiple of {size}")
    return [payload[i:i + size] for i in range(0, len(payload), size)]


def parse_sf2(path: Path) -> dict:
    data, chunks = read_chunks(Path(path))
    required = [
        "INFO:ifil", "INFO:INAM", "sdta:smpl", "pdta:phdr", "pdta:pbag",
        "pdta:pmod", "pdta:pgen", "pdta:inst", "pdta:ibag", "pdta:imod",
        "pdta:igen", "pdta:shdr",
    ]
    missing = [name for name in required if name not in chunks]
    if missing:
        raise Sf2Error(f"missing chunks: {', '.join(missing)}")

    version = struct.unpack("<HH", chunks["INFO:ifil"][:4])
    if version != (2, 1):
        raise Sf2Error(f"unexpected SF2 version {version}")

    phdr = _records(chunks["pdta:phdr"], 38, "phdr")
    pbag = _records(chunks["pdta:pbag"], 4, "pbag")
    pgen = _records(chunks["pdta:pgen"], 4, "pgen")
    inst = _records(chunks["pdta:inst"], 22, "inst")
    ibag = _records(chunks["pdta:ibag"], 4, "ibag")
    igen = _records(chunks["pdta:igen"], 4, "igen")
    shdr = _records(chunks["pdta:shdr"], 46, "shdr")
    pmod = chunks["pdta:pmod"]
    imod = chunks["pdta:imod"]
    if len(pmod) < 10:
        raise Sf2Error("pmod is shorter than one terminator record")
    if len(imod) < 10:
        raise Sf2Error("imod is shorter than one terminator record")

    presets = []
    for index in range(len(phdr) - 1):
        name = _text(phdr[index][:20])
        program, bank, bag_index = struct.unpack_from("<HHH", phdr[index], 20)
        next_bag = struct.unpack_from("<H", phdr[index + 1], 24)[0]
        instruments = []
        for bag in range(bag_index, min(next_bag, len(pbag) - 1)):
            gen_start = struct.unpack_from("<H", pbag[bag], 0)[0]
            gen_end = struct.unpack_from("<H", pbag[bag + 1], 0)[0]
            for gen in range(gen_start, gen_end):
                operator, amount = struct.unpack("<HH", pgen[gen])
                if operator == GEN_INSTRUMENT:
                    instruments.append(amount)
        presets.append(
            {"name": name, "program": program, "bank": bank, "instruments": instruments}
        )

    instruments = []
    instrument_records = []
    for index in range(len(inst) - 1):
        name = _text(inst[index][:20])
        bag_index = struct.unpack_from("<H", inst[index], 20)[0]
        next_bag = struct.unpack_from("<H", inst[index + 1], 20)[0]
        zones = []
        for bag in range(bag_index, min(next_bag, len(ibag) - 1)):
            gen_start = struct.unpack_from("<H", ibag[bag], 0)[0]
            gen_end = struct.unpack_from("<H", ibag[bag + 1], 0)[0]
            generators = [
                struct.unpack("<HH", igen[gen]) for gen in range(gen_start, gen_end)
            ]
            zones.append(generators)
        instruments.append({"name": name, "zones": zones})
        instrument_records.append(zones)

    samples = []
    for record in shdr[:-1]:
        name = _text(record[:20])
        start, end, loop_start, loop_end, rate, pitch, correction, link, sample_type = (
            struct.unpack_from("<IIIIIBbHH", record, 20)
        )
        samples.append(
            {
                "name": name,
                "start": start,
                "end": end,
                "loopStart": loop_start,
                "loopEnd": loop_end,
                "rate": rate,
                "pitch": pitch,
                "correction": correction,
                "link": link,
                "type": sample_type,
            }
        )
    if not samples:
        raise Sf2Error("no sample headers")
    return {
        "path": str(path),
        "bytes": len(data),
        "infoName": _text(chunks["INFO:INAM"]),
        "presets": presets,
        "instruments": instruments,
        "zones": instrument_records,
        "samples": samples,
        "sampleData": chunks["sdta:smpl"],
        "pmodBytes": len(pmod),
        "imodBytes": len(imod),
    }


def _read_wav(path: Path, expected_frames: int, sample_rate: int) -> tuple[bytes, float]:
    with wave.open(str(path), "rb") as handle:
        if handle.getnchannels() != 1:
            raise Sf2Error(f"{path.name}: not mono")
        if handle.getsampwidth() != 2:
            raise Sf2Error(f"{path.name}: not 16-bit PCM")
        if handle.getframerate() != sample_rate:
            raise Sf2Error(f"{path.name}: sample rate {handle.getframerate()}")
        frames = handle.getnframes()
        if frames != expected_frames:
            raise Sf2Error(f"{path.name}: {frames} frames, expected {expected_frames}")
        pcm = handle.readframes(frames)
    values = struct.unpack(f"<{frames}h", pcm)
    rms = math.sqrt(sum(value * value for value in values) / frames) / 32768.0
    return pcm, rms


def validate(
    path: Path,
    *,
    wav_dir: Path | None = None,
    mapping_path: Path | None = None,
    expected_midis: list[int] | None = None,
    sample_rate: int = SAMPLE_RATE,
    expected_frames: int = FRAMES,
) -> dict:
    font = parse_sf2(Path(path))
    if not font["infoName"]:
        raise Sf2Error("INFO:INAM is empty")
    if len(font["presets"]) != 1:
        raise Sf2Error(f"expected exactly one preset, found {len(font['presets'])}")
    preset = font["presets"][0]
    if (preset["bank"], preset["program"]) != (0, 0):
        raise Sf2Error(f"preset is bank {preset['bank']} program {preset['program']}")
    if len(font["instruments"]) != 1:
        raise Sf2Error(f"expected exactly one instrument, found {len(font['instruments'])}")
    if preset["instruments"] != [0]:
        raise Sf2Error(f"preset does not reference instrument 0: {preset['instruments']}")

    zones = font["zones"][0]
    if not zones:
        raise Sf2Error("instrument has no zones")
    if len(zones) != len(font["samples"]):
        raise Sf2Error(
            f"{len(zones)} zones but {len(font['samples'])} samples; expected one zone each"
        )
    sample_indexes = set()
    keys = []
    for index, generators in enumerate(zones):
        amounts = dict(generators)
        if set(amounts) - {GEN_KEY_RANGE, GEN_VEL_RANGE, GEN_SAMPLE_MODES,
                           GEN_OVERRIDING_ROOT_KEY, GEN_SAMPLE_ID}:
            raise Sf2Error(f"zone {index}: unexpected generators {sorted(set(amounts))}")
        if generators[-1][0] != GEN_SAMPLE_ID:
            raise Sf2Error(f"zone {index}: sampleID is not the last generator")
        key_range = amounts.get(GEN_KEY_RANGE)
        if key_range is None:
            raise Sf2Error(f"zone {index}: missing keyRange")
        low, high = key_range & 0xFF, (key_range >> 8) & 0xFF
        if low != high:
            raise Sf2Error(f"zone {index}: keyRange {low}-{high} is not a single key")
        if amounts.get(GEN_SAMPLE_MODES, 0) != 0:
            raise Sf2Error(f"zone {index}: sampleModes is not 0 (no loop)")
        if amounts.get(GEN_OVERRIDING_ROOT_KEY) != low:
            raise Sf2Error(f"zone {index}: overridingRootKey is not {low}")
        if amounts.get(GEN_SAMPLE_ID) != index:
            raise Sf2Error(f"zone {index}: sampleID is not {index}")
        keys.append(low)
        sample_indexes.add(amounts[GEN_SAMPLE_ID])
    if sorted(sample_indexes) != list(range(len(font["samples"]))):
        raise Sf2Error("sample coverage is incomplete")
    if keys != sorted(keys) or len(set(keys)) != len(keys):
        raise Sf2Error("key zones are not sorted and unique")
    if keys != list(range(keys[0], keys[0] + len(keys))):
        raise Sf2Error("key zones are not contiguous")
    if expected_midis is None:
        expected_midis = list(range(MIDI_LOW, MIDI_HIGH + 1))
    if keys != list(expected_midis):
        raise Sf2Error(f"key zones {keys[0]}-{keys[-1]} do not match the expected set")

    sample_data = font["sampleData"]
    for index, (key, sample) in enumerate(zip(keys, font["samples"])):
        if sample["name"] != f"{key:03d}":
            raise Sf2Error(f"sample {index}: name {sample['name']!r} does not match key {key}")
        if sample["pitch"] != key:
            raise Sf2Error(f"sample {index}: originalPitch {sample['pitch']} != {key}")
        if sample["correction"] != 0:
            raise Sf2Error(f"sample {index}: pitchCorrection {sample['correction']} != 0")
        if sample["rate"] != sample_rate:
            raise Sf2Error(f"sample {index}: sample rate {sample['rate']} != {sample_rate}")
        if sample["loopStart"] != 0 or sample["loopEnd"] != 0:
            raise Sf2Error(f"sample {index}: loop points are not 0")
        if sample["type"] != 1:
            raise Sf2Error(f"sample {index}: sampleType {sample['type']} is not mono")
        if sample["link"] != 0:
            raise Sf2Error(f"sample {index}: link {sample['link']} != 0")
        if not (0 <= sample["start"] < sample["end"]):
            raise Sf2Error(f"sample {index}: invalid start/end")
        if sample["end"] * 2 > len(sample_data):
            raise Sf2Error(f"sample {index}: end exceeds the smpl chunk")
        if sample["end"] - sample["start"] != expected_frames:
            raise Sf2Error(
                f"sample {index}: {sample['end'] - sample['start']} frames, "
                f"expected {expected_frames}"
            )

    summary = {
        "file": font["path"],
        "bytes": font["bytes"],
        "sha256": sha256_file(Path(path)),
        "preset": {"name": preset["name"], "bank": preset["bank"],
                   "program": preset["program"]},
        "instrument": font["instruments"][0]["name"],
        "sampleCount": len(font["samples"]),
        "keyRange": [keys[0], keys[-1]],
        "sampleRate": sample_rate,
        "frames": expected_frames,
        "pmodBytes": font["pmodBytes"],
        "imodBytes": font["imodBytes"],
    }

    split_midi = None
    declared = {}
    if mapping_path is not None:
        mapping = json.loads(Path(mapping_path).read_text(encoding="utf-8"))
        split_midi = int(mapping["splitMidi"])
        declared = {int(item["midi"]): item for item in mapping.get("files", [])}
        if split_midi < keys[0] or split_midi >= keys[-1]:
            raise Sf2Error(f"splitMidi {split_midi} is outside the mapped key range")
    if wav_dir is not None:
        wav_dir = Path(wav_dir)
        sources = {}
        for key, sample in zip(keys, font["samples"]):
            wav_path = wav_dir / f"{key:03d}.wav"
            pcm, rms = _read_wav(wav_path, expected_frames, sample_rate)
            if abs(rms - RMS_TARGET) > RMS_TOLERANCE:
                raise Sf2Error(f"{wav_path.name}: RMS {rms:.4f} is outside tolerance")
            embedded = sample_data[sample["start"] * 2:sample["end"] * 2]
            if embedded != pcm:
                raise Sf2Error(f"{wav_path.name}: SF2 PCM does not match the WAV")
            if split_midi is not None:
                expected_source = "bass" if key <= split_midi else "guitar"
                sources[key] = expected_source
                if key in declared and declared[key].get("source") != expected_source:
                    raise Sf2Error(f"{wav_path.name}: mapping source mismatch")
                if key in declared and declared[key].get("sha256") not in (
                    None,
                    "",
                    sha256_file(wav_path),
                ):
                    raise Sf2Error(f"{wav_path.name}: sha256 does not match the mapping")
        summary["wavDir"] = str(wav_dir)
        if sources:
            summary["sourceCounts"] = {
                source: sum(1 for value in sources.values() if value == source)
                for source in ("bass", "guitar")
            }
    if split_midi is not None:
        summary["splitMidi"] = split_midi
    return summary


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Validate the exported SF2")
    parser.add_argument("path", type=Path)
    parser.add_argument("--wav-dir", type=Path)
    parser.add_argument("--mapping", type=Path)
    args = parser.parse_args(argv)
    try:
        summary = validate(args.path, wav_dir=args.wav_dir, mapping_path=args.mapping)
    except (Sf2Error, OSError, KeyError, wave.Error) as error:
        print(f"validate_sf2: {error}", file=sys.stderr)
        return 1
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
