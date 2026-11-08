#!/usr/bin/env python3
"""Install official JDK/SDK tooling locally; never change system packages."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
LOCAL = ROOT / '.local'
SDK = Path(os.environ.get('ANDROID_HOME', LOCAL / 'android-sdk'))


def download(url, destination):
    subprocess.run([
        'curl', '--fail', '--location', '--retry', '2', '--connect-timeout', '15',
        '--proto', '=https', '--proto-redir', '=https', url, '-o', str(destination),
    ], check=True)


def verify(path, expected, algorithm):
    digest = hashlib.new(algorithm)
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    if digest.hexdigest().lower() != expected.strip().lower():
        raise RuntimeError(f'Checksum mismatch: {path.name}')


def install_jdk(staging):
    target = LOCAL / 'jdk-17'
    if (target / 'bin/javac').is_file():
        print(f'Using existing project JDK: {target}')
        return
    if target.exists():
        raise RuntimeError(f'Incomplete installation at {target}; inspect it before retrying')
    metadata = staging / 'jdk.json'
    download('https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=linux&vendor=eclipse', metadata)
    package = json.loads(metadata.read_text())[0]['binary']['package']
    archive = staging / 'jdk.tar.gz'
    download(package['link'], archive)
    verify(archive, package['checksum'], 'sha256')
    extracted = staging / 'jdk'
    extracted.mkdir()
    with tarfile.open(archive) as bundle:
        bundle.extractall(extracted, filter='data')
    candidates = [p for p in extracted.iterdir() if (p / 'bin/javac').is_file()]
    if len(candidates) != 1:
        raise RuntimeError('Unexpected JDK archive structure')
    shutil.move(str(candidates[0]), target)
    shutil.copyfile(metadata, LOCAL / 'jdk-source.json')


def install_command_line_tools(staging):
    target = SDK / 'cmdline-tools/19.0'
    if (target / 'bin/sdkmanager').is_file():
        print(f'Using existing command-line tools: {target}')
        return
    if target.exists():
        raise RuntimeError(f'Incomplete installation at {target}; inspect it before retrying')
    repository = staging / 'repository.xml'
    download('https://dl.google.com/android/repository/repository2-1.xml', repository)
    tree = ET.parse(repository)
    # Strip namespaces so repository schema namespace revisions do not matter.
    for node in tree.iter():
        node.tag = node.tag.rsplit('}', 1)[-1]
    package = next((p for p in tree.iter('remotePackage') if p.get('path') == 'cmdline-tools;19.0'), None)
    if package is None:
        raise RuntimeError('Google repository does not list pinned command-line tools 19.0')
    archive_info = next((a for a in package.findall('./archives/archive') if a.findtext('host-os') == 'linux'), None)
    if archive_info is None:
        raise RuntimeError('No Linux command-line tools archive found')
    complete = archive_info.find('complete')
    checksum = complete.find('checksum')
    url = complete.findtext('url')
    if not url or '/' in url or not url.endswith('.zip'):
        raise RuntimeError('Unexpected command-line tools archive URL')
    archive = staging / 'sdk.zip'
    download('https://dl.google.com/android/repository/' + url, archive)
    verify(archive, checksum.text, checksum.get('type', 'sha1').replace('-', '').lower())
    extracted = staging / 'sdk'
    with zipfile.ZipFile(archive) as bundle:
        for item in bundle.infolist():
            destination = (extracted / item.filename).resolve()
            if not destination.is_relative_to(extracted.resolve()):
                raise RuntimeError('Unsafe SDK archive entry')
        bundle.extractall(extracted)
    source = extracted / 'cmdline-tools'
    if not (source / 'bin/sdkmanager').is_file():
        raise RuntimeError('Unexpected command-line tools archive structure')
    for executable in (source / 'bin').iterdir():
        executable.chmod(0o755)
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(source), target)
    (LOCAL / 'sdk-source.json').write_text(json.dumps({
        'package': 'cmdline-tools;19.0', 'url': url,
        'checksum_type': checksum.get('type', 'sha1'), 'checksum': checksum.text,
    }, indent=2) + '\n')


ROBOLECTRIC_JAR = 'android-all-instrumented-14-robolectric-10818077-i7.jar'
ROBOLECTRIC_VERSION = '14-robolectric-10818077-i7'
ROBOLECTRIC_SHA256 = '3fdac2d463f431c6b8d83b9d0ca739e795de75fffedee648c6761bd4bd864701'
ROBOLECTRIC_URL = (
    'https://repo1.maven.org/maven2/org/robolectric/android-all-instrumented/'
    f'{ROBOLECTRIC_VERSION}/{ROBOLECTRIC_JAR}'
)


def install_robolectric():
    """Provision the android-all runtime Robolectric needs, without touching the user's Maven cache."""
    target_dir = LOCAL / 'robolectric-dependencies'
    target = target_dir / ROBOLECTRIC_JAR
    if target.is_file():
        print(f'Using existing Robolectric runtime: {target}')
        return
    target_dir.mkdir(parents=True, exist_ok=True)
    maven = (
        Path.home() / '.m2' / 'repository' / 'org' / 'robolectric'
        / 'android-all-instrumented' / ROBOLECTRIC_VERSION / ROBOLECTRIC_JAR
    )
    if maven.is_file():
        shutil.copyfile(maven, target)
        print(f'Copied the Robolectric runtime from the local Maven cache: {target}')
    else:
        download(ROBOLECTRIC_URL, target)
        print(f'Downloaded the Robolectric runtime: {target}')
    verify(target, ROBOLECTRIC_SHA256, 'sha256')


def main():
    LOCAL.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='toolchain-', dir=LOCAL) as temporary:
        staging = Path(temporary)
        install_jdk(staging)
        install_command_line_tools(staging)
    install_robolectric()


if __name__ == '__main__':
    try:
        main()
    except (OSError, RuntimeError, subprocess.CalledProcessError, ValueError) as error:
        raise SystemExit(f'Toolchain setup failed: {error}') from error
