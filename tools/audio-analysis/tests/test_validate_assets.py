#!/usr/bin/env python3
"""Tests for the voice-pack manifest validator.

Every malformed condition named in WP2.1 has a fixture here. The tests build a valid pack in
a temporary directory, then mutate one thing at a time and assert the validator reports it.
"""

from __future__ import annotations

import hashlib
import json
import math
import struct
import sys
import tempfile
import unittest
import wave
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import validate_assets as validator  # noqa: E402
import validate_soundfont  # noqa: E402

SAMPLE_RATE = 48_000


def write_wav(path: Path, frames: int, *, amplitude: int = 8_000, sample_rate: int = SAMPLE_RATE, channels: int = 1,
              bit_depth: int = 16, silent: bool = False) -> None:
    """Writes a small sine-tone PCM WAV with the requested format."""
    path.parent.mkdir(parents=True, exist_ok=True)
    samples = bytearray()
    width = bit_depth // 8
    for index in range(frames):
        value = 0 if silent else int(amplitude * math.sin(2 * math.pi * 440 * index / sample_rate))
        for _ in range(channels):
            if width == 2:
                samples += struct.pack("<h", value)
            else:
                samples += struct.pack("<i", value << 8)
    with wave.open(str(path), "wb") as handle:
        handle.setnchannels(channels)
        handle.setsampwidth(width)
        handle.setframerate(sample_rate)
        handle.writeframes(bytes(samples))


def wav_name(voice_id: str) -> str:
    """Canonical pack-relative file name for a voice id (namespace and label joined)."""
    return "wav/" + voice_id.replace(".", "_") + ".wav"


def entry_for(voice_id: str, path: Path, *, text: str | None = None, frame_count: int | None = None,
              sample_rate: int = SAMPLE_RATE, channels: int = 1, bit_depth: int = 16,
              human_accepted: bool = True, sha_override: str | None = None,
              file_override: str | None = None) -> dict:
    frames = frame_count if frame_count is not None else 24_000
    return {
        "id": voice_id,
        "file": file_override if file_override is not None else wav_name(voice_id),
        "text": text if text is not None else validator.EXPECTED_TEXTS[voice_id],
        "sampleRate": sample_rate,
        "channels": channels,
        "bitDepth": bit_depth,
        "frameCount": frames,
        "sha256": sha_override if sha_override is not None else hashlib.sha256(path.read_bytes()).hexdigest(),
        "source": {
            "kind": "generated",
            "reference": "synthetic-fixture",
            "license": "test fixture",
            "model": "none",
            "modelRevision": "none",
            "seed": 0,
            "postprocessing": "none",
            "generatedAt": 0,
        },
        "review": {"humanAccepted": human_accepted, "reviewedAt": "2026-10-07", "reviewer": "fixture"},
    }


class PackFixture:
    """Builds a complete valid pack in a temporary directory."""

    def __init__(self, root: Path, *, human_accepted: bool = True) -> None:
        self.root = root
        self.manifest = root / "voice-pack.json"
        self.entries: list[dict] = []
        self.build(human_accepted=human_accepted)

    def build(self, *, human_accepted: bool = True) -> None:
        self.entries = []
        for voice_id in validator.REQUIRED_VOICE_IDS:
            path = self.root / wav_name(voice_id)
            write_wav(path, 24_000)
            self.entries.append(entry_for(voice_id, path, human_accepted=human_accepted))
        self.write()

    def write(self) -> None:
        self.manifest.write_text(
            json.dumps(
                {
                    "schemaVersion": 1,
                    "packId": "english-intervals",
                    "packVersion": "1.0.0",
                    "language": "en",
                    "voice": "fixture-voice",
                    "entries": self.entries,
                },
                indent=2,
            ),
            encoding="utf-8",
        )

    def entry(self, voice_id: str) -> dict:
        return next(item for item in self.entries if item["id"] == voice_id)


class ValidPackTest(unittest.TestCase):
    def test_a_complete_reviewed_pack_passes(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fixture = PackFixture(Path(directory))
            report = validator.validate(fixture.manifest)
            self.assertTrue(report.ok, report.errors)
            self.assertEqual(len(validator.REQUIRED_VOICE_IDS), report.checked_entries)
            self.assertEqual([], report.pending_reviews)

    def test_pending_review_is_reported_but_does_not_fail_validation(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fixture = PackFixture(Path(directory), human_accepted=False)
            report = validator.validate(fixture.manifest)
            self.assertTrue(report.ok)
            self.assertEqual(sorted(validator.REQUIRED_VOICE_IDS), sorted(report.pending_reviews))

    def test_require_reviewed_makes_pending_review_a_failure(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fixture = PackFixture(Path(directory), human_accepted=False)
            exit_code = validator.main([str(fixture.manifest), "--require-reviewed"])
            self.assertEqual(1, exit_code)
            reviewed = PackFixture(Path(directory) / "second")
            self.assertEqual(0, validator.main([str(reviewed.manifest), "--require-reviewed"]))


class MalformedManifestTest(unittest.TestCase):
    def _expect_error(self, mutate, *, write: bool = True) -> str:
        """Applies a mutation and asserts validation fails.

        Mutations that touch only the in-memory entry list need `write`; mutations that
        replace the manifest file itself must pass `write=False`.
        """
        with tempfile.TemporaryDirectory() as directory:
            fixture = PackFixture(Path(directory))
            mutate(fixture)
            if write:
                fixture.write()
            with self.assertRaises(validator.ValidationError) as context:
                validator.validate(fixture.manifest)
            return str(context.exception)

    def test_schema_version_is_enforced(self) -> None:
        message = self._expect_error(
            lambda f: f.manifest.write_text(
                json.dumps({"schemaVersion": 99, "packId": "x", "packVersion": "1", "language": "en",
                            "voice": "v", "entries": []}),
            ),
            write=False,
        )
        self.assertIn("unsupported schemaVersion", message)

    def test_duplicate_ids_are_rejected(self) -> None:
        def mutate(fixture: PackFixture) -> None:
            fixture.entries.append(dict(fixture.entry("interval.unison")))

        self.assertIn("duplicate entry id", self._expect_error(mutate))

    def test_missing_file_is_rejected(self) -> None:
        def mutate(fixture: PackFixture) -> None:
            fixture.entry("interval.tritone")["file"] = "wav/does-not-exist.wav"

        self.assertIn("does not exist", self._expect_error(mutate))

    def test_absolute_and_traversing_paths_are_rejected(self) -> None:
        message = self._expect_error(lambda f: f.entry("interval.unison").update({"file": "/etc/passwd.wav"}))
        self.assertIn("must be relative", message)
        message = self._expect_error(lambda f: f.entry("interval.unison").update({"file": "../../evil.wav"}))
        self.assertIn("must not contain '..'", message)
        message = self._expect_error(lambda f: f.entry("interval.unison").update({"file": "wav/note.txt"}))
        self.assertIn("must be a .wav file", message)

    def test_invalid_hash_is_rejected(self) -> None:
        message = self._expect_error(
            lambda f: f.entry("interval.unison").update({"sha256": "0" * 64}),
        )
        self.assertIn("SHA-256 mismatch", message)
        message = self._expect_error(lambda f: f.entry("interval.unison").update({"sha256": "not-a-hash"}))
        self.assertIn("64 lowercase hex", message)

    def test_wrong_format_is_rejected(self) -> None:
        def stereo(fixture: PackFixture) -> None:
            path = fixture.root / "wav" / "stereo.wav"
            write_wav(path, 24_000, channels=2)
            fixture.entry("interval.unison").update(
                entry_for("interval.unison", path, channels=2, file_override="wav/stereo.wav"),
            )

        self.assertIn("must be mono", self._expect_error(stereo))

        def wrong_rate(fixture: PackFixture) -> None:
            path = fixture.root / "wav" / "44k.wav"
            write_wav(path, 24_000, sample_rate=44_100)
            fixture.entry("interval.unison").update(
                entry_for("interval.unison", path, sample_rate=44_100, file_override="wav/44k.wav"),
            )

        self.assertIn("must be 48000 Hz", self._expect_error(wrong_rate))

        def wrong_depth(fixture: PackFixture) -> None:
            path = fixture.root / "wav" / "32bit.wav"
            write_wav(path, 24_000, bit_depth=32)
            fixture.entry("interval.unison").update(
                entry_for("interval.unison", path, bit_depth=32, file_override="wav/32bit.wav"),
            )

        self.assertIn("16-bit PCM", self._expect_error(wrong_depth))

    def test_frame_count_mismatch_is_rejected(self) -> None:
        def mutate(fixture: PackFixture) -> None:
            fixture.entry("interval.unison")["frameCount"] = 12_345

        self.assertIn("manifest says 12345 frames", self._expect_error(mutate))

    def test_empty_and_too_long_clips_are_rejected(self) -> None:
        def empty(fixture: PackFixture) -> None:
            path = fixture.root / "wav" / "empty.wav"
            write_wav(path, 1, silent=True)
            fixture.entry("interval.unison").update(
                entry_for("interval.unison", path, frame_count=1, file_override="wav/empty.wav"),
            )

        self.assertIn("shorter than the 0.050 s floor", self._expect_error(empty))

        def too_long(fixture: PackFixture) -> None:
            frames = SAMPLE_RATE * 9
            path = fixture.root / "wav" / "long.wav"
            write_wav(path, frames)
            fixture.entry("interval.unison").update(
                entry_for("interval.unison", path, frame_count=frames, file_override="wav/long.wav"),
            )

        self.assertIn("longer than the 8 s limit", self._expect_error(too_long))

    def test_digital_silence_is_rejected_and_low_level_is_warned(self) -> None:
        def silent(fixture: PackFixture) -> None:
            path = fixture.root / "wav" / "silent.wav"
            write_wav(path, 24_000, silent=True)
            fixture.entry("interval.unison").update(
                entry_for("interval.unison", path, file_override="wav/silent.wav"),
            )

        self.assertIn("digital silence", self._expect_error(silent))

        def almost_silent(fixture: PackFixture) -> None:
            path = fixture.root / "wav" / "almost.wav"
            write_wav(path, 24_000, amplitude=1)
            fixture.entry("interval.unison").update(
                entry_for("interval.unison", path, file_override="wav/almost.wav"),
            )

        with tempfile.TemporaryDirectory() as directory:
            fixture = PackFixture(Path(directory))
            almost_silent(fixture)
            fixture.write()
            report = validator.validate(fixture.manifest)
            self.assertTrue(report.ok)
            self.assertTrue(any("almost entirely silent" in warning for warning in report.warnings))

    def test_vocabulary_gaps_are_rejected(self) -> None:
        def mutate(fixture: PackFixture) -> None:
            fixture.entries = [e for e in fixture.entries if e["id"] != "interval.octave"]

        self.assertIn("required vocabulary is incomplete", self._expect_error(mutate))

    def test_transcript_mismatch_is_rejected(self) -> None:
        message = self._expect_error(
            lambda f: f.entry("interval.minor_third").update({"text": "Major third"}),
        )
        self.assertIn("does not match the shipped answer", message)

    def test_missing_required_fields_are_rejected(self) -> None:
        message = self._expect_error(lambda f: f.entry("interval.unison").pop("sha256"))
        self.assertIn("missing 'sha256'", message)
        message = self._expect_error(lambda f: f.entry("interval.unison")["review"].pop("humanAccepted"))
        self.assertIn("'review.humanAccepted' is required", message)
        message = self._expect_error(lambda f: f.entry("interval.unison")["source"].pop("license"))
        self.assertIn("'source.license'", message)

    def test_corrupt_json_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory) / "voice-pack.json"
            manifest.write_text("{not json", encoding="utf-8")
            with self.assertRaises(validator.ValidationError) as context:
                validator.validate(manifest)
            self.assertIn("not valid UTF-8 JSON", str(context.exception))

    def test_unreferenced_wav_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fixture = PackFixture(Path(directory))
            write_wav(fixture.root / "wav" / "stray.wav", 24_000)
            report = validator.validate(fixture.manifest)
            self.assertTrue(report.ok)
            self.assertTrue(any("unreferenced WAV" in warning for warning in report.warnings))

    def test_missing_manifest_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(validator.ValidationError) as context:
                validator.validate(Path(directory) / "absent.json")
            self.assertIn("manifest not found", str(context.exception))


class FrameDerivedDurationTest(unittest.TestCase):
    def test_duration_comes_from_frame_count_and_sample_rate(self) -> None:
        frames = 24_000
        self.assertAlmostEqual(0.5, frames / SAMPLE_RATE)
        with tempfile.TemporaryDirectory() as directory:
            fixture = PackFixture(Path(directory))
            report = validator.validate(fixture.manifest)
            self.assertTrue(report.ok)
            for entry in fixture.entries:
                seconds = entry["frameCount"] / entry["sampleRate"]
                self.assertGreater(seconds, 0)
                self.assertLessEqual(seconds, validator.MAX_CLIP_SECONDS)


if __name__ == "__main__":
    unittest.main()


class InstrumentDescriptorTest(unittest.TestCase):
    """Descriptor checks for the pack-only instrument and its tracked generator SF2."""

    def _minimal_sf2(self, path: Path, payload: bytes = b"\x00" * 64) -> bytes:
        path.parent.mkdir(parents=True, exist_ok=True)
        body = b"sfbk" + payload
        data = b"RIFF" + len(body).to_bytes(4, "little") + body
        path.write_bytes(data)
        return data

    def _write_pack(self, path: Path, source_sha: bytes, frames: int = 1) -> bytes:
        path.parent.mkdir(parents=True, exist_ok=True)
        count = validate_soundfont.EAP1_ENTRIES
        data_offset = validate_soundfont.EAP1_HEADER.size + \
            validate_soundfont.EAP1_ENTRY.size * count
        payload = bytearray()
        entries = bytearray()
        for index in range(count):
            pcm = struct.pack("<h", 0x0100) * frames
            entries += validate_soundfont.EAP1_ENTRY.pack(
                validate_soundfont.EAP1_MIDI_LOW + index, 0, frames,
                data_offset + len(payload), source_sha, hashlib.sha256(pcm).digest())
            payload += pcm
        header = validate_soundfont.EAP1_HEADER.pack(
            validate_soundfont.EAP1_MAGIC, validate_soundfont.EAP1_VERSION,
            validate_soundfont.EAP1_SAMPLE_RATE, validate_soundfont.EAP1_CHANNELS,
            count, validate_soundfont.EAP1_HEADER.size, data_offset, len(payload), 0,
            hashlib.sha256(payload).digest())
        data = header + entries + bytes(payload)
        path.write_bytes(data)
        return data

    def _refresh_pack(self, descriptor_path: Path) -> None:
        descriptor = json.loads(descriptor_path.read_text(encoding="utf-8"))
        block = descriptor["samplePack"]
        data = self._write_pack(
            descriptor_path.parent / block["file"],
            bytes.fromhex(block["sourceSf2Sha256"]))
        block["bytes"] = len(data)
        block["sha256"] = hashlib.sha256(data).hexdigest()
        descriptor_path.write_text(json.dumps(descriptor), encoding="utf-8")

    def _descriptor(self, root: Path, *, human_accepted: bool = True, mutate=None) -> Path:
        layout = root / "app/src/main/assets/soundfont"
        layout.mkdir(parents=True, exist_ok=True)
        source_path = root / "synthNotes/StringSynth_A0-C8.sf2"
        source = self._minimal_sf2(source_path)
        source_sha = hashlib.sha256(source).digest()
        pack = layout / "earby-string-pack.bin"
        pack_data = self._write_pack(pack, source_sha)
        descriptor = {
            "schemaVersion": 1,
            "soundfontId": "fixture-string-synth",
            "bank": 0,
            "program": 0,
            "presetName": "Fixture synth",
            "source": {
                "name": "Fixture",
                "url": "local fixture",
                "upstreamVersion": "fixture",
                "license": "project",
                "sourceSha256": source_sha.hex(),
                "modifications": "fixture",
                "buildNotes": "fixture",
            },
            "review": {"humanAccepted": human_accepted},
            "samplePack": {
                "file": "earby-string-pack.bin",
                "format": "EARP1",
                "version": validate_soundfont.EAP1_VERSION,
                "sampleRate": validate_soundfont.EAP1_SAMPLE_RATE,
                "channels": validate_soundfont.EAP1_CHANNELS,
                "entries": validate_soundfont.EAP1_ENTRIES,
                "midiLow": validate_soundfont.EAP1_MIDI_LOW,
                "midiHigh": validate_soundfont.EAP1_MIDI_HIGH,
                "framesPerEntry": 1,
                "bytes": len(pack_data),
                "sha256": hashlib.sha256(pack_data).hexdigest(),
                "payloadSha256": hashlib.sha256(
                    pack_data[descriptor_data_offset():]).hexdigest(),
                "sourceSf2Path": "synthNotes/StringSynth_A0-C8.sf2",
                "sourceSf2Bytes": len(source),
                "sourceSf2Sha256": source_sha.hex(),
                "generator": {"script": "fixture", "sha256": "0" * 64},
            },
        }
        if mutate is not None:
            mutate(descriptor)
        path = layout / "piano.json"
        path.write_text(json.dumps(descriptor), encoding="utf-8")
        return path

    def test_a_valid_descriptor_passes(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = self._descriptor(Path(directory))
            warnings, review_pending = validate_soundfont.validate(path)
            self.assertEqual([], warnings)
            self.assertFalse(review_pending)

    def test_pending_review_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = self._descriptor(Path(directory), human_accepted=False)
            warnings, review_pending = validate_soundfont.validate(path)
            self.assertTrue(any("pending" in warning for warning in warnings))
            self.assertTrue(review_pending)

    def test_missing_pack_and_source_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = self._descriptor(root)
            (root / "app/src/main/assets/soundfont/earby-string-pack.bin").unlink()
            with self.assertRaises(validate_soundfont.ValidationError) as context:
                validate_soundfont.validate(path)
            self.assertIn("does not exist", str(context.exception))

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = self._descriptor(root)
            (root / "synthNotes/StringSynth_A0-C8.sf2").unlink()
            with self.assertRaises(validate_soundfont.ValidationError) as context:
                validate_soundfont.validate(path)
            self.assertIn("does not exist", str(context.exception))

    def test_pack_and_source_hash_or_size_mismatch_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = self._descriptor(
                Path(directory), mutate=lambda d: d["samplePack"].update({"sha256": "0" * 64}))
            with self.assertRaises(validate_soundfont.ValidationError) as context:
                validate_soundfont.validate(path)
            self.assertIn("SHA-256 mismatch for sample pack", str(context.exception))

        with tempfile.TemporaryDirectory() as directory:
            path = self._descriptor(
                Path(directory), mutate=lambda d: d["samplePack"].update({"bytes": 1}))
            with self.assertRaises(validate_soundfont.ValidationError) as context:
                validate_soundfont.validate(path)
            self.assertIn("bytes but", str(context.exception))

        with tempfile.TemporaryDirectory() as directory:
            def corrupt_source(descriptor: dict) -> None:
                descriptor["source"]["sourceSha256"] = "0" * 64
                descriptor["samplePack"]["sourceSf2Sha256"] = "0" * 64
            path = self._descriptor(Path(directory), mutate=corrupt_source)
            self._refresh_pack(path)
            with self.assertRaises(validate_soundfont.ValidationError) as context:
                validate_soundfont.validate(path)
            self.assertIn("SHA-256 mismatch for generator SoundFont", str(context.exception))

    def test_a_non_sf2_source_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = self._descriptor(root)
            source = root / "synthNotes/StringSynth_A0-C8.sf2"
            bad = b"RIFF" + (72).to_bytes(4, "little") + b"WAVE" + b"\x00" * 64
            source.write_bytes(bad)
            descriptor = json.loads(path.read_text(encoding="utf-8"))
            descriptor["source"]["sourceSha256"] = hashlib.sha256(bad).hexdigest()
            pack = descriptor["samplePack"]
            pack["sourceSf2Sha256"] = hashlib.sha256(bad).hexdigest()
            pack["sourceSf2Bytes"] = len(bad)
            path.write_text(json.dumps(descriptor), encoding="utf-8")
            self._refresh_pack(path)
            with self.assertRaises(validate_soundfont.ValidationError) as context:
                validate_soundfont.validate(path)
            self.assertIn("not an SF2 sound bank", str(context.exception))

    def test_bank_and_program_bounds_are_enforced(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = self._descriptor(Path(directory), mutate=lambda d: d.update({"program": 200}))
            with self.assertRaises(validate_soundfont.ValidationError) as context:
                validate_soundfont.validate(path)
            self.assertIn("program must be in", str(context.exception))

    def test_paths_outside_the_asset_directory_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = self._descriptor(
                Path(directory), mutate=lambda d: d["samplePack"].update({"file": "../evil.bin"}))
            with self.assertRaises(validate_soundfont.ValidationError) as context:
                validate_soundfont.validate(path)
            self.assertIn("relative .bin path", str(context.exception))


def descriptor_data_offset() -> int:
    return validate_soundfont.EAP1_HEADER.size + \
        validate_soundfont.EAP1_ENTRY.size * validate_soundfont.EAP1_ENTRIES
