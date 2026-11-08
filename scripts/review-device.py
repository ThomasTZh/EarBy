#!/usr/bin/env python3
"""Isolated, automated phone acceptance with bounded instrumentation watchdogs."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('review_results', ROOT / 'scripts/review-results.py')
results = importlib.util.module_from_spec(spec)
spec.loader.exec_module(results)
REGULAR = 'com.earby.app'
REVIEW = REGULAR + '.review'


def run(args, timeout=60, check=True):
    result = subprocess.run([str(arg) for arg in args], capture_output=True, timeout=timeout)
    if check and result.returncode:
        # Device serials live in ignored metadata, never in this exception's command text.
        raise RuntimeError(f'Command failed ({Path(str(args[0])).name}): {result.stderr.decode(errors="replace")}')
    return result.stdout.decode(errors='replace').strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--automated-only', action='store_true', help='Includes the real 30-minute endurance test')
    mode.add_argument('--smoke-only', action='store_true', help='Short suites; endurance remains unexecuted')
    parser.add_argument('--skip-build', action='store_true', help='Use freshly built APKs from check-full')
    parser.add_argument('--class', dest='classes', help='Short suite selection for repair iterations')
    parser.add_argument('--out', type=Path, default=ROOT / '.local/verification/project-review-2026-10-11/device')
    args = parser.parse_args()
    if args.classes and args.automated_only:
        parser.error('--class requires --smoke-only; final acceptance cannot narrow the suite')
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    devices = run(['adb', 'devices'])
    authorized = re.findall(r'^(\S+)\s+device$', devices, re.M)
    serial = os.environ.get('ANDROID_SERIAL')
    if serial is None:
        if len(authorized) != 1:
            raise RuntimeError('Select exactly one authorized phone with ANDROID_SERIAL')
        serial = authorized[0]
    if serial not in authorized:
        raise RuntimeError('Selected phone is not authorized')
    def adb(*arguments, timeout=60, check=True):
        return run(['adb', '-s', serial, *arguments], timeout, check)
    aapt = Path(os.environ['ANDROID_HOME']) / 'build-tools/36.0.0/aapt2'
    if not args.skip_build:
        with (out / 'build.log').open('wb') as log:
            subprocess.run([str(ROOT / 'gradlew'), '--dependency-verification', 'strict',
                            '-Pear.device.variant=review', ':app:assembleReview',
                            ':app:assembleReviewAndroidTest', ':core:data:assembleDebugAndroidTest',
                            ':core:audio:assembleDebugAndroidTest'], cwd=ROOT, stdout=log,
                           stderr=subprocess.STDOUT, check=True, timeout=900)
    paths = [ROOT / 'app/build/outputs/apk/review/app-review.apk',
             ROOT / 'app/build/outputs/apk/androidTest/review/app-review-androidTest.apk',
             ROOT / 'core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk',
             ROOT / 'core/audio/build/outputs/apk/androidTest/debug/audio-debug-androidTest.apk']
    artifacts = []
    for path in paths:
        badge = run([aapt, 'dump', 'badging', path])
        manifest = run([aapt, 'dump', 'xmltree', '--file', 'AndroidManifest.xml', path])
        package, target, runner = results.apk_identity(badge, manifest + '\n')
        if package == REGULAR or target == REGULAR:
            raise RuntimeError('Refusing to install or instrument the regular app')
        artifacts.append(dict(path=str(path.relative_to(ROOT)), package=package, target=target,
                              runner=runner, sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
        (out / f'{package}-manifest.txt').write_text(manifest)
    if artifacts[0]['package'] != REVIEW or artifacts[1]['target'] != REVIEW:
        raise RuntimeError('Review application/instrumentation identity mismatch')
    def regular_snapshot():
        package = adb('shell', 'dumpsys', 'package', REGULAR)
        apk_paths = adb('shell', 'pm', 'path', REGULAR, check=False)
        apk_hashes = [adb('shell', 'sha256sum', line.removeprefix('package:'), check=False)
                      for line in apk_paths.splitlines() if line.startswith('package:')]
        # Hashes only, no settings or personal data contents. Read-only access to this project's app.
        data_hashes = adb('shell', 'run-as', REGULAR, 'sh', '-c',
                         "'find files databases shared_prefs -type f -exec sha256sum {} \\; 2>/dev/null'", check=False)
        return dict(version=re.findall(r'version(?:Code|Name)=[^\n]+', package),
                    apk=apk_hashes, private_files=sorted(data_hashes.splitlines()))
    before = regular_snapshot()
    (out / 'regular-before.json').write_text(json.dumps(before, indent=2))
    power_before = adb('shell', 'dumpsys', 'power')
    initially_interactive = 'mWakefulness=Awake' in power_before
    metadata = dict(serial=serial, startedUTC=time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
                    model=adb('shell', 'getprop', 'ro.product.model'),
                    api=adb('shell', 'getprop', 'ro.build.version.sdk'),
                    abi=adb('shell', 'getprop', 'ro.product.cpu.abi'),
                    pageSize=adb('shell', 'getconf', 'PAGE_SIZE'), artifacts=artifacts,
                    inputs={str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest()
                            for path in [ROOT / 'app/src/main/assets/voice/voice-pack.json',
                                         ROOT / 'app/src/main/assets/soundfont/piano.json',
                                         ROOT / 'app/src/main/assets/soundfont/earby-string-pack.bin',
                                         ROOT / 'synthNotes/StringSynth_A0-C8.sf2']})
    (out / 'metadata.json').write_text(json.dumps(metadata, indent=2))
    outcomes = []
    def instrument(artifact, name, classes=None, large=False, timeout=600):
        command = ['adb', '-s', serial, 'shell', 'am', 'instrument', '-w', '-r',
                   '-e', 'notAnnotation', 'androidx.test.filters.SmallTest' if large else 'androidx.test.filters.LargeTest']
        if classes:
            command += ['-e', 'class', classes]
        command += [artifact['package'] + '/' + artifact['runner']]
        print(f'RUN {name}', flush=True)
        started = time.monotonic()
        log_path = out / (name + '.log')
        with log_path.open('wb') as log:
            process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT)
            try:
                next_sample = started + 60
                while process.poll() is None:
                    now = time.monotonic()
                    if now - started > timeout:
                        raise TimeoutError(f'{name} exceeded its {timeout}-second watchdog')
                    if now >= next_sample:
                        stamp = int(now - started)
                        sample = {'elapsedSeconds': stamp,
                                  'memory': adb('shell', 'dumpsys', 'meminfo', artifact['target']),
                                  'service': adb('shell', 'dumpsys', 'activity', 'services', artifact['target']),
                                  'power': '\n'.join(line for line in adb('shell', 'dumpsys', 'power').splitlines()
                                                     if 'mWakefulness=' in line or 'passiveeartraining' in line.lower())}
                        (out / f'{name}-{stamp:04d}.json').write_text(json.dumps(sample, indent=2))
                        print(f'RUN {name}: {stamp}s elapsed; sample captured', flush=True)
                        next_sample = now + 60
                    time.sleep(1)
            finally:
                if process.poll() is None:
                    process.terminate()
                    try:
                        process.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        process.kill()
        output = log_path.read_text(errors='replace')
        try:
            xml = results.instrumentation_xml(output, name)
            if process.returncode:
                raise ValueError(f'adb exited {process.returncode}')
        except ValueError as failure:
            suite = ET.Element('testsuite', name=name, tests='1', failures='1')
            case = ET.SubElement(suite, 'testcase', name='instrumentationCompletion')
            ET.SubElement(case, 'failure', message=str(failure)).text = output
            (out / (name + '.xml')).write_text(ET.tostring(suite, encoding='unicode'))
            outcomes.append(f'FAIL {name}: {failure}')
            raise
        (out / (name + '.xml')).write_text(xml)
        count = ET.fromstring(xml).get('tests')
        outcomes.append(f'PASS {name}: {count} tests, {int(time.monotonic()-started)}s')
        print(outcomes[-1], flush=True)
    failure = None
    try:
        adb('shell', 'am', 'force-stop', REVIEW)
        for path in paths:
            adb('install', '-r', '-t', str(path), timeout=240)
        # A prior screen-off test can leave a non-secure keyguard covering activities.
        # Dismiss only through the platform command; never bypass a secure credential.
        adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
        adb('shell', 'wm', 'dismiss-keyguard')
        for attempt in range(20):
            policy = adb('shell', 'dumpsys', 'window', 'policy')
            if re.search(r'KeyguardServiceDelegate\s+showing=false', policy):
                break
            time.sleep(0.5)
        (out / 'ui-keyguard-precondition.txt').write_text(policy)
        if not re.search(r'KeyguardServiceDelegate\s+showing=false', policy):
            raise RuntimeError('Keyguard still covers UI; automated activity tests cannot proceed')
        classes = args.classes or ','.join('com.thomas.passiveeartraining.' + klass for klass in [
            'AppLaunchTest', 'PlaybackServiceDeviceTest', 'SleepTimerCommandTest',
            'ReviewSessionDeviceTest', 'ReviewUiDeviceTest', 'ReviewPersistenceDeviceTest',
            'ReviewReliabilityDeviceTest'])
        instrument(artifacts[1], 'review-short', classes, timeout=900)
        if not args.classes:
            instrument(artifacts[2], 'data-platform')
            instrument(artifacts[3], 'audio-platform')
            klass = 'com.thomas.passiveeartraining.ReviewProcessDeviceTest#'
            instrument(artifacts[1], 'before-force-stop', klass + 'startForExternalForceStop', large=True)
            (out / 'before-force-stop-service.txt').write_text(
                adb('shell', 'dumpsys', 'activity', 'services', REVIEW))
            adb('shell', 'am', 'force-stop', REVIEW)
            if adb('shell', 'pidof', REVIEW, check=False):
                raise RuntimeError('Review process survived force-stop')
            instrument(artifacts[1], 'after-force-stop', klass + 'relaunchRestoresTheSetupAndStaysStopped', large=True)
        if args.automated_only:
            instrument(artifacts[1], 'endurance-30m',
                       'com.thomas.passiveeartraining.ReviewEnduranceDeviceTest', large=True, timeout=35 * 60)
            summary = results.endurance_memory([
                json.loads(path.read_text()) for path in out.glob('endurance-30m-*.json')])
            (out / 'endurance-memory.json').write_text(json.dumps(summary, indent=2))
            outcomes.append(f"PASS endurance memory: {summary['samples']} samples; "
                            f"PSS variation {summary['variationKiB']} KiB <= 65536 KiB")
        else:
            outcomes.append('UNEXECUTED real 30-minute screen-off endurance (--smoke-only)')
    except BaseException as error:
        failure = error
        outcomes.append(f'FAIL driver: {type(error).__name__}: {error}')
    finally:
        # Capture only this application UID's logs. Never clear the device's shared log buffer.
        package_state = adb('shell', 'dumpsys', 'package', REVIEW, check=False)
        uid = re.search(r'(?:userId|appId)=(\d+)', package_state)
        if uid:
            (out / 'app-logcat.txt').write_text(adb('logcat', '-d', '--uid=' + uid[1], check=False))
        (out / 'app-exit-info.txt').write_text(adb('shell', 'dumpsys', 'activity', 'exit-info', REVIEW, check=False))
        for name in ['review-endurance.tsv', 'review-cycles.tsv', 'review-ui-1.5.png', 'review-ui-2.0.png']:
            data = subprocess.run(['adb', '-s', serial, 'exec-out', 'run-as', REVIEW,
                                   'cat', 'files/' + name], capture_output=True, timeout=30)
            if data.returncode == 0:
                (out / name).write_bytes(data.stdout)
        adb('shell', 'am', 'force-stop', REVIEW, check=False)
        for artifact in artifacts[1:]:
            adb('shell', 'am', 'force-stop', artifact['package'], check=False)
        # Tests use an app-private font configuration; no global font/network settings change.
        adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP' if initially_interactive else 'KEYCODE_SLEEP', check=False)
        after = regular_snapshot()
        (out / 'regular-after.json').write_text(json.dumps(after, indent=2))
        if before != after:
            outcomes.append('FAIL regular app hash comparison changed; inspect before/after evidence')
            failure = failure or RuntimeError('Regular app hash comparison changed')
        else:
            outcomes.append('PASS regular app package and private-file hashes unchanged')
        outcomes.append('AUTOMATED ONLY: acoustic quality, accessories, TalkBack and interval-walk acceptance remain owner-reported; see the QA report')
        (out / 'report.txt').write_text('\n'.join(outcomes) + '\n')
        print('\n'.join(outcomes), flush=True)
    if failure:
        raise RuntimeError(f'Review device verification failed; see {out}') from failure


if __name__ == '__main__':
    main()
