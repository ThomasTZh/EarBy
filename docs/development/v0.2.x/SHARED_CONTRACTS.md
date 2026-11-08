# Shared v0.2.x implementation contracts

Status: specification to implement. Read with [decisions](DECISIONS.md) and the selected
[milestone](README.md). Names below are proposed unless identified as existing.

## SC1 — Module and dependency boundaries

`core/music` owns pitch spelling, keys, chord/scale tables and pure musical transformations.
`core/training` owns provider configuration, generation, validation, recipes, session state
and repository ports. Neither imports Android, Compose, database types or asset paths.
`core/audio` owns PCM/native/output mechanics; `core/media` owns media/focus/power integration;
`core/data` owns DTOs/storage/migration; `app` owns composition and UI. Use the existing six
modules. Do not create feature modules, a plugin framework or a second player.

Keep the current Android/Gradle/Kotlin/native dependencies. The one planned new tool is
isolated, development-only music21. No runtime Python/TTS/networking or INTERNET permission.

## SC2 — Identifiers, configurations and immutable generated data

| TrainingKind stable ID | Existing selection alias | Provider version |
| --- | --- | --- |
| `intervals` | `INTERVALS` | Existing interval v1/v2 |
| `chords` | `CHORDS` | 1 |
| `scales` | `SCALES` | 1 |
| `note_identification` | `PERFECT_PITCH` | 1 |
| `scale_degrees` | `SCALE_DEGREES` | 1 |
| `chord_progressions` | `CHORD_PROGRESSIONS` | 1 |
| `intervals_in_context` | `INTERVALS_IN_CONTEXT` | 1 |
| `melodic_dictation` | `MELODIC_DICTATION` | 1 |

Do not serialize enum ordinals, class names or display labels. Maintain explicit mapping
functions; reject unknown IDs/versions without destructive fallback. Presentation order
in the drawer stays as currently shown, independent of dependency/implementation order.

Required domain shapes:

- `ModeSetup(provider: ProviderConfig, recipe: RecipeDefinition, seed: Long?, stopPolicy)`.
  A config's sealed subtype fixes its kind; duplicate wire kind fields must agree.
- `ProviderConfig.Intervals` wraps the existing `SequencePolicy`; each subsequent provider
  adds its own concrete subtype/version as it obtains a real consumer.
- `ProviderValidation.Valid` carries normalized configuration, available answer categories,
  exact or conservative reachable duration bound and required assets. `Invalid` carries
  multiple typed field issues. Never represent an invalid UI draft as a valid config.
- `ExerciseProvider` exposes defaults, validation, required vocabulary and generator creation.
  `ProviderRegistry` explicitly registers implemented providers. Known-but-unimplemented
  kinds can be selected by the shell but cannot construct a Start request.
- `ConcreteExercise` carries kind, generator provenance, category ID, immutable context and
  target note groups, answer descriptor and optional real cycle metadata.
- `NoteGroup` carries sorted unique sounding notes, relative start, duration and role
  `CONTEXT` or `TARGET`. Equal sounding pitches in one group are one voice. Group order
  encodes a phrase; it does not infer timing from a two-note interval enum.
- `AnswerDescriptor` carries structured display data and ordered semantic voice IDs. Keep
  pitch spelling available for projection; do not persist a string frozen to a UI language.
- `CompiledExercise` carries the timeline, answer-start offset, mandatory-unit-end offset,
  each optional replay's start/end and trailing silence/cue boundaries.

Defensively copy input lists/maps. Do not expose mutable provider state, RNGs or answers
through media metadata. A retained controller item owns its complete compiled exercise and
immutable render settings. Generalizing contracts must not alter interval v1/v2 fixtures.

## SC3 — Randomness and candidate enumeration

Keep `java-random-48-v1` and the existing `SeededRandomSource`. Freeze the original interval
draw order. New label-based providers use stable ID ordering, Fisher–Yates shuffle with
`nextInt(i + 1)` for i from last down to 1, then exactly one `nextInt(candidateCount)`
for a canonical feasible realization. Repetition across bag boundaries is allowed; this
track does not add anti-repeat or weighted-cycle policies.

Contextual providers refine candidate selection as specified in M8: label shuffle, context
choice and target choice use separate derived streams and documented finite eligible sets.
M10 specifies graph sampling instead of a label bag. Freeze those provider-specific draw rules.

Record provider ID/version, RNG ID, effective seed and normalized typed config in provenance.
Generation order is independent of hash-map iteration, localized names and system time.
Every enabled answer category/presentation must have a feasible realization. Root sets
filter the candidate set; enumerate feasible roots rather than retrying random pitches.
Empty root/key selection means all roots/keys where specified by the provider UI. Empty
answer-category selection is invalid in new providers. Preserve legacy interval normalization.

Switch/child stream derivation version `mode-seed-v1`: hash a UTF-8 sequence consisting of
`mode-seed-v1`, decimal parent seed, stable kind ID, decimal activation ordinal and stream
label, separated by LF with a final LF. Interpret the first eight SHA-256 bytes as a signed
big-endian Long. Freeze test vectors. Labels are `activation`, `labels`, `context`, `target`;
derive children from the recorded effective activation seed with ordinal 0. Never use hashCode.

A Start with a fixed seed reproduces its first sequence. A Start without a seed draws and
records one seed. Switch activations use destination fixed seed or the derivation above.
Only committed switches increment activation ordinal; replaced pending requests do not.
Same-mode recipe/gain edits retain generator position; sequence edits preserve the stream
when the seed is unchanged, as existing interval reconfiguration does.

## SC4 — Musical timing and answers

| Setting | Default / bound |
| --- | --- |
| Tuning / instrument | Existing piano, 12-TET, A4=440 |
| Learning recipe | Recognition |
| Note duration | 700 ms; existing 100–4000 ms control bounds |
| Sequential-note gap | 100 ms; 0–30000 ms |
| Thinking / final silence | 2000 ms each; 0–30000 ms |
| Answer-to-example / replay gaps | 600 / 400 ms; 0–30000 ms |
| Context-to-target gap | 500 ms; 0–30000 ms |
| Additional example replays | 0 initially; 0–8 |
| Speech token gap | Exactly 80 ms between clips, in addition to recorded WAV padding |
| Single voice clip | Positive and at most 8 s |
| Complete exercise | At most 60 s including every context, repeat, cue and gap |
| Session / timer | Until stopped; timer off unless selected |

The compiler places context/target groups and speech on one timeline; it never schedules
music with coroutine delays. Notes end before new notes start at the same frame. Preserve
absolute-offset half-up frame conversion at 44.1/48 kHz. A composed answer contains one
reveal marker at its first token, not a new reveal per word.

The mandatory learning unit is Example–Thinking–Answer for Recognition, Answer–Gap–Example
for Exposure, and Example–Thinking–Answer–Gap–Example for Association. Every Example of a
contextual provider includes its complete context and target. Optional replays follow the
mandatory unit. Existing final silence/cue order remains unchanged when no switch occurs.

Use measured voice frames in both validation and compilation. For finite candidates take
the maximum reachable complete duration. For melody use dynamic programming over reachable
paths with answer-duration costs. A conservative bound may reject a setup only if it is
documented; never accept from a mean duration or a sample of random exercises. Arithmetic
uses checked/wide integers; 60 s exactly is valid, one scheduling unit above is invalid.

## SC5 — Visibility and naming

The controller is the answer-visibility authority. No answer-bearing payload reaches normal
snapshots, metadata, semantics or content descriptions before consumption or while HIDDEN.
Switch and Stop clear the old answer. Toggling HIDDEN removes an already-published answer
immediately while allowing speech to continue.

Global Scientific/Solfège naming is distinct from sharp/flat spelling and movable-do
functional answers. Absolute C is C or Do under the global preference, independent of key.
Degree syllables depend on the key: major do/re/mi/fa/sol/la/ti; natural minor
do/re/me/fa/sol/le/te. Keep semantic namespaces distinct even if clips share pronunciation.
No la-based minor or additional fixed-do functional training option is introduced.

`SpelledPitch(letter, accidental -2..2, writtenOctave)` converts by
`12 * (writtenOctave + 1) + naturalLetterSemitone + accidental`, with MIDI bounds checked
after conversion. Never derive a written octave from an enharmonically respelled MIDI value.
Test B#3=C4 and Cb4=B3. Key-aware notes follow theoretical letters, including double
accidentals where required. Absolute-note answers use the chosen sharp/flat chromatic table.

## SC6 — Selection, drafts and live service state

Saved selected kind, audible active kind and pending switch kind are separate fields.
Persist selected kind/configuration, never pending playback/queue state. On process recreation
restore stopped; no automatic resume. Play renders active/pending state from the service.
Setup and Presets edit the selected destination.

Every mode's saved config has a monotonically increasing revision. Draft actions capture
kind, base revision and local edit generation. A delayed save cannot target a different kind
or overwrite a newer preset application. Keep invalid drafts in saveable UI state with errors;
only validated config enters the repository/service. Mode selection persists valid outgoing
edits and the new selection in one settings update; write failure leaves the selection as-is.

Global gain edits affect newly drawn items, preserving current item gains. Answer hiding is
immediate. Updating an inactive mode does not reconfigure active playback. Unrelated theme
changes do not redraw queues. A preset application updates destination config and existing
global gain values atomically, then uses the same revision/command path as normal setup edits.

## SC7 — Storage and interoperability

Settings schema 2 retains existing global fields and adds explicit selected kind and per-kind
setup/revision entries. Migrate schema 1 exactly into intervals; missing new kinds get defaults
when their provider is available. Decode known but absent modes without pretending they have
a playable setup. Future schema/config versions are diagnosed and protected from writes.

Preset document schema 2 stores kind, config version and typed payload; legacy schema 1 is
read as intervals. Keep signed 64-bit seeds as decimal strings. Preserve existing 1 MiB,
100-document, 80-character name, 64-character ID, nesting and string limits. Keep duplicate
key validation. No import/export UI is restored.

SQLite 9 migrates using shared host/device code and one transaction. Do not rewrite migrations
1–8. Preserve every legacy row, slot identity, name, dates, gains and adoption marker/state.
Schema 9 has a generic payload table with unique (kind, slot_number), nullable slot number for
built-ins/legacy customs, and explicit empty/built-in flags. Empty slots may have null payload;
filled slots may not. M2 defines transformation and rollback tests in detail.

## SC8 — Resource/failure ownership

Retain one AudioTrack writer, the existing focus/route policy and service-owned wake lock.
Exactly two PCM entries and at most 48 MiB; discarded futures release their PCM promptly.
At most 20 backward items internally; mode switches keep identity ordinals monotonic.
Full compiled history may be re-rendered; never retain all generated timelines globally.

Reject user configuration before save/Start/switch. On generation/asset/compile/render/output
failure invalidate pending work, attempt all cleanup, clear stale answer and publish Error.
Preserve CancellationException. Persistence failures must not be reported as success or
start a session from an unsaved optimistic setup. Never recover by changing the musical answer.

## SC9 — Tests, evidence and source of truth

Each code milestone runs targeted tests, `scripts/check-fast.sh`, relevant assets/native checks
and scoped isolated-phone tests. At migration/audio integration gates also run the canonical
full workflow. M11 runs all providers and real endurance. Direct Gradle commands always use
`--dependency-verification strict`. Do not rewrite dependency checksums during verification.

Use `.local/verification/mode-expansion/Mn/` for raw evidence and update the implementation
ledger with exact input hashes, commands, exit codes and limitations. Label any new command
in a milestone as planned until implemented. New fixture generation is explicit; ordinary
checks compare against existing approved/frozen inputs. Preserve screenshot approval guards.

Keep human approval separate. Changes to the drawer/new screens may be pending visual review,
but missing/malformed images and unexpected comparator failures remain automated failures.
No blanket ignores, empty test filters or claiming cached results as newly executed tests.
