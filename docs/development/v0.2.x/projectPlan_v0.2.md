# Fill all seven training-mode placeholders

## 1. Outcome and product decisions

Implement Chords, Scales, Note identification, Scale degrees, Chord progressions, Intervals in context and Melodic dictation through the existing offline playback service.

Each completed mode must have working Play, Setup and Presets screens; deterministic exercise generation; bundled spoken answers; saved settings; and automated host and phone coverage.

The implementation follows these decisions:

- Preserve the current Start/Stop interface, three learning recipes, sleep timer and swipe-away stopping behavior.
- Keep Recognition, Exposure and Association available in every training mode. Distinguish the **training mode** from the **learning recipe** in domain types and UI labels.
- Give every training mode its own saved setup and **five editable preset slots**. Preserve existing interval slots, names and contents.
- Make Melodic dictation passive: hear a melody, think, then hear and optionally see the answer.
- Follow the existing roadmap’s name **Note identification** for the “Perfect pitch” placeholder. Preserve compatibility with the stored `PERFECT_PITCH` selection.
- Keep Previous/Repeat absent from app controls. Preserve existing internal and media-command compatibility without adding navigation features.
- Hidden answers remain absent from app text, accessibility and media metadata. Spoken answers remain enabled.
- Continue using the existing piano, voice-generation tools, modules and dependency versions.

This feature track includes the provider work from Stage 5 and the progression/context/melody work from Stage 6. Weighting, contrast exercises, interval walks, intonation, mixed sessions, clusters, statistics and adaptive learning remain deferred.

Approval to implement this plan authorizes these seven modes despite the older roadmap’s blanket Stage 5 restriction. Record that narrow authorization when implementation starts. Existing G3/G4 human acceptance gaps remain open.

### Mode content and starting configurations

| Mode | Implemented content and controls | First-use setup |
|---|---|---|
| **Chords** | Eleven qualities: major, minor, diminished, augmented, sus2, sus4, major seventh, minor seventh, dominant seventh, half-diminished seventh and diminished seventh. Closed-position inversions; harmonic or ascending arpeggiated presentation. Answers name quality, or quality plus inversion. | Major/minor, root position, harmonic, all roots, C3–C6, quality answer. |
| **Scales** | The thirteen unique formulas already specified in WP5.3: major, natural minor, harmonic minor, jazz melodic minor, Dorian, Phrygian, Lydian, Mixolydian, Locrian, major/minor pentatonic, minor blues and whole tone. Ascending, descending or both; complete octave or contiguous 2–8-note fragments. | Major/natural minor, ascending full octave, all roots, C3–C6. |
| **Note identification** | Select pitch classes, range, sharp/flat naming and whether speech includes the octave. Scientific note names or the existing solfège display preference. | All pitch classes, C4–C5, sharp naming, octave omitted from speech. |
| **Scale degrees** | Major/natural-minor keys; degrees 1–7; context from tonic, tonic triad, ascending scale or the documented cadences. Answers: degree number, note name, movable-do syllable, or number plus syllable. | C major, degrees 1–7, tonic-triad context, C4–C5, degree-number answer. |
| **Chord progressions** | Major I–IV–V–I, I–vi–IV–V and ii–V–I; minor i–iv–V–i. Root-position closed triads or nearest allowed inversion. Answers: Roman-numeral sequence, directed root-motion sequence, or harmonic functions. | C major, the three major templates, root position, C3–C6, Roman-numeral answer. |
| **Intervals in context** | A tonal context followed by two target scale degrees. Major/natural minor, selected interval labels and ascending/descending/harmonic presentation. Answers: interval name, degree pair, or both. | C major, tonic-triad context, minor/major thirds and perfect fifth, ascending, C4–C5, interval answer. |
| **Melodic dictation** | Major/natural-minor phrases of 2–16 equal-length notes; optional starting/ending degrees; allowed directed intervals; maximum leap 0–12 semitones; direction bias. Answers: degree sequence, movable-do sequence or directed interval sequence. | C major, four notes, tonic start and finish, maximum leap five semitones, balanced direction, C4–C5, degree-sequence answer. |

New modes initially use Recognition, 700 ms notes, 100 ms between sequential notes, 2 s thinking time, 2 s final silence, no extra repetitions and a 500 ms context-to-target gap where applicable. Existing interval settings remain unchanged.

Every selected configuration must fit the complete 60-second exercise limit, including context, speech, token gaps, repetitions and separator cues.

## 2. Shared contracts and behavior

### Replace interval assumptions with typed exercise contracts

Keep the existing six-module structure. Introduce the following contracts in the existing pure Kotlin modules:

| Contract | Responsibility |
|---|---|
| `TrainingKind` | Stable identifiers for all eight training modes, with explicit mappings from existing persisted selections. |
| `ProviderConfig` | A sealed set of typed mode configurations. The interval variant wraps the accepted `SequencePolicy`. |
| `ModeSetup` | Provider configuration, learning recipe, timing, seed and stop policy for one mode. |
| `ExerciseProvider` | Validate configuration, declare required voice IDs, construct a seeded generator and produce concrete exercises. |
| `ConcreteExercise` | Immutable context/example note groups, answer description and generation provenance. |
| `AnswerDescriptor` | Semantic answer category, display representation and ordered voice-token IDs. |
| `CompiledExercise` | Timeline, answer-reveal position and safe mode-switch boundaries. |
| `ProviderRegistry` | Explicit registration of the eight supported providers; no reflection or runtime plugin loading. |

Generalize `StartRequest`, `ExerciseSource`, `AudioPort.prepare`, session items and answer publication to consume these contracts.

The interval provider must adapt the existing v1/v2 generators. Preserve their random draw order, concrete outputs, identities and golden fixtures. Do not rewrite their algorithms as part of generalization.

Preserve `PlaybackIdentity(sessionGeneration, itemOrdinal)`. Generator sequence numbers remain reproducibility metadata. Mode switches must not reset the item ordinal or create cache collisions.

Every retained item captures its concrete notes, answer, recipe, gains, provenance and compiled timeline. Preserve the current bounds: twenty backward items internally, bounded future items, two PCM entries and 48 MiB of PCM.

### Compile musical examples and spoken sequences consistently

Represent a musical example as immutable note groups with relative timing. A group may contain one note or simultaneous chord notes. Contextual modes include their context and target in each example presentation, including Association and optional replays.

Use one compiler for:

- Recognition: example → thinking → answer.
- Exposure: answer → answer gap → example.
- Association: example → thinking → answer → answer gap → example.
- Optional additional examples and the existing final-silence/separator behavior.

Composed speech uses semantic voice IDs with the roadmap’s explicit **80 ms inter-token gap**. Reveal the complete textual answer when the first answer token is consumed, subject to the global visibility preference.

Use the same measured voice-duration catalog and duration calculation for setup validation, presets, compilation and playback. For constrained melodies, calculate a safe maximum over reachable valid phrases; random sampling is insufficient to prove the duration cap.

### Switch modes at a safe learning boundary

Add an explicit `SwitchMode` command, distinct from same-mode `Reconfigure`.

While playing:

1. Load and validate the destination mode’s latest saved setup.
2. Record it as the pending destination and discard obsolete queued future exercises.
3. Finish the current learning sequence:
   - Recognition finishes example and spoken answer.
   - Exposure finishes spoken answer and example.
   - Association finishes example → answer → example.
4. If an additional replay is already sounding, finish that replay. Skip additional replays that have not started.
5. Skip remaining final silence and transition to the destination setup.

Treat a chord, scale, progression or melody as one complete example. Never switch between its individual notes or spoken answer tokens.

Extend the audio writer with bounded segment playback. It may buffer within a learning sequence but must not submit sound beyond a safe switching boundary until the controller authorizes the next segment. A UI delay or cancellation flag alone cannot provide this guarantee because AudioTrack already buffers ahead.

Additional rules:

- Rapid selections use the latest valid destination. Selecting the active mode again cancels its pending departure.
- Destination edits made before the switch replace the pending setup.
- A switch before any sound starts may replace preparation immediately.
- Stop, focus loss, route loss and immediate timer expiry take precedence and cancel the pending switch.
- The session’s elapsed sleep deadline continues across mode changes.
- Invalid destination settings leave current playback unchanged and produce visible field feedback.
- Missing assets or render/output failures use the existing observable error and resource-cleanup path.
- While a switch is pending, reject conflicting internal navigation commands; Stop remains available.
- A stopped session changes its selected mode without starting playback.

Publish `activeKind`, `pendingKind` and configuration revision in the session snapshot. Setup and Presets follow the selected destination; Play clearly indicates when the previous mode is finishing. Answer text and media metadata always describe the audible item.

Retain existing seed behavior within a mode. A fresh mode activation uses its fixed seed when configured; otherwise derive a recorded seed from the session seed, stable mode ID and activation ordinal using a documented, versioned SHA-256 encoding. Do not consume extra draws from the legacy interval RNG.

### Persist mode-specific setups and slots safely

Upgrade settings to schema 2:

- Store selected mode and a map of saved mode setups.
- Migrate existing `lastSetup` into the interval entry exactly.
- Keep theme, display naming, answer visibility and music/voice gains global.
- Keep the running sleep deadline transient and service-owned.
- Initialize a new mode from its documented defaults when no saved setup exists.
- Preserve unreadable/future-schema handling; never overwrite unsupported data with defaults.

Upgrade the preset document format to schema 2 with explicit training kind, configuration version and typed payload. Continue reading v1 documents as interval presets.

Add SQLite migration 9 after the existing frozen migrations. Introduce generic mode-aware preset storage and migrate legacy rows transactionally. Preserve IDs, names, timestamps, gains, empty-slot state, built-ins and legacy custom rows. Preserve existing adoption behavior.

Use five stable slots per mode: forty total. Existing interval slot IDs remain unchanged. New slot IDs are fixed constants, and `(kind, slotNumber)` is unique. Seed one immutable starter matching each new mode’s default setup; retain the existing interval starters.

All migrations run through the same transformation logic on host SQLite and Android SQLite. A conversion failure rolls the transaction back.

## 3. Sequential implementation packages

Each package includes its code, regression coverage, documentation update and exact verification result before the next begins.

### M0 — Establish the feature baseline and handoff documents

- Create `docs/product/MODE_EXPANSION_PLAN.md` from this specification and `docs/development/MODE_EXPANSION_STATUS.md` as the execution ledger.
- Record source, fixture, asset and archived-APK hashes before edits.
- Reconcile README, STATUS and roadmap authorization/order with this feature track; retain historical acceptance records.
- Recheck host tools, connected-phone readiness and Android Studio MCP import state.
- Run the current fast checks once as the implementation baseline. The cleaned workspace will regenerate its build outputs normally.

**Exit:** a reproducible baseline, frozen interval fixtures and explicit package tracking.

### M1 — Generalize the exercise pipeline using intervals first

- Introduce the shared contracts and registry with the interval adapter as the first consumer.
- Replace interval-specific assumptions in the controller, renderer adapter, duration validation and answer publication.
- Keep the native renderer and single AudioTrack ownership model.
- Add support for variable-length examples, simultaneous note groups and multi-token answers.

**Verification:** interval v1/v2 golden sequences unchanged; interval timelines and gains unchanged; generic fake exercises exercise multiple notes/tokens, missing assets, duration boundaries and cache identity.

**Exit:** the existing interval app works through the generalized pipeline.

### M2 — Add mode storage and scoped UI state

- Implement settings schema 2, preset document schema 2 and SQLite migration 9.
- Key Setup and Presets ViewModels by training kind.
- Keep unsaved drafts separate by mode. A delayed autosave must carry its original mode and revision so it cannot overwrite a subsequently selected mode or newly applied preset.
- Preserve invalid drafts for correction without saving or playing them.
- Save valid pending edits before applying a mode change; report failed writes without falsely showing success.
- Make preset listing, save, rename, clear and apply operate within the selected mode.

**Verification:** old schemas and legacy presets, forty distinct slots, migration rollback, future schemas, failed writes, rapid mode changes, delayed autosaves, preset adoption and process recreation.

**Exit:** switching between mode screens preserves independent configuration and preset state, even before additional providers become playable.

### M3 — Implement safe mode switching

- Add compiled switching boundaries and segment-limited PCM submission.
- Implement active/pending mode state and the command rules in Section 2.
- Carry configuration revisions through settings observation and transport acknowledgements.
- Preserve Stop responsiveness, timer behavior, output ownership and cleanup during preparation or switching.

**Verification:** virtual-time/controller tests and fake-output tests for switches during context, example, thinking, every answer token, Association’s final example, extra repeats and silence. Test rapid A→B→C, cancellation back to A, late render callbacks, Stop/Start, timer expiry and output failures.

**Exit:** switching satisfies the consumed-audio boundary contract without queued old-mode sound leaking into the next mode.

### M4 — Establish theory fixtures and expandable voice assets

- Add key-aware `SpelledPitch`, `Key`, scale-degree and chord-theory values. Keep sounding MIDI pitch distinct from spelling, theoretical root and bass.
- Freeze the chord/scale formulas already specified in WP5.2/WP5.3.
- Add an isolated Python 3.11 fixture project pinned to **music21 10.5.0**, with its own `uv.lock`. The published package supports Python 3.11+ and uses BSD-3-Clause. Use it only for development fixtures. [Package metadata](https://pypi.org/project/music21/)
- Generate compact fixtures independently of Kotlin and compare them with the documented formulas. Explicitly test jazz melodic minor’s identical ascending/descending pitch collection.
- Add `scripts/check-music-fixtures.sh`; verification generates temporary candidates and compares them without rewriting committed fixtures.
- Replace interval-only voice lists with a declarative vocabulary covering chord qualities/inversions, scales, note letters/accidentals/octaves, degrees, solfège, directions and harmonic functions.
- Extend the existing Chatterbox/FFmpeg tools to generate only missing vocabulary. Preserve the thirteen existing interval WAV files and their review records byte-for-byte.
- Record model-file hashes, generation parameters, text, waveform measurements and provenance. Keep new `humanAccepted` values false until actual review.

**Verification:** independent pitch/spelling fixtures, deterministic fixture regeneration, vocabulary completeness, exact frame metadata, waveform bounds and unchanged existing asset hashes.

**Exit:** every later provider has a defined asset vocabulary and the tools can build and validate it without runtime TTS.

### M5 — Complete Chords

- Implement the eleven formulas, finite root/inversion enumeration and both presentations.
- Keep theoretical root distinct from inversion bass.
- Shuffle answer categories, then choose a feasible voicing in canonical order using the seeded RNG.
- Reject selected categories or presentations that cannot fit the range.
- Add mode-specific Setup controls, Play summary, starter and preset round trips.

**Verification:** every quality/inversion in all twelve roots, root-versus-bass answers, simultaneous/arpeggiated scheduling, four-note peak headroom, seeded fixtures and phone playback.

**Exit:** Chords loses its placeholder and completes the full saved-setup-to-spoken-answer workflow.

### M6 — Complete Scales

- Implement the thirteen formulas and direction/fragment controls.
- Treat major/Ionian and natural-minor/Aeolian as aliases without double sampling weight.
- For “both directions,” sound the upper turning tonic once.
- Select fragments from the configured traversal; reject full scales that cannot fit an octave.

**Verification:** all formulas and roots, spelling, alias weighting, descending order, turning point, fragment limits, maximum-length recipes and mode persistence.

**Exit:** Scales works through all three learning recipes and the shared service.

### M7 — Complete Note identification

- Implement pitch-class/range selection and optional spoken octave.
- Map the legacy `PERFECT_PITCH` selection to the new display name and provider ID.
- Use explicit sharp/flat spelling; ensure displayed and spoken note names agree.
- Cover MIDI 0–127 only where the complete required answer vocabulary exists.
- Shuffle selected pitch classes before choosing a feasible absolute note.

**Verification:** C4/A4, range endpoints, enharmonic naming, octave speech, complete class coverage, hidden answers and migration of the existing selection.

**Exit:** the former Perfect pitch placeholder becomes a usable note-naming mode.

### M8 — Complete Scale degrees and reusable tonal context

- Implement major/natural-minor keys and all documented context choices.
- Minor cadences use a major dominant with raised leading tone, while natural-minor target degrees retain their own scale definition.
- Present context on every example; do not assume that a prior item established the key.
- Implement degree, note-name and movable-do answers. Use major do–re–mi–fa–sol–la–ti and natural-minor do–re–me–fa–sol–le–te.
- Derive independent context and target RNG streams with the documented stable seed rule.

**Verification:** all keys/degrees, minor dominant, target/context range feasibility, spelling, syllable vocabulary, repeated contexts and complete duration validation.

**Exit:** Scale degrees works and supplies reusable context generation for later modes.

### M9 — Complete Chord progressions and Intervals in context

Implement these as two sequential vertical slices.

For progressions:

- Encode Roman numerals, chord quality and harmonic function as musical data.
- Implement the four templates and all three answer formats.
- For nearest voicing, minimize total movement between sorted voices over finite allowed inversions. Break ties by canonical MIDI tuple.
- Validate every chord and spoken sequence before Start.

For contextual intervals:

- Generate context followed by two valid target degrees.
- Filter finite degree/pitch pairs by selected interval and presentation.
- Keep context pitches separate from target pitches and answer categories.
- Implement interval, degree-pair and combined answers.

**Verification:** progression chord tones/order, minor dominant, voice-leading ties, context/target separation, all answer formats, seeded fixtures and switching between both modes.

**Exit:** both placeholders are fully usable through the same service.

### M10 — Complete passive Melodic dictation

- Build a finite graph of allowed pitches and directed transitions.
- Compute reachability to allowed endings for each remaining phrase length.
- Sample only transitions that can complete a valid phrase; do not use unbounded rejection loops.
- Apply direction bias with integer weights and canonical candidate ordering.
- Use tonic-triad context by default and the shared context controls.
- Implement degree, movable-do and directed-interval answers. Keep equal note lengths for this release.
- Describe the mode as listening, remembering and comparing with the answer; provide no answer-entry or scoring controls.

**Verification:** exact lengths, impossible constraints, narrow ranges, zero-leap repetitions, starting/ending degrees, direction bias, long spoken answers, reproducibility and at least 10,000 valid generated phrases.

**Exit:** all seven placeholders have working production providers.

### M11 — Integrate, review and produce final evidence

- Remove remaining placeholder branches and “Soon” indicators for completed modes.
- Review the generalized service, lifecycle, persistence and audio boundaries through source inspection and usable MCP tools.
- Run the complete automated verification package below.
- Update architecture, roadmap, testing, dependency and feature-status documents together.
- Produce a final change/hash inventory accounting for every modified input.

**Exit:** all eight modes, including intervals, pass the automated feature acceptance matrix; unsupported human acceptance remains explicitly open.

## 4. Verification and acceptance

### Checks throughout implementation

Add failing regressions before behavioral repairs. Use controlled dispatchers and virtual time for controller, switching and timer tests.

After each code package, run targeted checks and `scripts/check-fast.sh`. Use strict dependency verification for every direct Gradle command. Add new fixture, vocabulary and provider checks to the canonical scripts as their consumers arrive.

Each provider must pass a common contract suite covering:

- Valid/invalid setup, impossible ranges and empty-selection semantics.
- Deterministic seeds, complete answer-category coverage and immutable generated items.
- Complete duration immediately below, at and above 60 seconds.
- Recognition, Exposure, Association, maximum repeats and separator cues.
- Missing/corrupt assets and generation/compile/render/output failures.
- Hidden answers before publication and after visibility changes.
- Setup/preset round trips, old/future schemas and failed persistence.
- Two-entry PCM retention, 48 MiB budget and bounded controller history.

### UI and screenshots

Test every mode’s Play, Setup and Presets screens, invalid fields, dialogs, active-mode transitions, rotation and recreation. Include 150% and 200% text sizing and accessibility-semantic checks.

Preserve the nine existing approved screenshots. New-mode screenshots remain candidates until approved.

The drawer will intentionally change. Record that expected visual change explicitly. Keep strict comparison available, with missing baselines and unexpected differences treated as failures. The automated-only integration report may record declared visual changes and new cases as **PENDING approval**, while still requiring rendering, complete candidate sets, layout and semantics checks to pass. It must never report pending candidates as approved goldens.

### Automated phone verification

Use the isolated `com.thomas.passiveeartraining.review` variant and preserve the regular app’s data.

Extend the existing harness to cover:

- Starting and stopping every mode with every learning recipe.
- Mode switches during each relevant playback phase, rapid switching and edits to a pending destination.
- Mode-specific presets, migrations and restore in a stopped state.
- Activity recreation, Home/rebind, task removal and force-stop/relaunch.
- Hidden answers in controller snapshots, UI semantics and media metadata.
- Focus denial/loss/ducking and injected route-policy events.
- Repeated start/stop/error cycles and bounded memory.
- A real 30-minute display-off session using representative progression/melody configurations and an elapsed-time sleep deadline.

Run final `scripts/check-full.sh --automated-only`, including assets, native fixtures, debug/release/review builds, lint, manifest/packaging checks and connected tests.

Capture exact APK/configuration/asset hashes, instrumentation results, logs, screenshots, memory samples and cleanup state. Always stop review playback during harness cleanup.

Automated completion does not establish acoustic quality, real accessory behavior, TalkBack usability, pronunciation approval or visual approval. Report those rows separately.

## 5. Agent execution and handoff rules

Work sequentially through M0–M11. Continue between packages without requesting routine confirmation.

For each package, the execution ledger must record:

- Goal and completed behavior.
- Changed contracts and migration implications.
- Affected files and regression coverage.
- Exact commands, outcomes and evidence locations.
- Remaining limitations and the next package.

Store raw evidence under `.local/verification/mode-expansion/<package>/`. Maintain enough detail that a fresh agent can read the plan and ledger, verify the latest completed package and continue without reconstructing the conversation.

Preserve original interval fixtures, historical review evidence, archived releases, existing approved assets and `planDraft.md`. Do not introduce unrelated dependency upgrades, new runtime modules, commits or publication.

A mode is implementation-complete only when its generator, validation, speech assets, three learning recipes, screens, presets, migrations and automated tests work together. A selectable drawer entry or isolated generator test is insufficient.
