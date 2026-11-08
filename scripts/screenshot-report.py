#!/usr/bin/env python3
"""Report screenshot candidates against the manifest; never approve or rewrite anything.

Strict for every approved case: a candidate must be pixel-identical to its baseline.
A case with status `declared-change` may differ only inside its declared rectangle and is
reported PENDING_VISUAL_APPROVAL; malformed, missing, unexpected or out-of-region differences
are automation failures. Owner approval is a separate, explicit action.
"""
import argparse
import hashlib
import json
import sys
from pathlib import Path

from PIL import Image
import numpy as np

DEFAULT_MANIFEST = Path('app/src/test/screenshots/manifest.json')


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def is_png(path: Path) -> bool:
    return path.read_bytes().startswith(b'\x89PNG\r\n\x1a\n')


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--candidates', required=True, type=Path)
    parser.add_argument('--manifest', type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument(
        '--report-only',
        action='store_true',
        help='Exit zero when only declared candidates differ (the automated-full default)',
    )
    args = parser.parse_args()

    manifest = json.loads(args.manifest.read_text())
    baseline_dir = args.manifest.parent
    cases = manifest['cases']
    expected_files = {case['file'] for case in cases}

    errors: list[str] = []
    pending: list[str] = []
    unchanged: list[str] = []
    observed = {path.name for path in args.candidates.glob('*.png')}
    for name in sorted(observed - expected_files):
        errors.append(f'Unexpected candidate not declared in the manifest: {name}')
    for case in cases:
        name = case['name']
        candidate = args.candidates / case['file']
        baseline = baseline_dir / case['file']
        if not candidate.exists():
            errors.append(f'Missing candidate: {case["file"]}')
            continue
        if not is_png(candidate):
            errors.append(f'Invalid PNG candidate: {case["file"]}')
            continue
        if not baseline.exists() or not is_png(baseline):
            errors.append(f'Baseline is missing or malformed: {case["file"]}')
            continue
        with Image.open(candidate) as candidate_image, Image.open(baseline) as baseline_image:
            candidate_pixels = np.asarray(candidate_image.convert('RGBA'), dtype=np.int16)
            baseline_pixels = np.asarray(baseline_image.convert('RGBA'), dtype=np.int16)
        if candidate_pixels.shape != baseline_pixels.shape:
            errors.append(
                f'{name}: candidate size {candidate_pixels.shape} differs from baseline '
                f'{baseline_pixels.shape}'
            )
            continue
        differs = np.any(candidate_pixels != baseline_pixels, axis=2)
        status = case['status']
        if status == 'approved':
            if differs.any():
                rows, columns = np.where(differs)
                errors.append(
                    f'{name}: undeclared difference from the approved baseline '
                    f'({int(differs.sum())} pixels; bbox '
                    f'x={int(columns.min())}..{int(columns.max())}, '
                    f'y={int(rows.min())}..{int(rows.max())})'
                )
            else:
                unchanged.append(name)
        elif status == 'declared-change':
            if not differs.any():
                errors.append(
                    f'{name}: declared change did not occur; the declaration is stale'
                )
                continue
            region = case['changedRegion']
            x0, y0 = region['x'], region['y']
            x1, y1 = x0 + region['width'], y0 + region['height']
            outside = differs.copy()
            outside[y0:y1, x0:x1] = False
            if outside.any():
                rows, columns = np.where(outside)
                count = int(outside.sum())
                sample = (int(columns[0]), int(rows[0]))
                errors.append(
                    f'{name}: {count} changed pixels fall outside the declared region '
                    f'{region}; first at {sample}'
                )
            else:
                pending.append(
                    f'{name} (reason: {case["reason"]}; candidate '
                    f'{sha256(candidate)[:12]}, baseline {sha256(baseline)[:12]})'
                )
        else:
            errors.append(f'{name}: unknown manifest status {status!r}')

    for name in unchanged:
        print(f'UNCHANGED: {name}')
    for name in pending:
        print(f'PENDING_VISUAL_APPROVAL: {name}')
    if errors:
        for error in errors:
            print(f'ERROR: {error}', file=sys.stderr)
        return 2
    if pending and not args.report_only:
        return 0
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
