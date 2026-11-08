#!/usr/bin/env python3
"""Fail closed when artifact verification has not been bootstrapped."""
from pathlib import Path
import xml.etree.ElementTree as ET

path = Path(__file__).resolve().parents[1] / 'gradle/verification-metadata.xml'
if not path.is_file():
    raise SystemExit('BLOCKED: dependency checksums missing. Run scripts/record-dependencies.sh on a network-enabled host, review and commit its output.')
root = ET.parse(path).getroot()
for element in root.iter():
    element.tag = element.tag.rsplit('}', 1)[-1]
if root.findtext('configuration/verify-metadata') != 'true':
    raise SystemExit('FAIL: dependency metadata verification must be enabled')
if root.find('configuration/trusted-artifacts/*') is not None:
    raise SystemExit('FAIL: blanket trusted-artifact exceptions are not permitted')
artifacts = root.findall('components/component/artifact')
if not artifacts or any(artifact.find('sha256') is None for artifact in artifacts):
    raise SystemExit('FAIL: every resolved artifact must have a SHA-256 checksum')
print(f'PASS: checksum entries for {len(artifacts)} dependency artifacts')
