#!/usr/bin/env python3
"""Check local Markdown links and reject NUL bytes in maintained documents."""
from pathlib import Path
import re
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parent.parent
errors = []
paths = [ROOT / 'README.md']
if (ROOT / 'AGENTS.md').is_file():
    paths.append(ROOT / 'AGENTS.md')
paths.extend(sorted((ROOT / 'docs').rglob('*.md')))
for path in paths:
    text = path.read_text()
    if '\0' in text:
        errors.append(f'{path.relative_to(ROOT)}: NUL byte')
    for target in re.findall(r'\]\(([^)]+)\)', text):
        target = target.strip().split(' "', 1)[0].strip('<>')
        if re.match(r'^[a-zA-Z]+:', target) or target.startswith('#'):
            continue
        target = unquote(target.split('#', 1)[0])
        if target and not (path.parent / target).exists():
            errors.append(f'{path.relative_to(ROOT)}: missing link {target}')
if errors:
    raise SystemExit('\n'.join(errors))
print(f'PASS: local links and text integrity in {len(paths)} documents')
