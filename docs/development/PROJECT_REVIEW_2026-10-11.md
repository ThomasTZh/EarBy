# Project review and repairs — 2026-10-11

Authorized scope: the user's detailed Stage 0–4 MCP review and automated phone test plan.
Sequential work; no dependency upgrades, Stage 5 implementation, commits or publication.
Historical evidence and `planDraft.md` are preserved. All checks in this review are automated.

Raw evidence: `.local/verification/project-review-2026-10-11/`. `inputs-before.json`,
`inventory.txt` and `git-before.txt` capture the workspace before source edits. Device
serials remain in ignored evidence. Initial MCP model: 24 entries, all six consumers,
36 dependency entries, app launch configuration. Bounded call trees are supplemental
investigation and may truncate; canonical scripts establish build/test outcomes.

## Findings ledger

| ID | Kind | Trigger / evidence | Status |
| --- | --- | --- | --- |
| R01 | Confirmed bug | SetupValidator never measures complete recipes; oversized setups save, then fail during compilation | Repaired; checks below |
| R02 | Confirmed bug | Empty roots mean all in LastSetup, but fixed-note validation checks the empty list | Repaired; checks below |
| R03 | Confirmed bug | Timeline compilation and queue generation outside worker error handlers leave PREPARING/PLAYING on failure | Repaired; checks below |
| R04 | Confirmed bug | Source timelines indexed by generator index; generator reconfiguration restarts indices and clears original recipe timelines; cache grows without bound | Repaired; checks below |
| R05 | Confirmed bug | Audio cache identity is sequenceIndex, gains are mutable global state; history re-render changes sound after edits | Repaired; checks below |
| R06 | Confirmed bug | Reconfigure discards next but retains old forward history after Previous | Repaired; checks below |
| R07 | Confirmed bug | Hidden-answer filtering only in UI permits consumed answer metadata; audit preference toggles | Repaired; checks below |
| R08 | Confirmed bug | Setup collector calls toSetup on invalid draft; save errors escape; settings initially default without loaded barrier | Repaired; checks below |
| R09 | Suspected defect | Timer cancellation/replacement, fade and duck share one master gain; inspect cleanup and writer races | Repaired; checks below |
| R10 | Documentation contradiction | STATUS includes NULs and stale stage/gate summaries; scripts describe swipe-away continuation | Repaired; checks below |
| R11 | Suspected defect | Screenshot paths and verification arguments may accept partial image sets or recording | Repaired; checks below |
| R12 | Acceptance gap | Isolated review variant, automated phone harness and real 30-minute endurance evidence absent | Closed for automated scope: 33 phone tests pass, including real endurance |
| R13 | Acceptance gap | Acoustic quality, real accessories, TalkBack usability and visual approval cannot be established by automation | Unverified by design |
| R14 | Confirmed bug | Settings writes bypass complete recipe validation | Repaired, host checks pass |
| R15 | Confirmed bug | Legacy slot adoption commits rows separately from its retry marker | Repaired; rollback regression corrected and passed |
| R16 | Confirmed bug | Staged assets trust size, accepting same-size corruption | Repaired, red/green host regression |
| R17 | Confirmed bug | AudioTrack unsigned head wrap/duplicate flush accounting | Repaired, red/green host regression |
| R18 | Confirmed verification bug | Old device script accepts ADB exit zero despite failed instrumentation | Replaced with strict result parser and XML evidence |
| R19 | Confirmed bug | Future errors become null; Settings write exceptions escape; reconnect/query timeout gaps | Repaired, host and phone checks |
| R20 | Confirmed bug | Stop silently ignores cleanup failures; close can leave owned scopes after release failure | Repaired, red/green host regression |
| R21 | Confirmed verification gap | Architecture guard covers only two JVM modules; release native/debug-code guard stale | Six-module and actual APK/DEX guards implemented |
| R22 | Confirmed bug | Successful Stop retains old output error; asynchronous transport requests can reorder | Repaired; reproduced on host and repeated phone cycles |
| R23 | Confirmed bug | WAV helpers allocate before native bounds; frame multiplication can overflow | Bounded Kotlin/JNI checks, red phone regression |
| R24 | Confirmed bug | Wrong stored preference types throw outside generic read catch and terminate flow | Defaults plus visible diagnostic; red/green host regression |
| R25 | Confirmed bug | Native reset retains prior note PCM; previously skipped SoundFont device test exposed it | Host/phone regression; bounded reset flush implemented |
| R26 | Confirmed build bug | Wrong native header paths trigger upstream GCEM download during ordinary CMake configure | Explicit pinned headers, fail before upstream configure |
| R27 | Disproven suspicion | MA_AT_END short decode could lose the final partial block | Exact tail-length contract passes; decoder unchanged |
| R28 | Acceptance gap | IDE model reports unresolved Gradle catalog supertypes and the newly consumed JSON library; review source set is outside imported roots | CLI/MCP builds pass; semantic inspection limitations retained |
| R29 | Confirmed bug | Preset refresh failure discarded, settings read escapes preset save, cancellation reported as write failure, mode write exception escapes drawer | Four failing host regressions repaired; visible feedback and cancellation preservation |
| R30 | Confirmed evidence gap | Native .cxx cache was absent from ignore rules and included in the initial raw input snapshot | Ignore added; comparison excludes generated cache while preserving the original snapshot |
| R31 | Confirmed harness defect | A prior screen-off run leaves non-secure keyguard covering activities; UI recreation stalls while display is awake | Platform wake/dismiss precondition added; secure credentials are never bypassed |

## Execution record

Initial host ADB failed inside the sandbox (`smartsocket listener: Operation not permitted`);
this is a host permission failure, not a device test outcome. Retry using authorized host execution.
No results from earlier dates are presented as newly executed checks.

### Package B: R01/R02 validation

Reproduced all three new regressions before repair (`R01-R02-before-host.log`):
above-cap duration, maximum repeats/mixed presentation, and empty roots with fixed note.
SetupValidator now measures every selected answer and enabled presentation, including
separator/speech/repeats/pauses. It reports duration errors before save/start. Fixed roots
use the same empty-means-all rule as LastSetup. Nonfinite/submicrosecond timing is refused.
BundledVoiceDurations records exact frame-derived whole microseconds, with a read-only
manifest consistency check in check-fast. No asset or generator fixture changed.

Checks: targeted 23 tests PASS (`R01-R02-after.log`); check-fast PASS
(`package-B-fast.log`). Compatibility: oversized stored configurations now require correction;
valid existing setups keep their timings. Full runtime asset validation follows in package C.

### Packages C and D: controller, identity, visibility and persistence

R03/R06: three regressions failed against the original controller (`R03-R06-before.log`),
including compilation stuck in PREPARING, navigation generation exceptions and stale forward
items after a live edit. Playback now serializes workers, catches boundary failures, preserves
cancellation, invalidates failed work and releases resources. Timelines belong to reachable
items; the source is stateless with respect to timelines. Items capture recipes and render gains.
Typed PlaybackIdentity separates session/ordinal identity from reproducibility metadata.
History replay/recipe/gain/seed collision regression uses controlled dispatchers and virtual time.
Identity-specific coverage was added after the interface repair; no pre-repair execution is claimed
for that regression. The original deterministic generator fixtures remain unchanged.

R07/R08: hidden consumed answers and two setup failures reproduced (`R07-R08-before.log`,
`R08-before.log`). Visibility is now filtered before SessionSnapshot publication; settings changes
therefore remove answers from metadata and live queries as well as UI. Start awaits loaded settings.
Invalid drafts are compared without construction. Writes serialize, surface errors and retain dirty
state for retry; disposal saves use an application-owned scope. Transport exceptions complete futures.

R14/R15: settings writes now reject oversized recipes atomically and unreadable settings publish a
diagnostic. Preset adoption and its retry marker now share one transaction. The initial adoption
failure injector missed UPDATE and was corrected; its initial failure is test construction evidence,
not a demonstrated product regression. Existing schema-chain, future-schema, slot preservation and
rollback tests were rerun. No schema or generator version changed.

Checks: `package-CD-targeted-7.log` PASS and `package-CD-fast-7.log` PASS. Intermediate formatting,
compilation and test-injector failures are retained in numbered logs and corrected before acceptance.
Native decoder short-tail suspicion disproved: `wav_contract` passes lengths 1/127/4095/4096/4097/48001;
`native-review-after.log` also passes all seven numeric render fixtures. CTest selects the project
contract test explicitly because upstream excluded test binaries are not built by this harness.

Additional confirmed defects R16: staged assets trusted size alone; R17: unsigned playback heads
wrapped without epoch tracking and duplicate flushes double counted; R09: fade/duck overwrite.
Three targeted regressions fail in `R09-R16-before.log`. `package-D-audio-targeted.log` and
`package-D-audio-fast.log` PASS after repair. No baseline/asset/generator version changed.


### Package E: screenshot and document guards (R10/R11/R21)

Candidate generation now resolves one canonical output path and refuses the baseline directory.
Ordinary verification refuses arguments/recording, checks all nine expected PNGs and runs fresh
Roborazzi tests. Missing/extra/invalid images and unsafe recording modes have script regressions.
`package-E-fast-2.log`, `package-E-candidates-2.log` and `package-E-ui-verify-2.log` PASS.
The existing nine baselines retain their hashes; no new approval or baseline update is claimed.
STATUS NUL bytes were removed; stale stage/IDE/feature claims are dated historical records.
README/ROADMAP/architecture/work packages/testing/dependencies now describe current controls,
three learning modes, five slots, live edits and task-removal stopping. R21's two new boundary
regressions fail in `R21-before.log` and pass in `R21-after.log`. Release checks require both native
ABIs and inspect actual DEX entries for debug/review-only classes, instead of dexdump on an APK.

### Package F: isolated automated phone workflow (R12/R18–R26)

`review` reuses production/debug assets and code, with application ID
`com.thomas.passiveeartraining.review` and separately packaged instrumentation. Review-only
injection seams supply generation/compilation/render/output failures, focus denial and route
policy events, plus app-private font scaling. They are absent from normal production variants.
The driver discovers packaged app/test/runner IDs, explicitly selects an authorized phone,
installs only isolated packages, bounds every runner, and always stops review playback in cleanup.
ADB status zero is insufficient: failed/skipped/crashed/empty/incomplete runner results fail.
R18 parser guard tests are red in `R18-before.log`, green in `R18-after.log`; the red execution
was the new parser skeleton, while the old script defect is established by its exit-code logic.

Phone model/API/ABI/page size: ASUS_AI2302 / Android 14 API 34 / arm64-v8a / 4096 bytes.
Serials are confined to ignored raw metadata. Original and review package versions/APK hashes
are recorded. No regular app clear/uninstall, global network/font change, call, personal app or
accessory action occurs. Rotation is unfrozen by UI cleanup and initial display power restored.

`device-first`: 15/16 app tests passed; launcher assertion matched two labels, corrected to current
transport semantics. Raw logs preserve this failure. Isolated SQLite tests then exposed old
starter-only counts (3 instead of 3+5 slots); native tests exposed an old gain cap and missing
SoundFont asset, previously causing an assumption skip. Test assets now reuse the app inputs,
slot/gain assertions match existing owner decisions, and missing assets fail instead of skip.

`device-second` reproduced a real stale-error/rapid-command cycle failure. R22's host regression
fails in `R22-before-2.log`; a successful Stop now clears the prior failure, and transport requests
are FIFO through an owned mutex acquired before asynchronous request construction. A raced
old host cache test checked item selection before worker preparation; it now uses controlled
shared virtual time and actually renders each item before eviction/replay assertions.

R19's failed future and settings-write regressions fail in `R19-before-app.log`. R20's cleanup
regression fails in `R19-R20-before.log`. They pass in `R19-R20-after.log`. Failed futures now
propagate causes/cancellation; bounded UI queries and reconnect retries surface failures.
Settings writes show retryable errors. Cleanup attempts all steps, reports failures through
session Error, and cancels owned scopes even if release throws. Teardown-only cleanup failures
are logged rather than silently swallowed. Wake-lock leases renew during long active sessions.

R23's phone regression fails in `R23-before.log`. Voice decode bounds are checked before
allocation; WAV output validates mono/stereo, supported rates, 60-second duration and wide frame
arithmetic in Kotlin/JNI. R24 wrong preference types fail in `R24-before.log`; decoding now
publishes defaults with a diagnostic instead of terminating collection. Real protobuf corruption
is preserved until explicit reset, which retains a private copy; future schemas remain read-only.

`package-F-fast-3.log` PASS. `check-full-review-1.log` passes host assets/native fixtures, complete
UI comparison, debug/release/review builds/lint/APK inspections, 17 app tests and 4 SQLite tests.
It correctly FAILS on native reset: prior note PCM remains at peak 0.11373025 after reset.
`R25-native-before.log` reproduces the failure on the host. Reset now terminates voices and
flushes one bounded internal synth block before the next item. All eight subsequent host blocks
are silent (`R25-one-block.log`); numeric fixtures still pass. R26 configure logs exposed automatic
upstream GCEM downloads from incorrect paths; CMake now pins the separately locked GCEM include
path and fails on missing headers before upstream configuration. The optional Signalsmith backend,
previously undiscovered, is explicitly disabled to preserve existing behavior. No source revision,
license, dependency/toolchain version, lock or checksum metadata changes.

`R25-R26-native-after.log` and `check-full-review-2.log` record the next integration attempt.
Final phone/endurance outcomes are recorded below. Earlier failed runs remain in their
own directories; no historical result is presented as a new pass. Native fixture evidence is now
routed to the dated review directory instead of the historical WP2.3 output path.

### Final source/IDE pass and later repairs

R29: `R29-before.log` executes 21 ViewModel tests and fails the four new preset/mode cases.
The repaired operations catch storage/read failures, display feedback, preserve cancellation,
and change mode/navigation only after an accepted write. `R29-after-2.log` passes all 21;
`package-F-fast-4.log` passes. The first direct follow-up omitted `scripts/env.sh` and selected
the machine JDK without a compiler; `R29-after.log` and `R29-format.log` retain those host-command
failures. They are not product test passes. The corrected commands use the selected project JDK.

The second MCP pass requested inspections for 75 production/build paths across all six modules.
`mcp-final-inspection-summary.json` retains 118 ERROR diagnostics: 102 Gradle DSL/catalog model
errors and 16 unresolved JSON-library symbols in the newly consuming audio module. Canonical
strict builds and `mcp-final-build-2.json` pass (six compiler warnings). These mismatches are
recorded as R28, not called clean inspections. The review-only application is outside the IDE's
imported content roots; its inspection is incomplete. CLI compilation/lint/device tests cover it.
There are 56 warning/weak-warning inspection diagnostics; behavioral findings took priority.
Start, navigation, editing, timer, answer publication, persistence and teardown call-tree evidence
is retained separately. No IDE synchronization capability is exposed by this MCP connection.

`check-full-review-2.log` passes host/native/build/packaging and the short connected suites but
fails the external-force-stop runner's empty argument. That argument was corrected before
`check-full-review-3.log`, which passes 17 app, four SQLite, nine native and two process tests.
Its endurance attempt was deliberately interrupted after several minutes to repair R29; its
partial samples are retained in `device-final-2`, and do not establish 30-minute acceptance.

`check-full-review-4.log` passes the host integration checks, then reproduces R31: the non-secure
keyguard left by screen-off testing covers UI activities, causing recreation/semantics failures.
The run was stopped and cleanup preserved normal-app hashes. The driver now wakes and dismisses
through the platform command before UI instrumentation, records its precondition, and refuses a
still-covering keyguard. No secure credential is bypassed. UID log capture accepts both platform
package-dump field names. The following integration attempt is `check-full-review-5.log`.

The endurance driver additionally requires at least 25 post-warmup screen-off memory samples
spanning 24 minutes, with PSS variation at most 64 MiB. This observed-memory guard is separate
from the two-entry/48 MiB PCM assertions and repeated-cycle memory bounds. Its parser regression
passes in `R18-memory-after.log`. Shutdown samples are excluded because release intentionally
reduces memory. No additional session wake lock or global display-timeout change is used.

R30: the preserved initial raw hash snapshot inadvertently includes 6,954 generated `.cxx`
files. `input-exclusions.json` identifies them; the source comparison excludes that cache and
`.gitignore` now keeps it untracked. No cached file is counted as a source repair. Before/after
source hashes and the finding assigned to each changed input are recorded separately. The
original plan, toolchain/checksum files, approved screenshots, voice inputs and deterministic
fixtures compare unchanged in `preserved-inputs.json`.

### Integration execution summary

`final-host-tests-fresh.log` reruns all selected host tasks: 101 tasks executed, 450 tests in
48 suites, zero failures/errors/skips. Counts: music 24, training 211, audio 57, media 24,
data 101 and app 33. The XML timestamps are from 2026-10-11 08:59 UTC, recorded in
`final-host-test-summary.json`. Earlier up-to-date Gradle outcomes are not described as
new executions. The nine UI comparisons also execute freshly through `check-ui.sh`.

`check-full-review-5.log` passes host integration but catches the asynchronous keyguard
dismissal before completion. A bounded ten-second precondition wait fixes that harness race.
`device-final-4/keyguard-dismiss-after-delay.txt` proves the platform dismissal succeeds without credentials.

`check-full-review-6.log` is the final integration run and exits zero. It passes 19 script guards,
35 audio-tool tests, asset checks, two native CTest contracts, seven numeric render fixtures,
nine fresh screenshot comparisons, debug/release/review builds and lint, APK/DEX/manifest/native
packaging inspections and both NDK ABI probes. Connected suites pass 17 app, four SQLite, nine
native, two external process and one endurance test: **33 tests, no failures or skips**.

The real 30-minute display-off session passes in `device-final-5`: terminal resource release and
the five-second no-restart watch finish at elapsed **1,805,306 ms** (runner duration 1,809 s,
including setup). All 30 progress rows remain noninteractive and PLAYING with advancing consumed
frames, decreasing sleep countdown and at most two PCM entries under 48 MiB. The 26 post-warmup
memory samples span elapsed 180–1,690 s: PSS 312,687–315,639 KiB, variation **2,952 KiB**, below
65,536 KiB. Ten repeated start/stop/error cycles also pass (PSS 94,068–107,200 KiB; native
allocation 11,616,992–17,471,184 bytes). Timer completion releases output, renderer, focus and
wake resources; cleanup leaves no review process. No fatal exception, native fatal signal or ANR
marker appears in captured app-UID logs; exit records show expected instrumentation/task/force
stops. `final-summary.json`, XML, TSV, screenshots, samples and raw logs retain the evidence.

The normal app's one APK and four private-file hashes compare unchanged. The endurance settings
hash also remains unchanged after the run. No regular app clear/uninstall/replacement, global
font/network change, accessory action, commit or publication occurred. The display is restored
awake and rotation is unfrozen during automated cleanup. R13 and R28 remain explicit limits.
`endurance-configuration.json` and the isolated settings protobuf capture the exact persisted
configuration plus hashes of its defaults, Start construction and test recipe. `tested-apk-hashes.json`
confirms all four installed/test artifacts match the workspace outputs; debug/release hashes are
also retained. All four packaged review/library manifests are checked to contain no INTERNET.

### Affected contract/file map

| Findings | Owning files/contracts | Compatibility |
| --- | --- | --- |
| R01/R02/R14 | SetupValidator, BundledVoiceDurations, DataStoreSettingsRepository, voice-duration guard/tests | Reject oversized saved/draft recipes; valid timings and generator fixtures unchanged |
| R03–R06 | AudioPort/ExerciseSource, ConfiguredIntervalExerciseSource, DefaultSessionController, RendererAudioPort, controller/identity tests | Internal JVM port signature changed with all consumers; no persisted schema change; original backward-item sound retained |
| R07 | StartRequest, controller snapshots, service request factory, session/media/UI tests | Hidden mode removes already-shown metadata promptly; prerecorded speech remains |
| R08/R15/R19/R24 | SetupViewModel/SetupScreen, SettingsScreen, PassiveEarTrainingApp, PlaybackViewModel/FutureAwait, TrainingTransport, DataStore/SQLite repositories and tests | Loaded settings required; failed saves stay visible; future schema read-only; slot adoption atomic; no migration version change |
| R09/R17/R20/R22 | DefaultSessionController, FocusAndPower, TrainingMediaService/Transport, AudioTrackOutput/PlaybackHeadCounter and regressions | Stop releases and clears errors; fade/duck independent; output accounting wraps safely |
| R16/R23/R25/R26 | AssetPreparer, MusicRenderer/JNI/renderer/CMake, WAV/reset host contracts, Android audio tests/native scripts | Same assets/pins; corrupted bytes refused; bounded native calls and isolated reset |
| R10/R11/R18/R21 | AGENTS/README/STATUS/roadmap/workflow/testing/dependency/architecture docs, screenshot/doc/boundary/APK/device guards and script tests | No runtime feature expansion; checks cannot record approval |
| R12 | app review variant/manifest/hooks/tests, MainActivity composition seam, review-device/results scripts/check-full | Distinct app/data/instrumentation IDs; normal app data untouched |
| R29 | PresetsScreen, AppRoot, ViewModelTest | Storage failures stay visible; cancellation preserved; accepted mode writes precede navigation |
| R30/R31 | .gitignore, review-device.py, evidence normalization | Generated caches excluded; non-secure keyguard dismissal is bounded and recorded |

### Remaining acceptance limits (R13)

No listening, TalkBack session, real wired/USB/Bluetooth action or new visual approval was requested
or executed. Focus/route policy injection and semantics/layout tests do not establish those rows.
The real phone endurance test establishes only the monitored software/output/timer/power behavior.
G3/G4 remain open; there is no Stage 5 authorization, commit, publication or regular-app replacement.

### Complete changed-input index

Every changed or added project input is assigned below. Full before/after SHA-256 values
are in `input-changes.json`; generated build/cache/evidence outputs are excluded.
The normalized comparison contains 232 original and 257 final inputs: 25 added, 58 modified,
none removed. All 83 changes have finding IDs; the 6,954 original generated cache entries are
listed separately. No original source input was removed. `inputs-after.json`,
`input-comparison-summary.json` and `preserved-inputs.json` retain final accounting.

| Input | Change | Findings |
| --- | --- | --- |
| `.gitignore` | modified | R30 |
| `AGENTS.md` | modified | R10 |
| `README.md` | modified | R10/R12 |
| `app/build.gradle.kts` | modified | R11/R12/R21 |
| `app/src/androidTest/kotlin/com/thomas/passiveeartraining/AppLaunchTest.kt` | modified | R12 |
| `app/src/androidTest/kotlin/com/thomas/passiveeartraining/PlaybackServiceDeviceTest.kt` | modified | R07/R12 |
| `app/src/androidTest/kotlin/com/thomas/passiveeartraining/SleepTimerCommandTest.kt` | modified | R09/R12 |
| `app/src/main/kotlin/com/thomas/passiveeartraining/MainActivity.kt` | modified | R12 |
| `app/src/main/kotlin/com/thomas/passiveeartraining/app/AppRoot.kt` | modified | R29 |
| `app/src/main/kotlin/com/thomas/passiveeartraining/app/FutureAwait.kt` | added | R19 |
| `app/src/main/kotlin/com/thomas/passiveeartraining/app/PassiveEarTrainingApp.kt` | modified | R08/R12 |
| `app/src/main/kotlin/com/thomas/passiveeartraining/app/PlaybackViewModel.kt` | modified | R19 |
| `app/src/main/kotlin/com/thomas/passiveeartraining/app/PresetsScreen.kt` | modified | R29 |
| `app/src/main/kotlin/com/thomas/passiveeartraining/app/SettingsScreen.kt` | modified | R19 |
| `app/src/main/kotlin/com/thomas/passiveeartraining/app/SetupScreen.kt` | modified | R08 |
| `app/src/main/kotlin/com/thomas/passiveeartraining/app/SetupViewModel.kt` | modified | R08 |
| `app/src/main/kotlin/com/thomas/passiveeartraining/app/TrainingPlaybackService.kt` | modified | R03/R04/R05/R12/R19/R20 |
| `app/src/review/AndroidManifest.xml` | added | R12 |
| `app/src/review/kotlin/com/thomas/passiveeartraining/app/ReviewApplication.kt` | added | R12 |
| `app/src/reviewAndroidTest/kotlin/com/thomas/passiveeartraining/ReviewEnduranceDeviceTest.kt` | added | R12 |
| `app/src/reviewAndroidTest/kotlin/com/thomas/passiveeartraining/ReviewPersistenceDeviceTest.kt` | added | R12 |
| `app/src/reviewAndroidTest/kotlin/com/thomas/passiveeartraining/ReviewProcessDeviceTest.kt` | added | R12 |
| `app/src/reviewAndroidTest/kotlin/com/thomas/passiveeartraining/ReviewReliabilityDeviceTest.kt` | added | R12 |
| `app/src/reviewAndroidTest/kotlin/com/thomas/passiveeartraining/ReviewSessionDeviceTest.kt` | added | R12 |
| `app/src/reviewAndroidTest/kotlin/com/thomas/passiveeartraining/ReviewUiDeviceTest.kt` | added | R12 |
| `app/src/test/kotlin/com/thomas/passiveeartraining/app/FutureAwaitTest.kt` | added | R19 |
| `app/src/test/kotlin/com/thomas/passiveeartraining/app/ViewModelTest.kt` | modified | R08/R19/R29 |
| `core/audio/build.gradle.kts` | modified | R12/R16 |
| `core/audio/src/androidTest/kotlin/com/thomas/passiveeartraining/audio/AudioRendererTest.kt` | modified | R12/R23/R25 |
| `core/audio/src/main/cpp/CMakeLists.txt` | modified | R25/R26/R27 |
| `core/audio/src/main/cpp/jni_library.cpp` | modified | R23 |
| `core/audio/src/main/cpp/renderer.cpp` | modified | R25 |
| `core/audio/src/main/cpp/renderer_reset_test.cpp` | added | R25 |
| `core/audio/src/main/cpp/wav_contract_test.cpp` | added | R27 |
| `core/audio/src/main/kotlin/com/thomas/passiveeartraining/audio/MusicRenderer.kt` | modified | R23 |
| `core/audio/src/main/kotlin/com/thomas/passiveeartraining/audio/pcm/AssetPreparer.kt` | modified | R16 |
| `core/audio/src/main/kotlin/com/thomas/passiveeartraining/audio/pcm/AudioTrackOutput.kt` | modified | R17/R20 |
| `core/audio/src/main/kotlin/com/thomas/passiveeartraining/audio/pcm/PlaybackHeadCounter.kt` | added | R17 |
| `core/audio/src/test/kotlin/com/thomas/passiveeartraining/audio/pcm/PlaybackHeadCounterTest.kt` | added | R17 |
| `core/audio/src/test/kotlin/com/thomas/passiveeartraining/audio/pcm/StagedAssetTest.kt` | added | R16 |
| `core/data/src/androidTest/kotlin/com/thomas/passiveeartraining/data/preset/AndroidPresetStoreDeviceTest.kt` | modified | R12 |
| `core/data/src/main/kotlin/com/thomas/passiveeartraining/data/DataStoreSettingsRepository.kt` | modified | R01/R08/R14/R24 |
| `core/data/src/main/kotlin/com/thomas/passiveeartraining/data/preset/SqlitePresetRepository.kt` | modified | R15 |
| `core/data/src/test/kotlin/com/thomas/passiveeartraining/data/DataStoreSettingsRepositoryTest.kt` | modified | R01/R08/R14/R24 |
| `core/data/src/test/kotlin/com/thomas/passiveeartraining/data/preset/SqlitePresetRepositoryTest.kt` | modified | R15 |
| `core/media/src/main/kotlin/com/thomas/passiveeartraining/media/FocusAndPower.kt` | modified | R09/R20 |
| `core/media/src/main/kotlin/com/thomas/passiveeartraining/media/TrainingMediaService.kt` | modified | R07/R09/R12/R19/R20 |
| `core/media/src/main/kotlin/com/thomas/passiveeartraining/media/TrainingTransport.kt` | modified | R19/R20/R22 |
| `core/training/src/main/kotlin/com/thomas/passiveeartraining/training/BundledVoiceDurations.kt` | added | R01 |
| `core/training/src/main/kotlin/com/thomas/passiveeartraining/training/session/AudioPort.kt` | modified | R03/R04/R05 |
| `core/training/src/main/kotlin/com/thomas/passiveeartraining/training/session/ConfiguredIntervalExerciseSource.kt` | modified | R01/R03/R04/R05 |
| `core/training/src/main/kotlin/com/thomas/passiveeartraining/training/session/DefaultSessionController.kt` | modified | R03/R04/R05/R06/R07/R09/R20/R22 |
| `core/training/src/main/kotlin/com/thomas/passiveeartraining/training/session/SessionContracts.kt` | modified | R07 |
| `core/training/src/main/kotlin/com/thomas/passiveeartraining/training/settings/SetupValidator.kt` | modified | R01/R02 |
| `core/training/src/test/kotlin/com/thomas/passiveeartraining/training/session/ConfiguredIntervalExerciseSourceTest.kt` | modified | R01/R04/R05 |
| `core/training/src/test/kotlin/com/thomas/passiveeartraining/training/session/PlaybackIdentityTest.kt` | added | R04/R05/R06 |
| `core/training/src/test/kotlin/com/thomas/passiveeartraining/training/session/SessionControllerTest.kt` | modified | R03/R06/R07/R09/R20/R22 |
| `core/training/src/test/kotlin/com/thomas/passiveeartraining/training/settings/SetupValidatorTest.kt` | modified | R01/R02 |
| `docs/architecture/ARCHITECTURE.md` | modified | R01/R03/R04/R05/R06/R07/R09/R10 |
| `docs/development/AGENT_WORKFLOW.md` | modified | R10/R12 |
| `docs/development/OPEN_SOURCE.md` | modified | R12/R16 |
| `docs/development/PLAN_REVIEW_2026-10-09.md` | modified | R10 |
| `docs/development/PROJECT_REVIEW_2026-10-11.md` | added | R01–R31 |
| `docs/development/STATUS.md` | modified | R10/R12 |
| `docs/product/ROADMAP.md` | modified | R10 |
| `docs/product/STAGE_3_TASKS.md` | modified | R03/R04/R05/R06/R07/R09/R10 |
| `docs/product/STAGE_4_TASKS.md` | modified | R08/R10/R11/R12 |
| `docs/testing/TEST_STRATEGY.md` | modified | R10/R11/R12/R18/R21/R31 |
| `scripts/capture-ui-candidates.sh` | added | R11 |
| `scripts/check-apk.sh` | modified | R21 |
| `scripts/check-architecture.py` | modified | R21 |
| `scripts/check-docs.py` | added | R10 |
| `scripts/check-fast.sh` | modified | R01/R10/R18/R21 |
| `scripts/check-full.sh` | modified | R12/R18/R21 |
| `scripts/check-native-tests.sh` | modified | R25/R26/R27 |
| `scripts/check-ui.sh` | modified | R11 |
| `scripts/check-voice-durations.py` | added | R01 |
| `scripts/device-smoke.sh` | modified | R12/R18 |
| `scripts/review-device.py` | added | R12/R18/R31 |
| `scripts/review-results.py` | added | R18 |
| `scripts/screenshot-contract.py` | added | R11 |
| `scripts/tests/test_checks.py` | modified | R11/R21 |
| `scripts/tests/test_review_results.py` | added | R18/R26 |
