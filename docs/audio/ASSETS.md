# Audio assets: voice pack and instrument pack

This document records what the app bundles, how each asset was produced and what evidence is
still missing. Machine validation lives in `scripts/check-assets.sh`; it can pass while a
human listening review is still pending, and a pending review always keeps G2 open.
Reviewed status 2026-10-09: the existing voice entries and the then-current SoundFont descriptor
recorded human acceptance, and STATUS recorded the later phone listening pass closing G2. The
0.9.23 custom synth was accepted by the owner on 2026-10-24; the current instrument section records
that acceptance. This documentation review did not regenerate assets or repeat listening.

## Voice pack

- Semantic ID schemes: `interval.<label-id>`, `chord.<quality|inversion-label>`,
  `scale.<canonical-name>` and the connector `connector.or`, for example
  `interval.perfect_fifth`, `chord.major_seventh`, `chord.inversion.first`,
  `scale.natural_minor`.
- One reviewed English pack, mono 48 kHz PCM16 WAV, positive length and at most 8 s per phrase.
  The pack is catalog-driven: every entry marked `bundled` in
  `tools/voice-assets/vocabulary.json` ships, and only those.
- Two semantic ids may share one physical WAV. The pack shares the octave words one, three, four,
  five, six and seven with their degree takes, the octave two with the Cori degree two take, the
  chord major/minor seventh with the interval takes, the connector unison with the interval
  unison, and the scale "major" with the chord-quality take. Each entry's format, hash and frame
  count are still checked.
- Bundled today: the thirteen interval phrases (Unison, Minor second, ... Octave), the
  eleven chord qualities, the four inversion labels and the “or” connector, the thirteen
  canonical scale names for the Scales mode, and the note-identification vocabulary — note
  naturals, sharp and flat chromatic names, spoken octaves, the twelve fixed-do syllables and
  the scale-degree numbers with both movable-do syllable sets, and the direction connectors
  (102 entries), plus the three harmonic-function clips (tonic, predominant, dominant) that
  were accepted on 2026-10-14 and bundled, and the note-letter refresh. The fixed-do and
  movable-do syllable clips were removed with solfege on 2026-10-16 (D32), so the pack holds
  83 entries.
- Manifest: `app/src/main/assets/voice/voice-pack.json`, schema v1, `packId` `english-answers`
  version 2.0.0, validated by `tools/audio-analysis/validate_assets.py`.
- Generation tooling: `tools/voice-assets` (Python 3.11, locked with `uv`).

### Generation route

Every bundled clip is generated with Piper on CPU (decision D23) using the per-clip voices the
owner selected while listening (decision D24); the thirteen interval phrases were re-recorded
from the Stage-1 Chatterbox takes to `en_GB-alba-medium` on 2026-10-14 (decision D25), with the
old takes, manifest entries and hashes archived under
`tools/voice-assets/candidates-superseded-chatterbox-intervals-2026-10-14/`. The voices are: `en_GB-alba-medium` for most clips,
`en_GB-jenny_dioco-medium` for the “do” syllable and the “four” word, `en_US-amy-medium` for
the “fa”/“la” syllables, and `en_GB-cori-high` for the isolated letter A and the “two” answer word
(cut from the carrier “the number is . . . two”). The degree “two” and the perfect-pitch octave
“two” share one physical WAV; both manifest ids point to `wav/degree_two.wav`. Raw takes and
candidates stay in ignored local storage (`tools/voice-assets/raw`,
`tools/voice-assets/candidates`); only the accepted, postprocessed WAV files and their
provenance metadata are repository inputs. Dataset attribution is recorded in
`tools/voice-assets/NOTICE.md`.

### Note-letter refresh (2026-10-14, owner review)

The seven single-letter clip for Perfect pitch (A..G) were reworked after the owner called the
original takes usable but not fully satisfying. From one alba take of `"A. B. C. D. E. F. G."`
(periods hold the pauses; commas were too short to split) the tool
`tools/voice-assets/split_letter_list.py` cuts the six detected pauses and postprocesses each
piece exactly like the pack. The owner's listening verdict selected **B, D, F and G from that
split**, **A and C kept the original clips** and **E replaced by a cori take**
(`tools/voice-assets/probe_letter_e.py`, candidate `cori-E`, the same voice already used for
A). The catalog therefore keeps `note.a`/`note.e` on cori, drops the old "ee"/slowdown
overrides for E, and the review record `reviews-note-letters.json` carries the acceptance;
the split and probe summaries preserve the take provenance.

### Postprocessing contract


1. Convert to mono 48,000 Hz PCM16 little-endian WAV with FFmpeg.
2. Trim excessive outer silence while preserving consonants; keep 50-150 ms leading and
   trailing pads.
3. Apply measured two-pass FFmpeg `loudnorm`, target -20 LUFS integrated and -3 dBTP ceiling.
4. Very short clips may not produce a stable integrated measurement; those use a documented
   bounded-gain fallback and require listening review instead of an undefined gain.

### Review status

The pack is produced and bundled: 83/83 entries record `review.humanAccepted=true`; the `degree.two` clip was replaced by the owner's chosen Cori carrier take on 2026-10-17, and `note.octave.2` shares that file; the three harmonic-function clips were accepted in a dedicated listening round (`tools/voice-assets/reviews-m9a.json`), and the note-letter refresh in another (`tools/voice-assets/reviews-note-letters.json`).
The v0.2.x vocabulary clips (including the thirteen scale names) were accepted by the owner
across the 2026-10-12 listening rounds
(`tools/voice-assets/reviews-vocabulary.json`); the refreshed interval phrases were accepted on
2026-10-14 (`tools/voice-assets/reviews-intervals.json`), replacing the Stage-1 review record
that stays preserved in `reviews.json` and in the superseded-interval archive. The manifest
holds per-clip generation/postprocessing/hash/review metadata, and STATUS preserves phone
listening evidence. A changed/new clip needs its own review; numeric validation alone cannot
inherit that acceptance.

## Instrument pack

### Current instrument: custom additive string synth, V4 dark calibration (selected 2026-11-08)

The bundled instrument is a **custom one-preset synth**, bank 0 / program 0, no selector:

- **MIDI 21-108 (A0-C8):** 88 one-shot samples from the owner's additive synth generator, split
  into a bass timbre for MIDI 21-59 and a guitar timbre for MIDI 60-108.
- **V4 dark calibration:** slopeP 1.55, lowpass 1500 Hz for guitar / 900 Hz for bass at Q 0.5,
  body peaks scaled by 0.8. The owner selected this calibration on 2026-11-08 for a stronger
  fundamental; the final listening gate is the owner's.
- Samples are mono 48 kHz PCM16, 2.5 s, regenerated by the original MATLAB scripts at **0.120 RMS**
  with measured peaks 0.4306-0.7315 and no clipping. All zones are no-loop one-shots; the writer
  keeps zero-valued loop headers and the sample player ignores loop metadata entirely.
- The app's longest note setting is 2.0 s, so the samples do not truncate.
- Generator input (not packaged): `synthNotes/StringSynth_A0-C8.sf2`, 21,126,646 bytes, SHA-256
  `f3326f4c53c52a521bce7e9e2932edb9e34807f85a96b391eb224a5a0b499705`.
- Shipped asset: `app/src/main/assets/soundfont/earby-string-pack.bin`, 21,126,760 bytes, SHA-256
  `f2e14a32c252c5d94ec5f1b7f8cbf63da6ff7a2c08afb49470af591cc6c05462` (payload
  `295476119e0db682e793d1f36ca277d264c6f6fc9623682d9ed07fd96759d908`). It is the 88 one-shots
  in the EARP1 format read by the in-project sample player; the SoundFont is only the tracked
  generator input.
- Descriptor: `app/src/main/assets/soundfont/piano.json`; `review.humanAccepted=true` records the
  owner's 2026-10-24 acceptance of the 0.120 RMS level and the 0.9.28 +1.06 dB trim, and
  `review.reviewedAt=2026-11-08` records that the owner accepted the V4 dark calibration and the
  sample-player playback on 2026-11-08. The descriptor pins the shipped pack and the tracked SF2
  source; `scripts/check-assets.sh --require-reviewed` checks both.
- Generation: MATLAB sources and build tools are tracked under `synthNotes/`, and the font is
  written by `synthNotes/tools/build_sf2.py`; the pack is written by the deterministic
  `synthNotes/tools/build_sample_pack.py` from `synthNotes/StringSynth_A0-C8.sf2`. The generated
  `out/` tree (WAVs, reports and a font copy) is ignored. `scripts/fetch-soundfont.sh` verifies the
  source font and the pack.
- Verification: the V4 bank regeneration passed (88 notes, 14/14 Python and 103/103 MATLAB tests,
  `validate_sf2.py`, byte-identical SF2/pack/WAV samples, `build_sample_pack.py --check`). The
  sample-player A/B against the reference renderer passes the t13 thresholds. Full
  `scripts/qa.sh` last passed on 2026-10-25 for the 0.9.29 rename (1085/0 host tests, 60/60
  review-device tests); the V4 assets and the sample-player integration are covered by the t20
  checks. Evidence: `.local/verification/qa-2026-10-25-1243/QA-REPORT.md`,
  `.local/verification/tone-v4/` and
  `.local/verification/sample-player/v4/analysis/AB-REPORT.md`.
- Separator asset: `app/src/main/assets/separator/original-separator.wav`, 30,798 bytes, SHA-256
  `ed935306de57c8466d7d4a096817624d945ac0858771251adbc2d741c8ac96a6`, RIFF/WAVE PCM16, 48 kHz
  mono, 15,360 frames (0.320 s), measured peak -18.46 dBFS, RMS -28.18 dBFS and mean -2.6e-5 of
  full scale. It is unchanged from the accepted original level. Its provenance is recorded in
  `docs/legal/ASSET_SOURCES.md`: a C6/G6 render of the MIT-licensed MuseScore General v0.2 font
  (C6 MIDI 84 at 0.000-0.120 s, G6 MIDI 91 at 0.200-0.320 s, 5 ms fades, 80 ms gap), reproduced
  bit-exactly from the hash-verified upstream font on 2026-11-08. `scripts/check-assets.sh`
  validates the clip's format, frame count, peak, DC offset, hash and provenance record.
Separator listening: accepted by the owner on 2026-11-08
- History: the 0.9.23 custom synth timbre was accepted; the 0.9.25/0.9.28 0.120 level and uniform
  trim were accepted on 2026-10-24. The 2026-11-08 V4 dark calibration supersedes those presets
  while keeping the 0.120 RMS level and the accepted playback trim. The 0.9.21 D6 hybrid was the
  previous accepted instrument; the 0.9.22 D3 hybrid remained pending and is superseded.

### Historical instrument: Osiris low / MuseScore mid and high (0.9.22, superseded)

The 0.9.22 candidate was a **hybrid one-preset instrument**, bank 0 / program 0, no selector:

- **MIDI 21-49 (B0-C#3):** Osiris Piano Mic A natural samples, CC0 1.0, source commit
  `18c6afccb60cff458edbf7c394571783e074e1e9`. The accepted low-register leveling and release
  settings are retained.
- **MIDI 50-108 (D3-C8):** original MuseScore General v0.2, MIT. These are one-shot 48 kHz mono
  renders of the original samples, normalized to 0.060 RMS with a 0.85 peak ceiling.
- **Separator:** the fixed C6/G6 two-note cue is a separate 48 kHz clip rendered from MuseScore
  General, with the original 5 ms per-note fades. It is mixed at music gain and is not sent
  through the Osiris layer. The same clip still ships; its provenance and reproduction record are
  in `docs/legal/ASSET_SOURCES.md`.
- **D3 boundary:** D3 (MIDI 50) is the earlier note where the MuseScore low register starts to
  sound bad. Osiris therefore covers only B0 to C#3; all mid and high notes use MuseScore.
- Bundled font: `app/src/main/assets/soundfont/MuseScore_General.sf2`, formerly 39,688,036 bytes,
  SHA-256 `4f2c932bcb89218e206491e9fe5c10ac2bd52de9c4804096d901a29adec00a6d`.
- Descriptor: `app/src/main/assets/soundfont/piano.json`; the D3 split remained
  `review.humanAccepted=false` and is superseded.
- Generation: Osiris low samples and original MuseScore General renders combined with Polyphone
  2.6.0; the generated font was tracked because it is not a single upstream download.
- History: the 0.9.21 D6 hybrid was accepted on 2026-10-20, but a few mid/high notes still had a
  key-clash sound. The 0.9.22 D3 split superseded it; owner listening was still pending.

The technical boundary check for 0.9.22 passed: B0 and C#3 match the previous Osiris renditions,
while D3, G4, C6 and C7 match the original MuseScore General. That candidate was not listened to
and is superseded by 0.9.23. Current status, APK and next steps:
[HANDOFF.md](../development/HANDOFF.md).

The historical full-font notes below describe the pre-0.9.21 asset and are retained for the
record.

### Historical full-font state (before 0.9.21)

- Bank 0, program 0, one approved bundled SoundFont. The original 32 MiB target was waived by
  the project owner on 2026-10-07, so the full upstream font is bundled (205.6 MiB) instead of a
  piano subset; the subsetter is kept for a later size reduction.
- Descriptor: `app/src/main/assets/soundfont/piano.json`, schema v1, validated by
  `tools/audio-analysis/validate_soundfont.py`.
- Source material is the MIT-licensed MuseScore General SF2 as documented in
  `docs/development/OPEN_SOURCE.md`. Development downloads/candidates stay in ignored local
  storage; the accepted full font and its descriptor are bundled under the owner waiver.

### Preparation steps to perform when producing the subset

1. Download the MuseScore General SF2 from its documented source and record the archive URL
   and SHA-256.
2. Extract bank 0 / program 0 (piano) with all linked samples and instruments into a new SF2,
   removing unused content, using an existing SF2 editor such as Polyphone.
3. Record the exact editing/export steps, the resulting file hash and the included license text.
4. Leave `review.humanAccepted=false` until the instrument has been listened to on the phone.

### Current state

`app/src/main/assets/soundfont/MuseScore_General.sf2` is the upstream file, unchanged:
215,614,036 bytes, SHA-256
`ee51d2c4b1525e70f19a45909c4fd7a2e26d91d115fa89dbf5a6bc413d8b9bf3`, downloaded from
`https://ftp.osuosl.org/pub/musescore/soundfont/MuseScore_General/MuseScore_General.sf2`.
Its embedded metadata states "Released under the MIT license", and the descriptor records that.

Reading the file showed that bank 0 / program 0 ("Grand Piano") is built from six
velocity-layer instruments (`Piano MF-low/high`, `Piano FF-low/high`, ...) over 144 samples
(95.7 MiB of sample data). `tools/audio-analysis/sf2_tool.py` parses these sections and can
write a subset, but its rewritten header/zone/instrument sections were not accepted by the
renderer of the time, and the 32 MiB target needs a sample-trimming step. Both are future work now
that the size limit is waived.

### Review status

The descriptor records human acceptance and the reviewed music gain 1.0; STATUS records
the phone sequence/balance review. Production defaults pair that with voice gain 0.25.
Machine checks cover the hash, RIFF/SF2 structure, bank/program and production-renderer
numeric properties. A future reduced font is a new asset requiring those checks and listening;
the existing rejected subset output is not an accepted replacement.

## What machine checks do and do not prove

`scripts/check-assets.sh` verifies schema, hash, format, frame counts, frame-derived durations,
vocabulary completeness, path safety, RIFF/SF2 structure and the size budget. It cannot and
does not prove that a phrase is intelligible or that the piano sounds correct. Those are human
listening results and must be recorded as such.
