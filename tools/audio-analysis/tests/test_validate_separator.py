#!/usr/bin/env python3
"""Tests for the separator clip validator.

The shipped clip is the fixture: the checks assert its recorded format, level and hash, the
provenance-record gate and the reviewed-mode listening gate. A malformed copy proves that a
wrong hash and a missing acknowledgement are reported instead of passing.
"""
from __future__ import annotations

import shutil
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import validate_separator as validator  # noqa: E402

ROOT = Path(__file__).resolve().parents[3]


def copy_repository(audio_assets_extra: str = "") -> Path:
    """Copies the provenance inputs into a temporary repository root."""
    root = Path(tempfile.mkdtemp())
    for relative in ("docs/legal", "docs/audio", "LICENSES"):
        shutil.copytree(ROOT / relative, root / relative)
    shutil.copy2(ROOT / "THIRD_PARTY_NOTICES.md", root / "THIRD_PARTY_NOTICES.md")
    if audio_assets_extra:
        with (root / "docs/audio/ASSETS.md").open("a", encoding="utf-8") as handle:
            handle.write(audio_assets_extra)
    return root


class ShippedClipTest(unittest.TestCase):
    def test_format_level_and_hash_match_the_record(self):
        clip = ROOT / validator.EXPECTED_RELATIVE_CLIP
        problems = validator.check_audio(
            clip, validator.EXPECTED_SHA256, validator.EXPECTED_BYTES
        )
        self.assertEqual([], problems)

    def test_wrong_hash_is_reported(self):
        clip = ROOT / validator.EXPECTED_RELATIVE_CLIP
        problems = validator.check_audio(clip, "0" * 64, validator.EXPECTED_BYTES)
        self.assertTrue(any("SHA-256" in problem for problem in problems), problems)

    def test_missing_clip_is_reported(self):
        problems = validator.check_audio(Path("/nonexistent/separator.wav"), "0" * 64, None)
        self.assertTrue(any("missing clip" in problem for problem in problems), problems)


class ProvenanceRecordTest(unittest.TestCase):
    def test_recorded_provenance_passes(self):
        self.assertEqual([], validator.check_provenance(ROOT, require_listening=False))

    def test_missing_acknowledgement_is_reported(self):
        root = copy_repository()
        try:
            notice = root / "THIRD_PARTY_NOTICES.md"
            notice.write_text(
                notice.read_text(encoding="utf-8").replace("Frank Wen", "F. Wen"),
                encoding="utf-8",
            )
            problems = validator.check_provenance(root, require_listening=False)
            self.assertTrue(
                any("Frank Wen" in problem for problem in problems), problems
            )
        finally:
            shutil.rmtree(root)

    def test_reviewed_mode_requires_the_listening_record(self):
        root = copy_repository()
        try:
            assets = root / "docs/audio/ASSETS.md"
            assets.write_text(
                validator.LISTENING_RECORD.sub("", assets.read_text(encoding="utf-8")),
                encoding="utf-8",
            )
            problems = validator.check_provenance(root, require_listening=True)
            self.assertTrue(any("listening" in problem for problem in problems), problems)
        finally:
            shutil.rmtree(root)

    def test_reviewed_mode_accepts_a_recorded_listening_date(self):
        root = copy_repository("\nSeparator listening: accepted by the owner on 2026-11-08\n")
        try:
            self.assertEqual([], validator.check_provenance(root, require_listening=True))
        finally:
            shutil.rmtree(root)


if __name__ == "__main__":
    unittest.main()
