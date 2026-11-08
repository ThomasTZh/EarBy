# Stage 4: Usable intervals alpha

Status: IMPLEMENTED Stage 4 controls/settings/presets/UI harness, reviewed and repaired
2026-10-11. G3/G4 remain open for unsupported human/physical acceptance. Infrastructure is
Preferences DataStore/manual composition and SQLite schema v4; no schema/toolchain upgrade.
See [STATUS](../development/STATUS.md) and [the review ledger](../development/PROJECT_REVIEW_2026-10-11.md).
Inherit AGENT_WORKFLOW. Current owner decisions supersede earlier feature lists below:
Play/Setup/Presets tabs, Start/Stop, recognition/exposure/association, five editable user slots.
Recall/deep-repetition modes, pause/mini-player and document-picker UI are retired; retain old
format APIs/migration fixtures without reintroducing these features.

The review repair package covers complete 60-second validation using runtime-shared bundled
speech durations, empty-roots-as-all feasibility, loaded-settings Start, invalid draft handling,
serialized writes and visible retry errors. Corrupt bytes remain until explicit reset, which
preserves a private copy; future schemas remain read-only. Preset adoption plus its marker is
one transaction. Live edits preserve audible/backward items and replace queued future items.
Screenshot candidates, approved recording and read-only complete-set verification are separate.
The review app/test IDs isolate automated rotation/recreation/Home/task removal/force-stop,
large text semantics/layout, migrations/corruption, hidden answers and timer tests.

## WP4.1 — Interval controls and learning recipes

**Goal:** implement the product's basic/advanced interval options in domain logic.
**Scope:** core/training/music, tests, voice vocabulary additions only when needed.
**Interfaces:** IntervalDefinitionV2, RecipeDefinition, RecipeStep, SequencePolicy,
SessionStopPolicy. Keep generator v1 fixtures unchanged.

### Requirements

1. Add mixed presentation: choose from a sorted enabled set after drawing the bag label,
   then choose a feasible pair for that label/presentation. Both choices use nextInt,
   even for a singleton. Keep WP1.2 shuffle/boundary rules. Every selected label ×
   presentation combination must have a feasible pair, or validation fails.
   Record generator version 2 and its exact draw order/golden fixture.
2. Add optional fixed absolute MidiNote; it must match selected root classes and
   permit every requested combination. Fixed root, selected pitch classes and range
   are intersecting constraints. Do not silently expand range or change direction.
3. Provide these recipe templates, with Example meaning the same concrete notes and
   Answer meaning the same semantic answer:

| Mode | Steps before final post-answer/post-item silence |
| --- | --- |
| Recognition | Example → thinking → Answer → optional replay |
| Exposure | Answer → answer gap → Example → optional replay |
| Association | Example → answer gap → Answer → answer gap → Example |

N is 1..8; M is 0..8. Replay in the first two rows is 0..8 additional examples.
Use named steps and explicit gaps, not arbitrary script expressions.
Final silence occurs once per exercise. Apply roadmap timing bounds and the 60 s
total cap, including all speech. Preview total duration and validation failure.

4. Add exercise-count stop (1..10,000 completed playback traversals) and stop after
   current cycle. A completed traversal includes its full example/answer/end; a
   repeated full item may count again, while a skip/incomplete playback does not.
5. A cycle is the generator's bag, not a UI playlist. Stop-after-cycle drains through
   the current forward bag's last item; back-navigation does not create another bag.
   While a finish policy is draining, Previous/Repeat are unavailable, Next may skip
   forward, and Stop is always available. Duration finish-cycle captures that bag
   at expiry. Impose a 15-minute maximum drain and show the possible overrun.
   Reject a finish-cycle configuration whose remaining worst-case bag exceeds this
   bound; immediate/current-item remain available.
6. Duration, count and cycle policies form one selected stop policy. Avoid independent
   timers with contradictory completion rules. Persist defaults separately from an
   active timer. Ending cause is part of SessionSnapshot.

### Tests / done

Fixtures for every recipe, repeat boundaries, maximum duration, mixed-presentation
feasibility, fixed-note vs pitch-class root, v1 preservation/v2 reproduction.
Fake-output tests cover count/skip/repeat, last bag item, drain cancellation and
duration-vs-cycle boundaries. Run check-fast plus check-native-tests/check-audio-tools
for new recipes; extend host-render fixtures when the production recipe consumer changes.
No contrast/walk/weighted features here; their defined package is WP6.1.

## WP4.2 — Settings repository and Android composition

**Gate:** WP4.1.
**Scope:** core:data Android library, app/core:media composition, settings schema/tests.
**Packages:** existing Preferences DataStore 1.2.1; manual application/service/ViewModel
composition. Proto and Hilt were not introduced. Hilt/KSP compatibility was rejected on
the current toolchain in the recorded probe; reconsider only as a separate scoped decision.
**Interfaces:** SettingsRepository in training; immutable UserSettings and LastSetup;
DataStoreSettingsRepository in data.

Settings include schema version, theme (system/light/dark), dynamic color preference,
spelling preference, answer visibility, default instrument/voice, music/voice gains,
last valid setup, default stop policy and advanced-panel expansion preference.
The expanded UI flag is a setting, never a preset's musical configuration.

Use one Preferences DataStore per file; atomic `edit`, no read-modify-write races.
Version stable preference keys and structured payload fields with explicit defaults
and old fixtures. Validate decoded settings through domain validators.
For corrupt settings, expose a diagnostic and allow an explicit reset to defaults;
do not overwrite without indicating recovery. Do not reset SQLite presets.

Use the existing composition root for actual settings/service/audio dependencies; keep
SessionController service-scoped, repositories application-scoped and ViewModels
screen-scoped. No Context in domain/ViewModels and no database in Composables.
Debug diagnostics/fake repositories use explicit constructor/factory substitutions.

Implement explicit backup/extraction rules for supported Android versions to preserve
the no automatic cloud-backup product contract; record platform/OEM limits.
Do not promise that allowBackup=false alone prevents every device-transfer mechanism.

Tests: defaults, atomic concurrent updates, process recreation in stopped state,
old settings fixture, corruption/error path, singleton ownership and composition wiring.
Run data tests, check-fast and check-full for Android integration. Extend scripts to
include the new module's tests/lint rather than assuming current app-only tasks cover it.

## WP4.3 — Train, setup, player and settings UI

**Gate:** WP4.2.
**Scope:** app UI/ViewModels/resources/tests, Navigation 3 and lifecycle integration,
current app screen contracts in PlayScreen, SetupScreen, PresetsScreen and AppRoot.
**Goal:** usable Material 3 flow driven by immutable state and service commands.

Current navigation uses typed Navigation 3 keys with a saveable back stack. Play/Setup/Presets
are bottom tabs; the drawer selects training modes and opens Settings. Later musical modes
remain placeholders. No separate Train/Player/mini-player flow is reintroduced.

| Screen | Required behavior |
| --- | --- |
| Play | Live service state, Start/Stop, gated answer, elapsed/remaining time, sleep picker |
| Setup | Selected intervals, mixed presentation, three learning modes, thinking/repeats, roots/range/timing/seed/gains; autosave/live editing |
| Presets | Immutable starters plus five named slots; Use, Rename, Replace and sort/details controls |
| Settings | Theme/dynamic color, spelling/answer display, sound gains, diagnostics/explicit recovery |

Validation is shared with the domain. Show field-specific explanations and disable
Start while invalid. Keep simple/advanced values consistent; collapsing advanced
controls does not reset them. Starting during active playback offers stop-and-start
with an explicit action; no silent replacement.

Use ViewModel state, SavedStateHandle for unsaved setup where appropriate and
lifecycle-aware Flow collection. Rotation must not issue Start twice. Loading,
empty, Error, disconnected controller and permission-denial states need designs.

Use existing Material components with safe insets, 48 dp minimum interactive targets,
scrollable setup, TalkBack labels and focus order. Support light/dark and 150%/200%
font scaling without clipped controls. Do not announce hidden answers to accessibility.

Introduce Roborazzi/Robolectric with a fixed device/API/font/theme environment after
screen composition stabilizes. Baselines cover setup basic/advanced/errors, player
phases, timer sheet and settings. A human reviews first baselines; updates are tied
to an explicit UI change. Retain connected Compose tests for real controller behavior.

Run ViewModel tests (current suite uses coroutines-test), Compose/navigation tests,
check-ui, check-fast and full
integration checks. Check preview and runtime states use the same validation rules.

## WP4.4 — Presets and retained versioned data APIs

**Gate:** settings/shared validation for the store; WP4.3 for its user-facing flow.
**Scope:** core:data SQLite/serialization/schema migrations and five-slot app preset flow,
tests and backup documentation.
**Interfaces:** PresetRepository, PresetDocumentV1, ImportPreview, PresetId.

**Implementation note (2026-10-09):** Room requires KSP, and no KSP release supports the
project's Kotlin 2.4.20; a KSP-compatible Kotlin cannot be applied against AGP 9 (probe
evidence in STATUS). The store is therefore plain SQLite through `android.database.sqlite`
with hand-written, tested migrations. Its database version and JSON document version are
separate contracts; preserve all existing migration/fixture versions.

1. Store presets in SQLite. Preset has UUID, name (1..80
   trimmed characters), created/updated timestamps, schema version and a normalized
   domain payload. DTO uses stable kind/ID strings and explicit units.
2. Payload contains exercise definition/version, recipe, sequence policy, audio
   selection/gains and stop defaults. Seed is optional; if absent, Start chooses and
   records a seed. Store active state/history separately. No paths or serialized
   Kotlin class names. Export 64-bit seeds as signed decimal strings to avoid loss
   in JSON readers that represent all numbers as floating point; validate Long range.
3. Supply starter presets (minor/major thirds; consonant intervals; all simple intervals)
   with feasible ranges. Store immutable built-ins separately from editable user
   copies. Duplicate/rename/delete require deterministic repository operations.
4. Export .earpreset JSON, schemaVersion=1, using kotlinx.serialization and the
   Storage Access Framework. v1 can contain up to 100 presets and at most 1 MiB UTF-8.
   Read through a bounded stream regardless of provider-reported size.
5. Parse strictly: reject malformed JSON, duplicate JSON keys, unsupported versions/
   kinds, unknown fields/IDs, nonfinite/out-of-range values and invalid musical
   constraints. Use Jackson Core streaming STRICT_DUPLICATE_DETECTION for bounded
   structural preflight (nesting <=32; individual string <=16 KiB), followed by
   kotlinx.serialization DTO decoding. Do not hand-write a JSON tokenizer.
6. Stage a preview before import, show per-preset validation and duplicate-ID conflicts,
   and default to making new IDs. Replace existing IDs only on explicit selection.
   Commit the accepted batch in one store transaction; cancellation/failure changes
   nothing. A future-schema file is rejected with a useful message.
7. Export only selected presets; no history, assets, native state or private file
   paths. Document local-file backup and that a user-selected document provider may
   itself be cloud-backed. The app still has no network permission.
8. Schema migrations are explicit and tested. Never use destructive migration as a
   fix for a missing migration. Keep old fixture documents in tests.

Tests: CRUD, unique identity, round trips, malformed/oversized/truncated input,
duplicate keys, path-shaped unknown fields, provider cancellation/partial output,
conflicts, atomic rollback and schema migrations.
Verify app-data clear → import → equivalent normalized configuration → same seeded
sequence. Run data/migration tests, check-fast and check-full. Enable Presets tab.

## WP4.5 — G4 daily-use acceptance

**Gate:** G3 PASS, WP4.1–4.4 and all repair/coverage packages below complete.
**Scope:** verification, targeted fixes, docs and no vocabulary expansion.

On the phone save/use a preset slot, start a 30-minute session, leave the app,
use media controls, let the timer stop, relaunch and restore/import the preset.
Test airplane mode, light/dark, large text, TalkBack, invalid setup, process death
and failed asset preparation. Default UI must be usable without opening advanced
settings. Hidden answers stay hidden everywhere.

G4 requires all relevant scripts, reviewed UI baselines and a physical acceptance
report. Record known limitations: one piano, one English voice, no progress accuracy,
and no automatic resumption after process death. This is the first daily-use alpha.

## Remaining completion packages from the 2026-10-09 review

Work in the roadmap's revised order after WP3.1a/3.3a/3.4a. These packages complete
existing interval consumers and original requirements; they add no new musical vocabulary.
Use the imported Studio MCP for call/consumer traces and changed-file inspections;
[ANDROID_STUDIO_MCP](../development/ANDROID_STUDIO_MCP.md) defines readiness and evidence limits.

### WP4.1c — End-to-end saved configuration and stop execution

**Goal:** every selectable/exported musical option has the same meaning when played.
**Gate:** Stage 3 integration repairs; existing recipes/v2 generator/preset store.
**Scope:** training configuration/validation/StartRequest/controller; settings/preset adapters
and migrations in data; app setup/Start composition; focused tests and audio fixtures.
**Interfaces:** one normalized immutable configuration carrying sequence/presentation set,
optional fixed root/seed, full recipe timing/counts, gains and selected stop policy.

1. Carry stop policy into accepted Start and consume its tracker at full consumed item/cycle
   boundaries. Count only completed traversals; replay may count, skips may not. Apply the
   60-second item and 15-minute cycle drain bounds. Publish ending cause/drain controls.
2. Expose/persist mixed presentations; `LastSetup.toPolicy()` must not collapse a selected
   set to one direction. Preserve v1 output for old single-presentation setups and v2 identity
   for mixed/fixed-root setups. Reject every infeasible label × presentation combination.
3. Carry optional preset/user seed to Start; absence draws a fresh seed exactly once. Publish
   the chosen seed/algorithm/configuration so the user can reproduce it. Applying a seeded
   preset must preserve that seed; Next/Repeat/Resume never redraw it.
4. Represent the five templates completely: deep repetition before/after counts, optional
   replay, note length, melodic/answer/repeat gaps, thinking and final silence. Preview duration
   from the same measured voice catalog as preparation and enforce the domain bounds.
5. Add only compatible fields/defaults and explicit migrations; distinguish settings keys,
   SQLite schema and preset document versions. Freeze old export/database fixtures and prove
   they still load with documented defaults. Never rewrite an already recorded migration.

**Tests:** accepted Start equals direct generation/compilation for every template and mixed/fixed
root combination; same normalized seeded configuration after save/reload/export/import produces
the same sequence; count/cycle/duration races with real session consumption; cancellation and
invalid combinations; v1/v2 fixtures unchanged. Add representative production recipe WAV checks.
**Done:** targeted training/data/app tests, fast/full and affected native/audio checks pass;
phone observes selected direction/recipe and count/cycle stop outcomes from the normal UI.

### WP4.2a — Settings limits, recovery and backup completion

**Gate:** WP4.1c.
**Scope:** training validation, Preferences DataStore, import validation, app composition and
Android backup/extraction resources. No Hilt/Room/Proto migration or toolchain change.

1. Unify settings, preset, UI and renderer gain limits. Preserve reviewed defaults music 1.0 /
   voice 0.25; target 0..1. Existing stored values above that target need an explicit diagnostic
   and documented recovery, not a later renderer crash or silently clamped display.
2. Audit unreadable/corrupt/future-schema paths. A future-schema snapshot must not become an
   ordinary defaults write on the next edit. Keep it read-only until explicit reset or supported
   migration. Show recovery diagnostics and actionable write failures in Settings/Setup.
3. Maintain singleton repositories and owned scopes. Failed/cancelled save must not show Saved.
   Decode and validate the entire saved configuration with the shared rules.
4. Add explicit supported backup/extraction rules and test packaged declarations. Keep automatic
   cloud backup disabled; document OEM/device-transfer limits without claiming universal control.

**Tests:** old/current/future settings, corruption/type errors, concurrent atomic updates, rejected
gain values, failed persistence, singleton recreation and backup manifest/resources.
**Done:** targeted data/app tests and fast/full checks pass; relaunch restores configuration
stopped; error and reset paths are reviewable on device.

### WP4.3f — Complete setup/player state and controls

**Gate:** WP4.2a.
**Scope:** app ViewModels, navigation/screens/resources, narrow controller-state transport,
connected tests, `docs/ux/SCREENS.md` and `docs/ux/DESIGN_SYSTEM.md` created with this consumer.

1. Complete the setup fields from WP4.1c, duration preview and field-specific validation.
   Restore unsaved edits across recreation through SavedStateHandle or an equivalent bounded
   domain draft; persisted settings remain last valid configuration, not partial text input.
2. Player exposes accurate phases, consumed elapsed/remaining time, Repeat, stop reason/drain,
   selected/chosen seed and timer state. Resume and fresh Start are distinct controller actions.
   Derive command availability from the service; avoid optimistic success before acknowledgement.
3. Apply answer-display preference to Player/mini-player/media/accessibility with one consumed
   marker policy. Hidden answers never enter semantics; shown answers appear only when consumed.
4. Active setup changes offer explicit Stop then Start. Reconnect/recreate does not restart sound
   or redraw a seed. Handle preparation, disconnect, command rejection and failure visibly.
5. Finish the screen/design contracts and large-text layouts. Current fixed Rows, particularly
   five timer chips, need verification at 150%/200%; use wrapping/scrolling where required.

**Tests:** navigation/back/recreation, invalid Start, controller disconnect/rebind, Repeat/Resume,
command rejection, hidden/consumed semantics and restored draft. Run fast/full checks.
**Done:** connected screen flows work through the real controller seam; UX contract records
every designed state. Human TalkBack/font-scale and baseline approval still belong to acceptance.

### WP4.4a — Named custom presets and truthful document completion

**Gate:** WP4.3f.
**Scope:** app preset/setup/document flow and targeted repository adapters/tests only.

1. Add valid custom setup → named preset create, update and rename actions; starters remain
   immutable with explicit duplicate-to-edit. Persist the full normalized configuration/seed
   from WP4.1c. Document confirmation/cancellation and the 100-user-preset limit.
2. Report export success only after the selected provider stream writes/closes successfully.
   Handle provider cancellation, null stream, partial write/read and failure without claiming a
   completed backup. Keep the import byte bound, explicit preview/conflicts and atomic commit.
3. Test real picker/adapter paths and both fresh-app restoration and conflict import. Do not clear
   the owner's app data during routine checks; use an isolated test installation or a separately
   authorized reset after retaining an export.

**Tests:** custom create/update/rename/use/delete, immutable starter copy, seeded replay, picker
cancellation/IO failure, no false Exported message, old document compatibility and atomic import.
**Done:** fast/full checks and connected flows pass; WP4.5 can create/export/import a custom setup
through the phone UI, without relying on repository-only tests.

### WP4.3g — UI regression harness and reviewed first baselines

**Gate:** WP4.4a; screen composition/state contracts stabilized.
**Scope:** app UI test fixtures, selected Robolectric/Roborazzi dependencies and pins/checksums,
`scripts/check-ui.sh`, verification integration, screenshot candidate evidence and testing docs.
**Goal:** a real repeatable UI check before requesting human golden approval.

1. Verify a compatible stable Robolectric/Roborazzi pair against the pinned toolchain and selected
   Compose graph. Record license/provenance and strict checksums as an intentional dependency
   addition. No incidental AGP/Kotlin/Compose upgrade; record incompatibility if demonstrated.
2. Render screens using fake immutable state/repository/controller seams; avoid loading the
   206 MiB SoundFont in UI tests. Fix API, viewport, density, locale, clock, font scale and theme.
3. Cover Train, Setup basic/advanced/error/duration preview, Player phases/answer gating/drain,
   timer control, mini-player, preset create/list/import conflicts, Settings recovery and large
   text. Generate candidate images into local evidence for human review.
4. After explicit human review, establish baselines through the deliberate recording operation.
   `check-ui.sh` verifies; it never records or replaces goldens. Wire it into fast/full at the
   owning integration step and keep connected controller/navigation tests.
5. Integrate selected debug-only LeakCanary with its first lifetime checks if compatible;
   separately measure native/total process memory. MCP inspections do not replace leak evidence.

**Done:** reproducible harness and verification command exist; required screenshot comparisons
pass against human-reviewed first baselines; no unexplained differences or auto-approval.
An unavailable reviewer keeps baseline acceptance OPEN even if candidates render successfully.
