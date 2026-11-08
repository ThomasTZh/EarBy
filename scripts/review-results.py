"""Parsers for packaged instrumentation identity and Android runner results."""
import re
import xml.etree.ElementTree as ET


def endurance_memory(samples):
    """Bound PSS variation after warmup, excluding shutdown's expected resource release."""
    active = sorted((sample for sample in samples if 180 <= sample['elapsedSeconds'] <= 1740),
                    key=lambda sample: sample['elapsedSeconds'])
    if len(active) < 25 or active[-1]['elapsedSeconds'] - active[0]['elapsedSeconds'] < 24 * 60:
        raise ValueError('Endurance memory evidence is incomplete')
    pss = []
    for sample in active:
        match = re.search(r'TOTAL PSS:\s+(\d+)', sample['memory'])
        if not match:
            raise ValueError('Endurance sample lacks TOTAL PSS')
        if 'mWakefulness=Asleep' not in sample['power']:
            raise ValueError('Endurance sample does not establish display off')
        pss.append(int(match[1]))
    variation = max(pss) - min(pss)
    if variation > 64 * 1024:
        raise ValueError(f'Endurance PSS variation exceeds 64 MiB: {variation} KiB')
    return dict(samples=len(active), firstElapsedSeconds=active[0]['elapsedSeconds'],
                lastElapsedSeconds=active[-1]['elapsedSeconds'], minimumPssKiB=min(pss),
                maximumPssKiB=max(pss), variationKiB=variation, limitKiB=64 * 1024)


def apk_identity(badging, manifest):
    package = re.search(r"^package: name='([^']+)'", badging, re.M)
    if not package:
        raise ValueError('Packaged application ID is missing')
    instrumentation = re.search(r'^([ ]*)E: instrumentation[^\n]*\n((?:\1 +[^\n]*\n)*)', manifest, re.M)
    if not instrumentation:
        return package[1], None, None
    attributes = instrumentation[2]
    def attribute(name):
        match = re.search(rf':{name}\([^)]*\)="([^"]+)"', attributes)
        if not match:
            raise ValueError(f'Packaged instrumentation {name} is missing')
        return match[1]
    return package[1], attribute('targetPackage'), attribute('name')


def instrumentation_xml(output, suite):
    """Require a completed nonempty runner result; adb's exit status is insufficient."""
    summary = re.search(r'OK \((\d+) tests?\)', output)
    statuses = {}
    cases = []
    invalid = False
    for line in output.splitlines():
        if line.startswith('INSTRUMENTATION_STATUS: '):
            key, separator, value = line[len('INSTRUMENTATION_STATUS: '):].partition('=')
            if separator:
                statuses[key] = value
        elif line.startswith('INSTRUMENTATION_STATUS_CODE: '):
            code = int(line.split(': ', 1)[1])
            if code != 1:
                invalid |= code != 0
                cases.append((statuses.get('class', ''), statuses.get('test', ''), code))
            statuses = {}
    if (not summary or int(summary[1]) <= 0 or invalid or
            len(cases) != int(summary[1]) or any(not name for _, name, _ in cases) or
            re.search(r'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|INSTRUMENTATION_ABORTED', output)):
        raise ValueError('Instrumentation did not report a complete nonempty passing test suite')
    root = ET.Element('testsuite', name=suite, tests=str(len(cases)), failures='0', errors='0', skipped='0')
    for klass, name, _ in cases:
        ET.SubElement(root, 'testcase', classname=klass, name=name)
    return ET.tostring(root, encoding='unicode', xml_declaration=True)
