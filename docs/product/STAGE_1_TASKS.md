# Stage 1: Interval domain

Status: implemented and verified on the host (2026-10-07). Prerequisite: G0-host (recorded PASS).
WP1.1, WP1.2 and WP1.3 are complete; 81 core JVM tests pass and check-fast passes.
See [STATUS](../development/STATUS.md) for evidence and the fixed defects found while testing.
G0-device and G0-IDE remain separate; they do not block these JVM packages.
Follow [AGENT_WORKFLOW](../development/AGENT_WORKFLOW.md) for inherited rules.

Only WP1.1 → WP1.2 → WP1.3 are in this stage. Reuse Kotlin/JDK and existing JUnit.
No Android, audio, persistence, UI, TTS or new third-party dependency is needed here.

## WP1.1 — Pitch and interval model

**Goal:** represent and validate the exact pitches and labels needed for intervals.
**Scope:** core/music sources and tests; package com.thomas.passiveeartraining.music.
**Interfaces:** MidiNote, PitchClass, SpellingPreference, SimpleInterval,
IntervalPresentation; checked conversions/transposition.

### Requirements

1. MidiNote is an immutable value constrained to 0..127. Conversion uses pitchClass =
   value modulo 12 and scientific octave = floor(value / 12) - 1. C4 = 60, A4 = 69.
   Octave/pitch-class construction validates the resulting MIDI value; octave 9 is
   only partially representable. Checked transposition uses widened arithmetic.
2. PitchClass represents twelve sounding classes, C = 0 through B = 11. Display uses
   C/C-sharp/D/... or C/D-flat/D/... according to spelling preference. Spelling does
   not change sound. Do not yet model key-dependent B-sharp/C-flat spellings.
3. Implement all simple labels with stable string IDs, separate from localized text:

| Semitones | ID / English answer |
| --- | --- |
| 0 | unison / Unison |
| 1 | minor_second / Minor second |
| 2 | major_second / Major second |
| 3 | minor_third / Minor third |
| 4 | major_third / Major third |
| 5 | perfect_fourth / Perfect fourth |
| 6 | tritone / Tritone |
| 7 | perfect_fifth / Perfect fifth |
| 8 | minor_sixth / Minor sixth |
| 9 | major_sixth / Major sixth |
| 10 | minor_seventh / Minor seventh |
| 11 | major_seventh / Major seventh |
| 12 | octave / Octave |

4. Presentations are ASCENDING, DESCENDING, HARMONIC. Ascending uses root + offset;
   descending uses root - offset; harmonic uses root + offset with simultaneous notes.
   The root is the first melodic note or lower harmonic note. Unison has zero offset
   in every presentation. Never infer interval quality solely from display spelling.
5. Collections exposed by public values must be defensively copied; caller mutation
   cannot alter an existing definition. Do not serialize enum ordinal values.

### Tests and acceptance

Exhaust all 128 MIDI values through pitch-class/octave round trips; include 0 and 127,
C4=60, A4=69, B3+1=C4, overflow on both ends and Int extremes.
Assert the complete interval table and directions; sharp/flat display changes preserve
MIDI. Test invalid construction. No Android classes may appear at runtime.

Run :core:music:test through Gradle strict verification, then check-fast.
Done when the table and conversions have independent expected fixtures and the app
still builds. Update STATUS and architecture names if naming differs.

## WP1.2 — Reproducible constrained interval generation

**Gate:** WP1.1 complete.
**Scope:** core/training and tests; essential additions to core/music only.
**Interfaces:** RandomSource.nextInt(bound), SeededRandomSource, IntervalDefinition,
IntervalExercise, GeneratorIdentity, IntervalGenerator; typed validation errors.

### Configuration and normalization

IntervalDefinition contains selected interval IDs, selected root pitch classes,
inclusive minimum/maximum MidiNote and one presentation. Treat user selections as
sets, deduplicate and sort intervals by semitone then ID and roots by numeric class.
Copy input collections. All selections must be nonempty.

GeneratorIdentity records generatorId = "interval", generatorVersion = 1,
rngId = "java-random-48-v1", signed 64-bit seed and the normalized definition.
Use java.util.Random through the injected adapter; do not implement a PRNG.
Java specifies this generator's algorithm for portable seeded sequences
([Random contract](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Random.html)).
This is reproducible sampling, not a security feature. Seeds differing only outside
the generator's effective state can produce the same sequence.

A concrete exercise records identity/reference, sequence index starting at zero,
cycle index, position in cycle, label, presentation, root, target and semantic answer
ID "interval.<label-id>". A fresh generator owns its own random source and bag cursor.

### Feasibility and algorithm version 1

1. Enumerate roots from minimum to maximum in ascending MIDI order. Filter by selected
   pitch classes. Compute target with checked interval/direction arithmetic.
2. Keep pairs only when both pitches are in range. Group by interval in canonical
   order, with pairs sorted by root then target.
3. Reject a definition if any selected label has no feasible pair. Return all invalid
   labels and the range; do not silently drop one or use retry-until-valid generation.
4. At each bag start copy canonical labels. Fisher–Yates from last index down to 1:
   draw nextInt(i + 1) and swap. This small bag policy calls the standard PRNG.
5. If there are at least two labels and bag[0] equals the previous bag's last label,
   swap bag[0] with the first later unequal label. No additional random draw.
6. For each selected label draw nextInt(feasiblePairCount), including when count=1.
   Consume no other randomness. Return the concrete exercise and increment its index.
7. One label may repeat. Multiple labels occur exactly once per bag. The boundary
   correction imposes this rule; do not claim uniform distribution over every possible
   bag permutation. Root pairs are sampled uniformly conditional on the label.

Default fixture: C4..C5, all roots, minor/major thirds and perfect fifth, ascending.
A fixed absolute root is a later optional constraint; selecting one pitch class is
not the same thing as selecting a fixed note across multiple octaves.

### Tests and acceptance

- All presentations and unison; MIDI boundaries and impossible ranges.
- Empty selections, mutable caller collections, reordered/duplicate selections.
- One-label bag, every label exactly once per cycle, anti-repeat across boundaries.
- Pair ordering and required random draw count using a scripted RandomSource.
- Same seed/normalized definition/version reproduces several bags in a fresh instance.
- Different input collection orders reproduce identical output.
- Fixed golden sequence for a recorded seed and concrete normalized definition; review
  expected pairs and random call order before accepting it.
- Seeded 10,000-item invariant run: legal label, correct offset, valid roots/targets,
  bag coverage, boundary rule; include failures that finish without looping.

Do not require every distinct seed to produce a distinct sequence.
Run :core:training:test and check-fast. Done when the golden sequence fixes the v1
contract. Future changes that alter output require a new generator version and fixtures.

## WP1.3 — Recognition recipe and timeline compiler

**Gate:** WP1.2 complete.
**Scope:** core/training and its test resources.
**Interfaces:** VoiceAssetId, VoiceDurationCatalog, RecognitionRecipe, Timeline,
NoteEvent, VoiceEvent, MarkerEvent, TimelineCompiler, MonotonicClock.

The recipe contains note duration, melodic gap, thinking silence and post-answer
silence. Presentation belongs to the exercise; do not create a contradictory second
presentation field. Use Kotlin Duration at this boundary.

### Compilation contract

1. Recognition order: example → thinking silence → semantic answer → final silence.
2. Melodic notes: root starts at zero; target starts at noteDuration + melodicGap.
   Example ends at 2 × noteDuration + melodicGap.
3. Harmonic notes begin at zero and end at noteDuration. Sort simultaneous notes
   by MIDI value. Harmonic unison produces one audible note, not two copies.
4. Answer starts at exampleEnd + thinkingSilence. Its positive duration comes from
   the injected voice catalog. Emit AnswerStarted and ExerciseEnded markers.
   End = answerStart + voiceDuration + postAnswerSilence.
5. Each audible event has startOffset, duration and semantic payload; silence is a
   gap, not a sleeping operation. Intervals are half-open [start, end).
6. Stable ordering: ascending startOffset; notes first (MIDI ascending), then voice,
   then AnswerStarted, then ExerciseEnded. At equal boundaries consumers process
   ending audible windows before starting new ones. Only the end marker has no
   following duration. Consumers must not infer completion from a scheduled end
   that has not yet been consumed.
7. Reject nonfinite durations, nonpositive audible durations, negative gaps, missing
   answer IDs, overflow and total duration over 60 s. Validate before adding durations;
   Kotlin Duration saturation must not turn an overflow into accepted infinity.
   Stage 1 accepts finite whole-microsecond durations; reject finer precision.
8. The compiler is pure. MonotonicClock is only an interface for later session work,
   with a test fake that refuses backward movement; this compiler never reads it.
9. VoiceDurationCatalog represents measured frame lengths converted to the domain's
   microsecond precision. Stage 2 computes exact output frames from manifest frame
   counts and a documented rounding rule. Do not trust hand-entered milliseconds.

### Worked fixture

Ascending C4 → G4, 700 ms notes, 100 ms gap, 4 s thinking, 900 ms fixture voice,
2 s post-answer:

| Offset | Event |
| --- | --- |
| 0 ms | Note C4, duration 700 ms |
| 800 ms | Note G4, duration 700 ms |
| 5500 ms | Voice interval.perfect_fifth, duration 900 ms; AnswerStarted |
| 8400 ms | ExerciseEnded |

Harmonic C4/G4 uses two notes at 0 ms, answer at 4700 ms and end at 7600 ms.
These voice lengths are synthetic test inputs, not production asset measurements.

### Tests and acceptance

Test exact offsets/end time for all presentations, unison deduplication, zero gaps,
zero thinking, missing/invalid voice duration, simultaneous ordering, 60-second
boundary and overflow. Check identical input/output, immutable lists and valid
nonoverlap of voice/music windows. Store readable fixtures under
core/training/src/test/resources/timelines.

Run :core:training:test and check-fast. G1 closes when all three packages pass,
core remains Android-free and the app still builds. No scheduler, UI or playback is
implemented in Stage 1. WP2.1 followed this original G1 milestone; current remaining
work is listed in STATUS/ROADMAP.
