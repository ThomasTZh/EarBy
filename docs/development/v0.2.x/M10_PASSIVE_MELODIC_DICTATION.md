# M10 — Passive melodic dictation

State: PLANNED. Prerequisite: M9A and M9B. Next: [M11](M11_INTEGRATION_AND_ACCEPTANCE.md).
Apply D02/D21 and [shared contracts](SHARED_CONTRACTS.md). This is passive listening,
remembering and comparison with spoken answers, without transcription or scoring.

## Outcome and scope

Generate bounded, reproducible tonal phrases satisfying every selected constraint, then play
them with context and composed speech through the existing service. Own training melody config/
graph/provider, small music helpers if required, data codecs, editor/summary/starter, vocabulary
references and host/phone tests. Reuse M8 context and M4 spelling. No new rhythm engine, notation
editor, answer keyboard, recording, adaptive difficulty, statistics or fabricated bag cycle.

## Settings and defaults

| Field | Values / default | Validation |
| --- | --- | --- |
| key/context/range | M8 controls; C major, tonic triad, C4–C5 | Context and every target pitch fit. |
| phrase length | Integer 2–16; 4 | Exact note count, including start/end. |
| start/end degree | Each any or 1–7; tonic/tonic | Endpoint pitch may use any feasible octave of that degree. |
| allowed directed steps | Nonempty subset of integers -12..12; all -5..5 | Signed semitones; zero explicitly allows repetitions. |
| maximum leap | Integer 0–12; 5 | Intersect with allowed steps; no hidden exception at endpoints. |
| direction bias | Balanced, ascending, descending; balanced | Positive integer transition weights below. |
| answer | Degree sequence, movable-do sequence, directed interval sequence; degrees | Complete composed speech must fit. |
| note length / gap | Shared 700 / 100 ms defaults | Equal lengths within phrase. |
| recipe / seed / stop | Shared controls; Recognition/until stopped | Reject AfterCycle and FINISH_CYCLE; no meaningful label cycle exists. |

Bias weights: balanced up/down/repeat = 1/1/1; ascending = 3/1/1; descending = 1/3/1.
Bias changes likelihood among feasible choices, not constraints and not guaranteed monotonicity.
Keep this three-choice control explicit; do not invent arbitrary adaptive weighting.

## M10.1 — Finite graph and reachability

1. For each selected key, enumerate absolute scale pitches in range, each with degree/spelling.
   Nodes are actual MIDI notes (at most 128), not just pitch classes.
2. Add edge p→q exactly when `q-p` is selected and `abs(q-p) <= maximumLeap`. Zero edges exist
   only when step zero is allowed. No chromatic notes enter the graph.
3. Define `canFinish[remainingTransitions][node]`. Base row is true at allowed ending-degree
   nodes. Later rows are true when an outgoing edge reaches a true previous row.
4. Eligible starts have the requested start degree (or any) and a true entry at length-1.
   Combine them with feasible context anchors. Reject a selected key with no complete path;
   do not omit it from generation or retry indefinitely.
5. Preserve a compact graph/DP table bounded by 128 nodes × 16 rows; do not enumerate or retain
   all phrases. Validate memory/time with the maximum-range, maximum-length configuration.

Report impossible endpoint/step/range combinations using affected fields and concrete suggestions,
without automatically relaxing them. Examples: length 2 with tonic endpoints and step zero disabled
may still be possible through an octave if allowed; zero-leap requires repeated pitch and compatible
endpoints. Tests must reason in absolute pitches rather than assume equal degree means unison.

## M10.2 — Versioned sampling

Use SC3-derived context and target streams. Choose a feasible key/context using one context-stream
draw in M8 canonical order. Choose an eligible start uniformly with one target-stream draw. At
each subsequent position, list outgoing pitches that can still reach an allowed ending; sort by
MIDI, apply the direction weights and draw once with `nextInt(totalWeight)` using cumulative
integer buckets. Even a singleton list consumes the documented draw, matching the frozen v1 rule.

This samples feasible paths with locally biased transitions; it does not claim uniformity over
all possible melodies. No final “fix-up” note, rejection loop or implicit phrase-cycle counter.
Store full concrete notes, derived degrees/steps, context and provenance. Repeat/history never
redraws. A same-seed configuration reproduces phrase order independently of coroutine scheduling.

## M10.3 — Exact reachable duration bound and answer

The target phrase duration is `length*d + (length-1)*g`. Add full context duration/context gap
for every Example. Degree/solfège answers contain one token/phrase per note; directed interval
answers contain one direction+interval phrase per transition, with unison stated without a false
direction. No octave numbers are added to degree answers. Token order follows played order.

Use dynamic programming over the same reachable graph to find the maximum speech-frame cost
among valid complete phrases. For degree/syllable answers attach clip cost to nodes; for interval
answers attach the complete phrase cost to edges. Include 80 ms between actual clips, with no
leading/trailing synthetic token gap. If token counts differ by edge, track the total cost with
the connection gap explicitly rather than multiply by a fixed word count.

Evaluate each feasible key/context, combine its example cost with its compatible maximum answer
cost, and apply the shared recipe/repeat/final-tail calculation. A random sample or independent
maxima that reject an otherwise-valid setup without explanation are insufficient. Use checked
integers. Test the DP against exhaustive enumeration on tiny graphs; never enumerate all 16-note
phrases in production. Start/save/switch all use this same validated bound and asset union.

## M10.4 — Product integration

Add phrase length, endpoints, directed steps, maximum leap, bias and answer controls, reusing
key/context/range/common recipe sections. Explain passive use in one concise help sentence.
Do not expose answer entry, marks, accuracy or “correct/incorrect” states. Keep Start/Stop only.

Add `builtin:melodic_dictation:starter:v1`, five slots and strict typed codec. The default four-
note tonic-to-tonic setup with steps -5..5 including zero must validate and play. Disable cycle
stop choices for this provider with clear explanation; importing them returns a validation error.
Completed-items and elapsed-duration stopping remain available with existing service semantics.

Mode switching preserves the entire context+melody+answer learning unit, including Association's
second full example. Long answer sequences reveal once at their first consumed token, remain
hidden when requested, and cannot overflow app layout at 150%/200% text. Do not send raw melody
notes into ordinary metadata to provide progress diagnostics.

## Regression and acceptance matrix

| ID | Cases and oracle |
| --- | --- |
| M10-T01 | Lengths 2/16, fixed/any endpoints, octave endpoints, narrow ranges and impossible constraints. |
| M10-T02 | Zero leap with/without repeats, signed-step filtering, exact maximum leap and no chromatic target notes. |
| M10-T03 | Exhaustive tiny-graph comparison for reachability and duration DP, including variable-length interval speech. |
| M10-T04 | Deterministic seed vectors, exact weighted bucket choices and singleton draws; no flaky statistical threshold. |
| M10-T05 | At least 10,000 phrases across a fixed seed/config corpus satisfy length, range, edges and endpoints with bounded generation time. |
| M10-T06 | Every answer mode/recipe, 16-note/15-interval speech, cap below/at/above 60 s and maximum replay rejection. |
| M10-T07 | Cycle policies rejected before persistence/Start; count/time policies and mode-switch deadline preservation work. |
| M10-T08 | Preset/config/draft persistence, failed saves, live edits/history and stopped recreation. |
| M10-T09 | Phone switching during each melody/answer phase, hidden semantics/media, focus/route/Stop/error cleanup and bounded memory. |

## Commands and completion

```bash
./scripts/check-music-fixtures.sh
./scripts/check-assets.sh
./gradlew --dependency-verification strict :core:music:test :core:training:test :core:data:testDebugUnitTest :app:testDebugUnitTest
./scripts/check-fast.sh
EAR_NATIVE_EVIDENCE_DIR=.local/verification/mode-expansion/M10/native ./scripts/check-native-tests.sh
./scripts/device-smoke.sh --smoke-only --out .local/verification/mode-expansion/M10/device
./scripts/capture-ui-candidates.sh .local/verification/mode-expansion/M10/ui-candidates
```

These are future commands after environment setup. Complete with M10-T01–T09 evidence, graph/
sampling/duration contracts, mode UI/persistence and updated ledger. All eight providers must
now be production-wired. M11 supplies final full/endurance acceptance; new listening/pronunciation,
visual and TalkBack acceptance stays explicitly open.
