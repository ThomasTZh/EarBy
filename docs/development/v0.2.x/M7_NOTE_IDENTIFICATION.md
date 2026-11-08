# M7 — Note identification

State: PLANNED. Prerequisite: M6. Next: [M8](M8_SCALE_DEGREES_AND_CONTEXT.md).
Apply decisions D10/D13/D20 and [shared naming contracts](SHARED_CONTRACTS.md).

## Outcome and scope

The Perfect pitch placeholder becomes Note identification (display label: Perfect pitch, owner decision 2026-10-11): hear an absolute note and its
prerecorded name, optionally including octave. Keep the legacy selection alias readable.
Own absolute-note spelling projection, training config/provider, codec, app mode editor/summary,
starter, semantic voice references and host/phone tests. No pitch-input microphone, tuning,
ability claim, scoring or statistics. Do not add audio-recording permission.

## Configuration and answers

| Field | Values / default | Validation |
| --- | --- | --- |
| pitch classes | C through B; all twelve | Nonempty selected set. |
| range | Inclusive MIDI 0–127; C4–C5 (60–72) | Each selected class has at least one absolute pitch. |
| register selection | Inclusive range, optionally fixed octave | A fixed octave is an explicit range constraint; intersect rather than silently widen. |
| accidentals | Sharp or flat; sharp | Independent from global Scientific/Solfège preference. |
| speak octave | Boolean; false | Required octave token exists for every reachable note. |
| recipe/timing/seed/stop | SC4 defaults/shared controls | Full measured duration valid. |

Use the existing Scientific/Solfège preference for absolute note names: C=Do independent of
key. This is distinct from movable-do functional training in M8–M10. Spell chromatic classes
with the chosen sharp or flat table and apply the accidental to the corresponding note name/
fixed-note syllable. Do not turn C# into an unrelated syllable by using movable-do degree logic.

Answers are structured letter/fixed-note syllable + optional accidental + optional octave.
If octave speech is disabled, omit it from the revealed answer too so text and speech identify
the same target. Setup's range labels may retain octave numbers; those are configuration, not
the current answer. MIDI 0 is C-1; MIDI 69 is A4; MIDI 127 is G9. Full-range selection requires
octave -1..9 vocabulary if enabled, with no runtime TTS fallback.

Use the common Recognition, Exposure and Association recipes. The older WP5.4 sentence
describing answer→note→answer is superseded by the owner's shared Association choice,
example→answer→example. This mode does not create its own fourth recipe.

## M7.1 — Provider and finite feasibility

Enumerate each selected pitch class's feasible MIDI notes in numeric order, respecting range/
fixed octave. Shuffle pitch-class categories then choose one absolute note uniformly using SC3.
Do not sample uniformly over all MIDI notes, which would overweight a class with more available
octaves. A one-class setup is valid; an empty selected set or missing realization is not.

Capture the chosen spelling/naming/voice tokens in the concrete item. Existing history retains
its original answer and audio after naming/gain/recipe changes; newly drawn items use the edit.
The provider exposes required assets over all reachable classes/octaves, not only the first draw.
Example duration is noteDuration; complete answer duration includes every 80 ms inter-token gap.

## M7.2 — Persistence and screen integration

Use stable kind `note_identification`; decode `PERFECT_PITCH` from legacy settings through M2's
selection adapter. Existing interval setup/presets remain interval data even if old lastMode
selected the placeholder; do not reclassify them into note-identification presets.

Add pitch-class selection, range/fixed-octave, accidental style and octave-speech controls with
field-specific errors. Global naming stays in Settings. Add a concise Play setup summary and
`builtin:note_identification:starter:v1`; support the five mode slots and process recreation.
Selection/preset writes use revision checks, preserve invalid drafts and surface failures.

Hidden answer preference applies before consumption and after toggling hidden, across all app
text, content descriptions, notifications and media metadata. Speak answers even when hidden.
There must be no always-visible “current MIDI note” diagnostic in production UI or metadata.

## Regression and acceptance matrix

| ID | Cases and oracle |
| --- | --- |
| M7-T01 | C4=60, A4=69, C-1=0, G9=127; sharp/flat tables preserve pitch and expected absolute names. |
| M7-T02 | Global Scientific/Solfège × accidentals × octave on/off yields matching display/token descriptors. |
| M7-T03 | Empty classes, narrow/fixed-octave ranges, missing selected class and out-of-range values fail at the correct field. |
| M7-T04 | Category bag coverage independent of number of octaves, fixed seeds and unchanged draw order after naming-only changes. |
| M7-T05 | All three recipes, maximum repeats, longest octave phrase, cap boundaries and missing/corrupt token handling. |
| M7-T06 | Legacy PERFECT_PITCH selection, v2 round trips, five slots, presets, failed writes and stopped recreation. |
| M7-T07 | Live edits/history replay, rapid mode switching/Stop/Start, phone output cleanup and no identity collision. |
| M7-T08 | Hidden answers across snapshots/semantics/metadata, 150%/200% text and dialog recreation. |

Use fixed expected names for accidental/octave edges, not merely equality between the same
production display and speech mapper. Check all 128 notes with both naming preferences and
octave modes for vocabulary coverage. Human pronunciation remains separate.

## Commands and completion

```bash
./scripts/check-assets.sh
./gradlew --dependency-verification strict :core:music:test :core:training:test :core:data:testDebugUnitTest :app:testDebugUnitTest
./scripts/check-fast.sh
./scripts/device-smoke.sh --smoke-only --out .local/verification/mode-expansion/M7/device
./scripts/capture-ui-candidates.sh .local/verification/mode-expansion/M7/ui-candidates
```

Future commands assume the environment and new suite registration. Complete with M7-T01–T08
evidence, updated naming/migration contracts and ledger. Search active UI/strings for the old
label; retain it only in compatibility/history documentation and decoding tests. Do not change
historical evidence or claim that the feature establishes or teaches perfect pitch.
