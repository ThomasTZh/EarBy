#!/usr/bin/env python3
"""Generate the v0.2.x voice clips with Piper (en_GB-alba-medium).

Owner decision 2026-10-12: the new vocabulary uses Piper's en_GB-alba-medium voice, a calm
British English female voice, instead of the Chatterbox default. The 13 accepted interval clips
are frozen and never regenerated. Postprocessing (trim, pad, 48 kHz mono PCM16, two-pass
loudness) is shared with the accepted pack's generator so every clip follows one convention.

License/provenance: Piper code MIT; voice dataset CC BY 4.0
(https://datashare.ed.ac.uk/handle/10283/3270), model from rhasspy/piper-voices.

This script writes candidate assets; it is not a routine check.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
import wave
from pathlib import Path

from generate_voice_pack import postprocess

VOCABULARY_PATH = Path(__file__).resolve().parent / "vocabulary.json"
MODEL_FILES = {
    "alba": "en_GB-alba-medium.onnx",
    "jenny": "en_GB-jenny_dioco-medium.onnx",
    "cori": "en_GB-cori-high.onnx",
    "amy": "en_US-amy-medium.onnx",
}
MODELS_DIR = Path(__file__).resolve().parents[2] / ".local" / "tts-audition" / "models"
DEFAULT_MODEL = MODELS_DIR / MODEL_FILES["alba"]
VOICE_ID = "piper-alba"
VOICE_PROVENANCE = {
    "alba": {
        "voice": "en_GB-alba-medium",
        "modelRevision": "rhasspy/piper-voices en_GB-alba-medium",
        "datasetLicense": "CC BY 4.0 (https://datashare.ed.ac.uk/handle/10283/3270)",
    },
    "jenny": {
        "voice": "en_GB-jenny_dioco-medium",
        "modelRevision": "rhasspy/piper-voices en_GB-jenny_dioco-medium",
        "datasetLicense": "see https://github.com/dioco-group/jenny-tts-dataset",
    },
    "cori": {
        "voice": "en_GB-cori-high",
        "modelRevision": "rhasspy/piper-voices en_GB-cori-high",
        "datasetLicense": "public domain (LibriVox recordings, https://librivox.org)",
    },
    "amy": {
        "voice": "en_US-amy-medium",
        "modelRevision": "rhasspy/piper-voices en_US-amy-medium",
        "datasetLicense": "CC BY 4.0 (HiFi-TTS, OpenSLR SLR109 via MycroftAI/mimic3-voices)",
    },
}
VOICE_NAME = VOICE_PROVENANCE["alba"]["voice"]
MODEL_REVISION = VOICE_PROVENANCE["alba"]["modelRevision"]
DATASET_LICENSE = VOICE_PROVENANCE["alba"]["datasetLicense"]


def load_phrases(
    only: list[str] | None, include_bundled: bool = False
) -> list[tuple[str, str, float, str]]:
    """Selected entries as (id, synthesis text, length scale, voice).

    `ttsText` is a pronunciation respelling for words whose spelling produces the wrong
    phonemes (for example "dough" for the solfège "do" or "eigh" for the letter A);
    `lengthScale` stretches short clips that stop too bluntly; `ttsVoice` selects the model
    ("alba" default, "jenny" where the owner preferred it). The catalog's `spokenText` stays
    the documented answer.

    Bundled clips are frozen by default; `include_bundled` is the explicit owner-approved
    re-record path (the interval refresh) and must name the clips via `--only`.
    """
    data = json.loads(VOCABULARY_PATH.read_text(encoding="utf-8"))
    entries = [
        (
            entry["id"],
            entry.get("ttsText", entry["spokenText"]),
            float(entry.get("lengthScale", 1.0)),
            entry.get("ttsVoice", "alba"),
        )
        for entry in data.get("entries", [])
        if include_bundled or not entry.get("bundled")
    ]
    if only:
        wanted = set(only)
        entries = [entry for entry in entries if entry[0] in wanted]
        missing = wanted - {entry[0] for entry in entries}
        if missing:
            raise SystemExit(f"unknown or excluded voice ids: {sorted(missing)}")
    if not entries:
        raise SystemExit("no phrases selected")
    return entries


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path, default=DEFAULT_MODEL)
    parser.add_argument("--out", default="candidates")
    parser.add_argument("--raw", default="raw/piper-alba")
    parser.add_argument("--only", nargs="*", default=None)
    parser.add_argument(
        "--include-bundled",
        action="store_true",
        help="allow --only to name bundled (frozen) clips, for an approved re-record",
    )
    args = parser.parse_args(argv)

    if not args.model.is_file():
        raise SystemExit(f"missing Piper model: {args.model}")

    from piper import PiperVoice

    out_dir = Path(args.out)
    raw_dir = Path(args.raw)
    out_dir.mkdir(parents=True, exist_ok=True)
    raw_dir.mkdir(parents=True, exist_ok=True)

    from piper.config import SynthesisConfig

    loaded = {}
    records = []
    for voice_id, text, length_scale, voice_name in load_phrases(
        args.only, include_bundled=args.include_bundled
    ):
        if voice_name not in loaded:
            model = args.model if voice_name == "alba" else MODELS_DIR / MODEL_FILES[voice_name]
            loaded[voice_name] = PiperVoice.load(str(model))
        voice = loaded[voice_name]
        raw_path = raw_dir / f"{voice_id}.wav"
        with wave.open(str(raw_path), "wb") as handle:
            voice.synthesize_wav(
                text,
                handle,
                syn_config=SynthesisConfig(length_scale=length_scale),
            )
        target = out_dir / f"{voice_id}.wav"
        detail = postprocess(raw_path, target)
        provenance = VOICE_PROVENANCE[voice_name]
        record = {
            "voiceId": voice_id,
            "text": text,
            "engine": "piper",
            "voice": provenance["voice"],
            "modelRevision": provenance["modelRevision"],
            "datasetLicense": provenance["datasetLicense"],
            "sha256": hashlib.sha256(target.read_bytes()).hexdigest(),
            **detail,
        }
        records.append(record)
        print(json.dumps(record), flush=True)
        if detail["seconds"] > 8.0:
            print(
                f"FAIL: {voice_id} is {detail['seconds']:.3f} s, above the 8 s limit",
                file=sys.stderr,
            )
            return 1

    summary = {
        "generator": "piper",
        "engineVersion": _piper_version(),
        "voice": VOICE_NAME,
        "modelRevision": MODEL_REVISION,
        "datasetLicense": DATASET_LICENSE,
        "phrases": records,
    }
    (out_dir / "generation-summary-piper-alba.json").write_text(
        json.dumps(summary, indent=2), encoding="utf-8"
    )
    print(json.dumps({"generated": len(records)}, indent=2))
    return 0


def _piper_version() -> str:
    try:
        from piper import __version__ as version

        return version
    except Exception:
        return "unknown"


if __name__ == "__main__":
    raise SystemExit(main())
