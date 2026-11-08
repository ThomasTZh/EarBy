"""Tests for the EARP1 pack generator (standard library only).

The generator pins the bundled SoundFont by SHA-256 and reuses validate_sf2 for the container
contract, so these tests build small synthetic SoundFonts, re-pin the hash and assert that every
refusal path fires before a pack can be written.
"""

from __future__ import annotations

import hashlib
import struct
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import build_sample_pack as pack  # noqa: E402
from build_sf2 import build_sf2  # noqa: E402
from validate_sf2 import parse_sf2  # noqa: E402

# validate() inside extract_samples requires the reviewed 120000-frame shape.
FRAMES = 120000
PCM = bytes(FRAMES * 2)


def make_soundfont(path: Path, midi_low: int, midi_high: int, sample_rate: int = 48000) -> str:
    midi_values = list(range(midi_low, midi_high + 1))
    entries = [
        {
            "midi": midi,
            "pcm": PCM,
            "rms": 0.12,
            "source": "bass" if midi <= 59 else "guitar",
        }
        for midi in midi_values
    ]
    build_sf2(entries, path, "Test preset", 59, sample_rate=sample_rate)
    return hashlib.sha256(path.read_bytes()).hexdigest()


class BuildSamplePackTest(unittest.TestCase):
    def setUp(self) -> None:
        self._pinned = pack.SOURCE_SF2_SHA256
        self.addCleanup(setattr, pack, "SOURCE_SF2_SHA256", self._pinned)

    def test_wrong_source_hash_is_refused(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "unreviewed.sf2"
            source.write_bytes(b"not the tracked font")
            with self.assertRaisesRegex(pack.PackError, "SHA-256"):
                pack.extract_samples(source)

    def test_missing_keys_are_refused(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "short.sf2"
            pack.SOURCE_SF2_SHA256 = make_soundfont(source, 21, 107)
            with self.assertRaisesRegex(pack.PackError, "MIDI 21-108|expected set"):
                pack.extract_samples(source)

    def test_non_48_khz_source_is_refused(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "44k1.sf2"
            pack.SOURCE_SF2_SHA256 = make_soundfont(source, 21, 108, sample_rate=44100)
            with self.assertRaisesRegex(pack.PackError, "44100"):
                pack.extract_samples(source)

    def test_duplicate_key_zones_are_refused_by_the_shared_validator(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "duplicate.sf2"
            pack.SOURCE_SF2_SHA256 = make_soundfont(source, 21, 108)
            data = bytearray(source.read_bytes())
            # Point the second key zone and its overriding root key at key 21 as well. The shared
            # validator rejects the now non-unique key zones before the generator can pack them.
            igen_offset = data.find(b"igen") + 8
            zone_one = igen_offset + 20
            data[zone_one + 2] = 21
            data[zone_one + 3] = 21
            data[zone_one + 14] = 21
            data[zone_one + 15] = 0
            source.write_bytes(data)
            pack.SOURCE_SF2_SHA256 = hashlib.sha256(data).hexdigest()
            with self.assertRaisesRegex(pack.PackError, "sorted and unique|duplicate"):
                pack.extract_samples(source)

    def test_pack_serialization_is_deterministic(self) -> None:
        samples = [(21, b"\x01\x02\x03\x04"), (22, b"\x05\x06")]
        source_hash = bytes.fromhex(pack.SOURCE_SF2_SHA256)
        first, first_hash = pack.build_pack(samples, source_hash)
        second, second_hash = pack.build_pack(samples, source_hash)
        self.assertEqual(first, second)
        self.assertEqual(first_hash, second_hash)
        header = pack.HEADER.unpack_from(first)
        self.assertEqual(header[0], pack.MAGIC)
        self.assertEqual(header[1], pack.VERSION)
        self.assertEqual(header[2], pack.SAMPLE_RATE)
        self.assertEqual(header[3], pack.CHANNELS)
        self.assertEqual(header[4], len(samples))
        self.assertEqual(header[5], pack.INDEX_OFFSET)
        self.assertEqual(header[6], pack.DATA_OFFSET)
        self.assertEqual(header[7], 4 + 2)

    def test_descriptor_refuses_a_non_frame_payload(self) -> None:
        # A one-byte payload is not a whole 16-bit frame; the descriptor must never advertise it.
        with self.assertRaisesRegex(pack.PackError, "16-bit frames"):
            pack.descriptor_block(b"", [(21, b"\x00")], b"\x00" * 32)

    def test_real_source_produces_a_valid_pack_header(self) -> None:
        samples = pack.extract_samples(pack.DEFAULT_SF2)
        self.assertEqual(len(samples), 88)
        self.assertEqual(samples[0][0], 21)
        self.assertEqual(samples[-1][0], 108)
        self.assertTrue(all(len(pcm) == FRAMES * 2 for _, pcm in samples))
        built, payload_hash = pack.build_pack(samples, bytes.fromhex(pack.SOURCE_SF2_SHA256))
        self.assertEqual(len(built), pack.DATA_OFFSET + FRAMES * 2 * 88)
        header = pack.HEADER.unpack_from(built)
        self.assertEqual(header[7], FRAMES * 2 * 88)
        self.assertEqual(header[9], payload_hash)
        first_entry = pack.ENTRY.unpack_from(built, pack.INDEX_OFFSET)
        self.assertEqual(first_entry[0], 21)
        self.assertEqual(first_entry[2], FRAMES)
        self.assertEqual(first_entry[3], pack.DATA_OFFSET)

    def test_layout_sizes_match_the_documented_format(self) -> None:
        font = parse_sf2(pack.DEFAULT_SF2)
        self.assertEqual(len(font["samples"]), 88)
        self.assertIsInstance(font["samples"][0]["rate"], int)
        self.assertEqual(struct.calcsize("<8s8I32s"), pack.HEADER_SIZE)
        self.assertEqual(struct.calcsize("<HHII32s32s"), pack.ENTRY_SIZE)
        self.assertEqual(pack.DATA_OFFSET, pack.HEADER_SIZE + pack.ENTRY_SIZE * 88)


if __name__ == "__main__":
    unittest.main()
