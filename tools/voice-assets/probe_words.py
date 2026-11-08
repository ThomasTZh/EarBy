#!/usr/bin/env python3
"""Per-word generation probe for a falling, unstressed delivery.

The reviewer asked for a contour that falls at the end and no stress on the first word. Two-word
phrases are therefore built from separately generated words with a controlled gap, so each word's
intonation can be chosen instead of accepting whatever contour one take happens to have.
"""

from __future__ import annotations

import argparse
import json
import subprocess
from pathlib import Path

# Word-level candidates: a single word has a more controllable contour than a fixed phrase.
WORD_CANDIDATES = (
    ("cfg5_ex2", 0.5, 0.2),
    ("cfg7_ex3", 0.7, 0.3),
    ("cfg3_ex2", 0.3, 0.2),
)

PHRASES = {
    "interval.major_second": ("Major", "second"),
    "interval.major_third": ("Major", "third"),
    "interval.minor_second": ("Minor", "second"),
    "interval.minor_seventh": ("Minor", "seventh"),
    "interval.minor_sixth": ("Minor", "sixth"),
    "interval.minor_third": ("Minor", "third"),
    "interval.tritone": ("Tritone",),
    "interval.octave": ("Octave",),
}


def postprocess(raw: Path, target: Path, tempo: float, leading_ms: int) -> None:
    filters = [
        "silenceremove=start_periods=1:start_silence=0:start_threshold=-50dB:detection=peak",
        "areverse",
        "silenceremove=start_periods=1:start_silence=0:start_threshold=-50dB:detection=peak",
        "areverse",
        f"adelay={leading_ms}:all=1",
        "apad=pad_dur=0.12",
    ]
    if tempo != 1.0:
        filters.append(f"atempo={tempo}")
    filters.append("loudnorm=I=-20:TP=-3:linear=true")
    subprocess.run(
        ["ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-i", str(raw), "-af", ",".join(filters),
         "-ac", "1", "-ar", "48000", "-c:a", "pcm_s16le", str(target)],
        check=True,
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="candidates/word-probe")
    parser.add_argument("--gap-ms", type=int, default=120)
    args = parser.parse_args()

    import torch
    import torchaudio

    from chatterbox.tts import ChatterboxTTS

    out = Path(args.out)
    raw_dir = Path("raw/words")
    out.mkdir(parents=True, exist_ok=True)
    raw_dir.mkdir(parents=True, exist_ok=True)
    model = ChatterboxTTS.from_pretrained(device="cpu")
    rate = model.sr

    records = []
    for voice_id, words in PHRASES.items():
        for name, cfg_weight, exaggeration in WORD_CANDIDATES:
            pieces = []
            for word in words:
                raw = raw_dir / f"{word.lower()}.{name}.wav"
                if not raw.is_file():
                    wav = model.generate(word, cfg_weight=cfg_weight, exaggeration=exaggeration)
                    torchaudio.save(str(raw), wav, rate)
                data, sr = torchaudio.load(str(raw))
                pieces.append(data)
            gap = torch.zeros(1, int(rate * args.gap_ms / 1000))
            combined = pieces[0]
            for piece in pieces[1:]:
                combined = torch.cat([combined, gap, piece], dim=1)
            combined_path = raw_dir / f"{voice_id}.{name}.combined.wav"
            torchaudio.save(str(combined_path), combined, rate)
            target = out / f"{voice_id}.{name}.wav"
            postprocess(combined_path, target, tempo=0.85, leading_ms=80)
            records.append({"voiceId": voice_id, "candidate": name, "cfgWeight": cfg_weight,
                            "exaggeration": exaggeration, "path": str(target)})
            print(json.dumps(records[-1]), flush=True)
    (out / "probe-summary.json").write_text(json.dumps(records, indent=2), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
