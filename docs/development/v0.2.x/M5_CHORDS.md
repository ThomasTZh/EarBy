# M5 — Chord quality and inversion training

State: PLANNED. Prerequisite: M4. Next: [M6](M6_SCALES.md).
Apply [D08](DECISIONS.md), the [ToneDear procedure](REFERENCE_PROCEDURES.md), and shared
provider/randomness/timing contracts. D08 is resolved; no further ambiguity approval is needed.

## Outcome and owning paths

Chords becomes a complete offline mode with validated setup, deterministic closed-position
voicings, equivalent spoken answers, three learning recipes, five slots and production playback.
Own music chord definitions, a new training chord provider/config, typed data codecs, app mode
editor/summary, starter preset, voice registry entries and associated host/native/phone tests.
Keep the existing player/compiler and dependency versions. No open voicings, extensions beyond
the eleven qualities, root cue, answer-entry UI or scoring is included.

## Configuration and musical contract

| Field | Values / default | Validation |
| --- | --- | --- |
| qualities | Eleven catalog IDs; major + minor | Nonempty; unknown IDs rejected. |
| inversions | Root, first, second, third; root only | Third exists only for four-note chords. |
| presentation | Harmonic or ascending arpeggio; harmonic | Use one explicit selected presentation per setup. |
| roots / fixedRoot | Pitch classes; empty = all; no fixed note | Fixed MIDI root must agree with explicit roots and be feasible. |
| inclusive range | MIDI 0–127; C3–C6 (48–84) | Every sounding note must fit; never transpose/clamp silently. |
| answer | Quality / quality plus inversion; quality | Alternative valid interpretations included under D08. |
| timing / recipe / seed / stop | SC4 and existing shared controls | Validate complete maximum duration and required assets. |

| Stable quality ID | Semitone offsets from theoretical root |
| --- | --- |
| major / minor | 0,4,7 / 0,3,7 |
| diminished / augmented | 0,3,6 / 0,4,8 |
| sus2 / sus4 | 0,2,7 / 0,5,7 |
| major_seventh / minor_seventh | 0,4,7,11 / 0,3,7,10 |
| dominant_seventh | 0,4,7,10 |
| half_diminished / diminished_seventh | 0,3,6,10 / 0,3,6,9 |

For inversion k, raise the first k formula members by 12 and sort numerically. Preserve
theoretical root separately from bass. Require theoretical root to be a valid MidiNote and
sounding notes to fit the configured range; the silent theoretical root may lie below that
range in an inversion. Fixed-root validation uses that theoretical root, not the bass.

Allowed categories are selected qualities paired with applicable inversions, then projected
to the selected answer mode. In mixed triad/seventh selection, third inversion applies only to
seventh chords. Reject any selected quality with no applicable inversion or selected inversion
that applies to no selected quality. Do not turn third-inversion triads into root-position items.

## M5.1 — Finite generator and equivalent answer data

Enumerate feasible candidates by stable quality ID, inversion number, theoretical MIDI root,
then sounding tuple. Each enabled category must have at least one candidate. Shuffle categories
using SC3 and choose one realization uniformly; do not rejection-sample roots or let a wider
root range give one answer category extra bag weight. Record config/provider version and seed.

Implement D08's equivalence policy as an independent Kotlin musical operation. Compare each
allowed quality/inversion's bass-relative closed voicing with the played voicing. Keep matching
pairs; quality-only output deduplicates qualities. Root restrictions govern generation, while
interpretation matching follows the selected quality/inversion vocabulary, as in the reference.

Represent alternatives as structured pairs, not separate quality and inversion sets whose
Cartesian product could invent an invalid answer. Keep the generated theoretical interpretation
in provenance for reproducibility. Sort alternatives by catalog ID then inversion; the spoken
and displayed alternative order must agree. Equivalence does not merge generator categories
or silently change their documented bag weights.

Passive adaptation: one complete phrase per alternative separated by the “or” token. For
quality-plus-inversion include the quality with each inversion so the pairing is unambiguous.
Do not announce root names that the selected answer format does not request. Recognition,
Exposure and Association all use the same full alternative set; this behavior is app-specific.

## M5.2 — Timeline, duration and audio

Harmonic example: all chord notes start together and last noteDuration. Arpeggio: notes in
ascending sounding order, each lasting noteDuration, with sequential gap after each except last.
For n members, example duration is `n*noteDuration + (n-1)*gap` for arpeggio or noteDuration
for harmonic. Answer duration sums all alternative clips plus 80 ms between every pair of
clips. Use the actual registry frames and shared recipe calculation, including repeats/tail.

Check the worst reachable answer/voicing for all selected categories. An ambiguous configuration
is valid if feasible and within duration; excessive composed speech is a duration error with
field feedback, never silently omitted alternatives. Preserve simultaneous note-off/on ordering,
renderer headroom policy, two PCM entries and 48 MiB. Verify four-note onset and finite PCM on
the production renderer; automated spectral/peak evidence is not acoustic approval.

## M5.3 — Product integration

Register the provider only when codec, validation, required voices and screens exist. Replace
the Chords placeholder with its actual Play summary and mode-specific Setup controls. Disable
inapplicable inversion choices with explanation; preserve invalid range/selection drafts for
correction. Add a read-only starter `builtin:chords:starter:v1` matching the default table and
make the five existing mode slots support save/rename/clear/apply. Do not add a sixth slot.

Live edits preserve the audible item, replace future chord items and retain original history
sound. Switching to/from intervals uses M3. Pending edits must update pending revision. Include
hidden-answer semantics and failure messages for missing speech, invalid ranges and failed saves.

## Regression and acceptance matrix

| ID | Cases and oracle |
| --- | --- |
| M5-T01 | All eleven formulas, all applicable inversions and twelve roots match independent M4 fixtures. |
| M5-T02 | Theoretical root vs bass, boundary ranges, fixed root + empty/all roots and incompatible explicit roots. |
| M5-T03 | Augmented/diminished-seventh symmetric inversions and suspended equivalents produce complete valid pairs; non-equivalent intervals do not match. |
| M5-T04 | Quality-only deduplication, selected-vocabulary filtering, deterministic alternative ordering and exact speech union. |
| M5-T05 | Frozen seeds, bag coverage, stable enumeration and immutable replay through edits/Previous/Repeat compatibility commands. |
| M5-T06 | Harmonic simultaneity/arpeggio offsets, every recipe, maximum repeats and below/at/above 60 s with longest alternative speech. |
| M5-T07 | Codec/preset round trips, five isolated slots, failed writes, valid defaults and starter adoption exactly once. |
| M5-T08 | Phone Start/Stop, interval↔chord switching, Association boundary, rotation/rebind and hidden snapshots/metadata/semantics. |
| M5-T09 | Missing token, generation/compile/render/output failure and repeated start/stop release resources within bounds. |

Use parameterized finite tests plus fixed external fixtures, not only self-consistency with the
production formula array. Test numeric sorting across two-/three-digit MIDI values. UI candidates
cover both presentations, multi-answer display and 150%/200% text with complete semantics.

## Commands and completion

Future checks after sourcing the environment:

```bash
./scripts/check-music-fixtures.sh
./scripts/check-assets.sh
./gradlew --dependency-verification strict :core:music:test :core:training:test :core:data:testDebugUnitTest :app:testDebugUnitTest
./scripts/check-fast.sh
EAR_NATIVE_EVIDENCE_DIR=.local/verification/mode-expansion/M5/native ./scripts/check-native-tests.sh
EAR_NATIVE_EVIDENCE_DIR=.local/verification/mode-expansion/M5/native EAR_REVIEW_OUT=.local/verification/mode-expansion/M5/device ./scripts/check-full.sh --automated-only
```

Complete with M5-T01–T09 evidence, source/asset/config hashes, updated provider/UI/voice contracts
and ledger. New pronunciation, acoustic and visual acceptance remain named open rows. A working
domain chord generator without saved setup, speech and phone service integration is incomplete.
