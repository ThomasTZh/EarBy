# Voice asset tooling notice

The bundled English answer pack is generated at development time; the app ships WAV files and
never uses the network or runtime TTS.

## Chatterbox (historical, no longer shipped)

The thirteen interval clips were originally generated with
[Chatterbox](https://github.com/resemble-ai/chatterbox) `chatterbox-tts 0.1.7` (MIT) on CPU
using its default English voice. They were replaced by Piper takes on 2026-10-14 (decision
D25); the old files, manifest entries, review record and hashes stay archived under
`candidates-superseded-chatterbox-intervals-2026-10-14/`. Nothing generated with Chatterbox
ships any more.

## Piper (every bundled clip since 2026-10-14)

The bundled clips are generated with [Piper](https://github.com/rhasspy/piper) (MIT) using these
voices from [rhasspy/piper-voices](https://huggingface.co/rhasspy/piper-voices):

| Voice | Used for | Dataset and license |
| --- | --- | --- |
| `en_GB-alba-medium` | Most clips, including every interval phrase (refreshed 2026-10-14) | CC BY 4.0 (University of Edinburgh DataShare, https://datashare.ed.ac.uk/handle/10283/3270) |
| `en_GB-jenny_dioco-medium` | The "do" syllable (incl. sharp), "four" and "two" words | Custom Jenny dataset terms: commercial use permitted; attribution to **Jenny (Dioco)** is required. See https://github.com/dioco-group/jenny-tts-dataset |
| `en_US-amy-medium` | The "fa" and "la" syllables (incl. sharp forms) | HiFi-TTS, CC BY 4.0 (OpenSLR SLR109 via MycroftAI/mimic3-voices) |
| `en_GB-cori-high` | The isolated letter A | LibriVox recordings, public domain |

Any packaged use must carry these attributions in the asset notices. The owner selected the
per-clip voices during the 2026-10-12 listening pass (decision D24) and approved the interval
refresh on 2026-10-14 (decision D25; recorded in `reviews-intervals.json`); once packaged, the
approved takes stay byte-identical.

## FFmpeg postprocessing

All clips are trimmed, padded and normalised by the shared pipeline: mono 48 kHz PCM16,
two-pass loudness to -20 LUFS / -3 dBTP with the documented bounded-gain fallback for very
short clips.
