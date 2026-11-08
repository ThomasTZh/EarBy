#!/usr/bin/env python3
"""Compare generation settings for a slower, flatter answer delivery.

This is a development probe, not a production step: it writes several variants of one phrase
so the effect of cfg_weight/exaggeration/tempo can be measured before regenerating the pack.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

VARIANTS = (
    # name, cfg_weight, exaggeration
    ("baseline", 0.5, 0.5),
    ("slower", 0.3, 0.5),
    ("slower_flatter", 0.3, 0.2),
    ("slowest_flattest", 0.2, 0.1),
)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--text", default="Major seventh")
    parser.add_argument("--out", default="raw/probe")
    args = parser.parse_args()

    import torchaudio

    from chatterbox.tts import ChatterboxTTS

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    model = ChatterboxTTS.from_pretrained(device="cpu")
    summary = []
    for name, cfg_weight, exaggeration in VARIANTS:
        wav = model.generate(args.text, cfg_weight=cfg_weight, exaggeration=exaggeration)
        path = out / f"{name}.wav"
        torchaudio.save(str(path), wav, model.sr)
        summary.append(
            {
                "variant": name,
                "cfgWeight": cfg_weight,
                "exaggeration": exaggeration,
                "audioSeconds": round(wav.shape[-1] / model.sr, 3),
                "path": str(path),
            }
        )
        print(json.dumps(summary[-1]), flush=True)
    (out / "probe-summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
