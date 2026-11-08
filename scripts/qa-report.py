#!/usr/bin/env python3
"""Write `QA-REPORT.md` for one scripts/qa.sh evidence folder.

Read-only with respect to the project: it reads the stage logs, host test results and device
evidence recorded in the folder and writes only `QA-REPORT.md` there. The report always ends
with the open human rows, so the automated run never claims them.
"""
import argparse
import datetime as dt
import hashlib
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# Where each module's fresh JUnit XML results land; the first populated candidate wins so a
# report never double-counts an older variant directory.
HOST_RESULTS = {
    'core:music': ('core/music/build/test-results/test',),
    'core:training': ('core/training/build/test-results/test',),
    'core:audio': ('core/audio/build/test-results/testDebugUnitTest',
                   'core/audio/build/test-results/test'),
    'core:media': ('core/media/build/test-results/testDebugUnitTest',
                   'core/media/build/test-results/test'),
    'core:data': ('core/data/build/test-results/testDebugUnitTest',),
    'app': ('app/build/test-results/testDebugUnitTest',),
}

IDENTITY_FILES = (
    'app/src/main/assets/voice/voice-pack.json',
    'app/src/main/assets/soundfont/earby-string-pack.bin',
    'app/src/main/assets/soundfont/piano.json',
    'synthNotes/StringSynth_A0-C8.sf2',
    'gradle/verification-metadata.xml',
    'planDraft.md',
)

ENDURANCE_CITATION = (
    'Endurance: excluded from this run by the owner decision of 2026-10-15; the recorded M11 '
    'evidence is cited instead - real 30-minute screen-off session, 1818 s, memory guard '
    '23,209 KiB PSS variation against the 64 MiB bound (factory build 0.8.0).'
)

ENDURANCE_RAN_MARKER = 'PASS endurance-30m:'
ENDURANCE_RAN = (
    'Endurance: the real 30-minute screen-off session ran in this QA pass; the PASS lines and '
    'the memory guard are in this evidence folder\'s integration log.'
)


def endurance_line(stages: list[dict]) -> str:
    """The endurance statement: the real run when it happened, otherwise the recorded citation."""
    for stage in stages:
        if stage['name'] != 'integration':
            continue
        log = Path(stage['log'])
        if log.is_file() and ENDURANCE_RAN_MARKER in log.read_text(errors='replace'):
            return ENDURANCE_RAN
    return ENDURANCE_CITATION

# Automated QA cannot perform these; they remain empty only when the owner has accepted every
# row. Acceptance is recorded separately so a report never presents it as an automated result.
HUMAN_ROWS = ()

OWNER_ACCEPTED = (
    'Acoustic quality: owner-reported good on 0.10.4, 2026-10-25.',
    'Actual accessories: owner-reported good, including earbuds stopping playback on removal, '
    '2026-10-25.',
    'TalkBack: owner-reported good, 2026-10-25.',
    'G3/G4: owner-reported PASS on 0.9.33/42.',
    'Interval walk, its starter and the Stopping chooser: owner-reported good on 0.10.4, '
    '2026-10-25.',
)

OPTIONAL_SECTIONS = (
    ('ide-review.md', 'IDE review'),
    ('coverage.md', 'Coverage and gap report (M11 acceptance matrix)'),
    ('repairs.md', 'Repairs and their regressions'),
)


def read_stages(path: Path) -> list[dict]:
    if not path.exists():
        return []
    stages = []
    for line in path.read_text().splitlines():
        if not line.strip():
            continue
        status, name, command, log = line.split('\t')
        stages.append(dict(status=status, name=name, command=command, log=log))
    return stages


def module_totals(root: Path, module: str, candidates: tuple[str, ...]) -> dict | None:
    for relative in candidates:
        directory = root / relative
        if not directory.is_dir() or not list(directory.glob('*.xml')):
            continue
        totals = dict(tests=0, failures=0, errors=0, skipped=0, newest=0.0)
        for report in sorted(directory.glob('*.xml')):
            suite = ET.parse(report).getroot()
            for key in ('tests', 'failures', 'errors', 'skipped'):
                totals[key] += int(suite.get(key, 0))
            totals['newest'] = max(totals['newest'], report.stat().st_mtime)
        totals['directory'] = relative
        return totals
    return None


def sha256_prefix(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()[:16]


def file_section(evidence: Path, name: str, title: str) -> list[str]:
    path = evidence / name
    if not path.exists():
        return []
    return [f'## {title}', '', '```text', path.read_text().rstrip(), '```', '']


def device_section(evidence: Path) -> list[str]:
    device = evidence / 'device'
    lines = ['## Device results (isolated review app)', '']
    metadata = device / 'metadata.json'
    if metadata.exists():
        data = json.loads(metadata.read_text())
        lines.append(f"- Device: {data.get('model')}, API {data.get('api')}, {data.get('abi')}, "
                     f"{data.get('pageSize')}-byte pages (serial stays in the ignored metadata).")
        for artifact in data.get('artifacts', []):
            lines.append(f"- Review artifact: `{artifact['path']}` {artifact['sha256'][:16]} "
                         f"(package {artifact['package']})")
    else:
        lines.append('- No device metadata was recorded.')
    report = device / 'report.txt'
    if report.exists():
        lines.extend(['', '```text', report.read_text().rstrip(), '```'])
    else:
        lines.append('')
        lines.append('- The device driver did not write a report; the device stage did not run.')
    return lines + ['']


def visual_section(evidence: Path) -> list[str]:
    lines = ['## Visual results', '']
    strict = evidence / 'visual-strict.log'
    if strict.exists():
        text = strict.read_text(errors='replace')
        markers = []
        if 'PASS: complete' in text:
            markers.extend(line for line in text.splitlines() if line.startswith('PASS: complete'))
        if 'BUILD SUCCESSFUL' in text:
            markers.append('strict comparison: BUILD SUCCESSFUL')
        lines.append(f"- Strict owner gate: {'; '.join(markers) if markers else 'no result marker found'}")
    else:
        lines.append('- Strict owner gate: not run.')
    candidates = evidence / 'visual-report.log'
    if candidates.exists():
        text = candidates.read_text(errors='replace')
        unchanged = sum(1 for line in text.splitlines() if line.startswith('UNCHANGED:'))
        pending = sum(1 for line in text.splitlines() if line.startswith('PENDING_VISUAL_APPROVAL'))
        undeclared = sum(1 for line in text.splitlines() if line.startswith('ERROR:'))
        lines.append(f'- Light candidate set: {unchanged} unchanged, {pending} pending, '
                     f'{undeclared} undeclared.')
    else:
        lines.append('- Light candidate set: no report recorded.')
    dark = sorted((evidence / 'dark-candidates').glob('*.png'))
    if dark:
        names = ', '.join(path.name for path in dark)
        lines.append(f'- Dark review set re-captured: {len(dark)} images ({names}); review artifact, '
                     'no baseline was added or changed.')
    else:
        lines.append('- Dark review set: not captured.')
    return lines + ['']


def scan_section(evidence: Path) -> list[str]:
    path = evidence / 'device-scan.log'
    if not path.exists():
        return ['## Crash/ANR and terminal-resource scan', '',
                '- The device scan did not run.', '']
    return ['## Crash/ANR and terminal-resource scan', '', '```text',
            path.read_text().rstrip(), '```', '']


def build_report(evidence: Path) -> str:
    started = json.loads((evidence / 'run-started.json').read_text())
    stages = read_stages(evidence / 'stages.tsv')
    failures = [stage for stage in stages if stage['status'] != 'PASS']
    lines = [
        f"# QA report — {started['local']}",
        '',
        f'- Evidence: `{evidence.relative_to(ROOT) if evidence.is_relative_to(ROOT) else evidence}`',
        '- Command: `scripts/qa.sh`',
        f"- Result: **{'FAIL — ' + str(len(failures)) + ' finding(s)' if failures else 'PASS'}**; "
        'the open human rows are listed in every case.',
        f'- {endurance_line(stages)}',
        '',
        '## Stage results',
        '',
        '| Stage | Command | Result | Log |',
        '| --- | --- | --- | --- |',
    ]
    for stage in stages:
        log = Path(stage['log'])
        lines.append(f"| `{stage['name']}` | `{stage['command']}` | {stage['status']} | "
                     f"`{log.name}` |")
    lines.append('')
    environment = evidence / 'environment.txt'
    lines.extend(['## Environment and preflight', ''])
    if environment.exists():
        lines.extend(['```text', environment.read_text().rstrip(), '```'])
    else:
        lines.append('- No environment record; the preflight stage did not run.')
    lines.append('')
    lines.extend(['## Host tests (fresh `--rerun-tasks` sweep)', ''])
    lines.append('| Module | Tests | Failures | Errors | Skipped | Results |')
    lines.append('| --- | --- | --- | --- | --- | --- |')
    total = dict(tests=0, failures=0, errors=0, skipped=0)
    stale = []
    for module, candidates in HOST_RESULTS.items():
        counts = module_totals(ROOT, module, candidates)
        if counts is None:
            lines.append(f'| {module} | — | — | — | — | not recorded |')
            continue
        for key in ('tests', 'failures', 'errors', 'skipped'):
            total[key] += counts[key]
        state = counts['directory']
        if counts['newest'] < started['epochSeconds'] - 5:
            state = f"{state} (STALE: older than this run)"
            stale.append(module)
        lines.append(f"| {module} | {counts['tests']} | {counts['failures']} | "
                     f"{counts['errors']} | {counts['skipped']} | `{state}` |")
    lines.append(f"| **total** | **{total['tests']}** | **{total['failures']}** | "
                 f"**{total['errors']}** | **{total['skipped']}** | |")
    if stale:
        lines.extend(['', f'STALE results for: {", ".join(stale)} — treat the numbers as unusable.'])
    lines.append('')
    lines.extend(device_section(evidence))
    lines.extend(visual_section(evidence))
    apk = evidence / 'apk-audit.log'
    lines.extend(['## Packaged-APK audit', ''])
    if apk.exists():
        lines.extend(['```text', apk.read_text().rstrip(), '```'])
    else:
        lines.append('- The APK audit did not run.')
    lines.append('')
    lines.extend(scan_section(evidence))
    findings = evidence / 'findings.md'
    if findings.exists() and findings.read_text().strip() not in ('', '# Findings'):
        lines.extend(['## Findings (stable IDs)', '', findings.read_text().rstrip(), ''])
    else:
        lines.extend(['## Findings (stable IDs)', '', 'None: every stage passed.', ''])
    for name, title in OPTIONAL_SECTIONS:
        lines.extend(file_section(evidence, name, title))
    lines.extend(['## Input identities (16-character SHA-256 prefixes)', ''])
    for relative in IDENTITY_FILES:
        path = ROOT / relative
        if path.exists():
            lines.append(f'- `{relative}`: {sha256_prefix(path)}')
    for baseline in sorted((ROOT / 'app/src/test/screenshots').glob('*.png')):
        lines.append(f'- `app/src/test/screenshots/{baseline.name}`: {sha256_prefix(baseline)}')
    lines.append('')
    lines.extend(['## Human rows still open', ''])
    if HUMAN_ROWS:
        lines.extend(f'- {row}' for row in HUMAN_ROWS)
    else:
        lines.append('- None. The owner acceptance below is not an automated result.')
    lines.append('')
    lines.extend(['## Owner acceptance (not automated)', ''])
    lines.extend(f'- {row}' for row in OWNER_ACCEPTED)
    lines.append('')
    return '\n'.join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence', required=True, type=Path)
    args = parser.parse_args()
    if not args.evidence.is_dir():
        print(f'FAIL: {args.evidence} is not a directory', file=sys.stderr)
        return 1
    report = build_report(args.evidence.resolve())
    (args.evidence / 'QA-REPORT.md').write_text(report)
    print(f'QA report written: {args.evidence / "QA-REPORT.md"}')
    stages = read_stages(args.evidence / 'stages.tsv')
    failed = [stage['name'] for stage in stages if stage['status'] != 'PASS']
    print(f"stages: {len(stages)} run, {'all PASS' if not failed else 'FAIL: ' + ', '.join(failed)}")
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
