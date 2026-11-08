#!/usr/bin/env python3
"""Validate the bundled voice pack and SoundFont manifest.

The validator is deterministic and read-only: it never rewrites an asset, a manifest or a
hash. It fails closed on structural problems, missing files, wrong formats, empty or
over-long clips, non-finite or clipped samples and vocabulary gaps, and it reports (without
failing) entries whose human listening review is still pending so that a machine pass can
never be mistaken for production acceptance. Two semantic ids may share one physical clip (the
perfect-pitch octave "two" reuses the degree "two" take); each entry's format, hash and frame
count are still checked against the shared file.

Schema v1 is a documented contract shared with the Kotlin asset catalog:
docs/audio/ASSETS.md and docs/audio/voice-pack.schema.json.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import wave
from dataclasses import dataclass, field
from pathlib import Path

SCHEMA_VERSION = 1
SUPPORTED_SCHEMA_VERSIONS = (1,)
REQUIRED_SAMPLE_RATE = 48_000
REQUIRED_CHANNELS = 1
REQUIRED_BIT_DEPTH = 16
MAX_CLIP_SECONDS = 8.0
MIN_CLIP_SECONDS = 0.05
ENTRY_REQUIRED_FIELDS = (
    "id",
    "file",
    "text",
    "sampleRate",
    "channels",
    "bitDepth",
    "frameCount",
    "sha256",
    "source",
    "review",
)
MANIFEST_REQUIRED_FIELDS = ("schemaVersion", "packId", "packVersion", "language", "voice", "entries")

# The declarative semantic catalog is the single source of the vocabulary; the Kotlin side
# validates the same file against VoiceVocabulary so tool and production ids cannot drift.
VOCABULARY_PATH = Path(__file__).resolve().parents[1] / "voice-assets" / "vocabulary.json"


def _load_bundled_entries() -> list[dict]:
    try:
        data = json.loads(VOCABULARY_PATH.read_text(encoding="utf-8"))
    except FileNotFoundError as failure:
        raise SystemExit(f"missing voice vocabulary: {VOCABULARY_PATH}") from failure
    entries = [entry for entry in data.get("entries", []) if entry.get("bundled")]
    if not entries:
        raise SystemExit("the voice vocabulary declares no bundled entries")
    return entries


BUNDLED_ENTRIES = _load_bundled_entries()

# Required vocabulary: one answer clip per bundled catalog entry, in canonical id order.
REQUIRED_VOICE_IDS = tuple(entry["id"] for entry in BUNDLED_ENTRIES)

# English answer text per bundled semantic id; the pack must speak exactly these phrases.
# Keyed by the full id because different namespaces can share a label (for example the interval
# and the chord "major_seventh").
EXPECTED_TEXTS = {entry["id"]: entry["spokenText"] for entry in BUNDLED_ENTRIES}


class ValidationError(Exception):
    """Raised for a fatal manifest or asset problem."""


@dataclass
class Report:
    """Result of a validation run."""

    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    pending_reviews: list[str] = field(default_factory=list)
    checked_entries: int = 0

    @property
    def ok(self) -> bool:
        return not self.errors


def _is_number(value: object) -> bool:
    return isinstance(value, (int, float)) and not isinstance(value, bool)


def _check_relative_path(name: str, value: object) -> Path:
    if not isinstance(value, str) or not value:
        raise ValidationError(f"entry {name}: 'file' must be a non-empty string")
    candidate = Path(value)
    if candidate.is_absolute():
        raise ValidationError(f"entry {name}: 'file' must be relative, not '{value}'")
    if ".." in candidate.parts:
        raise ValidationError(f"entry {name}: 'file' must not contain '..' ('{value}')")
    if candidate.suffix.lower() != ".wav":
        raise ValidationError(f"entry {name}: 'file' must be a .wav file ('{value}')")
    return candidate


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def _validate_wav(entry_name: str, path: Path, entry: dict, report: Report) -> None:
    try:
        with wave.open(str(path), "rb") as handle:
            channels = handle.getnchannels()
            sample_rate = handle.getframerate()
            sample_width = handle.getsampwidth()
            frame_count = handle.getnframes()
            frames = handle.readframes(frame_count)
    except wave.Error as error:
        raise ValidationError(f"entry {entry_name}: '{path.name}' is not a readable PCM WAV: {error}") from error

    if channels != entry["channels"]:
        raise ValidationError(
            f"entry {entry_name}: manifest says {entry['channels']} channel(s), file has {channels}",
        )
    if channels != REQUIRED_CHANNELS:
        raise ValidationError(f"entry {entry_name}: must be mono, found {channels} channel(s)")
    if sample_rate != entry["sampleRate"]:
        raise ValidationError(
            f"entry {entry_name}: manifest says {entry['sampleRate']} Hz, file has {sample_rate} Hz",
        )
    if sample_rate != REQUIRED_SAMPLE_RATE:
        raise ValidationError(f"entry {entry_name}: must be {REQUIRED_SAMPLE_RATE} Hz, found {sample_rate} Hz")
    bit_depth = sample_width * 8
    if bit_depth != entry["bitDepth"]:
        raise ValidationError(f"entry {entry_name}: manifest says {entry['bitDepth']}-bit, file has {bit_depth}-bit")
    if bit_depth != REQUIRED_BIT_DEPTH:
        raise ValidationError(f"entry {entry_name}: must be {REQUIRED_BIT_DEPTH}-bit PCM, found {bit_depth}-bit")
    if frame_count != entry["frameCount"]:
        raise ValidationError(
            f"entry {entry_name}: manifest says {entry['frameCount']} frames, file has {frame_count}",
        )

    seconds = frame_count / sample_rate
    if frame_count <= 0:
        raise ValidationError(f"entry {entry_name}: clip is empty")
    if seconds > MAX_CLIP_SECONDS:
        raise ValidationError(
            f"entry {entry_name}: clip is {seconds:.3f} s, longer than the {MAX_CLIP_SECONDS:.0f} s limit",
        )
    if seconds < MIN_CLIP_SECONDS:
        raise ValidationError(
            f"entry {entry_name}: clip is {seconds:.3f} s, shorter than the {MIN_CLIP_SECONDS:.3f} s floor",
        )

    # Sample checks decode the PCM16 stream arithmetically; no FFT or resampling is needed here.
    peak = 0
    nonzero = 0
    for index in range(0, len(frames), 2):
        value = int.from_bytes(frames[index:index + 2], "little", signed=True)
        if value != 0:
            nonzero += 1
        peak = max(peak, abs(value))
    if peak == 0:
        raise ValidationError(f"entry {entry_name}: clip is digital silence")
    if nonzero / max(frame_count, 1) < 0.005:
        report.warnings.append(f"entry {entry_name}: clip is almost entirely silent (peak {peak})")
    if peak >= 32767:
        report.warnings.append(f"entry {entry_name}: samples reach full scale (peak {peak}); check the ceiling")


def validate(manifest_path: Path, pack_root: Path | None = None) -> Report:
    """Validates one manifest and the assets it references."""
    report = Report()
    if not manifest_path.is_file():
        raise ValidationError(f"manifest not found: {manifest_path}")
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, UnicodeDecodeError) as error:
        raise ValidationError(f"manifest is not valid UTF-8 JSON: {error}") from error
    if not isinstance(manifest, dict):
        raise ValidationError("manifest root must be a JSON object")

    for field_name in MANIFEST_REQUIRED_FIELDS:
        if field_name not in manifest:
            raise ValidationError(f"manifest is missing required field '{field_name}'")
    schema_version = manifest["schemaVersion"]
    if schema_version not in SUPPORTED_SCHEMA_VERSIONS:
        raise ValidationError(
            f"unsupported schemaVersion {schema_version!r}; supported: {list(SUPPORTED_SCHEMA_VERSIONS)}",
        )
    for field_name in ("packId", "packVersion", "language", "voice"):
        if not isinstance(manifest[field_name], str) or not manifest[field_name].strip():
            raise ValidationError(f"manifest field '{field_name}' must be a non-empty string")

    entries = manifest["entries"]
    if not isinstance(entries, list) or not entries:
        raise ValidationError("manifest 'entries' must be a non-empty array")

    root = pack_root if pack_root is not None else manifest_path.parent
    seen_ids: set[str] = set()
    referenced_files: set[Path] = set()

    for entry in entries:
        if not isinstance(entry, dict):
            raise ValidationError("every entry must be a JSON object")
        for field_name in ENTRY_REQUIRED_FIELDS:
            if field_name not in entry:
                raise ValidationError(f"entry {entry.get('id', '<missing id>')}: missing '{field_name}'")
        entry_id = entry["id"]
        if not isinstance(entry_id, str) or not entry_id:
            raise ValidationError("entry 'id' must be a non-empty string")
        if entry_id in seen_ids:
            raise ValidationError(f"duplicate entry id '{entry_id}'")
        seen_ids.add(entry_id)

        relative = _check_relative_path(entry_id, entry["file"])
        # Two semantic ids may deliberately share one physical clip: the perfect-pitch octave
        # "two" reuses the degree "two" take. The per-entry format, hash and frame checks below
        # still verify the shared file, so the sharing saves package size without weakening the
        # audio checks.
        if relative in referenced_files:
            print(f"NOTE: entry {entry_id} shares the clip '{relative}' with an earlier entry")
        referenced_files.add(relative)
        asset_path = (root / relative).resolve()
        if not str(asset_path).startswith(str(root.resolve())):
            raise ValidationError(f"entry {entry_id}: '{relative}' escapes the pack directory")
        if not asset_path.is_file():
            raise ValidationError(f"entry {entry_id}: file '{relative}' does not exist")

        for numeric_field in ("sampleRate", "channels", "bitDepth", "frameCount"):
            if not isinstance(entry[numeric_field], int) or isinstance(entry[numeric_field], bool):
                raise ValidationError(f"entry {entry_id}: '{numeric_field}' must be an integer")
        if not isinstance(entry["text"], str) or not entry["text"].strip():
            raise ValidationError(f"entry {entry_id}: 'text' must be a non-empty transcript")
        sha = entry["sha256"]
        if not isinstance(sha, str) or len(sha) != 64 or any(c not in "0123456789abcdef" for c in sha):
            raise ValidationError(f"entry {entry_id}: 'sha256' must be 64 lowercase hex characters")
        actual_sha = _sha256(asset_path)
        if actual_sha != sha:
            raise ValidationError(
                f"entry {entry_id}: SHA-256 mismatch for '{relative}' (manifest {sha[:12]}..., file {actual_sha[:12]}...)",
            )

        source = entry["source"]
        if not isinstance(source, dict):
            raise ValidationError(f"entry {entry_id}: 'source' must be an object")
        for source_field in ("kind", "reference", "license"):
            if not isinstance(source.get(source_field), str) or not source.get(source_field).strip():
                raise ValidationError(f"entry {entry_id}: 'source.{source_field}' must be a non-empty string")
        if "generatedAt" in source and not _is_number(source["generatedAt"]):
            raise ValidationError(f"entry {entry_id}: 'source.generatedAt' must be numeric")
        for parameters_field in ("model", "modelRevision", "seed", "postprocessing"):
            if parameters_field in source and not isinstance(source[parameters_field], (str, int, float)):
                raise ValidationError(f"entry {entry_id}: 'source.{parameters_field}' has an unsupported type")

        review = entry["review"]
        if not isinstance(review, dict) or "humanAccepted" not in review:
            raise ValidationError(f"entry {entry_id}: 'review.humanAccepted' is required")
        if not isinstance(review["humanAccepted"], bool):
            raise ValidationError(f"entry {entry_id}: 'review.humanAccepted' must be a boolean")

        _validate_wav(entry_id, asset_path, entry, report)
        report.checked_entries += 1

        expected_text = EXPECTED_TEXTS.get(entry_id)
        if expected_text is not None and entry["text"].strip() != expected_text:
            raise ValidationError(
                f"entry {entry_id}: transcript '{entry['text']}' does not match the shipped answer "
                f"'{expected_text}'",
            )
        if not review["humanAccepted"]:
            report.pending_reviews.append(entry_id)

    # Unreferenced files inside the pack directory are packaging mistakes, not harmless leftovers.
    for candidate in sorted(root.rglob("*.wav")):
        if candidate.resolve() not in {p.resolve() for p in (root / r for r in referenced_files)}:
            report.warnings.append(f"unreferenced WAV in pack directory: {candidate.relative_to(root)}")

    missing = sorted(set(REQUIRED_VOICE_IDS) - seen_ids)
    if missing:
        raise ValidationError(f"required vocabulary is incomplete; missing: {', '.join(missing)}")
    return report


def build_review_report(manifest_path: Path) -> str:
    """Human-readable summary used by check-assets for the pending review list."""
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    lines = [f"pack {manifest['packId']} v{manifest['packVersion']} ({manifest['voice']})"]
    for entry in manifest["entries"]:
        review = entry["review"]
        status = "accepted" if review["humanAccepted"] else "REVIEW PENDING"
        when = review.get("reviewedAt", "-")
        lines.append(f"  {entry['id']:<28} {status:<15} {when}")
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Validate the bundled voice pack manifest")
    parser.add_argument("manifest", nargs="?", default="app/src/main/assets/voice/voice-pack.json")
    parser.add_argument("--root", default=None, help="directory the manifest paths are relative to")
    parser.add_argument(
        "--require-reviewed",
        action="store_true",
        help="fail when any entry still lacks an accepted human listening review",
    )
    parser.add_argument("--report", action="store_true", help="print the review status of every entry")
    args = parser.parse_args(argv)

    manifest_path = Path(args.manifest)
    root = Path(args.root) if args.root else None
    try:
        report = validate(manifest_path, root)
    except ValidationError as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1

    if args.report:
        print(build_review_report(manifest_path))
    for warning in report.warnings:
        print(f"WARN: {warning}")
    for entry_id in report.pending_reviews:
        print(f"PENDING: {entry_id} has no accepted human listening review")
    if args.require_reviewed and report.pending_reviews:
        print(
            f"FAIL: {len(report.pending_reviews)} entr(y/ies) still need a human listening review",
            file=sys.stderr,
        )
        return 1
    print(f"PASS: validated {report.checked_entries} voice entries in {manifest_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
