#!/usr/bin/env python3
"""Validate the bundled instrument descriptor, its EARP1 pack and its tracked SF2 source.

Read-only: it never rewrites the descriptor, the pack or the generator input. The shipped asset is
the EARP1 pack; the SoundFont is a tracked generator input under `synthNotes/`, not a packaged
asset. The validator checks the descriptor schema, the pack header, payload and every per-entry
hash, and the source SF2 size/hash/RIFF identity. A pending human review is reported separately.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import struct
import sys
from pathlib import Path

SCHEMA_VERSION = 1
SUPPORTED_SCHEMA_VERSIONS = (1,)
EAP1_MAGIC = b"EARP1\x00\x00\x00"
EAP1_VERSION = 1
EAP1_SAMPLE_RATE = 48_000
EAP1_CHANNELS = 1
EAP1_ENTRIES = 88
EAP1_MIDI_LOW = 21
EAP1_MIDI_HIGH = 108
EAP1_HEADER = struct.Struct("<8s8I32s")
EAP1_ENTRY = struct.Struct("<HHII32s32s")
SAMPLE_PACK_REQUIRED = (
    "file",
    "format",
    "version",
    "sampleRate",
    "channels",
    "entries",
    "midiLow",
    "midiHigh",
    "framesPerEntry",
    "bytes",
    "sha256",
    "payloadSha256",
    "sourceSf2Path",
    "sourceSf2Bytes",
    "sourceSf2Sha256",
    "generator",
)
REQUIRED_FIELDS = (
    "schemaVersion",
    "soundfontId",
    "bank",
    "program",
    "presetName",
    "source",
    "review",
    "samplePack",
)


class ValidationError(Exception):
    """Raised for a fatal descriptor, pack or source problem."""


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def _repo_root(descriptor_path: Path) -> Path:
    """Repository root for a descriptor at <root>/app/src/main/assets/soundfont/piano.json."""
    resolved = descriptor_path.resolve()
    if len(resolved.parents) > 5:
        return resolved.parents[5]
    return resolved.parent


def _is_hex_sha256(value: object) -> bool:
    return (
        isinstance(value, str)
        and len(value) == 64
        and all(character in "0123456789abcdef" for character in value)
    )


def _validate_source_sf2(block: dict, descriptor_path: Path) -> None:
    """Checks the tracked generator SoundFont named by the pack block."""
    relative = Path(str(block["sourceSf2Path"]))
    if relative.is_absolute() or ".." in relative.parts or relative.suffix.lower() != ".sf2":
        raise ValidationError(
            f"samplePack.sourceSf2Path must be a relative .sf2 path ('{relative}')"
        )
    root = _repo_root(descriptor_path)
    source = (root / relative).resolve()
    if not str(source).startswith(str(root.resolve())):
        raise ValidationError(f"'{relative}' escapes the repository root")
    if not source.is_file():
        raise ValidationError(f"generator SoundFont '{relative}' does not exist")
    size = source.stat().st_size
    if size != block["sourceSf2Bytes"]:
        raise ValidationError(
            f"samplePack says the source SF2 is {block['sourceSf2Bytes']} bytes but "
            f"'{relative}' is {size} bytes"
        )
    if not _is_hex_sha256(block["sourceSf2Sha256"]):
        raise ValidationError("samplePack.sourceSf2Sha256 must be 64 lowercase hex characters")
    actual = _sha256(source)
    if actual != block["sourceSf2Sha256"]:
        raise ValidationError(
            f"SHA-256 mismatch for generator SoundFont '{relative}' "
            f"(descriptor {block['sourceSf2Sha256'][:12]}..., file {actual[:12]}...)"
        )
    header = source.read_bytes()[:12]
    if header[:4] != b"RIFF" or header[8:12] != b"sfbk":
        raise ValidationError(f"'{relative}' is not an SF2 sound bank")


def _validate_sample_pack(descriptor: dict, descriptor_path: Path) -> None:
    """Validates the EARP1 pack shipped in the asset directory."""
    block = descriptor["samplePack"]
    if not isinstance(block, dict):
        raise ValidationError("'samplePack' must be an object")
    for name in SAMPLE_PACK_REQUIRED:
        if name not in block:
            raise ValidationError(f"'samplePack' is missing required field '{name}'")
    if block["format"] != "EARP1":
        raise ValidationError(f"samplePack.format must be 'EARP1' but was {block['format']!r}")
    for name in ("version", "sampleRate", "channels", "entries", "midiLow", "midiHigh",
                 "framesPerEntry", "bytes", "sourceSf2Bytes"):
        if not isinstance(block[name], int) or isinstance(block[name], bool):
            raise ValidationError(f"'samplePack.{name}' must be an integer")
    if (block["version"], block["sampleRate"], block["channels"], block["entries"]) != (
            EAP1_VERSION, EAP1_SAMPLE_RATE, EAP1_CHANNELS, EAP1_ENTRIES):
        raise ValidationError("samplePack header fields do not match the EARP1 contract")
    if (block["midiLow"], block["midiHigh"]) != (EAP1_MIDI_LOW, EAP1_MIDI_HIGH):
        raise ValidationError("samplePack MIDI range is not 21-108")
    if block["framesPerEntry"] <= 0:
        raise ValidationError("samplePack.framesPerEntry must be positive")
    for name in ("sha256", "payloadSha256", "sourceSf2Sha256"):
        if not _is_hex_sha256(block[name]):
            raise ValidationError(f"'samplePack.{name}' must be 64 lowercase hex characters")
    source_sha = descriptor["source"].get("sourceSha256")
    if source_sha != block["sourceSf2Sha256"]:
        raise ValidationError("samplePack.sourceSf2Sha256 does not match source.sourceSha256")
    generator = block["generator"]
    if not isinstance(generator, dict) or not isinstance(generator.get("script"), str) \
            or not isinstance(generator.get("sha256"), str):
        raise ValidationError("samplePack.generator must record script and sha256")

    relative = Path(str(block["file"]))
    if relative.is_absolute() or ".." in relative.parts or relative.suffix.lower() != ".bin":
        raise ValidationError(f"samplePack.file must be a relative .bin path ('{relative}')")
    asset = (descriptor_path.parent / relative).resolve()
    if not str(asset).startswith(str(descriptor_path.parent.resolve())):
        raise ValidationError(f"'{relative}' escapes the asset directory")
    if not asset.is_file():
        raise ValidationError(f"sample pack '{relative}' does not exist")

    data = asset.read_bytes()
    if len(data) != block["bytes"]:
        raise ValidationError(
            f"samplePack says {block['bytes']} bytes but '{relative}' is {len(data)} bytes")
    if hashlib.sha256(data).hexdigest() != block["sha256"]:
        raise ValidationError(f"SHA-256 mismatch for sample pack '{relative}'")
    if len(data) < EAP1_HEADER.size:
        raise ValidationError(f"'{relative}' is shorter than the EARP1 header")
    magic, version, sample_rate, channels, entries, index_offset, data_offset, payload_bytes, \
        flags, payload_hash = EAP1_HEADER.unpack_from(data)
    if magic != EAP1_MAGIC:
        raise ValidationError(f"'{relative}' does not start with the EARP1 magic")
    if (version, sample_rate, channels, entries, flags) != (
            EAP1_VERSION, EAP1_SAMPLE_RATE, EAP1_CHANNELS, EAP1_ENTRIES, 0):
        raise ValidationError(f"'{relative}' header fields violate the EARP1 contract")
    expected_data_offset = EAP1_HEADER.size + EAP1_ENTRY.size * EAP1_ENTRIES
    if index_offset != EAP1_HEADER.size or data_offset != expected_data_offset:
        raise ValidationError(f"'{relative}' index/data offsets are not the fixed EARP1 layout")
    if payload_bytes != len(data) - data_offset:
        raise ValidationError(f"'{relative}' payload length disagrees with the file size")
    payload = data[data_offset:]
    if hashlib.sha256(payload).digest() != payload_hash:
        raise ValidationError(f"'{relative}' payload SHA-256 mismatch")
    if payload_hash.hex() != block["payloadSha256"]:
        raise ValidationError("samplePack.payloadSha256 does not match the pack header")

    source_hash = bytes.fromhex(block["sourceSf2Sha256"])
    cursor = 0
    for index in range(EAP1_ENTRIES):
        entry = EAP1_ENTRY.unpack_from(data, index_offset + index * EAP1_ENTRY.size)
        midi, entry_flags, frames, offset, entry_source_hash, pcm_hash = entry
        if midi != EAP1_MIDI_LOW + index or entry_flags != 0 or frames <= 0:
            raise ValidationError(f"sample pack entry {index} has an invalid key/frame count")
        if offset != data_offset + cursor:
            raise ValidationError(f"sample pack entry {index} is not contiguous")
        if entry_source_hash != source_hash:
            raise ValidationError(f"sample pack entry {index} names a different source SF2")
        size = frames * 2
        if cursor + size > payload_bytes:
            raise ValidationError(f"sample pack entry {index} exceeds the payload")
        if hashlib.sha256(payload[cursor:cursor + size]).digest() != pcm_hash:
            raise ValidationError(f"sample pack entry {index} PCM SHA-256 mismatch")
        cursor += size
        if frames != block["framesPerEntry"]:
            raise ValidationError("sample pack entries do not share framesPerEntry")
    if cursor != payload_bytes:
        raise ValidationError("sample pack payload has trailing bytes")
    _validate_source_sf2(block, descriptor_path)


def validate(descriptor_path: Path) -> tuple[list[str], bool]:
    """Returns (warnings, reviewPending); raises ValidationError for a fatal problem."""
    warnings: list[str] = []
    review_pending = False
    if not descriptor_path.is_file():
        raise ValidationError(f"descriptor not found: {descriptor_path}")
    try:
        descriptor = json.loads(descriptor_path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, UnicodeDecodeError) as error:
        raise ValidationError(f"descriptor is not valid UTF-8 JSON: {error}") from error
    if not isinstance(descriptor, dict):
        raise ValidationError("descriptor root must be a JSON object")

    for name in REQUIRED_FIELDS:
        if name not in descriptor:
            raise ValidationError(f"descriptor is missing required field '{name}'")
    if descriptor["schemaVersion"] not in SUPPORTED_SCHEMA_VERSIONS:
        raise ValidationError(
            f"unsupported schemaVersion {descriptor['schemaVersion']!r}; "
            f"supported: {list(SUPPORTED_SCHEMA_VERSIONS)}",
        )
    for name in ("soundfontId", "presetName"):
        if not isinstance(descriptor[name], str) or not descriptor[name].strip():
            raise ValidationError(f"descriptor field '{name}' must be a non-empty string")
    for name in ("bank", "program"):
        if not isinstance(descriptor[name], int) or isinstance(descriptor[name], bool):
            raise ValidationError(f"descriptor field '{name}' must be an integer")
    if not 0 <= descriptor["bank"] <= 128:
        raise ValidationError(f"bank must be in 0..128 but was {descriptor['bank']}")
    if not 0 <= descriptor["program"] <= 127:
        raise ValidationError(f"program must be in 0..127 but was {descriptor['program']}")

    source = descriptor["source"]
    if not isinstance(source, dict):
        raise ValidationError("'source' must be an object")
    for name in ("name", "url", "upstreamVersion", "license"):
        if not isinstance(source.get(name), str) or not source[name].strip():
            raise ValidationError(f"'source.{name}' must be a non-empty string")
    for name in ("sourceSha256", "modifications", "buildNotes"):
        if name in source and not isinstance(source[name], str):
            raise ValidationError(f"'source.{name}' must be a string")
    if not _is_hex_sha256(source.get("sourceSha256")):
        raise ValidationError("'source.sourceSha256' must be 64 lowercase hex characters")

    _validate_sample_pack(descriptor, descriptor_path)

    review = descriptor["review"]
    if not isinstance(review, dict) or not isinstance(review.get("humanAccepted"), bool):
        raise ValidationError("'review.humanAccepted' must be a boolean")
    if not review["humanAccepted"]:
        review_pending = True
        warnings.append("instrument review is pending; G2 cannot close on machine checks alone")
    return warnings, review_pending


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Validate the bundled piano instrument asset")
    parser.add_argument("descriptor", nargs="?", default="app/src/main/assets/soundfont/piano.json")
    parser.add_argument(
        "--require-reviewed",
        action="store_true",
        help="fail when the instrument review is still pending",
    )
    args = parser.parse_args(argv)
    try:
        warnings, review_pending = validate(Path(args.descriptor))
    except (ValidationError, OSError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1
    for warning in warnings:
        print(f"PENDING: {warning}")
    if args.require_reviewed and review_pending:
        print("FAIL: the piano instrument has no accepted listening review", file=sys.stderr)
        return 1
    print(f"PASS: validated instrument descriptor {args.descriptor}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
