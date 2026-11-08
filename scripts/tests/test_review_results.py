import importlib.util
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location('review_results', Path(__file__).parents[1] / 'review-results.py')
results = importlib.util.module_from_spec(spec)
spec.loader.exec_module(results)


class ReviewResultsTest(unittest.TestCase):
    def test_endurance_memory_requires_complete_screen_off_bounded_samples(self):
        samples = [dict(elapsedSeconds=minute * 60, memory=f'TOTAL PSS: {300000 + minute * 100}',
                        power='mWakefulness=Asleep') for minute in range(3, 30)]
        self.assertEqual(27, results.endurance_memory(samples)['samples'])
        self.assertEqual(2600, results.endurance_memory(samples)['variationKiB'])
        with self.assertRaises(ValueError):
            results.endurance_memory(samples[:4])
        for field, value in [('power', 'mWakefulness=Awake'), ('memory', 'missing'),
                             ('memory', 'TOTAL PSS: 500000')]:
            invalid = [dict(sample) for sample in samples]
            invalid[0][field] = value
            with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                results.endurance_memory(invalid)

    def test_packaged_instrumentation_identity(self):
        badge = "package: name='example.review.test' versionCode='2'"
        manifest = '''  E: manifest
    A: package="example.review.test"
      E: instrumentation (line=9)
        A: android:name(0x01010003)="example.Runner" (Raw: "example.Runner")
        A: android:targetPackage(0x01010021)="example.review" (Raw: "example.review")
      E: application
        A: android:name(0x01010003)="example.Application"
'''
        self.assertEqual(('example.review.test', 'example.review', 'example.Runner'), results.apk_identity(badge, manifest))

    def test_runner_success_requires_actual_completed_test(self):
        output = '''INSTRUMENTATION_STATUS: class=example.Test
INSTRUMENTATION_STATUS: test=works
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=example.Test
INSTRUMENTATION_STATUS: test=works
INSTRUMENTATION_STATUS_CODE: 0
OK (1 test)
INSTRUMENTATION_CODE: -1
'''
        xml = results.instrumentation_xml(output, 'device')
        self.assertEqual('1', ET.fromstring(xml).get('tests'))
        self.assertEqual('0', ET.fromstring(xml).get('failures'))

    def test_adb_zero_exit_is_not_a_pass(self):
        for output in ['FAILURES!!!\nTests run: 1, Failures: 1', 'INSTRUMENTATION_FAILED: Process crashed.', 'OK (0 tests)', '']:
            with self.subTest(output=output):
                with self.assertRaises(ValueError):
                    results.instrumentation_xml(output, 'device')

    def test_ignored_and_incomplete_results_are_not_passes(self):
        for code in [-3, -4, 1]:
            output = f'INSTRUMENTATION_STATUS: class=T\nINSTRUMENTATION_STATUS: test=t\nINSTRUMENTATION_STATUS_CODE: {code}\nOK (1 test)'
            with self.assertRaises(ValueError):
                results.instrumentation_xml(output, 'device')

class NativeInputsTest(unittest.TestCase):
    def test_missing_miniaudio_source_fails_before_any_download(self):
        import subprocess
        import tempfile
        root = Path(__file__).resolve().parents[2]
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            result = subprocess.run(['cmake', '-S', str(root / 'core/audio/src/main/cpp'),
                                     '-B', str(directory / 'build'),
                                     '-DMINIAUDIO_SOURCE_DIR=' + str(directory / 'missing')],
                                    capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertIn('MINIAUDIO_SOURCE_DIR is not a directory', result.stderr)
            self.assertNotIn('Attempting to download', result.stderr)
