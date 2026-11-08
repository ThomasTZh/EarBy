# M3 — Switch after the current learning sequence

State: PLANNED. Prerequisite: M2. Next: [M4](M4_THEORY_FIXTURES_AND_VOICE_ASSETS.md).
Contract: decisions D04–D06/D12/D16; [SC2–SC8](SHARED_CONTRACTS.md).

## Outcome and scope

A mode request preserves the learning sequence currently heard, then starts the destination's
latest validated saved setup. Stop and interruptions remain prompt. Future old-mode audio
cannot leak through an already-filled AudioTrack buffer.

Own `DefaultSessionController`, session/AudioPort contracts, service composition and its
RendererAudioPort, `ExerciseOutputWriter`, media commands/results, Play state, tests and
architecture docs. Read actual write/consumed-frame behavior, lock ordering and cleanup first.
Use an internal deterministic fake second provider in tests until real modes arrive. No test
provider, debug mode or answer-bearing debug extras may enter production user interfaces.

## M3.1 — Command and state contract

Add `SwitchMode(kind, expectedSavedRevision)` through the controller/media command bridge.
The service resolves the saved config, validates assets/duration and acknowledges the resolved
revision. UI reports pending/active state from snapshots, never from a successful button click.
Snapshots include selected/active/pending kind and applied/pending revision; hidden answers
remain stripped before publishing those snapshots or metadata.

| Current situation | Required transition |
| --- | --- |
| Stopped | Save selection only; no audio/focus acquisition or automatic Start. |
| Preparing, no frames submitted | Invalidate old work and prepare destination immediately. |
| Mandatory unit in progress | Keep that unit; store latest valid destination and discard old next item. |
| Optional example already consumed | Finish that whole example, including its context, then switch. |
| Optional example queued but not consumed | Cancel that example at its safe start boundary; switch. |
| Final silence/separator tail | Skip remaining tail and switch; do not start a new old-mode item. |
| A→B→C pending | Replace B with C; only C may commit and increment activation ordinal. |
| A→B→A before commitment | Cancel departure; resume A's remaining recipe/queue without redrawing current. |
| Destination edited or preset applied | Replace pending config with its newest acknowledged valid revision. |
| Invalid destination / failed save | Keep audible state unchanged; return field/write error, no false pending state. |
| Stop, fatal focus/route event, immediate timer | Cancel pending work and release output using existing policy. |

Serialize these decisions on the existing controller actor/single writer. Carry session
generation, item ordinal and request revision through every render/preparation/callback.
Stale callbacks cannot publish an answer, acquire output or restart a stopped session.

## M3.2 — Consumed-frame boundary protocol

M1's `CompiledExercise` exposes a mandatory-unit end, optional replay intervals and final tail.
All boundaries are absolute frame positions in the cached item. They include every token and
recorded padding, all chord/scale/melody notes and Association's second complete example.

1. Give the output port explicit `[startFrame, endFrameExclusive)` segment submission plus
   consumed-position reporting. Validate bounds, channel alignment and identity on each call.
2. Never submit across a unit boundary until the controller authorizes the next unit. Keep
   AudioTrack ownership single; changing segments does not create a new track per note.
3. At a switch request, classify the current phase using consumed frames, not written frames
   or a UI clock. Recheck that position while resolving an in-flight submission race.
4. If an optional unit is buffered but has not begun consumption, atomically revoke its
   submission and flush only at the completed prior boundary. If consumption has begun,
   finish the optional unit instead. Reset output-head accounting using the existing flush
   epoch; never replay already-heard mandatory frames or cut a spoken token.
5. If a request races the first consumed frame, define one linearization point under output
   ownership; tests must establish either “not begun, skipped” or “begun, completed”. Do not
   promise precision from a stale periodically sampled playback position.
6. On normal playback with no pending request, authorize successive units promptly. Verify
   no systematic extra gaps/underruns relative to the current timeline. A prepared destination
   can replace the normal next PCM entry; do not retain current + old next + destination.

Handle short/zero/error writes, completion before callback, flush/head wrap and cancellation
while blocked on output. Avoid holding controller locks across suspending native work. If the
platform cannot establish a safe unconsumed-unit cancellation with existing output primitives,
record the concrete experiment and refine the protocol before claiming the contract passes.

## M3.3 — Commit, history and timers

Commit only when the safe boundary is reached and destination preparation is valid. Increment
activation ordinal then, using SC3 seed derivation. Preparation may use an isolated provisional
stream for that prospective ordinal; discarded requests must not advance any retained stream.
Fixed destination seeds reproduce their first item on activation. Keep item ordinals monotonic
for the whole session and interval RNG draw order unchanged within each activation.

Current/history items retain original recipe, gains and answers. Replaced futures release
timelines/PCM; internal history remains capped at 20 and PCM at two entries/48 MiB. Pending
switches refuse conflicting Previous/Next/Repeat commands with explicit results. Do not add
these controls to the app. Preserve existing behavior outside pending transitions.

The service's elapsed sleep deadline/fade continues unchanged. An already-expired timer takes
priority over committing a new mode. For a finish-current-item/cycle timer drain already in
progress, finish according to that existing drain contract and stop; do not activate a pending
destination to extend the drain. Preserve the existing maximum drain bound. Active mode-local
count/cycle stopping restarts on destination commitment; an interrupted old tail is not counted
as a fully completed old item. A same-mode edit follows Reconfigure, not SwitchMode.

## M3.4 — Failure and UI integration

Expose command rejection, disconnected controller, failed futures and stale revisions as
observable state. Retry/rebind must query current service state rather than replaying an old
SwitchMode request. Play identifies the audible mode and pending destination separately;
Setup/Presets follow selected destination. Answer text remains tied to audible identity.

Runtime generation/compile/asset/render/output errors clear pending state, invalidate work,
silence output and attempt all resource cleanup before reporting Error. Preserve coroutine
cancellation and the original failure when cleanup also fails. Persistence/validation errors
before acceptance leave current playback intact. No hidden interval fallback is permitted.

## Regression and acceptance matrix

| ID | Cases and oracle |
| --- | --- |
| M3-T01 | Every recipe phase, every answer token, context/target boundary and Association's second example: emitted frame IDs follow the specified boundary. |
| M3-T02 | Optional replay not submitted, buffered-unconsumed, first consumed frame and nearly finished; no partial example or old future leak. |
| M3-T03 | A→B→C, cancellation to A, pending edits/preset updates and revision conflicts; latest valid request wins exactly once. |
| M3-T04 | Stop/Start, late render callback, focus/route loss, command reconnect and destruction release resources; no stale restart. |
| M3-T05 | Immediate/finish-item/finish-cycle timer at each boundary; original elapsed deadline and independent fade/duck retained. |
| M3-T06 | Fixed and derived seeds, cancelled preparations, same-mode edits and history replay preserve provenance without identity collisions. |
| M3-T07 | Short writes, wrap/flush accounting, rendering/output failures and simultaneous configuration changes are bounded and observable. |
| M3-T08 | Long simulated switching run retains at most 20 historical items, two PCM entries and 48 MiB. |
| M3-T09 | Phone MediaController, activity recreation/Home/rebind and hidden metadata report the same active/pending identity as consumed audio. |

Use virtual time, controllable render completions and an instrumented fake output with separate
written/consumed counters. Tests based only on coroutine delay or the number of play calls are
insufficient. Phone tests verify output accounting and service state; they do not prove listening
quality or accessory behavior. Capture transition traces without publishing answers externally.

## Commands and completion

```bash
./gradlew --dependency-verification strict :core:training:test :core:audio:testDebugUnitTest :core:media:testDebugUnitTest :app:testDebugUnitTest
./scripts/check-fast.sh
EAR_NATIVE_EVIDENCE_DIR=.local/verification/mode-expansion/M3/native ./scripts/check-native-tests.sh
EAR_NATIVE_EVIDENCE_DIR=.local/verification/mode-expansion/M3/native EAR_REVIEW_OUT=.local/verification/mode-expansion/M3/device ./scripts/check-full.sh --automated-only
```

These are future checks after sourcing `scripts/env.sh`. Complete with M3-T01–T09 evidence,
fresh native/phone results, updated transport/output/lifecycle contracts, and a ledger entry
explaining any changed timing behavior. Human visual/acoustic rows stay open. Do not proceed
with real providers if consumed-frame switching remains only a UI approximation.
