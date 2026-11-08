# Stages 5–8: Expansion and release packages

Status: planned. Begin only after G4. All packages inherit AGENT_WORKFLOW.
Reviewed 2026-10-09: first finish the Stage 3/4 repair and acceptance sequence in ROADMAP.
Use the installed [Studio MCP](../development/ANDROID_STUDIO_MCP.md) for verified semantic
traces/inspections after import readiness. It does not replace musical/audio/physical evidence
or justify a toolchain change. The scope and vocabulary below remain unchanged.
Within each stage, complete the listed packages in order. Common expansion rule:
each new provider needs domain validation, deterministic fixtures, required voice
vocabulary, setup controls, preset schema handling and audio acceptance.

## Stage 5 — Core training

### WP5.1 — Shared exercise provider and independent musical fixtures

**Gate:** G4. **Scope:** core/music/training, tools/music-fixtures, tests.
**Goal:** reuse the interval engine for additional vocabulary without new players.
**Interfaces:** ExerciseProvider, TrainingKind, ConcreteExercise, RequiredVoiceIds,
ProviderConfig; retain a thin interval adapter over the accepted v1/v2 implementations.

A provider validates/normalizes configuration, declares required semantic assets,
creates a seeded generator and returns concrete note groups plus answer tokens.
Recipe compilation consumes that common representation. Keep presentation/context
data in domain values, not provider-specific UI callbacks or Android types.
Provider IDs/config versions persist as explicit fields; unknown kinds fail import.

Add key-aware spelling now: SpelledPitch(letter A..G, accidental -2..2, written octave)
maps to a checked MidiNote; Key carries a spelled tonic and scale kind.
Diatonic degree selects the letter, semitone offset selects the accidental.
Test B-sharp3=C4 and C-flat4=B3 without changing their written octave labels.
Choose tonic spelling from the user's sharp/flat preference; validate generated
spellings rather than replacing them with unrelated enharmonic names.
The simple Stage 1 display API keeps its original behavior.

Use locked music21 in development tooling to check expected pitches/spellings for the
chosen chord/scale vocabulary. Hand-review supported tables, check both tools against
them and commit compact JSON fixtures. A package disagreement is investigated; do not
generate "expected" data using the same Kotlin code under test. The [music21 scale reference](https://music21.org/music21docs/moduleReference/moduleScale.html)
documents directional variants; compare the chosen jazz melodic-minor policy using
its explicit pitch formula, not music21's classical descending default.
No Python enters Android or core JVM tests.

Tests: interval output remains identical through the adapter; provider dispatch rejects
unknown types; required asset union is complete; fixtures regenerate reproducibly with
recorded version. Add check-music-fixtures. Run check-fast and audio regressions.

### WP5.2 — Chords and inversions

**Gate:** WP5.1. **Scope:** music/training, relevant asset vocabulary, app setup,
data schema adapters/tests. **Interfaces:** ChordQuality, ChordInversion,
ChordPresentation, ChordDefinition and provider.

| Quality ID | Root-relative semitones |
| --- | --- |
| major / minor | 0,4,7 / 0,3,7 |
| diminished / augmented | 0,3,6 / 0,4,8 |
| sus2 / sus4 | 0,2,7 / 0,5,7 |
| major_seventh / minor_seventh | 0,4,7,11 / 0,3,7,10 |
| dominant_seventh | 0,4,7,10 |
| half_diminished / diminished_seventh | 0,3,6,10 / 0,3,6,9 |

Initial voicings are closed position. Inversion k raises the lowest k chord members
by an octave then sorts; require all sounding notes in range and preserve theoretical
root separately from bass. Harmonic and ascending arpeggiated playback share the
timeline. Do not use modulo-12 equality to discard octave placement.
Answer mode is quality alone or quality plus inversion; vocabulary must cover both.

Enumerate finite feasible root/quality/inversion combinations before generation.
Shuffle selected answer categories once per bag and sample feasible voicings uniformly.
Reject impossible selected categories. Root-position unisons/duplicates are not invented.
Record provider version/seed and canonical ordering.

Test every formula/inversion against independent fixtures in all twelve roots,
range boundaries, root-vs-bass answers, arpeggio offsets and simultaneity.
Validate new voice clips and peak headroom for four-note playback.
Run music/training/data tests, asset/native/audio/UI checks and check-full.
Done: a saved chord setup plays through the existing service offline.

### WP5.3 — Scales and modes

**Gate:** WP5.2. **Scope:** same provider integration paths.
**Interfaces:** ScaleKind, ScaleDirection, ScaleDefinition, scale provider.

Canonical octave offsets:

| Scale | Semitones, excluding repeated octave |
| --- | --- |
| Major / Ionian | 0,2,4,5,7,9,11 |
| Natural minor / Aeolian | 0,2,3,5,7,8,10 |
| Harmonic minor | 0,2,3,5,7,8,11 |
| Jazz melodic minor | 0,2,3,5,7,9,11 |
| Dorian | 0,2,3,5,7,9,10 |
| Phrygian | 0,1,3,5,7,8,10 |
| Lydian | 0,2,4,6,7,9,11 |
| Mixolydian | 0,2,4,5,7,9,10 |
| Locrian | 0,1,3,5,6,8,10 |
| Major / minor pentatonic | 0,2,4,7,9 / 0,3,5,7,10 |
| Minor blues | 0,3,5,6,7,10 |
| Whole tone | 0,2,4,6,8,10 |

Treat major/Ionian and natural-minor/Aeolian as aliases to one sounding definition;
do not double their sampling weight because both display names exist.
Use jazz melodic minor ascending/descending consistently. Label this choice; classical
melodic-minor descent is a later explicit option, not an implicit exception.

Play one octave ascending, descending or both; both includes the top tonic once.
Fragments choose a contiguous 2..8-note segment from the configured traversal; retain
scale ID for the answer. Validate every note, selected root and total timeline duration.
A full-scale configuration requires an octave of space; do not truncate silently.

Tests: formulas, aliases, all keys, descending order, both turning point, fragment
bounds, spelling and recipe duration. Check new voice pack, cached rendering bounds
and seeded sequence. Run common provider checks and saved-preset round trips.

### WP5.4 — Note identification

**Gate:** WP5.3. **Scope:** music/training provider, voice tokens, setup/tests.
**Interfaces:** NoteIdentificationDefinition, AnswerNaming and provider.

Select pitch classes, inclusive octave/MIDI range, sharp/flat spelling and whether
the spoken octave is included. Name feature "Note identification"; do not promise
acquired perfect pitch. Recipes: note → answer, answer → note, answer → note → answer.

Build answers from approved note-name/accidental/octave tokens or reviewed complete
phrases; list required IDs before starting. Speech concatenation inserts a defined
80 ms inter-token gap, included in timeline/60-second validation.
Restrict selectable spoken octave numbers to the bundled vocabulary; extend it if
the full MIDI range is offered. Display spelling and answer spelling agree.

Shuffle pitch-class labels then choose a feasible MIDI note. Fixed octave vs all
octaves is explicit. Test C4/A4/extreme MIDI values, accidental equivalence, token
timing, class coverage, replay and asset completeness.
Run shared checks including pronunciation review and import round trips.

### WP5.5 — Functional scale-degree training

**Gate:** WP5.4. **Scope:** music/training context/answer model, assets/setup/tests.
**Interfaces:** TonalContext, Key, ScaleDegree, ContextRecipe, DegreeDefinition.

Start with major and natural minor degrees 1..7. Context options: tonic note,
tonic triad, ascending scale, I–V–I or IV–V–I major cadence. Minor cadence uses
i–V–i / iv–V–i with raised leading tone in the dominant; encode harmonic function
separately from the target natural-minor scale and document this choice.

Every generated item includes context → context gap → target degree → thinking →
answer. Default context gap 500 ms. No hidden "context carried over" assumption.
Range validation covers context and target. Default answer is degree number.
Additional answer modes: note name, movable-do syllable or number+syllable.
Use do-re-mi-fa-sol-la-ti for major, do-re-me-fa-sol-le-te for natural minor.
Fixed-do and la-based minor are deferred.

Enumerate valid tonic octaves and target octaves; reject impossible combinations.
Use independent key/context/target stream seeds derived by a documented stable rule
when contexts are randomized; never ambient randomness.
Test all keys, minor dominant leading tone, MIDI bounds, answer spelling/solfège,
context replays and no false attribution of context notes as target exposures.
G5 closes after every provider passes shared checks, audio/voice review, persistence
and physical smoke tests through the same service.

## Stage 6 — Advanced training

**Owner revision 2026-10-25:** G6 is reduced to **interval walks** only.
Contrast drills, weighted practice, intonation, mixed-provider sessions and cluster/quartal
providers are dropped. WP6.2 progressions/functional intervals and WP6.3 melodies already exist
in v0.2.x. Interval walk is implemented in 0.10.0; the dropped material below is historical.

### WP6.1 — Contrast and interval walks (weighted sequencing dropped)

**Gate:** G5. **Scope:** training/music policies, configuration/voice/UI adapters/tests.
**Interfaces:** ContrastDefinition, WalkDefinition. The historical `WeightedCyclePolicy` and
`AntiRepeatPolicy` are not to be implemented.

The weighted-cycle and anti-repeat paragraphs below are historical and dropped. Weighted practice
is not authorized.

Weighted cycles accept integer weights 1..100 and at most 100 slots after dividing all
weights by their GCD. Reject larger cycles with a clear message. Create exactly that
many copies per cycle, then order them using the following versioned policy.
For strict anti-repeat and more than one label, require 2 × largest count <= cycle
size, which permits repeating cycles without an unavoidable boundary duplicate.
At each slot enumerate candidates other than the previous label. After tentatively
taking candidate c, let n be the remaining size: require count(c) <= floor(n/2) and
every other count <= ceil(n/2). Choose among feasible candidates in canonical ID order
with probability proportional to their remaining pre-selection counts, using one
nextInt(sumOfEligibleCounts). This finite count check avoids a search framework.
Every full cycle has exact proportions; a shortened session need not.

Answer anti-repeat is best effort when a majority label makes it mathematically
impossible: relax that rule explicitly in the normalized config and show why.
A strict option rejects such a configuration. Relaxed ordering samples proportionally
among labels unequal to the previous one while any exist, then allows repetition;
a singleton always repeats. Use the same canonical draw rule. Same-root and identical-pair avoidance
filters finite candidate lists. For strict mode, preflight every reachable label
transition and every possible previous pair: each must leave at least one allowed
current pair. Reject configurations that fail this conservative guarantee before
Start. Relaxed mode falls back to the unfiltered feasible list and records that
relaxation. Never alter a note range or omit labels to rescue a policy.

Contrast selects 2..4 labels sharing a root/range where possible; require a common
feasible root for strict same-root mode. Compile labeled example groups through the
shared recipe, fitting the 60 s cap. All group labels and gaps have explicit order.

An interval walk requires previous target = next root. For a finite length 2..32,
build a pitch-transition graph and use reachability for the remaining length to
select only viable edges. If no path exists reject it. For continuous walk require
a reachable closed recurrent component with at least one outgoing edge per state
and at least one edge for every selected interval label; otherwise reject it.
Walks use seeded feasible-edge sampling and do not promise one label per bag.
Walk, shuffle and weighted-cycle sequencing are mutually exclusive. No silent reset
at a range edge.

Tests: exact cycle counts, singleton/majority cases, boundary adjacency, feasible-candidate math,
tiny ranges, same-root conflicts, dead-end graph, constrained path completion and
seeded golden sequences. Keep v1/v2 fixtures. Run check-fast/audio/UI/persistence checks.

### WP6.2 — Progressions and functional intervals (already implemented in v0.2.x)

**Gate:** WP6.1. **Scope:** music/training, assets/setup/import tests.
**Interfaces:** HarmonicFunction, ProgressionTemplate, VoicingPolicy, FunctionalAnswer.

Initial progression templates: major I–IV–V–I, I–vi–IV–V and ii–V–I; minor i–iv–V–i.
Treat Roman-numeral quality/key as musical data, not text parsing during playback.
Map to root-position closed triads initially; optional nearest-voicing chooses among
finite allowed inversions by minimum total voice motion with canonical tie-breaking.
No claim of a complete harmony engine.

Answers can give Roman numerals, root-motion intervals or tonic/predominant/dominant
functions. Label IV/ii predominant and V dominant; keep categories explicit.
Functional interval drills select two scale degrees within a presented tonal context.
Add required spoken phrase/token IDs, including minor qualities.

Enumerate key/range feasibility for all chords/context; max eight chords, total <=60 s.
Test actual chord tones, minor dominant, progression order, inversion constraints,
voice-leading cost/ties, answer tokens and offline spectral windows.
Run shared provider checks and device playback.

### WP6.3 — Constrained melodic phrases (already implemented in v0.2.x)

**Gate:** WP6.2. **Scope:** music/training, assets/setup/tests.
**Interfaces:** MelodyDefinition, PhraseConstraints, MelodyProvider.

Select key/scale, 2..16 notes, fixed initial degree if chosen, allowed directed
intervals, maximum leap (0..12 semitones), range, ascending/descending bias and final
degree requirement. Initially equal note lengths; rhythm generation is deferred.

Build a finite pitch graph. Compute which states can reach an allowed ending in
each remaining number of steps; sample only valid outgoing transitions using seeded
integer weights. Reject impossible start/end constraints before playback. Record
canonical pitch order and random draw order. The small graph bounds work without
retry loops or a generic search framework.

Answers: degree sequence, movable-do sequence or directed interval sequence; use
approved tokens and measured gaps. Validate complete spoken duration, not only notes.
Tests: narrow range, zero-leap/repeated notes, absent path, starting/ending rules,
extreme biases, exact generated length, 10,000 seeded valid phrases and golden output.
Render representative long answers to catch truncation and cache limits.

### WP6.4 — Tuning and intonation (**DROPPED 2026-10-25**)

**Gate:** WP6.3. **Scope:** training/audio pure-tone provider, analysis fixtures, UI/assets.
**Interfaces:** TonePitch(referenceHz, centsOffset), IntonationDefinition.

Introduce a pure-tone renderer seam using the pinned miniaudio oscillator/test-tone APIs,
isolated from the instrument pack; this provider/adapter is planned, not an existing accepted feature.
Reference pitch A4=440 by default; compare reference then target, separated by the
configured gap. Target f = reference × 2^(cents/1200).
Initial choices are 0, ±5, ±10, ±25, ±50 cents; selectable labels flat/in-tune/sharp.
Pure tones default to 1 s, with a 5 ms onset/offset envelope and bounded gain.

Shuffle labels, then magnitudes within a label; zero belongs only to in-tune.
Never infer sharp/flat from whether the target MIDI integer changed.
No microphone input or pitch detection feature is required.

Tests: analytic frequencies, sign/zero, phase continuity within each tone,
envelopes/clipping, sample rate conversion and measured steady frequency within
1 cent using a sufficiently long window. Compare cents error, not nearest FFT bin.
Run audio checks on both supported sample rates and human phone listening review.

### WP6.5 — Mixed-provider sessions (**DROPPED 2026-10-25**)

**Gate:** WP6.4. **Scope:** training coordinator, presets/setup, common service tests.
**Interfaces:** MixedDefinition, ProviderWeight, MixedGeneratorIdentity.

Use WP6.1's bounded weighted-cycle policy for provider selection. Each child provider
has a normalized configuration, algorithm version and separate seeded random stream.
Derive each child seed from SHA-256(master seed bytes + stable UTF-8 provider ID +
stream version), taking the first eight digest bytes as a signed big-endian Long.
Encode the master seed as eight big-endian bytes, then a zero byte, the stable
ASCII provider ID, another zero byte and the ASCII decimal stream version.
Use JDK MessageDigest.
Do not use platform String.hashCode, collection iteration order or Kotlin Random.Default.
Provider keys are unique in v1; reject duplicates instead of merging implicitly.

Generate one child item when selected; other child cursors do not advance.
Stop-after-cycle refers to the outer provider bag. Exposures refer to actual child
targets. Validate all configurations/assets and worst-case timing before Start.
No provider may seize focus, start its own player or own a timer.

Tests: exact provider proportions per full cycle, paused/resumed sequence, independent
child sequences, seed reproduction, history traversal, one invalid child preventing
Start, stale callback cancellation and schema migration. Run a 30-minute phone mix.

### WP6.6 — Cluster and quartal contrast provider (**DROPPED 2026-10-25**)

**Gate:** WP6.5. **Scope:** music/training, assets/setup/tests.
**Goal:** make the draft's cluster expansion explicit and bounded.
**Interfaces:** SonorityDefinition and provider.

Initial labels: chromatic cluster (0,1,2), whole-tone cluster (0,2,4), quartal stack
(0,5,10). Three notes only, root position, simultaneous piano; compare two selected
labels sharing a feasible root. These are structural labels, not claims that a pitch
set has one universally accepted harmonic function.

Use common contrast compilation, range checks, shuffle, voice IDs and preset schema.
Test formulas, root feasibility, simultaneous starts, three-note headroom and exact
spoken label. This package is dropped and must not be implemented. G6 now closes on contrast and
interval-walk host checks, audio/UI checks and physical smoke results.

## Stage 7 — History and transparent adaptation

**DROPPED by the owner on 2026-10-25.** Do not implement WP7.1 or WP7.2. No user-facing listening
history, Home/Progress screens, ratings or adaptation. The existing internal bounded playback
history remains unchanged. The text below is retained only as historical scope.

### WP7.1 — Listening/exposure history

**Gate:** G6. **Scope:** training event contract, media event adapter, core:data SQLite,
app Home/Progress screens, migrations/tests.
**Interfaces:** PlaybackAccountingEvent, SessionRecord, ExposureRecord, HistoryRepository.

Extend the selected platform SQLite repository/migration path, not a new Room prerequisite.
Preserve existing preset schema versions and frozen fixtures; add explicit migrations and
idempotency/retention transactions for the actual history consumer. Preferences DataStore
continues to hold settings. Reconsider code generation only in a separately scoped decision.

Count active consumed timeline duration, including intentional thinking silence.
Exclude Preparing, Paused and buffered-but-unheard PCM. Separately report audible
music/voice duration if needed; name metrics clearly.
A target exposure completes after its required example windows and answer window
were fully consumed in one traversal. Skip/pause/process death before completion
does not count a full exposure. Replay can count another full exposure, marked replay.
Context notes are not additional target exposures.

Events carry session/exercise/traversal IDs and monotonically increasing event IDs.
Persist idempotently with a unique key/transaction; reconnecting a controller does
not duplicate events. Checkpoint consumed duration at most every 15 active seconds;
document that an abrupt kill may lose up to that much uncommitted time. On restart
mark an open persisted session interrupted, without fabricating missing completion.

Store configuration/algorithm/asset identities sufficient to explain a session.
Do not retain every PCM buffer. Settings include history enable/disable (default on),
clear-all and retention (default 90 days). Disabling prevents future history writes;
deletion is explicit. Ratings and exposure aggregates must respect deletion/retention.

Add Home and Progress navigation now: duration, session count and exposure by type/
label. No accuracy, score, streak or inferred proficiency.
Tests: duplicate delivery, replay, skips, process kill, partial answer, clock/date
changes, retention, disabled history, migrations and clear transaction.
Run data/state/UI tests, full checks and physical accounting smoke.

### WP7.2 — Manual ratings and bounded adaptation

**Gate:** WP7.1. **Scope:** training weighting, data ratings, app controls/explanations.
**Interfaces:** SelfRating(Easy, Unsure, Difficult), AdaptationPolicy and snapshot.

No input means no rating. Adaptation defaults off and is enabled per preset.
For a label use the user's latest explicit rating and retained exposure count:
rating multiplier Easy=0.75, Unsure=1, Difficult=1.5, unrated=1.
Exposure multiplier = clamp(sqrt((meanExposure+1)/(labelExposure+1)), 0.5, 2).
Effective weight = clamp(baseWeight × rating × exposure, 1, 100).

At session start freeze counts/ratings and effective weights; existing sessions never
change sequence in response to concurrent database updates. Convert effective weights
to a 100-slot integer cycle: reserve one slot per enabled label, distribute the
remaining slots in proportion to effective weights, take floors, then assign leftover
slots by descending fractional remainder with canonical ID tie order.
Reject more than 100 labels. Apply the same explicit
anti-repeat feasibility policy as WP6.1. Persist the resulting integer weights with
the generator identity so replay does not require a historical database snapshot.

Show base/rating/exposure/effective values and provide reset/disable.
Tests: no history/rating, zero exposure, clamps, ties, fair quantization, determinism,
disabled equality to base policy, deletion and persisted replay.
G7 closes after honest metric/UI review, migrations and common verification.

## Stage 8 — Reliability, optimization and personal release

### WP8.1 — Full physical reliability report

**Gate:** G7. **Scope:** tests, measurements, targeted defects and documentation.
Run TEST_STRATEGY's physical matrix, API 29/36 emulators and a 16 KiB page-size
runtime when available. Test at least 30 minutes on speaker/Bluetooth, 2 hours mixed
locked playback, paused service demotion, interrupted calls, low-battery/battery-saver
behavior, native failure, bad assets, process death and repeated start/stop.

Record actual phone model/API/ABI, software revision, routes, asset hashes, underruns,
Java/native memory trend, wake/focus release and stop latency. Bluetooth acoustic
delay is measured separately from PCM timing. No unsupported OEM-wide claims.
Resolve severe defects; record unavailable accessories/device modes as OPEN with
their effect on release acceptance.

### WP8.2 — Measured optimization and release configuration

**Gate:** WP8.1 defects understood. **Scope:** affected code, benchmark module if useful,
release build config, R8 rules and performance documentation.

Baseline cold launch, session preparation, first sound, transitions, 30-minute CPU/
battery, PSS and stop latency on the same phone. Use Perfetto/AndroidX benchmarks.
Initial targets: visible launch and warm session preparation each <=2 s; transport
command applied within 250 ms excluding route acoustic buffering; no recurrent
underruns or monotonic memory growth after warmup. Record distributions and conditions,
not a single emulator number. Revise a target only with evidence and a documented reason.

If a reproduced AudioTrack problem survives buffer/worker fixes, prototype Oboe behind
AudioOutput with the same cached PCM and contract tests. Adopt it only if measurements
improve the defect without regressions. Do not replace the sample player or session logic.

Enable R8/resource shrinking; retain only necessary JNI/serialization/generated-code
rules, and test the actual minified release. Add Baseline Profiles only for measured
startup/session journeys. Compare before/after with identical seeds/assets/device.
Run all checks after changes; no unmeasured "optimization" is a gate requirement.

### WP8.3 — Signed APK, notices and backup

**Gate:** WP8.1/8.2 acceptance and all required assets reviewed.
**Scope:** scripts/build-personal-release.sh, signing inputs outside Git, release docs.

Use a user-owned keystore under ~/.config/passive-ear/signing; never print passwords
or commit key/config secrets. Reuse an existing release key when supplied; generating
a key or choosing its secret material is an explicit release operation. Document
secure backup and that losing the signing key prevents compatible APK updates.

Build a named personal release artifact, inspect permissions/native ABI/page alignment,
verify with apksigner, record APK SHA-256/versionCode/versionName and include notices/
asset-source records. Increment versionCode for upgrades. Test install and update
with retained presets/history on the phone; test a clean install separately without
destroying the user's active data.

Document export/import, backup contents (v1 preset export excludes history), local
history deletion, offline use, timer/resume semantics and known device limits.
Include pinned source/build instructions and relevant native-license materials.
Produce a release checklist and evidence summary; do not publish or push automatically.

G8 closes when the signed artifact and phone install/update/restore checks pass and
the user has confirmed the signing-key backup. Missing signing/physical evidence
keeps that gate open even when the unsigned build passes.
