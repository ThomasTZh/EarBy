#!/usr/bin/env python3
"""Compare rate-control settings for one phrase, for the reviewer to listen to.

Development probe: generates one take with the flatter delivery parameters, then applies
different tempo factors and writes PCM16 candidates so the reviewer can pick a speaking rate.
"""

from __future__ import annotations

import argparse
import json
import subprocess
from pathlib import Path

TEMPO_FACTORS = (1.0, 0.85, 0.75)

# Flatter delivery: lower exaggeration and cfg_weight reduce the expressive pitch contour.
CFG_WEIGHT = 0.2
EXAGGERATION = 0.1


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--id", default="interval.major_seventh")
    parser.add_argument("--text", default="Major seventh")
    parser.add_argument("--out", default="candidates/rate-probe")
    args = parser.parse_args()

    import torchaudio

    from chatterbox.tts import ChatterboxTTS

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    raw = out / f"{args.id}.raw.wav"

    model = ChatterboxTTS.from_pretrained(device="cpu")
    wav = model.generate(args.text, cfg_weight=CFG_WEIGHT, exaggeration=EXAGGERATION)
    torchaudio.save(str(raw), wav, model.sr)

    records = []
    for factor in TEMPO_FACTORS:
        target = out / f"{args.id}.tempo{int(factor * 100)}.wav"
        filters = [
            "silenceremove=start_periods=1:start_silence=0:start_threshold=-50dB:detection=peak",
            "areverse",
            "silenceremove=start_periods=1:start_silence=0:start_threshold=-50dB:detection=peak",
            "areverse",
            "adelay=80:all=1",
            "apad=pad_dur=0.12",
        ]
        if factor != 1.0:
            filters.append(f"atempo={factor}")
        filters.append("loudnorm=I=-20:TP=-3:linear=true")
        subprocess.run(
            [
                "ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-i", str(raw),
                "-af", ",".join(filters), "-ac", "1", "-ar", "48000", "-c:a", "pcm_s16le", str(target),
            ],
            check=True,
        )
        records.append({"tempo": factor, "path": str(target)}) 
        print(json.dumps(records[-1]), flush=True)
    (out / "rate-probe-summary.json").write_text(json.dumps(records, indent=2), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
