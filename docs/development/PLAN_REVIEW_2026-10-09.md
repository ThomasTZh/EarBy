# Plan and progress review: 2026-10-09

Scope: inspect plans, current source, existing test/evidence files and the newly installed
Android Studio MCP; adjust documentation. This review authorizes no implementation or
publication. [STATUS](STATUS.md) is the current ledger and [ROADMAP](../product/ROADMAP.md)
controls ordering. `AGENTS.md` and the original `planDraft.md` are preserved.

## Evidence and actual progress

| Area | Supported result | Limit |
| --- | --- | --- |
| Preparation | G0-host recorded PASS; ASUS Zenfone 10 / API 34 / arm64 phone installation recorded PASS | G0-IDE OPEN; present device connection not rechecked |
| Stage 1 | Interval model, v1 generation and recognition compiler implemented; original 81 JVM tests recorded passing | Later training test totals include subsequent stages |
| Stage 2 | Reviewed 13-phrase voice pack, full SoundFont under owner size waiver, native/PCM pipeline and phone listening recorded; G2 PASS | Listening was not repeated during this review |
| Stage 3 | Session/controller, Media3, interruption/power adapters and timer rules exist | Production queue/timer/focus integration gaps below; G3 PARTIAL |
| Stage 4 | Recipes, v2 generator, Preferences DataStore, SQLite presets, six screens/flows and mini-player exist | Configuration wiring, UI requirements and regression coverage remain; G4 OPEN |
| Stages 5–8 | Detailed provider/history/release specifications exist | No implementations found; G4 remains prerequisite |
| Existing host results | 360 JVM tests, 0 failures/errors/skips: music 23, training 165, audio 51, media 25, data 89, app 7 | Read existing XML; no tests rerun |
| Existing connected results | App 4 and data 4, 0 failures/errors/skips, on ASUS_AI2302 / Android 14 | Narrow launch/controller/timer-command/store coverage |
| Latest full log | `.local/verification/wp4.3e-check-full.log` and its smoke report record successful automated checks | Human report rows are unchecked; does not close G3/G4 |
| MCP | Live handshake and 25-tool discovery succeeded; project file readable | One generic module, no dependencies/configurations; Kotlin inspection rejected; import not ready |

Source inspection identifies integration defects even when component tests pass. The findings
below are static observations; this review did not reproduce them with audible device runs.

Source review targets:

- [Session controller](../../core/training/src/main/kotlin/com/thomas/passiveeartraining/training/session/DefaultSessionController.kt): Start, completion, paused navigation and prepared-item flags.
- [Start contracts](../../core/training/src/main/kotlin/com/thomas/passiveeartraining/training/session/SessionContracts.kt) and [saved settings](../../core/training/src/main/kotlin/com/thomas/passiveeartraining/training/settings/UserSettings.kt): missing stop/seed/mixed-presentation transfer.
- [Production service/audio port](../../app/src/main/kotlin/com/thomas/passiveeartraining/app/TrainingPlaybackService.kt) and [media service](../../core/media/src/main/kotlin/com/thomas/passiveeartraining/media/TrainingMediaService.kt): timer effects, focus ordering and resource lifetime.
- [Playback ViewModel](../../app/src/main/kotlin/com/thomas/passiveeartraining/app/PlaybackViewModel.kt), [Player](../../app/src/main/kotlin/com/thomas/passiveeartraining/app/PlayScreen.kt) and [Presets](../../app/src/main/kotlin/com/thomas/passiveeartraining/app/PresetsScreen.kt): state/command results and incomplete user flows.
- [Settings store](../../core/data/src/main/kotlin/com/thomas/passiveeartraining/data/DataStoreSettingsRepository.kt) and [manifest](../../app/src/main/AndroidManifest.xml): future-schema update handling and backup declarations.

## Findings and required corrections

### P1: Continuous playback, paused navigation and cached identity are not accepted

`DefaultSessionController.start()` plays the first item and returns without advancing after
full consumption. The production `RendererAudioPort.play()` writes one cached exercise;
it supplies no continuation loop. The 30-minute continuous-use claim is therefore unsupported.
In `advance()`, `previous()` and `repeat()`, the controller calls `audio.play()` even when
previously paused, restoring PAUSED afterward. Previously prepared history items also retain
a boolean while the production port caches only its current PCM; selecting a prepared older
item can bypass rendering the selected identity.

Reopen **WP3.1a** for consumption-driven continuation, silent paused navigation, cache identity
and live consumption/answer updates. Test the real controller/port seam, not just prepared flags.
See [Stage 3 repair packages](../product/STAGE_3_TASKS.md).

### P1: Sleep timer command success is not timer execution

`TrainingPlaybackService.onSleepTimerRequested()` calls `SleepTimer.arm()`/`cancel()`.
No production call evaluates timer state, applies `SleepTimerEffect`, gates resume/start,
or sends expiry to the controller/output. `SleepTimer.arm()` records a deadline; it does
not schedule expiry itself. Its separately created scheduler scope is not owned by service
destruction. `SleepTimerCommandTest` checks accepted/refused payloads, not expiry or silence.

Reopen **WP3.4a** for service-owned scheduling, serialized effects, before-sound checks,
gain/drain behavior and cleanup. Retain the command test as command coverage; add actual
expiry coverage. G3 cannot pass before this repair and physical screen-off expiry.

### P1: Audio focus is observed after the controller publishes PLAYING

`TrainingMediaService.observeSession()` requests focus from an asynchronous phase observer.
`DefaultSessionController` publishes PLAYING and immediately calls `audio.play()`.
There is no demonstrated acknowledgement between focus grant and submitting sound. The source
therefore permits a race with focus denial; successful command-surface tests do not disprove it.

**WP3.3a** must make focus/output readiness a prerequisite for every possible sound start
and cover delayed/denied focus without submitting PCM. Measure interruption/route behavior.

### P1: Saved setup and preset semantics do not all reach playback

- `LastSetup.stopPolicy` is stored, but `StartRequest` has no stop-policy field and the session
  does not consume `SessionStopTracker`. Setup count/cycle choices have no demonstrated effect.
- `LastSetup.toPolicy()` always supplies one presentation and `mixedPresentation=false`.
  Mixed presentation exists in the generator but has no complete UI/settings/preset path.
- `PresetsViewModel.use()` applies setup and gains but drops the preset seed; the service always
  chooses a fresh seed. The specified export/import → same seeded session is not available.
- Deep-repetition counts and all timing choices are not represented across the complete saved
  configuration. The UI lacks seed input and reports only a small transport state.

**WP4.1c** completes configuration → validation → persistence → accepted Start → generator,
recipe and stop execution, with frozen v1/v2 fixtures and compatible schema migrations.

### P2: Stage 4 UI exists but does not satisfy its whole package

Setup saves settings; Presets offers use/duplicate/delete/import/export, with no custom
setup → named preset create/update/rename flow. Player omits Repeat, live elapsed/remaining
time and consumed answer display. The answer-visibility preference has no complete consumer
path. `PlaybackViewModel` publishes play/timer choices optimistically and ignores the timer
command result. Export reports success before the document stream has been written. Settings
permits gains up to 4 while the native music contract is 0..1; UI sliders cover 0..1.
The manifest sets `allowBackup=false` but explicit extraction/backup rules are absent.

**WP4.2a, WP4.3f and WP4.4a** close these specific gaps, including useful read/write errors,
future-schema write protection and restoration of an unsaved draft. Preserve the approved
music gain 1.0 / voice gain 0.25 and 2-second thinking default; the roadmap now records them.

### P2: Verification plans describe tools and coverage that do not exist

There is no `scripts/check-ui.sh`, no Robolectric/Roborazzi dependency or screenshot suite,
and no `docs/ux` screen/design contract. The missing work includes implementing and checking
the harness before the owner can review its first baselines. LeakCanary is selected but not
integrated; leak/native-memory evidence is still outstanding. The existing app device tests
do not cover the new navigation, setup, document-picker or reconnect flows.

`check-full.sh` already inspects both APK variants, checks production native packaging and
renders/analyzes host WAV fixtures through `check-native-tests.sh`; older text says otherwise.
There is no `check-audio.sh`. Plans now name the existing audio checks and assign the missing
UI check to **WP4.3g**, with first-baseline approval remaining human.

### P2: Progress and gate language drifted across documents

README, roadmap, architecture, Stage 4 tasks and the gate ledger disagree on implemented
modules/UI and on G3 acceptance. Proto/Hilt are still mandatory in old text although production
uses Preferences DataStore/manual composition, and Stage 7 still requests Room after its
documented rejection. Asset documentation says accepted assets have not been produced.

Correct the shared documents together. Preserve historical dated evidence under explicit
historical headings. Do not convert a passing test count or installed MCP into a passed gate.
The supplied Stage 0 authorization guard in AGENTS remains unchanged; factual later source
and recorded evidence do not create a new implementation instruction.

## Revised sequence and closure

1. Complete G0-IDE import/settings/client readiness using [ANDROID_STUDIO_MCP](ANDROID_STUDIO_MCP.md).
2. WP3.1a queue/cache/navigation → WP3.3a focus/readiness → WP3.4a timer execution.
3. WP4.1c full configuration wiring → WP4.2a persistence/limits/backup → WP4.3f remaining
   screen state/controls → WP4.4a named presets/document completion → WP4.3g UI regression harness.
4. WP3.5 physical G3 report, then WP4.5 G4 daily-use/restore/accessibility report. One reviewed
   session may supply shared rows if the exact build, setup, route and observations are recorded.
5. Begin WP5.1 only after G3 and G4 PASS; retain the ordered Stages 5–8 expansion plan.

This ordering permits repairs to already existing Stage 4 consumers while G3 is incomplete;
it does not waive G3 or authorize new vocabulary. Each repair inherits the original package
scope and AGENT_WORKFLOW, runs targeted tests then check-fast, and runs check-full at integration.
The physical matrix and visual approval remain separate. Use MCP for verified semantic traces
and inspections after import; canonical scripts continue to own verification.

## Review validation

Document links, work-package references, command existence/status and consistency are checked
for this documentation change. Source/build/script/asset files, AGENTS and planDraft are compared
with pre-review SHA-256 snapshots. No dependency checksums, golden files, app builds, phone data
or Git metadata are changed. Exact check outcomes are recorded in STATUS and local review evidence.
