#!/usr/bin/env python3
"""Scan isolated-phone QA evidence for crashes, ANRs and missing result markers.

Read-only. The scan fails (exit 1 with the reasons on stderr) when the evidence shows a crash
or an ANR inside the recorded run window, a failed suite line, or a missing expected result
marker - including the recorded endurance exclusion. Entries older than the run window (the
log buffer and the exit-info history span previous sessions) are ignored by comparing the
device-local timestamps against the device clock captured before the run.
"""
import argparse
import datetime as dt
import json
import re
import sys
from pathlib import Path

REVIEW_PACKAGE = 'com.earby.app.review'
LOGCAT_LINE = re.compile(r'^(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})\.(\d{3})\s')
ENTRY_HEADER = re.compile(r'^\s*ApplicationExitInfo #\d+:')
ENTRY_TIMESTAMP = re.compile(
    r'timestamp=(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})\.(\d{3})'
)
ENTRY_REASON = re.compile(r'reason=(\d+) \(([^)]+)\)')
# Android's ApplicationExitInfo crash and ANR reasons: a healthy QA run only force-stops.
CRASH_REASONS = {4: 'CRASH', 6: 'ANR'}
LOG_MARKERS = (
    ('fatal exception', re.compile(r'FATAL EXCEPTION')),
    ('crash header', re.compile(re.escape('Process: ' + REVIEW_PACKAGE + ', PID:'))),
    ('ANR', re.compile(re.escape('ANR in ' + REVIEW_PACKAGE))),
)
ENDURANCE_EXCLUSION = 'UNEXECUTED real 30-minute screen-off endurance'
ENDURANCE_PASS = 'PASS endurance-30m:'
ENDURANCE_MEMORY_PASS = 'PASS endurance memory:'
REQUIRED_PASSES = (
    'PASS review-short:',
    'PASS data-platform:',
    'PASS audio-platform:',
    'PASS before-force-stop:',
    'PASS after-force-stop:',
    'PASS regular app package and private-file hashes unchanged',
)


def offset_timezone(text: str) -> dt.timezone:
    """Parse a device UTC offset such as +0200 from `date +%z`."""
    sign = 1 if text[0] == '+' else -1
    return dt.timezone(sign * dt.timedelta(hours=int(text[1:3]), minutes=int(text[3:5])))


def device_epoch(window: dict, month: int, day: int, hour: int, minute: int, second: int,
                 micro: int, year: int | None = None) -> float:
    """Convert a device-local wall-clock fragment to an epoch.

    Logcat timestamps omit the year; those are resolved against the captured year with a
    one-year rollover when the buffer spans New Year. Exit-info timestamps carry their year.
    """
    zone = offset_timezone(window['utcOffset'])
    if year is None:
        year = int(window['year'])
        naive = dt.datetime(year, month, day, hour, minute, second, micro)
        start = dt.datetime.fromtimestamp(int(window['epochSeconds']), zone).replace(tzinfo=None)
        while naive - start > dt.timedelta(days=1):
            naive = naive.replace(year=naive.year - 1)
    else:
        naive = dt.datetime(year, month, day, hour, minute, second, micro)
    return naive.replace(tzinfo=zone).timestamp()


def load_window(path: Path | None) -> dict | None:
    if path is None:
        return None
    window = json.loads(path.read_text())
    window['year'] = int(window['year'])
    window['epochSeconds'] = int(window['epochSeconds'])
    return window


def scan_logcat(text: str, window: dict | None) -> list[tuple[int, str, str]]:
    """Crash/ANR markers with their line numbers, filtered to the run window."""
    hits = []
    for number, line in enumerate(text.splitlines(), start=1):
        marker = next((name for name, pattern in LOG_MARKERS if pattern.search(line)), None)
        if marker is None:
            continue
        stamp = LOGCAT_LINE.match(line)
        if stamp is not None and window is not None:
            parsed = device_epoch(window, *(int(value) for value in stamp.groups()))
            if parsed < window['epochSeconds']:
                continue
        hits.append((number, marker, line.strip()))
    return hits


def scan_exit_info(text: str, window: dict | None) -> tuple[list[str], list[dict]]:
    """Return (crash/ANR descriptions, all in-window exit records)."""
    lines = text.splitlines()
    crashes, records = [], []
    for index, line in enumerate(lines):
        if not ENTRY_HEADER.match(line):
            continue
        block = '\n'.join(lines[index:index + 4])
        stamp = ENTRY_TIMESTAMP.search(block)
        reason = ENTRY_REASON.search(block)
        if stamp is None or reason is None:
            continue
        parsed = None
        if window is not None:
            year, month, day, hour, minute, second, micro = (int(value)
                                                             for value in stamp.groups())
            parsed = device_epoch(window, month, day, hour, minute, second, micro, year=year)
        if parsed is not None and parsed < window['epochSeconds']:
            continue
        record = dict(code=int(reason[1]), name=reason[2].strip(), timestamp=stamp[0])
        records.append(record)
        if record['code'] in CRASH_REASONS:
            crashes.append(f"exit-info: reason={record['code']} ({record['name']}) "
                           f"{record['timestamp']}")
    return crashes, records


def parse_report(text: str) -> tuple[list[str], list[str], list[str]]:
    passes, failures, other = [], [], []
    for line in text.splitlines():
        if line.startswith('PASS '):
            passes.append(line)
        elif line.startswith('FAIL'):
            failures.append(line)
        else:
            other.append(line)
    return passes, failures, other


def scan(
    device_dir: Path, window: dict | None, expect_endurance: bool = False
) -> tuple[list[str], list[str]]:
    """Return (problems, summary lines) for one device evidence directory."""
    problems: list[str] = []
    summary: list[str] = []
    report_path = device_dir / 'report.txt'
    if not report_path.exists():
        problems.append(f'missing {report_path}')
    else:
        passes, failures, other = parse_report(report_path.read_text())
        summary.extend(f'device: {line}' for line in passes)
        problems.extend(failures)
        for marker in REQUIRED_PASSES:
            if not any(line.startswith(marker) for line in passes):
                problems.append(f'report.txt lacks the expected result: {marker}')
        if expect_endurance:
            if not any(line.startswith(ENDURANCE_PASS) for line in passes):
                problems.append('report.txt does not record the real endurance pass')
            if not any(line.startswith(ENDURANCE_MEMORY_PASS) for line in passes):
                problems.append('report.txt does not record the endurance memory guard')
        elif not any(ENDURANCE_EXCLUSION in line for line in passes + other):
            problems.append('report.txt does not record the endurance exclusion')
    logcat = device_dir / 'app-logcat.txt'
    if logcat.exists():
        hits = scan_logcat(logcat.read_text(errors='replace'), window)
        summary.append(f'logcat: {len(hits)} crash/ANR marker(s) inside the run window')
        problems.extend(f'app-logcat.txt:{number}: {marker}: {line[:160]}'
                        for number, marker, line in hits)
    else:
        problems.append(f'missing {logcat}')
    exit_info = device_dir / 'app-exit-info.txt'
    if exit_info.exists():
        crashes, records = scan_exit_info(exit_info.read_text(errors='replace'), window)
        summary.append(f'exit-info: {len(records)} exit record(s) inside the run window')
        problems.extend(crashes)
    else:
        problems.append(f'missing {exit_info}')
    metadata = device_dir / 'metadata.json'
    if metadata.exists():
        data = json.loads(metadata.read_text())
        summary.append('device: {model} API {api} {abi} {page}-byte pages'.format(
            model=data.get('model'), api=data.get('api'), abi=data.get('abi'),
            page=data.get('pageSize')))
    return problems, summary


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--device-dir', required=True, type=Path)
    parser.add_argument('--window', type=Path,
                        help='device clock captured before the run; filters older entries')
    parser.add_argument('--expect-endurance', action='store_true',
                        help='require the real endurance PASS markers instead of the exclusion')
    args = parser.parse_args()
    window = load_window(args.window)
    if not args.device_dir.is_dir():
        print(f'FAIL: device evidence directory {args.device_dir} does not exist', file=sys.stderr)
        return 1
    problems, summary = scan(args.device_dir, window, expect_endurance=args.expect_endurance)
    for line in summary:
        print(line)
    if problems:
        for problem in problems:
            print(f'FAIL: {problem}', file=sys.stderr)
        return 1
    print('PASS: no crash or ANR inside the run window; every expected device result is recorded')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
