# M8 — Scale degrees and reusable tonal context

State: PLANNED. Prerequisite: M7. Next: [M9](M9_PROGRESSIONS_AND_CONTEXTUAL_INTERVALS.md).
Read [shared contracts](SHARED_CONTRACTS.md), especially spelling, stream derivation and
context inclusion in every Example. This milestone supplies M9B/M10's context implementation.

## Outcome and scope

Every exercise establishes a key, plays one target degree and speaks its selected answer.
Context is immutable generated musical data, reused by later providers without a second audio
pipeline. Own pure theory/context values, degree provider/config, voice mapping, typed codecs,
mode editor/summary/starter, tests and architecture docs. No chromatic target degrees, key
modulation, carried-over context, la-based minor or fixed-do functional option is included.

## Settings and first-use setup

| Field | Values / default | Feasibility rule |
| --- | --- | --- |
| key tonics | Twelve pitch classes; C | Empty means all; spelling preference selects tonic spelling. |
| tonalities | Major/natural minor; major | Nonempty; each selected key/tonality must support requested targets. |
| target degrees | 1–7; all | Nonempty; no silent removal of an infeasible degree. |
| context | Tonic note, tonic triad, ascending octave scale, tonic–dominant–tonic, subdominant–dominant–tonic; tonic triad | Cadence quality depends on tonality. |
| context gap | 0–30000 ms; 500 ms | Included after context before target, in every Example. |
| range | MIDI 0–127; C4–C5 | Covers every sounding context and target note. |
| answer | Degree, note name, movable-do, degree+syllable; degree | Required semantic vocabulary must be complete. |
| recipe/timing/seed/stop | Shared defaults | Complete worst case ≤60 s. |

Major offsets: 0,2,4,5,7,9,11. Natural minor: 0,2,3,5,7,8,10. Target degree is theoretical
degree relative to key, even when target octave differs from the tonic anchor. It is never
inferred from the last context note. Answers with note names follow key spelling/global naming;
movable-do uses major do/re/mi/fa/sol/la/ti or minor do/re/me/fa/sol/le/te.

## M8.1 — Context builder

Introduce a pure `TonalContext` carrying spelled key, tonic MIDI anchor, context kind and note
groups marked CONTEXT. `ContextBuilder` enumerates feasible constructions and produces immutable
groups. Target notes use TARGET. Exposure accounting, answers and target-interval calculations
must exclude context notes, while range, timeline duration and PCM include them.

| Context | Exact musical content |
| --- | --- |
| Tonic note | One tonic at the selected anchor. |
| Tonic triad | Root-position major/minor triad at anchor, one simultaneous group. |
| Ascending scale | Complete major/natural-minor octave, including upper tonic. |
| Tonic–dominant–tonic | Major I–V–I; minor i–V–i, with major V. |
| Subdominant–dominant–tonic | Major IV–V–I; minor iv–V–i, with major V. |

Use root-position closed triads for cadences in this release. Roots use the tonic anchor plus
the relevant diatonic offset in that octave; do not silently choose inversions to fit a narrow
range. Each chord/note lasts noteDuration; sequential groups have the shared gap. A cadence may
need a wider range than the default tonic-triad setup; explain the range error explicitly.

Minor dominant uses the raised leading tone only in the context V chord. Targets remain natural
minor, including lowered degree 7 and its `te` answer. Store harmonic function separately from
target scale. Test A minor's E–G#–B dominant followed by a target G natural as degree 7.

## M8.2 — Finite candidate selection and child streams

For each selected key/tonality and target degree, enumerate all feasible tonic anchors/context
groups and target MIDI pitches in range. Require every selected key/degree combination to have
a completion. Never draw impossible roots and retry indefinitely. Sort keys by pitch class then
tonality ID, anchors numerically, target notes numerically.

Derive `labels`, `context` and `target` RNG streams with SC3's versioned hash vectors. Shuffle
degree categories with labels; for each category, take one context-stream draw over eligible
key/anchor constructions, then one target-stream draw over that construction's eligible pitches.
This precise rule replaces SC3's single realization draw for contextual providers. Record effective
seed/config so all child streams can be reconstructed. There is no new user-selectable RNG.

Every Example compiles its full context, context gap and target. Optional replays and both
Association examples repeat the same concrete context/target; they do not redraw context. A
mode switch cannot leave an old context followed by a new mode's target. Retained history keeps
all original groups and answer tokens through key, recipe, gain and naming edits.

## M8.3 — Validation, duration and UI

Context duration is one noteDuration for tonic/triad, `8*d + 7*g` for heptatonic scale or
`3*d + 2*g` for a three-chord cadence. Example duration adds contextGap + target duration.
Evaluate measured answer duration for every reachable spelling/degree/syllable combination;
apply the common recipe/cue/replay cap, not just target-note duration.

Provide key/tonality, degree checklist, context, answer format, range and context-gap controls.
Use existing compact selection dialogs without adding scoring or notation editors. Preserve
invalid drafts and give field-specific errors connecting impossible context to range. Play
summary may show configured keys/context but never the selected current target before reveal.
Add `builtin:scale_degrees:starter:v1`, five-slot operations and v2 codecs with stable IDs.

## Regression and acceptance matrix

| ID | Cases and oracle |
| --- | --- |
| M8-T01 | All 12 keys × 2 tonalities × 7 target degrees, spelling and each answer format. |
| M8-T02 | Five context choices, exact note/chord order, root-position range bounds and independently checked cadences. |
| M8-T03 | Minor raised leading tone in V but natural-minor target 7/te; no accidental leakage between them. |
| M8-T04 | Frozen seed/child-stream vectors, per-key/degree feasibility and repeated examples retaining identical context. |
| M8-T05 | Context vs target role separation, answer reveal, full recipe cap boundaries and maximum repeats. |
| M8-T06 | Codec/preset round trips, five slots, failed writes, live key edits and immutable history. |
| M8-T07 | Phone context/target/answer phase switches, Association, pending edits, hidden metadata and Stop/timer priority. |
| M8-T08 | Missing/corrupt context/answer asset or compile/render/output failure releases bounded resources. |
| M8-T09 | Rotation/rebind, dialogs and 150%/200% layout/semantics for long key/context labels. |

## Commands and completion

```bash
./scripts/check-music-fixtures.sh
./scripts/check-assets.sh
./gradlew --dependency-verification strict :core:music:test :core:training:test :core:data:testDebugUnitTest :app:testDebugUnitTest
./scripts/check-fast.sh
EAR_NATIVE_EVIDENCE_DIR=.local/verification/mode-expansion/M8/native ./scripts/check-native-tests.sh
EAR_NATIVE_EVIDENCE_DIR=.local/verification/mode-expansion/M8/native EAR_REVIEW_OUT=.local/verification/mode-expansion/M8/device ./scripts/check-full.sh --automated-only
```

These are future checks after environment setup. Complete with M8-T01–T09 evidence, documented
context timing/seed/spelling APIs, updated ledger and no context duplication in the audio layer.
Human acoustic, pronunciation, visual and TalkBack rows remain open. M9 must consume this builder
rather than copy its cadence or minor-key rules into a second implementation.
