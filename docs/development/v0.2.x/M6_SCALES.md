# M6 — Complete scales and modes

State: PLANNED. Prerequisite: M5. Next: [M7](M7_NOTE_IDENTIFICATION.md).
Follow [D09](DECISIONS.md) and the [ToneDear reference](REFERENCE_PROCEDURES.md): v0.2.x
plays complete octave scales. Fragments in the original plan/WP5.3 are deferred, not pending
implementation. Keep the project's thirteen formulas and both-direction extension.

## Outcome and scope

Scales works through all three recipes with independent settings/presets, explicit directions,
correct spelling and full duration validation. Own music scale definitions, training provider/
config, typed data codecs, app editor/summary, starter and tests. Reuse M1/M4 compiler/assets.
No fragment UI, inferred fragment label, classical melodic-minor variant or scale statistics.

## Configuration and formulas

| Field | Values / default | Rule |
| --- | --- | --- |
| scales | Catalog below; major + natural minor | Nonempty canonical set; aliases normalize once. |
| direction | Ascending, descending, both; ascending | Both traverses up then down with turning tonic once. |
| roots / fixedRoot | Pitch classes; empty = all; none fixed | Fixed note is lower tonic of the complete octave. |
| range | MIDI 0–127; C3–C6 | Every selected scale must have feasible complete octave. |
| timing/recipe/seed/stop | Shared SC4 controls | All speech, notes, repeats and tail fit 60 s. |

| Canonical ID | Octave offsets excluding repeated tonic |
| --- | --- |
| major | 0,2,4,5,7,9,11 |
| natural_minor | 0,2,3,5,7,8,10 |
| harmonic_minor | 0,2,3,5,7,8,11 |
| jazz_melodic_minor | 0,2,3,5,7,9,11 |
| dorian | 0,2,3,5,7,9,10 |
| phrygian | 0,1,3,5,7,8,10 |
| lydian | 0,2,4,6,7,9,11 |
| mixolydian | 0,2,4,5,7,9,10 |
| locrian | 0,1,3,5,6,8,10 |
| major_pentatonic / minor_pentatonic | 0,2,4,7,9 / 0,3,5,7,10 |
| minor_blues | 0,3,5,6,7,10 |
| whole_tone | 0,2,4,6,8,10 |

Display aliases Major (Ionian) and Natural minor (Aeolian) without separate probability mass.
Answer speech uses the canonical name once. Label jazz melodic minor explicitly; descending
uses its same collection. Whole-tone/blues spelling follows the frozen M4 convention.

## M6.1 — Traversal and deterministic generation

1. Build the ascending sequence from tonic+offsets, then append tonic+12. Descending reverses
   that complete sequence. Both appends the reversed sequence with its first/top note removed.
2. If the formula has k distinct degrees, emitted lengths are k+1 ascending/descending and
   2k+1 for both. Verify the top tonic once and lower tonic at both ends in both mode.
3. Enumerate lower tonic MIDI values fitting all notes and selected roots. A range less than
   twelve semitones cannot fit any full scale; return a range error, never truncate/wrap.
4. Shuffle selected canonical scale labels; choose a feasible tonic uniformly in numeric order
   using SC3. Freeze provider-v1 seeds. Spelling/naming must not alter RNG consumption.
5. Build immutable sequential target groups. There is no contextual prelude in this mode and
   no root cue inserted to identify a selected fragment, since fragments are out of scope.

Reject persisted/imported fragment fields under the strict new schema instead of interpreting
them as full-scale settings. No released v0.2.x fragment schema exists to migrate. Keep the
original overall-plan document untouched; document the scope refinement in the active ledger.

## M6.2 — Duration and product integration

For n emitted notes, example duration is `n*noteDuration + (n-1)*gap`. Take the maximum over
selected formulas and tonic/name assets, then apply the shared recipe including Association's
second full traversal and optional replays. Use measured scale-name clips. Both-direction
Association or large repeat settings may exceed 60 s; reject them before saving/starting with
timing feedback. Do not shorten a scale, speech or replay count automatically.

Implement scale checklist, direction selector, root/fixed-root/range controls and shared recipe
controls. No fragment toggle or “coming soon” subcontrol remains in the completed screen. Play
summary names selection/direction/range without exposing the current generated scale early.
Save mode-local drafts, support all five slots, and add `builtin:scales:starter:v1` using the
default table. Codec normalization accepts documented aliases but exports only canonical IDs.

Live edits change queued future exercises only. Mode switching waits for a whole traversal
and spoken answer according to M3; it never switches at a turning tonic or intermediate note.
Hidden mode strips answer text from screen/semantics/media while retaining scale-name speech.

## Regression and acceptance matrix

| ID | Required evidence |
| --- | --- |
| M6-T01 | Thirteen formulas × twelve roots × all directions match independent fixtures and correct spellings. |
| M6-T02 | Major/Ionian and minor/Aeolian inputs canonicalize without duplicate bags or preset drift. |
| M6-T03 | Correct k+1/2k+1 lengths, descending order, single turning tonic and consistent jazz minor descent. |
| M6-T04 | Exact-octave/narrow/extreme/fixed-root ranges; no silent truncation and correct empty-root normalization. |
| M6-T05 | Below/at/above 60 s, longest formula, both-direction Association, maximum repeats and measured speech. |
| M6-T06 | Seed fixtures, edits/history replay, codec/preset round trips, failed writes and one-time starter installation. |
| M6-T07 | Phone Start/Stop in every recipe, chord↔scale/interval↔scale switches during traversal/answer and pending edits. |
| M6-T08 | Hidden snapshots/semantics/media, rotation, 150%/200% layout and corrupt/missing asset failure cleanup. |

Use the common provider suite introduced in M1 and extend the native sequential-note fixture
to 15 notes for a seven-degree both traversal. Confirm buffer/timeline retention stays bounded.

## Commands and completion

```bash
./scripts/check-music-fixtures.sh
./scripts/check-assets.sh
./gradlew --dependency-verification strict :core:music:test :core:training:test :core:data:testDebugUnitTest :app:testDebugUnitTest
./scripts/check-fast.sh
./scripts/device-smoke.sh --smoke-only --out .local/verification/mode-expansion/M6/device
./scripts/capture-ui-candidates.sh .local/verification/mode-expansion/M6/ui-candidates
```

These are future checks after environment setup. Extend the smoke suite to include the new
provider; an unchanged interval-only run does not satisfy M6-T07. Run native checks if render
code changed. Complete when M6-T01–T08 pass and scale docs/registry/status agree that fragments
are deferred. Record pronunciation/visual/acoustic rows open rather than seeking manual tests.
