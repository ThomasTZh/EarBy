#!/usr/bin/env python3
"""Produce octave variants for review after the "tave" report.

Development probe: the reviewer reported the opening "oc" missing, so this writes several takes
of the same word with different generation settings and tempo factors for a listening decision.
"""

from __future__ import annotations

import argparse
import json
import subprocess
from pathlib import Path

VARIANTS = (
    ("flat100", 0.2, 0.1, 1.0),
    ("flat85", 0.2, 0.1, 0.85),
    ("mid85", 0.35, 0.3, 0.85),
)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--text", default="Octave")
    parser.add_argument("--out", default="candidates/octave-probe")
    args = parser.parse_args()

    import torchaudio

    from chatterbox.tts import ChatterboxTTS

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    model = ChatterboxTTS.from_pretrained(device="cpu")
    records = []
    for name, cfg_weight, exaggeration, tempo in VARIANTS:
        raw = out / f"octave.{name}.raw.wav"
        wav = model.generate(args.text, cfg_weight=cfg_weight, exaggeration=exaggeration)
        torchaudio.save(str(raw), wav, model.sr)
        target = out / f"octave.{name}.wav"
        filters = [
            "silenceremove=start_periods=1:start_silence=0:start_threshold=-50dB:detection=peak",
            "areverse",
            "silenceremove=start_periods=1:start_silence=0:start_threshold=-50dB:detection=peak",
            "areverse",
            "adelay=100:all=1",
            "apad=pad_dur=0.12",
        ]
        if tempo != 1.0:
            filters.append(f"atempo={tempo}")
        filters.append("loudnorm=I=-20:TP=-3:linear=true")
        subprocess.run(
            [
                "ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-i", str(raw),
                "-af", ",".join(filters), "-ac", "1", "-ar", "48000", "-c:a", "pcm_s16le", str(target),
            ],
            check=True,
        )
        records.append({"variant": name, "cfgWeight": cfg_weight, "exaggeration": exaggeration,
                        "tempo": tempo, "audioSeconds": round(wav.shape[-1] / model.sr, 3), "path": str(target)})
        print(json.dumps(records[-1]), flush=True)
    (out / "summary.json").write_text(json.dumps(records, indent=2), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
