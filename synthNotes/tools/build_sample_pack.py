#!/usr/bin/env python3
"""Build the EARP1 sample pack and its piano descriptor from the tracked SoundFont.

The sample player in core/audio/src/main/cpp/sample_player.cpp reads this pack directly instead
of the SF2 container. The generator is deterministic: the same tracked SF2 always produces the
same pack bytes and the same descriptor fields, so the pack can be hash-pinned and regenerated
in CI.

Input:   synthNotes/StringSynth_A0-C8.sf2 (tracked generator input, not shipped)
         (SHA-256 f3326f4c..., V4 dark calibration: 88 one-shot mono 48 kHz PCM16 samples for
         MIDI 21-108, 0.120 RMS)
Output:  app/src/main/assets/soundfont/earby-string-pack.bin  (EARP1)
         app/src/main/assets/soundfont/piano.json              ("samplePack" block)

The pack layout is documented in sample_player.h and mirrored here:

  header (72 bytes, little-endian):
    0   char[8]  magic "EARP1\\0\\0\\0"
    8   u32      format version (1)
    12  u32      sample rate (48000)
    16  u32      channels (1)
    20  u32      entry count (88)
    24  u32      index offset (72)
    28  u32      data offset (72 + 76 * entry count)
    32  u32      payload bytes (sum of ROM frame counts * 2)
    36  u32      reserved flags (0)
    40  u8[32]   SHA-256 of the whole PCM payload
  index entry (76 bytes each):
    0   u16      MIDI note (21-108)
    2   u16      reserved flags (0)
    4   u32      frame count
    8   u32      absolute file offset of the first PCM byte
    12  u8[32]   SHA-256 of the source SF2 file
    44  u8[32]   SHA-256 of this entry's PCM bytes
  payload: PCM16 little-endian mono frames, entries in ascending MIDI note order.

Usage:
    python3 synthNotes/tools/build_sample_pack.py            # rewrite the pack and descriptor
    python3 synthNotes/tools/build_sample_pack.py --check    # verify the tracked files

Exit codes: 0 success, 1 refusal (bad hash/coverage/rate) or --check mismatch, 2 usage error.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import struct
import sys
from pathlib import Path

# Reuse the independent SF2 reader so the generator and the validator cannot drift apart.
sys.path.insert(0, str(Path(__file__).resolve().parent))
import validate_sf2 as sf2  # noqa: E402  (path set up above)

MAGIC = b"EARP1\x00\x00\x00"
VERSION = 1
SAMPLE_RATE = 48_000
CHANNELS = 1
MIDI_LOW = 21
MIDI_HIGH = 108
EXPECTED_ENTRIES = MIDI_HIGH - MIDI_LOW + 1
HEADER = struct.Struct("<8s8I32s")
ENTRY = struct.Struct("<HHII32s32s")
HEADER_SIZE = HEADER.size
ENTRY_SIZE = ENTRY.size
INDEX_OFFSET = HEADER_SIZE
DATA_OFFSET = HEADER_SIZE + ENTRY_SIZE * EXPECTED_ENTRIES

# Pinned provenance: the bundled SF2 this generator was written against. A new source font needs
# a new constant here and a human review; a silent substitution must fail.
SOURCE_SF2_SHA256 = "f3326f4c53c52a521bce7e9e2932edb9e34807f85a96b391eb224a5a0b499705"

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_SF2 = REPO_ROOT / "synthNotes/StringSynth_A0-C8.sf2"
DEFAULT_PACK = REPO_ROOT / "app/src/main/assets/soundfont/earby-string-pack.bin"
DEFAULT_DESCRIPTOR = REPO_ROOT / "app/src/main/assets/soundfont/piano.json"


class PackError(Exception):
    """Raised when the source or the requested output cannot satisfy the pack contract."""


def sha256_bytes(payload: bytes) -> bytes:
    return hashlib.sha256(payload).digest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with Path(path).open("rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def extract_samples(sf2_path: Path) -> list[tuple[int, bytes]]:
    """Return (midi, PCM16LE bytes) pairs in ascending MIDI order from the pinned SF2."""
    if not sf2_path.is_file():
        raise PackError(f"SoundFont not found: {sf2_path}")
    actual = sha256_file(sf2_path)
    if actual != SOURCE_SF2_SHA256:
        raise PackError(
            f"SoundFont SHA-256 is {actual}, expected {SOURCE_SF2_SHA256}; "
            "refusing to pack an unreviewed font"
        )

    # validate() enforces one bank-0/program-0 preset, one instrument, one zone per key, no
    # loops, a 48 kHz rate and exactly 120000 frames per sample. It raises sf2.Sf2Error.
    try:
        sf2.validate(sf2_path, sample_rate=SAMPLE_RATE)
        font = sf2.parse_sf2(sf2_path)
    except sf2.Sf2Error as error:
        raise PackError(f"SoundFont does not satisfy the pack contract: {error}") from error

    pcm_by_key: dict[int, bytes] = {}
    for zone in font["zones"][0]:
        generators = dict(zone)
        key_range = generators.get(sf2.GEN_KEY_RANGE)
        sample_id = generators.get(sf2.GEN_SAMPLE_ID)
        if key_range is None or sample_id is None:
            raise PackError(f"zone without keyRange/sampleID: {generators}")
        low, high = key_range & 0xFF, (key_range >> 8) & 0xFF
        if low != high:
            raise PackError(f"key zone {low}-{high} is not a single one-shot key")
        if low in pcm_by_key:
            raise PackError(f"duplicate key zone for MIDI {low}")
        sample = font["samples"][sample_id]
        if sample["rate"] != SAMPLE_RATE:
            raise PackError(
                f"MIDI {low}: sample rate {sample['rate']} is not {SAMPLE_RATE} Hz"
            )
        if not (0 <= sample["start"] < sample["end"]):
            raise PackError(f"MIDI {low}: invalid sample offsets")
        pcm_by_key[low] = font["sampleData"][sample["start"] * 2:sample["end"] * 2]

    expected = set(range(MIDI_LOW, MIDI_HIGH + 1))
    missing = sorted(expected - set(pcm_by_key))
    if missing:
        raise PackError(f"SoundFont is missing MIDI keys {missing[0]}-{missing[-1]}")
    if len(pcm_by_key) != EXPECTED_ENTRIES:
        raise PackError(
            f"expected {EXPECTED_ENTRIES} keys, found {len(pcm_by_key)}"
        )

    result: list[tuple[int, bytes]] = []
    for midi in range(MIDI_LOW, MIDI_HIGH + 1):
        pcm = pcm_by_key[midi]
        if len(pcm) % 2 or len(pcm) == 0:
            raise PackError(f"MIDI {midi}: PCM length {len(pcm)} is not whole 16-bit frames")
        result.append((midi, pcm))
    return result


def build_pack(samples: list[tuple[int, bytes]], source_sha256: bytes) -> tuple[bytes, bytes]:
    """Serialize the pack; returns (bytes, payload SHA-256) so callers can cross-check."""
    payload = b"".join(pcm for _, pcm in samples)
    payload_hash = sha256_bytes(payload)
    offset = DATA_OFFSET
    entries = bytearray()
    for midi, pcm in samples:
        entries += ENTRY.pack(
            midi,
            0,
            len(pcm) // 2,
            offset,
            source_sha256,
            sha256_bytes(pcm),
        )
        offset += len(pcm)
    if offset - DATA_OFFSET != len(payload):
        raise PackError("internal payload offset mismatch")
    header = HEADER.pack(
        MAGIC,
        VERSION,
        SAMPLE_RATE,
        CHANNELS,
        len(samples),
        INDEX_OFFSET,
        DATA_OFFSET,
        len(payload),
        0,
        payload_hash,
    )
    return header + bytes(entries) + payload, payload_hash


def descriptor_block(
    pack: bytes,
    samples: list[tuple[int, bytes]],
    payload_hash: bytes,
    source_path: Path = DEFAULT_SF2,
) -> dict:
    generator_sha = sha256_file(Path(__file__).resolve())
    try:
        source_relative = source_path.resolve().relative_to(REPO_ROOT).as_posix()
    except ValueError:
        source_relative = source_path.name
    if any(len(pcm) == 0 or len(pcm) % 2 for _, pcm in samples):
        raise PackError("pack entries must be whole, non-empty 16-bit frames")
    frames = {len(pcm) // 2 for _, pcm in samples}
    if len(frames) != 1:
        raise PackError(f"pack entries do not share one frame count: {sorted(frames)}")
    return {
        "file": DEFAULT_PACK.name,
        "format": "EARP1",
        "version": VERSION,
        "sampleRate": SAMPLE_RATE,
        "channels": CHANNELS,
        "entries": len(samples),
        "midiLow": samples[0][0],
        "midiHigh": samples[-1][0],
        "framesPerEntry": frames.pop(),
        "bytes": len(pack),
        "sha256": hashlib.sha256(pack).hexdigest(),
        "payloadSha256": payload_hash.hex(),
        "sourceSf2Path": source_relative,
        "sourceSf2Bytes": source_path.stat().st_size,
        "sourceSf2Sha256": SOURCE_SF2_SHA256,
        "generator": {
            "script": str(Path(__file__).resolve().relative_to(REPO_ROOT)).replace("\\", "/"),
            "sha256": generator_sha,
        },
    }


def write_descriptor(path: Path, block: dict) -> None:
    if not path.is_file():
        raise PackError(f"descriptor not found: {path}")
    descriptor = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(descriptor, dict):
        raise PackError(f"{path} is not a JSON object")
    descriptor["samplePack"] = block
    text = json.dumps(descriptor, indent=2, ensure_ascii=False) + "\n"
    path.write_text(text, encoding="utf-8")


def check_descriptor(path: Path, block: dict) -> list[str]:
    problems: list[str] = []
    if not path.is_file():
        return [f"descriptor not found: {path}"]
    try:
        descriptor = json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, UnicodeDecodeError) as error:
        return [f"descriptor is not valid JSON: {error}"]
    current = descriptor.get("samplePack")
    if current != block:
        problems.append("descriptor samplePack block is stale; rerun the generator")
    return problems


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Build the EARP1 sample pack from the tracked SF2")
    parser.add_argument("--sf2", type=Path, default=DEFAULT_SF2, help="tracked SoundFont input")
    parser.add_argument("--pack", type=Path, default=DEFAULT_PACK, help="EARP1 output")
    parser.add_argument("--descriptor", type=Path, default=DEFAULT_DESCRIPTOR,
                        help="piano descriptor to update")
    parser.add_argument("--check", action="store_true",
                        help="verify the tracked pack and descriptor without writing")
    args = parser.parse_args(argv)

    try:
        samples = extract_samples(Path(args.sf2))
        source_sha = bytes.fromhex(SOURCE_SF2_SHA256)
        pack, payload_hash = build_pack(samples, source_sha)
        block = descriptor_block(pack, samples, payload_hash, Path(args.sf2))
    except (PackError, sf2.Sf2Error, OSError) as error:
        print(f"build_sample_pack: {error}", file=sys.stderr)
        return 1

    pack_path = Path(args.pack)
    descriptor_path = Path(args.descriptor)

    if args.check:
        problems: list[str] = []
        if not pack_path.is_file():
            problems.append(f"pack not found: {pack_path}")
        elif pack_path.read_bytes() != pack:
            problems.append("tracked pack bytes differ from a fresh build; rerun the generator")
        problems.extend(check_descriptor(descriptor_path, block))
        if problems:
            for problem in problems:
                print(f"FAIL: {problem}", file=sys.stderr)
            return 1
        print(f"PASS: {pack_path} ({len(pack)} bytes, sha256 {block['sha256']})")
        return 0

    pack_path.write_bytes(pack)
    write_descriptor(descriptor_path, block)
    summary = {
        "pack": str(pack_path.relative_to(REPO_ROOT)),
        "bytes": len(pack),
        "sha256": block["sha256"],
        "payloadSha256": payload_hash.hex(),
        "entries": len(samples),
        "midiLow": samples[0][0],
        "midiHigh": samples[-1][0],
        "framesPerEntry": block["framesPerEntry"],
        "sourceSf2Sha256": SOURCE_SF2_SHA256,
        "descriptor": str(descriptor_path.relative_to(REPO_ROOT)),
    }
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
