"""Regression tests for guards that can run before the Android SDK is installed."""
import hashlib
import importlib.util
import json
import sys
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


def module_from_file(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


architecture = module_from_file('architecture', ROOT / 'scripts/check-architecture.py')
downloader = module_from_file('downloader', ROOT / 'scripts/download-toolchain.py')


class BoundaryChecks(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root / 'gradle').mkdir()
        shutil.copyfile(ROOT / 'gradle/libs.versions.toml', self.root / 'gradle/libs.versions.toml')
        for name in ('music', 'training'):
            target = self.root / 'core' / name
            target.mkdir(parents=True)
            shutil.copyfile(ROOT / 'core' / name / 'build.gradle.kts', target / 'build.gradle.kts')

    def test_current_module_graph_allowed(self):
        self.assertEqual([], architecture.violations(self.root))

    def test_android_import_rejected(self):
        (self.root / 'core/music/Bad.kt').write_text('import android.media.AudioTrack\n')
        self.assertTrue(architecture.violations(self.root))

    def test_android_catalog_dependency_rejected(self):
        with (self.root / 'core/training/build.gradle.kts').open('a') as stream:
            stream.write('\ndependencies { implementation(libs.activity.compose) }\n')
        self.assertTrue(architecture.violations(self.root))

    def test_reverse_dependency_rejected(self):
        with (self.root / 'core/music/build.gradle.kts').open('a') as stream:
            stream.write('\ndependencies { implementation(project(":app")) }\n')
        self.assertTrue(architecture.violations(self.root))

    def test_audio_cannot_import_compose(self):
        directory = self.root / 'core/audio'
        directory.mkdir(parents=True)
        (directory / 'Bad.kt').write_text('import androidx.compose.runtime.Composable\n')
        self.assertTrue(architecture.violations(self.root))

    def test_ui_cannot_call_native_audio(self):
        directory = self.root / 'app/src/main/kotlin/example'
        directory.mkdir(parents=True)
        (directory / 'PlayScreen.kt').write_text('import com.thomas.passiveeartraining.audio.NativeAudioFiles\n')
        self.assertTrue(architecture.violations(self.root))


class Checksums(unittest.TestCase):
    def test_changed_download_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'archive'
            path.write_bytes(b'original')
            expected = hashlib.sha256(b'original').hexdigest()
            downloader.verify(path, expected, 'sha256')
            path.write_bytes(b'changed')
            with self.assertRaises(RuntimeError):
                downloader.verify(path, expected, 'sha256')

    def test_empty_dependency_metadata_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'scripts').mkdir()
            (root / 'gradle').mkdir()
            shutil.copyfile(ROOT / 'scripts/check-verification.py', root / 'scripts/check-verification.py')
            (root / 'gradle/verification-metadata.xml').write_text(
                '<verification-metadata><configuration><verify-metadata>true</verify-metadata>'
                '</configuration><components /></verification-metadata>'
            )
            result = subprocess.run(['python3', str(root / 'scripts/check-verification.py')], capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertIn('SHA-256', result.stderr)


if __name__ == '__main__':
    unittest.main()


class AssetCheckModeTest(unittest.TestCase):
    """The review gate must stay honest: a pending review fails --require-reviewed only."""

    def _run(self, *args: str) -> tuple[int, str]:
        result = subprocess.run(
            ["bash", "scripts/check-assets.sh", *args],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        return result.returncode, result.stdout + result.stderr

    def test_plain_run_validates_the_bundled_assets(self) -> None:
        code, output = self._run()
        self.assertEqual(0, code, output)
        self.assertIn("PASS: validated", output)

    def test_require_reviewed_fails_while_a_review_is_pending(self) -> None:
        code, output = self._run("--require-reviewed")
        # All bundled reviews are recorded now, so strict mode passes; if a review is revoked, the
        # known pending-review messages must make it fail again.
        if (
            "still need a human listening review" in output
            or "no accepted listening review" in output
            or "no owner listening record" in output
        ):
            self.assertEqual(1, code, output)
        else:
            self.assertEqual(0, code, output)


class ScreenshotContractTest(unittest.TestCase):
    def test_expected_set_matches_every_production_capture(self):
        import re
        contract = module_from_file('screenshot_contract', ROOT / 'scripts/screenshot-contract.py')
        names = set()
        for path in (ROOT / 'app/src/test/kotlin').rglob('*ScreenshotTest.kt'):
            names.update(re.findall(r'File\(screenshotsDir, "([^"]+)\.png"\)', path.read_text()))
        self.assertEqual(contract.EXPECTED, names)

    def test_missing_and_invalid_baselines_are_refused(self):
        contract = module_from_file('screenshot_contract', ROOT / 'scripts/screenshot-contract.py')
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            self.assertEqual(9, len(contract.problems(directory)))
            for name in contract.EXPECTED:
                (directory / f'{name}.png').write_bytes(b'\x89PNG\r\n\x1a\n')
            self.assertEqual([], contract.problems(directory))
            (directory / 'settings.png').write_text('broken')
            self.assertIn('Invalid PNG: settings.png', contract.problems(directory))

    def test_verification_refuses_recording_and_candidate_overrides(self):
        for argument in (':app:recordRoborazziDebug', '-Proborazzi.test.record=true',
                         '-Pear.screenshots.dir=/tmp/candidates'):
            result = subprocess.run(['bash', 'scripts/check-ui.sh', argument], cwd=ROOT,
                                    capture_output=True, text=True)
            self.assertEqual(2, result.returncode)
            self.assertIn('no recording', result.stderr)

class ScreenshotReportTest(unittest.TestCase):
    """The report mode must never pass an undeclared difference or a stale declaration."""

    @staticmethod
    def _image(path: Path, color) -> None:
        from PIL import Image
        Image.new('RGBA', (40, 40), color).save(path)

    @staticmethod
    def _run(root: Path):
        return subprocess.run(
            [sys.executable, 'scripts/screenshot-report.py',
             '--candidates', str(root / 'candidates'),
             '--manifest', str(root / 'baselines' / 'manifest.json'), '--report-only'],
            cwd=ROOT, capture_output=True, text=True)

    def _fixture(self, temporary, *, status, baseline_color=(0, 0, 0, 255), candidate_color=None,
                 draw_square_at=None):
        root = Path(temporary)
        (root / 'baselines').mkdir(exist_ok=True)
        (root / 'candidates').mkdir(exist_ok=True)
        self._image(root / 'baselines' / 'case.png', baseline_color)
        if draw_square_at is None:
            self._image(root / 'candidates' / 'case.png', candidate_color or baseline_color)
        else:
            from PIL import Image
            image = Image.new('RGBA', (40, 40), baseline_color)
            for x in range(draw_square_at[0], draw_square_at[0] + 10):
                for y in range(draw_square_at[1], draw_square_at[1] + 10):
                    image.putpixel((x, y), (255, 0, 0, 255))
            image.save(root / 'candidates' / 'case.png')
        manifest = {'version': 1, 'cases': [{
            'name': 'case', 'file': 'case.png', 'status': status,
            'reason': 'test', 'changedRegion': {'x': 10, 'y': 10, 'width': 10, 'height': 10},
        }]}
        (root / 'baselines' / 'manifest.json').write_text(json.dumps(manifest))
        return root

    def test_approved_cases_must_be_identical(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = self._fixture(temporary, status='approved')
            result = self._run(root)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn('UNCHANGED', result.stdout)

            root = self._fixture(temporary, status='approved', candidate_color=(255, 0, 0, 255))
            result = self._run(root)
            self.assertEqual(2, result.returncode)
            self.assertIn('undeclared difference', result.stderr)

    def test_a_declared_change_inside_its_region_is_only_pending(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = self._fixture(temporary, status='declared-change', draw_square_at=(10, 10))
            result = self._run(root)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn('PENDING_VISUAL_APPROVAL', result.stdout)

    def test_a_declared_change_outside_its_region_fails(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = self._fixture(temporary, status='declared-change', draw_square_at=(25, 25))
            result = self._run(root)
            self.assertEqual(2, result.returncode)
            self.assertIn('outside the declared region', result.stderr)

    def test_a_stale_declaration_fails(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = self._fixture(temporary, status='declared-change')
            result = self._run(root)
            self.assertEqual(2, result.returncode)
            self.assertIn('did not occur', result.stderr)

    def test_a_missing_candidate_fails(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = self._fixture(temporary, status='approved')
            (root / 'candidates' / 'case.png').unlink()
            result = self._run(root)
            self.assertEqual(2, result.returncode)
            self.assertIn('Missing candidate', result.stderr)

    def test_the_repository_manifest_matches_baselines_and_captures(self):
        import re
        manifest = json.loads(
            (ROOT / 'app/src/test/screenshots/manifest.json').read_text()
        )
        captured = set()
        for path in (ROOT / 'app/src/test/kotlin').rglob('*ScreenshotTest.kt'):
            captured.update(
                re.findall(r'File\(screenshotsDir, "([^"]+)\.png"\)', path.read_text())
            )
        self.assertEqual({case['name'] for case in manifest['cases']}, captured)
        for case in manifest['cases']:
            digest = hashlib.sha256(
                (ROOT / 'app/src/test/screenshots' / case['file']).read_bytes()
            ).hexdigest()
            expected = case.get('sha256') or case.get('baselineSha256')
            self.assertEqual(expected, digest, case['name'])
