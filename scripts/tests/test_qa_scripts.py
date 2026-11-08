"""Tests for the QA entry point's report builder and device evidence scanner."""
import datetime as dt
import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def module_from_file(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


report = module_from_file('qa_report', ROOT / 'scripts/qa-report.py')
scan = module_from_file('qa_device_scan', ROOT / 'scripts/qa-device-scan.py')

WINDOW = {
    'year': 2026,
    'epochSeconds': int(dt.datetime(2026, 10, 15, 12, 0,
                                    tzinfo=dt.timezone(dt.timedelta(hours=2))).timestamp()),
    'utcOffset': '+0200',
}
REPORT_LINES = '\n'.join([
    'PASS review-short: 58 tests, 508s',
    'PASS data-platform: 5 tests, 3s',
    'PASS audio-platform: 9 tests, 3s',
    'PASS before-force-stop: 1 tests, 4s',
    'PASS after-force-stop: 1 tests, 3s',
    'PASS regular app package and private-file hashes unchanged',
    'UNEXECUTED real 30-minute screen-off endurance (--smoke-only)',
    'AUTOMATED ONLY: acoustic quality, accessories, TalkBack and interval-walk acceptance remain owner-reported; see the QA report',
])


class LogcatScanTest(unittest.TestCase):
    def test_a_crash_inside_the_run_window_is_found(self):
        text = '10-15 12:05:00.000 1234 1234 E AndroidRuntime: FATAL EXCEPTION: main\n'
        hits = scan.scan_logcat(text, WINDOW)
        self.assertEqual(1, len(hits))
        self.assertIn('fatal exception', hits[0][1])

    def test_a_stale_crash_is_ignored(self):
        text = '10-14 23:59:00.000 1234 1234 E AndroidRuntime: FATAL EXCEPTION: main\n'
        self.assertEqual([], scan.scan_logcat(text, WINDOW))

    def test_an_anr_and_a_crash_header_are_found(self):
        text = ('10-15 12:06:00.000 1234 1234 E ActivityManager: ANR in '
                'com.earby.app.review\n'
                '10-15 12:07:00.000 1234 1234 E AndroidRuntime: Process: '
                'com.earby.app.review, PID: 1234\n')
        self.assertEqual(['ANR', 'crash header'], [hit[1] for hit in scan.scan_logcat(text, WINDOW)])


class ExitInfoScanTest(unittest.TestCase):
    def _block(self, timestamp, reason, name):
        return ('        ApplicationExitInfo #0:\n'
                f'          timestamp={timestamp} pid=1 realUid=1 packageUid=1 definingUid=1\n'
                f'          process=com.earby.app.review reason={reason} '
                f'({name}) subreason=21 (FORCE STOP) status=0\n')

    def test_a_crash_inside_the_window_fails(self):
        text = self._block('2026-10-15 12:10:00.000', 4, 'CRASH')
        crashes, records = scan.scan_exit_info(text, WINDOW)
        self.assertEqual(1, len(crashes))
        self.assertEqual(1, len(records))

    def test_a_force_stop_inside_the_window_is_expected(self):
        text = self._block('2026-10-15 12:20:00.000', 10, 'USER REQUESTED')
        crashes, records = scan.scan_exit_info(text, WINDOW)
        self.assertEqual([], crashes)
        self.assertEqual(1, len(records))

    def test_a_stale_crash_is_ignored(self):
        text = self._block('2026-10-14 20:00:00.000', 4, 'CRASH')
        self.assertEqual([], scan.scan_exit_info(text, WINDOW)[0])


class DeviceScanTest(unittest.TestCase):
    def _device_dir(self, temporary, report_text=REPORT_LINES, logcat='', exit_info=''):
        directory = Path(temporary)
        (directory / 'report.txt').write_text(report_text)
        (directory / 'app-logcat.txt').write_text(logcat)
        (directory / 'app-exit-info.txt').write_text(exit_info)
        (directory / 'metadata.json').write_text(json.dumps(
            {'model': 'ASUS_AI2302', 'api': '34', 'abi': 'arm64-v8a', 'pageSize': '4096'}))
        return directory

    def test_clean_evidence_passes_with_the_endurance_exclusion_recorded(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = self._device_dir(temporary)
            problems, summary = scan.scan(directory, WINDOW)
            self.assertEqual([], problems, problems)
            self.assertTrue(any('ASUS_AI2302' in line for line in summary))

    def test_a_missing_result_marker_fails(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = self._device_dir(
                temporary,
                report_text=REPORT_LINES.replace('PASS data-platform: 5 tests, 3s\n', ''))
            problems, _ = scan.scan(directory, WINDOW)
            self.assertTrue(any('data-platform' in problem for problem in problems), problems)

    def test_a_missing_endurance_exclusion_fails(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = self._device_dir(
                temporary,
                report_text=REPORT_LINES.replace(
                    'UNEXECUTED real 30-minute screen-off endurance (--smoke-only)\n', ''))
            problems, _ = scan.scan(directory, WINDOW)
            self.assertTrue(any('endurance exclusion' in problem for problem in problems), problems)

    def test_clean_evidence_with_the_real_endurance_passes_when_expected(self):
        endurance = REPORT_LINES.replace(
            'UNEXECUTED real 30-minute screen-off endurance (--smoke-only)\n',
            'PASS endurance-30m: 1 tests, 1818s\n'
            'PASS endurance memory: 26 samples; PSS variation 23209 KiB <= 65536 KiB\n')
        with tempfile.TemporaryDirectory() as temporary:
            directory = self._device_dir(temporary, report_text=endurance)
            problems, _ = scan.scan(directory, WINDOW, expect_endurance=True)
            self.assertEqual([], problems, problems)

    def test_the_real_endurance_markers_are_required_when_expected(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = self._device_dir(temporary)
            problems, _ = scan.scan(directory, WINDOW, expect_endurance=True)
            self.assertTrue(any('real endurance pass' in problem for problem in problems), problems)
            self.assertTrue(any('memory guard' in problem for problem in problems), problems)


class ReportBuilderTest(unittest.TestCase):
    def _evidence(self, temporary, stage_status='PASS'):
        directory = Path(temporary)
        (directory / 'run-started.json').write_text(
            json.dumps({'epochSeconds': WINDOW['epochSeconds'], 'local': '2026-10-15T12:00:00+02:00'}))
        (directory / 'stages.tsv').write_text(
            f'{stage_status}\tpreflight\trecord_environment\t{directory}/preflight.log\n')
        (directory / 'preflight.log').write_text('host: Linux\n')
        return directory

    def test_host_totals_sum_xml_results(self):
        with tempfile.TemporaryDirectory() as temporary:
            results = Path(temporary) / 'build/test-results/test'
            results.mkdir(parents=True)
            (results / 'a.xml').write_text('<testsuite tests="3" failures="0" errors="0" skipped="0"/>')
            (results / 'b.xml').write_text('<testsuite tests="2" failures="1" errors="0" skipped="1"/>')
            totals = report.module_totals(Path(temporary), 'core:music', ('build/test-results/test',))
            self.assertEqual(dict(tests=5, failures=1, errors=0, skipped=1),
                             {key: totals[key] for key in ('tests', 'failures', 'errors', 'skipped')})

    def test_the_report_always_names_the_open_human_rows(self):
        with tempfile.TemporaryDirectory() as temporary:
            text = report.build_report(self._evidence(temporary))
            self.assertIn('## Human rows still open', text)
            for row in report.HUMAN_ROWS:
                self.assertIn(row, text)
            self.assertIn('**PASS**', text)

    def test_a_failed_stage_is_reported_as_a_finding(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = self._evidence(temporary, stage_status='FAIL')
            (directory / 'findings.md').write_text(
                '# Findings\n\n## QA-F01 — integration\n\n- Stage failed\n')
            text = report.build_report(directory)
            self.assertIn('FAIL — 1 finding(s)', text)
            self.assertIn('QA-F01', text)

    def test_the_report_records_the_real_endurance_when_it_ran(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = self._evidence(temporary)
            with (directory / 'stages.tsv').open('a') as handle:
                handle.write(f'PASS\tintegration\tscripts/check-full.sh\t'
                             f'{directory}/integration.log\n')
            (directory / 'integration.log').write_text('PASS endurance-30m: 1 tests, 1818s\n')
            text = report.build_report(directory)
            self.assertIn('ran in this QA pass', text)

    def test_a_pending_visual_candidate_is_counted(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = self._evidence(temporary)
            (directory / 'visual-report.log').write_text(
                'UNCHANGED: drawer\nPENDING_VISUAL_APPROVAL: settings (reason: theme)\n')
            text = report.build_report(directory)
            self.assertIn('1 unchanged, 1 pending, 0 undeclared', text)


class QaEntryPointTest(unittest.TestCase):
    def test_scripts_are_syntactically_valid(self):
        for script in ('scripts/qa.sh', 'scripts/check-full.sh', 'scripts/check-apk.sh'):
            result = subprocess.run(['bash', '-n', script], cwd=ROOT, capture_output=True, text=True)
            self.assertEqual(0, result.returncode, f'{script}: {result.stderr}')

    def test_qa_sh_refuses_unknown_arguments(self):
        result = subprocess.run(['bash', 'scripts/qa.sh', '--fast'], cwd=ROOT,
                                capture_output=True, text=True)
        self.assertEqual(2, result.returncode)
        self.assertIn('Usage: scripts/qa.sh [--with-endurance]', result.stderr)

    def test_qa_sh_usage_names_the_endurance_flag(self):
        result = subprocess.run(['bash', 'scripts/qa.sh', '--with-endurance', 'extra'],
                                cwd=ROOT, capture_output=True, text=True)
        self.assertEqual(2, result.returncode)
        self.assertIn('--with-endurance', result.stderr)

    def test_check_full_usage_names_the_endurance_skip(self):
        result = subprocess.run(['bash', 'scripts/check-full.sh', '--bogus'], cwd=ROOT,
                                capture_output=True, text=True)
        self.assertEqual(2, result.returncode)
        self.assertIn('--skip-endurance', result.stderr)


if __name__ == '__main__':
    unittest.main()
