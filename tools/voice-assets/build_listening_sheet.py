#!/usr/bin/env python3
"""Build the owner listening sheet for the v0.2.x voice candidates.

Reads the semantic catalog and the Piper generation summary, writes a markdown table and an
HTML page with inline players under `.local/verification/mode-expansion/M4/`. Read-only with
respect to the audio; an ordinary run never marks anything accepted.
"""
from __future__ import annotations

import argparse
import html
import json
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VOCABULARY = Path(__file__).resolve().parent / "vocabulary.json"

ORDER = ["chord", "scale", "note", "fixeddo", "degree", "connector", "interval"]


def clip_seconds(path: Path) -> float:
    with wave.open(str(path), "rb") as handle:
        return handle.getnframes() / handle.getframerate()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--candidates", default=None)
    parser.add_argument("--out-dir", default=None)
    args = parser.parse_args()
    candidates = Path(args.candidates) if args.candidates else Path(__file__).parent / "candidates"
    out_dir = (
        Path(args.out_dir) if args.out_dir else ROOT / ".local/verification/mode-expansion/M4"
    )
    out_dir.mkdir(parents=True, exist_ok=True)

    entries = json.loads(VOCABULARY.read_text(encoding="utf-8"))["entries"]
    summary_path = candidates / "generation-summary-piper-alba.json"
    generated = {}
    if summary_path.is_file():
        for record in json.loads(summary_path.read_text(encoding="utf-8"))["phrases"]:
            generated[record["voiceId"]] = record

    pending = [e for e in entries if not e.get("bundled")]
    pending.sort(key=lambda e: (ORDER.index(e["namespace"]) if e["namespace"] in ORDER else 99,
                                e["id"]))

    rows = []
    for entry in pending:
        path = candidates / f"{entry['id']}.wav"
        seconds = clip_seconds(path) if path.is_file() else None
        rows.append((entry["id"], entry["spokenText"], seconds))

    md = out_dir / "listening-sheet.md"
    with md.open("w", encoding="utf-8") as handle:
        handle.write("# M4.4 voice listening sheet (piper-alba)\n\n")
        handle.write("Listen to each candidate under `tools/voice-assets/candidates/`. Mark each\n")
        handle.write("row `accept` or `regenerate`. The 13 interval clips already have their\n")
        handle.write("review and stay frozen.\n\n")
        handle.write("| # | Voice id | Spoken text | Seconds | File | Decision |\n")
        handle.write("| --- | --- | --- | --- | --- | --- |\n")
        for index, (voice_id, text, seconds) in enumerate(rows, 1):
            shown = f"{seconds:.3f}" if seconds is not None else "missing"
            handle.write(
                f"| {index} | `{voice_id}` | {text} | {shown} | `candidates/{voice_id}.wav` | |\n"
            )

    page = out_dir / "listening-sheet.html"
    with page.open("w", encoding="utf-8") as handle:
        handle.write(
            '<!doctype html><meta charset="utf-8"><title>M4.4 listening sheet</title>'
            '<style>body{font-family:sans-serif;margin:24px}td{padding:4px 10px}'
            'audio{height:28px;vertical-align:middle}</style>'
            '<h1>M4.4 voice listening sheet — piper-alba</h1><table><tr><th>#</th>'
            '<th>Voice id</th><th>Spoken text</th><th>Seconds</th><th>Audio</th></tr>'
        )
        for index, (voice_id, text, seconds) in enumerate(rows, 1):
            shown = f"{seconds:.3f}" if seconds is not None else "missing"
            handle.write(
                f"<tr><td>{index}</td><td><code>{voice_id}</code></td>"
                f"<td>{html.escape(text)}</td><td>{shown}</td>"
                f'<td><audio controls preload="none" src="'
                f'../../../../tools/voice-assets/candidates/{voice_id}.wav"></audio></td></tr>'
            )
        handle.write("</table>")
    print(f"rows: {len(rows)}; missing: {sum(1 for r in rows if r[2] is None)}")
    print(md)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
