#!/usr/bin/env python3
"""Search delivery settings that produce a falling, unstressed answer.

The reviewer requires a statement contour that falls at the end and refuses first-word
accentuation. This probe writes one take per candidate setting per phrase, so the settings can
be chosen from measurements (and then confirmed by listening) instead of guessed.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

# Candidate (name, cfg_weight, exaggeration) settings. What each does was measured, not assumed.
CANDIDATES = (
    ("cfg7_ex3", 0.7, 0.3),
    ("cfg7_ex5", 0.7, 0.5),
    ("cfg9_ex3", 0.9, 0.3),
    ("cfg9_ex5", 0.9, 0.5),
)

PHRASES = {
    "interval.major_second": "Major second",
    "interval.major_third": "Major third",
    "interval.minor_second": "Minor second",
    "interval.minor_seventh": "Minor seventh",
    "interval.minor_sixth": "Minor sixth",
    "interval.minor_third": "Minor third",
    "interval.octave": "Octave",
    "interval.tritone": "Tritone",
}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="raw/delivery")
    parser.add_argument("--only", nargs="*", default=None)
    args = parser.parse_args()

    import torchaudio

    from chatterbox.tts import ChatterboxTTS

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    model = ChatterboxTTS.from_pretrained(device="cpu")

    records = []
    for voice_id, text in PHRASES.items():
        if args.only and voice_id not in args.only:
            continue
        for name, cfg_weight, exaggeration in CANDIDATES:
            wav = model.generate(text, cfg_weight=cfg_weight, exaggeration=exaggeration)
            path = out / f"{voice_id}.{name}.wav"
            torchaudio.save(str(path), wav, model.sr)
            records.append({
                "voiceId": voice_id,
                "text": text,
                "candidate": name,
                "cfgWeight": cfg_weight,
                "exaggeration": exaggeration,
                "seconds": round(wav.shape[-1] / model.sr, 3),
                "path": str(path),
            })
            print(json.dumps(records[-1]), flush=True)
    (out / "probe-summary.json").write_text(json.dumps(records, indent=2), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
