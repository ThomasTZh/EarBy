#!/usr/bin/env python3
"""Read-only screenshot set checks shared by verification and candidate generation.

The manifest next to the baselines is the single source of truth for which cases exist, so
adding a screen touches one file instead of every script.
"""
import json
import sys
from pathlib import Path

MANIFEST = Path(__file__).resolve().parent.parent / 'app/src/test/screenshots/manifest.json'


def load_cases(manifest_path: Path = MANIFEST) -> list[dict]:
    return json.loads(manifest_path.read_text())['cases']


def expected_names(manifest_path: Path = MANIFEST) -> frozenset[str]:
    return frozenset(Path(case['file']).stem for case in load_cases(manifest_path))


EXPECTED: frozenset[str] = expected_names()


def problems(directory: Path) -> list[str]:
    present = {path.stem for path in directory.glob('*.png')}
    result = [f'Missing screenshot: {name}.png' for name in sorted(EXPECTED - present)]
    result += [f'Unexpected screenshot: {name}.png' for name in sorted(present - EXPECTED)]
    for name in sorted(EXPECTED & present):
        if not (directory / f'{name}.png').read_bytes().startswith(b'\x89PNG\r\n\x1a\n'):
            result.append(f'Invalid PNG: {name}.png')
    return result


if __name__ == '__main__':
    errors = problems(Path(sys.argv[1]))
    if errors:
        raise SystemExit('\n'.join(errors))
    print(f'PASS: complete {len(EXPECTED)}-image screenshot set')
