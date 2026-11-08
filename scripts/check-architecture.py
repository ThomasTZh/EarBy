#!/usr/bin/env python3
"""Static dependency guards for the six modules; not a full Kotlin parser."""
from pathlib import Path
import re
import sys
import tomllib

ROOT = Path(__file__).resolve().parents[1]
FORBIDDEN = re.compile(r'^\s*import\s+(?:android|androidx|com\.thomas\.passiveeartraining\.(?:audio|media|ui))(?:\.|\s|$)', re.MULTILINE)


def violations(root):
    problems = []
    catalog = tomllib.loads((root / 'gradle/libs.versions.toml').read_text())
    libraries = {key.replace('-', '.').replace('_', '.'): value for key, value in catalog['libraries'].items()}
    for module in ('music', 'training'):
        directory = root / 'core' / module
        for source in directory.rglob('*.kt'):
            if 'build' in source.relative_to(directory).parts:
                continue
            if FORBIDDEN.search(source.read_text()):
                problems.append(f'{source.relative_to(root)} imports an Android/UI dependency')
        build = directory / 'build.gradle.kts'
        text = build.read_text()
        if re.search(r'com\.android|libs\.plugins\.android', text):
            problems.append(f'{build.relative_to(root)} applies an Android plugin')
        for alias in re.findall(r'\b(?:implementation|api|compileOnly|testImplementation)\(libs\.([\w.]+)\)', text):
            library = libraries.get(alias)
            if library is None:
                problems.append(f'Unknown dependency alias: {alias}')
                continue
            artifact = library.get('module', library.get('group', ''))
            if artifact.startswith(('android', 'com.android', 'com.google.android')):
                problems.append(f'{build.relative_to(root)} depends on {artifact}')
        for dependency in re.findall(r'project\("([^"]+)"\)', text):
            if dependency != ':core:music' or module != 'training':
                problems.append(f'Forbidden module dependency: {module} -> {dependency}')
    allowed = {
        'audio': {':core:music', ':core:training'},
        'media': {':core:training', ':core:audio'},
        'data': {':core:music', ':core:training'},
        'app': {':core:music', ':core:training', ':core:audio', ':core:media', ':core:data'},
    }
    for module, dependencies in allowed.items():
        directory = root / ('app' if module == 'app' else f'core/{module}')
        build = directory / 'build.gradle.kts'
        if build.exists():
            for dependency in re.findall(r'project\("([^"]+)"\)', build.read_text()):
                if dependency not in dependencies:
                    problems.append(f'Forbidden module dependency: {module} -> {dependency}')
        for source in directory.rglob('*.kt'):
            relative = source.relative_to(directory)
            if 'build' in relative.parts or any(part in ('test', 'androidTest', 'reviewAndroidTest') for part in relative.parts):
                continue
            text = source.read_text()
            if module != 'app' and re.search(r'^\s*import\s+(?:androidx\.compose|com\.thomas\.passiveeartraining\.app)\.', text, re.M):
                problems.append(f'{source.relative_to(root)} imports UI into infrastructure')
            if module == 'app' and source.name != 'TrainingPlaybackService.kt' and re.search(
                    r'^\s*import\s+com\.thomas\.passiveeartraining\.audio\.', text, re.M):
                # Debug diagnostics and review-only injection own their dedicated audio consumers.
                if 'main' in relative.parts:
                    problems.append(f'{source.relative_to(root)} calls audio outside the service')
    return problems


if __name__ == '__main__':
    errors = violations(ROOT)
    for error in errors:
        print(f'FAIL: {error}', file=sys.stderr)
    if errors:
        sys.exit(1)
    print('PASS: static boundaries for all six modules')
