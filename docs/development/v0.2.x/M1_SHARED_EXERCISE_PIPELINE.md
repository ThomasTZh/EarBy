# M1 — Shared exercise pipeline with an interval adapter

Status: PLANNED. Requires [M0](M0_BASELINE_AND_HANDOFF.md).
Next: [M2](M2_MODE_STORAGE_AND_UI_STATE.md). Contracts: [SC1–SC5/SC8](SHARED_CONTRACTS.md).

## Goal

Make the accepted interval app the first production consumer of a provider-independent
exercise pipeline. Keep audible interval behavior and deterministic v1/v2 output unchanged.
This is a consumed abstraction, not an empty plugin framework for hypothetical features.

## Current seams to change

| Owner | Existing source / assumption |
| --- | --- |
| training | `IntervalExercise`, `GeneratorIdentity`, `ConfiguredIntervalExerciseSource`, `SequencePolicy` |
| compiler | `RecipeStep.Example.duration` assumes two notes; `RecipeExerciseCompiler` accepts one interval answer ID |
| session | `StartRequest.policy`, `ExerciseSource.next`, controller Item and `publish()` refer directly to intervals |
| audio adapter | `AudioPort.prepare/answerStartFrame` and app `RendererAudioPort` accept IntervalExercise |
| app/media | `TrainingPlaybackService` creates the interval source; metadata projects an interval label |

Preserve the legacy generator classes and their fixture files. Change adapters and consumers
around them. `core/audio/pcm/ExerciseRenderer` already consumes generic timeline events;
retain this renderer rather than introducing a player for each mode.

## Proposed public interfaces

Implement TrainingKind, ProviderConfig, ModeSetup, ProviderValidation, ExerciseProvider,
ProviderRegistry, ConcreteExercise, AnswerDescriptor and CompiledExercise from SC2.

Keep musical values in music, provider/session values in training, DTO serialization in data.
Provider validation takes an injected voice-duration catalog and returns field issues or a
normalized immutable config. A generator exposes `next()` and optional real bag/cycle metadata.

Generalize StartRequest to carry ModeSetup plus gains/visibility. Generalize ExerciseSource
to return ConcreteExercise and compile under the item's captured recipe. AudioPort.prepare
accepts identity + compiled exercise + RenderSettings; it does not need a provider-specific
object to find the answer frame. Snapshot answer data remains visibility-filtered text/values.

Retain a narrow temporary LastSetup→interval ModeSetup conversion for M2 migration. Do not
have two competing representations determine live playback or derive one from UI strings.

## Ordered work packages

### M1.1 — Freeze compatibility at adapter boundaries

Add tests that compare legacy output to adapted ConcreteExercise for existing golden seeds,
all interval presentations, fixed roots and mixed presentation. Compare notes, category,
legacy provenance, sequence/cycle position, answer ID and normalized config. Compare timelines
under each recipe and timing control, including harmonic unison and separator placement.

These tests may use the old compiler temporarily as an oracle for unchanged interval behavior;
new musical provider fixtures must be independent in M4. Never regenerate old expectations.

### M1.2 — Add minimal models and real interval registration

1. Add immutable domain shapes and stable kind IDs. Add only the interval config subtype now.
2. Register the interval provider in production; known absent modes still show placeholders.
3. Adapt legacy generator results without additional RNG calls or sorting changes.
4. Preserve legacy GeneratorIdentity separately within provenance or losslessly map its fields.
5. Add test-only fake providers for multi-note and multi-token exercises. They cannot be
   registered in production or provide an accidental alternate Start route.

### M1.3 — Compile variable examples and answers

1. Build the example's relative note-group schedule using recipe timing.
2. Repeat the complete context/target example for every Example step.
3. Compile every answer token with measured frames and exactly 80 ms between tokens.
4. Emit one answer-start marker and explicit mandatory/replay/trailing boundaries without
   changing sound offsets of existing interval timelines.
5. Validate non-negative offsets, positive audible windows, same-group simultaneity and
   supported overlap rules. Reject accidental voice/music overlap rather than masking it.
6. Share the complete duration evaluator with validation; remove two-note shortcuts from
   generic code while keeping legacy helper APIs available where fixtures require them.

### M1.4 — Move the service/controller onto shared data

1. Update Start/source/item/prepare signatures and production composition in one coherent edit.
2. Resolve assets as the union of a concrete exercise's voice tokens.
3. Publish its answer descriptor through the existing consumed/hidden gate.
4. Keep current/next PCM identity and immutable per-item gains; compiler/source retains no
   unbounded timeline map. Navigation reuses retained concrete items without new randomness.
5. Ensure generator/compile/asset/render failures all reach the existing session error path.
6. Update media fakes, failing source adapters and diagnostic test adapters alongside production.

## Regression matrix

| ID | Scenario / required result |
| --- | --- |
| M1-T01 | Interval v1/v2 fixtures and random-call counts are unchanged through the adapter. |
| M1-T02 | One note, two sequential notes, simultaneous four-note group and sixteen-note example compile correctly. |
| M1-T03 | Multiple voice tokens have correct gaps and one reveal point; missing middle token fails preflight. |
| M1-T04 | Full recipe at 60 s passes; above cap fails; context, repeats and separator are included. |
| M1-T05 | Hidden/consumed answers remain absent on app/media/accessibility before allowed publication. |
| M1-T06 | Colliding sequence indices across test providers cannot collide in PCM or callbacks. |
| M1-T07 | Previous/Repeat compatibility replays concrete content/gains without generating more items. |
| M1-T08 | Generation, compilation, prepare and output failures clean up; cancellation propagates. |
| M1-T09 | Long fake sessions retain only bounded items and two PCM entries, within 48 MiB. |

Extend the existing generator, compiler, ConfiguredIntervalExerciseSource, SessionController,
PlaybackIdentity, PcmPipeline and media mapping suites rather than duplicating their setup.

## Commands and acceptance

```bash
./gradlew --dependency-verification strict :core:music:test :core:training:test :core:audio:testDebugUnitTest :core:media:testDebugUnitTest :app:testDebugUnitTest
./scripts/check-fast.sh
EAR_NATIVE_EVIDENCE_DIR=.local/verification/mode-expansion/M1/native ./scripts/check-native-tests.sh
EAR_NATIVE_EVIDENCE_DIR=.local/verification/mode-expansion/M1/native EAR_REVIEW_OUT=.local/verification/mode-expansion/M1/device ./scripts/check-full.sh --automated-only
```

Use `EAR_NATIVE_EVIDENCE_DIR=.local/verification/mode-expansion/M1/native` for native evidence
to avoid writing into the old review folder. Record actual test execution, APK hashes and
phone isolation. A missing phone leaves integration acceptance open; it is not a pass.

Exit: all M1-T rows pass, no interval behavior/asset/fixture changes are unexplained, domain
modules remain Android-free, and the generalized interval provider is the production path.
Document the final signatures and temporary compatibility adapters for M2; do not enable
the new musical modes yet.
