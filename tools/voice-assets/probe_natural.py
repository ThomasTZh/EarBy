#!/usr/bin/env python3
"""Search for takes whose own delivery already falls, without any word splitting.

Two rejected strategies are deliberately avoided here: per-word joining (audible break) and
aggressive low-emphasis settings (odd tone). Instead this draws several whole-phrase takes per
phrase and keeps the ones with a measured falling ending and no early pitch spike, so the
audio is unmodified model speech.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

# Whole-phrase settings around the model's own defaults.
SETTINGS = (
    ("d1", 0.5, 0.5),
    ("d2", 0.5, 0.25),
    ("d3", 0.4, 0.35),
    ("d4", 0.6, 0.5),
    ("d5", 0.5, 0.75),
)

PHRASES = {
    "interval.unison": "Unison",
    "interval.minor_second": "Minor second",
    "interval.major_second": "Major second",
    "interval.minor_third": "Minor third",
    "interval.major_third": "Major third",
    "interval.perfect_fourth": "Perfect fourth",
    "interval.tritone": "Tritone",
    "interval.perfect_fifth": "Perfect fifth",
    "interval.minor_sixth": "Minor sixth",
    "interval.major_sixth": "Major sixth",
    "interval.minor_seventh": "Minor seventh",
    "interval.major_seventh": "Major seventh",
}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="raw/natural")
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
        for name, cfg_weight, exaggeration in SETTINGS:
            wav = model.generate(text, cfg_weight=cfg_weight, exaggeration=exaggeration)
            path = out / f"{voice_id}.{name}.wav"
            torchaudio.save(str(path), wav, model.sr)
            records.append({"voiceId": voice_id, "take": name, "cfgWeight": cfg_weight,
                            "exaggeration": exaggeration, "path": str(path)})
            print(json.dumps(records[-1]), flush=True)
    (out / "probe-summary.json").write_text(json.dumps(records, indent=2), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
