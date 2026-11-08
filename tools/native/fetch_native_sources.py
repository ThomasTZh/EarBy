#!/usr/bin/env python3
"""Fetch and verify the pinned native sources described by tools/native/sources.json.

The manifest records an immutable revision and a SHA-256 per source. This script verifies the
recorded hash when one is present and refuses to continue on a mismatch; with `--record` it
writes a missing hash after the first successful download, which is an intentional
dependency-recording operation, not part of a routine check. Nothing is downloaded during an
ordinary Gradle build.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import sys
import tarfile
import urllib.request
from pathlib import Path

MANIFEST = Path(__file__).resolve().parent / "sources.json"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def download(url: str, target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    partial = target.with_suffix(target.suffix + ".part")
    with urllib.request.urlopen(url) as response, partial.open("wb") as handle:  # noqa: S310
        shutil.copyfileobj(response, handle)
    partial.replace(target)


def extract(archive: Path, name: str, destination: Path, strip_components: int) -> None:
    if destination.exists():
        return
    work = destination.with_name(destination.name + ".extract")
    if work.exists():
        shutil.rmtree(work)
    work.mkdir(parents=True)
    if tarfile.is_tarfile(archive):
        with tarfile.open(archive) as tar:
            members = []
            for member in tar.getmembers():
                parts = Path(member.name).parts
                if len(parts) <= strip_components:
                    continue
                member.name = str(Path(*parts[strip_components:]))
                members.append(member)
            tar.extractall(work, members=members)  # noqa: S202
    else:
        shutil.copyfile(archive, work / archive.name)
    work.replace(destination)
    print(f"extracted {name} -> {destination}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Fetch pinned native sources")
    parser.add_argument("--destination", default=".local/native-sources")
    parser.add_argument("--cache", default=".local/native-downloads")
    parser.add_argument("--record", action="store_true", help="record missing SHA-256 values")
    parser.add_argument("--check", action="store_true", help="verify cached archives only")
    args = parser.parse_args(argv)

    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    cache = Path(args.cache)
    destination = Path(args.destination)
    failures = 0
    for source in manifest["sources"]:
        name = source["name"]
        archive = cache / Path(source["url"]).name
        expected = source.get("archiveSha256") or ""
        if not archive.is_file():
            if args.check:
                print(f"BLOCKED: {name} is not cached at {archive}; run scripts/fetch-native-sources.sh")
                failures += 1
                continue
            print(f"downloading {name} from {source['url']}")
            download(source["url"], archive)
        actual = sha256(archive)
        if expected and actual != expected:
            print(f"FAIL: {name} archive SHA-256 mismatch ({actual} != {expected})", file=sys.stderr)
            failures += 1
            continue
        if not expected:
            if args.record:
                source["archiveSha256"] = actual
                print(f"recorded {name} SHA-256 {actual}")
            else:
                print(f"WARN: {name} has no recorded SHA-256 yet; rerun with --record to record {actual}")
        if not args.check:
            extract(archive, name, destination / name, int(source.get("stripComponents", 0)))
        print(f"OK: {name} {source['revision']} sha256={actual}")

    if args.record and failures == 0:
        MANIFEST.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
        print(f"updated {MANIFEST}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
