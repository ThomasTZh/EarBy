#!/usr/bin/env python3
"""Measure a three-phrase CPU batch before committing to the full voice pack run."""

from __future__ import annotations

import argparse
import json
import resource
import time
from pathlib import Path

PHRASES = [
    ("interval.minor_third", "Minor third"),
    ("interval.perfect_fifth", "Perfect fifth"),
    ("interval.octave", "Octave"),
]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="raw/smoke")
    parser.add_argument("--device", default="cpu")
    args = parser.parse_args()

    import torch
    import torchaudio

    from chatterbox.tts import ChatterboxTTS

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    started = time.monotonic()
    model = ChatterboxTTS.from_pretrained(device=args.device)
    load_seconds = time.monotonic() - started

    records = []
    for voice_id, text in PHRASES:
        item_started = time.monotonic()
        wav = model.generate(text)
        seconds = time.monotonic() - item_started
        path = out / f"{voice_id}.wav"
        torchaudio.save(str(path), wav, model.sr)
        duration = wav.shape[-1] / model.sr
        peak_mb = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024
        records.append(
            {
                "voiceId": voice_id,
                "text": text,
                "sampleRate": model.sr,
                "seconds": round(seconds, 2),
                "audioSeconds": round(duration, 3),
                "peakRssMiB": round(peak_mb, 1),
                "path": str(path),
            }
        )
        print(json.dumps(records[-1]), flush=True)

    summary = {
        "device": args.device,
        "modelLoadSeconds": round(load_seconds, 2),
        "phrases": records,
        "totalSeconds": round(sum(r["seconds"] for r in records), 2),
        "peakRssMiB": round(resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024, 1),
        "torchThreads": torch.get_num_threads(),
    }
    (out / "smoke-summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps({k: v for k, v in summary.items() if k != "phrases"}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
