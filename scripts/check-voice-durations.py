#!/usr/bin/env python3
"""Refuse stale domain voice metadata; ordinary checks never regenerate it."""
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent.parent
manifest = json.loads((ROOT / 'app/src/main/assets/voice/voice-pack.json').read_text())
source = (ROOT / 'core/training/src/main/kotlin/com/thomas/passiveeartraining/training/BundledVoiceDurations.kt').read_text()
actual = {key: int(value) for key, value in re.findall(r'VoiceAssetId\("([^"]+)"\) to (\d+)\.microseconds', source)}
expected = {entry['id']: entry['frameCount'] * 1_000_000 // entry['sampleRate'] for entry in manifest['entries']}
if actual != expected:
    raise SystemExit('FAIL: domain voice durations differ from packaged voice metadata')
print(f'PASS: {len(expected)} domain voice durations match packaged metadata')
