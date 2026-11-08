# EarBy instrument audition plan — low-register timbre

Status: the 0.9.21 D6 hybrid was accepted, then superseded on 2026-10-23 after the owner found
a key-clash sound in a few mid/high notes. The 0.9.22 Osiris/MuseScore D3 candidate remained
pending, and on 2026-10-24 the owner selected a custom additive string synth as 0.9.23 instead.
This audition plan is retained as history for the piano/low-register search; the current
instrument is not a piano. The bass+guitar source family remains blocked by the B0 licence gate,
but it is no longer on the critical path because the owner generated the replacement samples.
Date: 2026-10-17.
Owner: project owner. Implementer: coding agent after the owner switches out of Plan mode.

## Resume state — 2026-10-24

- Main build **1.0.0 / 100** (the 0.9.23 timbre at the revised 0.120 level), instrument commit
  `225aff6`; a debug-key test-signed `com.earby.app` build is installed. The owner upload-key APK
  is not built yet.
- The accepted 0.10.4 standalone APK remains at `dist/EarBy-0.10.4.apk` (71,408,841 bytes,
  SHA-256 `4faaf12afa924d75effa6e86b8a5df700e9a4070170ecb40614bff9755508432`).
- Instrument: one bank 0/program 0 preset `String Synth A0-C8`, 88 one-shot mono 48 kHz PCM16
  samples for MIDI 21-108; bass timbre MIDI 21-59, guitar timbre MIDI 60-108. The 0.9.25 samples
  are regenerated at 0.120 RMS, twice the original 0.060 level. The 0.9.27 volume sliders span
  0-100% with the reviewed normal at 50%; 0.9.28 adds a uniform 1.13x final-mix trim without
  compression. The 0.9.29 font asset is renamed `StringSynth_A0-C8.sf2`; its bytes are unchanged.
- Automated checks: MATLAB synthesis and `build_final(59)` PASS, synth tool tests 6/6, native render
  fixtures 9/9 PASS, output-trim unit test PASS, and the packaged APK audit PASS.
- Full `scripts/qa.sh` for 0.9.29 passed on 2026-10-25: 1085/0 host tests, 60/60 review-device
  tests, 5 data-platform and 9 audio-platform tests, 0 crash/ANR, strict screenshots and APK audit.
  Evidence: `.local/verification/qa-2026-10-25-1243/QA-REPORT.md`. QA for the 0.9.30 preset-seed
  change, the 0.9.31 root-range change, the 0.9.32 slider response, the 0.9.33 Help page
refresh, the 0.10.0-0.10.4 interval walk/QA automation and the 1.0.0 release prep are owner-deferred for audition.
- The 0.9.23 timbre and the 0.9.25/0.9.28 0.120 level were accepted on 2026-10-24; the 0.9.29
  rename preserves the same content, so the descriptor stays `review.humanAccepted=true`.
- The 30-minute endurance stage remains excluded by the default owner decision. See
  [HANDOFF.md](HANDOFF.md) for commands and constraints.

## Previous resume state — 2026-10-23

- Main build 0.9.22 / 31, instrument commit `cde34f0`; testing phone installed.
- Daily APK: `dist/EarBy-0.9.22.apk`; backup: `an external backup`.
- Technical D3 boundary check: PASS (B0/C#3 Osiris; D3/G4/C6/C7 MuseScore).
- Owner listening on the daily phone is the next action. The descriptor remains
  `review.humanAccepted=false` until then.
- Full QA has not run since 0.9.6. See [HANDOFF.md](HANDOFF.md) for commands and constraints.

## 0. Implementation status — 2026-10-17

- Licence screen completed. Eligible methods: **Piano A — Salamander Grand Piano v3** (CC BY 3.0;
  B0 from the C1 root at −1 semitone), **Piano B — Osiris Piano** (CC0 1.0; exact B0 sample), and
  the two synthetic wildcards (FM electric piano and Karplus-Strong plucked string; project code).
- **Bass+guitar is blocked.** No redistributable 5-string bass with a genuine B0 sample was found.
  Karoryfer Black And Blue Basses is 5-string but its sample tree runs E1..E5 and has no B0.
  Fashionbass reaches A0/F#0 but has no B0. Pianobook/Freesound B0 sources are login-gated and
  not complete instruments; commercial 5-string libraries forbid redistribution. No bass+guitar
  render was made.
- Delivered: 100 mono WAVs (17 notes × 5 methods at 700 ms, plus B0/C2/C3 at 2 s), `listen.html`,
  `LISTEN-ME.txt`, `sources.json`, `NOTICE.md`, `VERIFICATION.md`, and the fetch/render/verify
  scripts. All files are 48 kHz mono PCM16, RMS-normalised, and verified.
- No app asset, `versionCode`, screenshot baseline or phone install was changed.
- Next: the owner listens on headphones first, then the testing-phone speaker, and returns a
  two-method shortlist. Chords and in-app test builds follow that shortlist. If the owner provides
  a licence-cleared genuine-B0 bass, the G2/A2/C3/D3/G3 split round can be added.

## 1. Goal

Replace the single instrument used for note playback so that **monophonic notes below about
D3 (MIDI 50), down to B0 (MIDI 23), sound smoother and fuller**, without changing the current
note timing, velocity, sustain or decay.

The owner reports that the current low notes sound compressed, processed, not smooth and not
full. The same impression occurs on headphones and on the testing-phone speaker. Chords are a
secondary check: low chords first, then one or two mid/high chords.

Two instrument families are acceptable:

1. **Piano only** for the whole range.
2. **Guitar + bass** for the whole range, with the crossover tested in the overlap between the
   bass high G string and the guitar low strings.

A **synthetic wildcard** is included: an FM electric piano and a Karplus-Strong plucked-string
model, to see whether a synthetic timbre is clearer in the low register.

No instrument selector will ship. One instrument serves every mode.

## 2. Fixed requirements from the owner

- Lowest required note: **B0 (MIDI 23)**.
- Low-note problem range: **below approximately D3**; the crossover tests use the G2..G3
  overlap.
- Listen on **headphones first**, then the **testing-phone speaker**. Both matter equally.
- Method files must be **labelled by method** and **approximate asset size**, for example
  "Piano A, ~480 MB" or "Bass+guitar split at D3, ~260 MB". Blind labels are not wanted.
- Size is not a constraint for now, but must be reported.
- Keep the current **sustain and decay**; the renderer and timing stay as they are.
- Mono low notes must pass before chords are evaluated.
- Chords: mainly low chords, plus one or two mid/high chords.
- No runtime network, TTS, streaming or downloaded instrument at runtime.

## 3. Current system

- Runtime synthesis: **FluidSynth 2.6.1** (native), one bundled SF2, bank 0, program 0,
  "Grand Piano".
- Bundled font: full **MuseScore General v0.2**, MIT, 215,614,036 bytes, SHA-256
  `ee51d2c4b1525e70f19a45909c4fd7a2e26d91d115fa89dbf5a6bc413d8b9bf3`, bundled unchanged under
  the owner's 2026-10-07 size waiver.
- Descriptor: `app/src/main/assets/soundfont/piano.json`.
- Fetch/verify: `scripts/fetch-soundfont.sh`.
- Validation: `tools/audio-analysis/validate_soundfont.py`, `scripts/check-assets.sh`.
- Renderer: `core/audio` PCM renderer, note velocity **96**, 48 kHz mono PCM16, note durations
  from the recipe (default 700 ms, up to 2 s).
- Host render harness: `scripts/check-native-tests.sh` renders standard WAV fixtures with the
  production renderer sources and the bundled font. Candidate SF2s can be rendered through the
  same path with a dev override, so audition WAVs match the app's note events.
- Existing Sustain/decay behaviour is accepted by the owner; do not change the renderer
  envelope in the audition.

## 4. Candidate methods

| Label | Method | Low end B0 | Integration | Size label |
| --- | --- | --- | --- | --- |
| Reference | Current MuseScore General Grand Piano | yes | none | 205.6 MB |
| Piano A | High-quality sampled piano, one velocity layer, converted to SF2 | yes for a full piano | replace/point the SF2 | measure (large) |
| Piano B | A second high-quality piano font, for example VSCO 2 CE piano or FluidR3Mono, depending on license | verify | replace the SF2 | measure |
| Guitar only | Acoustic/electric guitar, full range by pitch-shifting below E2 | only by pitch shift; state the limitation | SF2 | measure |
| Bass + guitar G2 | 5-string bass below G2, guitar above G2 | yes, bass | SF2 with two zones | measure |
| Bass + guitar A2 | split at A2 | yes | SF2 | measure |
| Bass + guitar C3 | split at C3 | yes | SF2 | measure |
| Bass + guitar D3 | split at D3 | yes | SF2 | measure |
| Bass + guitar G3 | split at G3 | yes | SF2 | measure |
| Wildcard FM | FM electric piano rendered offline | yes | SF2 or tiny sample bank | tiny |
| Wildcard PM | Karplus-Strong plucked string rendered offline | yes | tiny sample bank or generated WAVs | tiny |

Notes:

- The 5-string bass must have a genuine B0 sample. A 4-string bass pitch-shifted to B0 is a
  fallback only and must be labelled as such; it is not expected to win.
- The guitar-only candidate is a reference for the guitar timbre, not a B0 solution.
- The split variants use hard key-range splits at first. If the best hard split is audible, the
  second round may use a short crossfade in a custom sample player. A custom player is not part
  of the first audition.
- A piano-only candidate is the main alternative to the crossed plucked-string instrument; the
  owner wants to compare which is smoother in the low register.

## 5. License and provenance gate

Before rendering any candidate, verify for each library:

- The license allows **redistribution of rendered audio inside a closed app**, not only music
  use or personal use.
- Attribution requirements are recorded in `tools/voice-assets/NOTICE.md` or a new
  `tools/instruments/NOTICE.md`.
- No NC, ND, "no redistribution of samples", Kontakt/Spitfire-style restrictions that forbid
  shipping rendered samples.
- The exact upstream URL, version, SHA-256 and license text are recorded.

Candidate sources to screen (licenses to verify, not assumed):

- **Salamander Grand Piano** (commonly CC BY 3.0, SFZ, multiple velocity layers).
- **VSCO 2 Community Edition** (commonly CC0; piano, guitar and bass may be present).
- **FluidR3Mono** (MIT; smaller, older; check low-register quality).
- **Karoryfer** libraries (commonly CC0; verify a 5-string bass and guitar).
- Any other CC0/CC BY 5-string bass with a genuine B0 sample.

Do not download or commit assets until the license gate passes. Raw downloads and candidate
renders stay under `.local/`, not in the app assets.

## 6. Audition corpus and render protocol

All files are rendered at 48 kHz mono PCM16 with the app's velocity 96 and 700 ms note length,
then a separate 2 s pass for sustain.

Mono notes:

- B0, C1, E1, G1, A1, B1, C2, E2, G2, A2, B2, C3, D3, E3, G3, C4, C5.
- 2 s versions: B0, C2, C3.
- If an instrument cannot cover a note, the file is marked "pitch-shifted" or "missing".

Chords (second pass, after mono shortlist):

- Low: B0 major (B0-D#1-F#1), C2 major, C3 major.
- Crossover: G2-B2-D3 and, for the split variants, the chord spans the split.
- Mid/high: C4 major and G4 major.

Loudness and format:

- Normalise each candidate to the same integrated loudness for judgement, record the peak and
  the duration, and preserve the app's decay.
- Name files with method, split point and size, for example
  `bass-guitar-split-D3__B0__260MB.wav`.

Delivery:

- `.local/verification/instrument-audition-2026-10-17/renders/` — WAV files.
- `.local/verification/instrument-audition-2026-10-17/listen.html` — one labelled player.
- `LISTEN-ME.txt` — what to compare and in which order.
- `sources.json` — library, URL, version, license, hash and size per candidate.

## 7. Human gates

1. License screen result is reported. If no B0-capable 5-string bass is redistributable, the
   owner is told before the bass+guitar family is sampled.
2. **Mono round:** the owner listens to headphones then the phone speaker, and shortlists the
   smoothest low-register methods. Chords are not judged in this round.
3. **Chord round:** the shortlist is rendered on the chord set; the owner picks the family and
   the split point, if relevant.
4. **In-app round:** the top two become SF2 swaps in test builds. The owner listens in the real
   modes on the testing phone, on headphones and the speaker.
5. **Acceptance:** the owner accepts one instrument. The listening review is recorded; G2 and
   the acoustic human row stay open until then.

## 8. In-app test builds

- The easiest path is a candidate SF2. Build a debug/review APK with the candidate font and the
  same descriptor fields (bank 0, program 0, preset name), install on the testing phone, and
  listen in the real modes.
- Do not add an instrument selector. One build carries one candidate.
- Keep the existing renderer, velocity, note durations and release. Only the font changes.
- For the guitar+bass split, the SF2 contains bass and guitar zones; the split is in the font,
  not in app code.
- If the winning candidate needs a custom sample player, that is a separate spike after the
  first two rounds. It must fit the existing PCM/render pipeline and be tested for latency,
  memory, chords and the 30-minute endurance path.

## 9. Integration of the winner

1. Record the decision in `docs/development/v0.2.x/DECISIONS.md`.
2. Replace the descriptor source fields: name, URL, upstream version, license, SHA-256, size,
   bank/program/preset and any conversion steps.
3. Update `scripts/fetch-soundfont.sh` if the URL or file name changes.
4. Update `docs/audio/ASSETS.md` and the license notice.
5. Update `app/src/main/assets/soundfont/piano.json`, the hash and any tests that pin the font
   metadata.
6. Run `scripts/check-assets.sh --require-reviewed`, `scripts/check-fast.sh`, the phone smoke,
   and the normal Android/UI gates.
7. Bump the version under the D31 rule (next 0.9.x), install on the testing phone, and record
   the owner's listening acceptance.
8. Offer a minimal backup after acceptance.

## 10. Risks and fallbacks

- **Phone speaker physics:** B0 is 30.9 Hz and cannot be reproduced by a phone speaker. A
  candidate can sound smooth on headphones but weak on the speaker. Label speaker findings
  separately and choose for both, or state the limitation.
- **5-string bass licensing:** free redistributable B0 libraries are scarce. If none passes the
  license gate, fall back to the piano-only family or the synthetic wildcard, and tell the
  owner.
- **SF2 conversion:** multi-mic/multi-velocity libraries must be reduced to what the app uses
  (velocity 96). Conversion can lose loops or release tails. A/B the converted file against
  the source render.
- **Split transition:** a hard bass/guitar split may be audible; test all five split points, and
  use a short crossfade only if necessary.
- **Chords across the split:** mixed timbres inside one chord may sound inconsistent. Judge
  after mono.
- **Sustain:** keep the current decay. A one-shot sample that dies before 2 s is a failure; use
  loops or long tails as the current font does.
- **Size:** not a constraint now, but every method is labelled with its measured size; a very
  large piano may be acceptable.
- **Determinism/offline:** every candidate is rendered offline and bundled; no runtime network
  or TTS.

## 11. Deliverables

- `instrument-audition-2026-10-17/sources.json` with license and provenance per candidate.
- `instrument-audition-2026-10-17/renders/*.wav` with mono and chord sets.
- `instrument-audition-2026-10-17/listen.html` and `LISTEN-ME.txt`.
- Owner's shortlist and final choice recorded in the verification folder.
- After the choice: one integrated instrument, updated descriptor/fetch/docs/license, passing
  host and phone gates, a new version, and a listening-acceptance record.

## 12. Assumptions

- The owner wants one instrument for every mode and no selector.
- B0 is mandatory; if no licensed 5-string bass reaches it, a piano or synthetic instrument is
  the fallback.
- The current sustain/decay is preserved.
- Chords are checked after mono notes, with mainly low chords plus one or two mid/high chords.
- The synthetic wildcard includes both FM electric piano and Karplus-Strong plucked string;
  both are tiny and labelled separately.
- Audition files are for the owner only, under `.local`, and are not shipped.
