# Verification strategy and acceptance evidence

This file describes current checks and later required additions.
Checks that have passed, and their evidence, are recorded in
[STATUS](../development/STATUS.md); this file is the strategy they follow.
Reviewed 2026-10-11. The dated project ledger records new executions separately from
historical results. Stage 0–4 repairs and isolated automated phone coverage are implemented;
unsupported human/physical acceptance still keeps G3/G4 open.

## Current commands and exact coverage

| Command | Current behavior |
| --- | --- |
| scripts/check-env.sh --build | JDK 17 java/javac, SDK platform/build tools and wrapper prerequisites |
| scripts/check-env.sh --all | Adds native/FFmpeg/ADB/SDK/KVM/Git diagnostics; displayed Studio SDK/JDK and Gradle import need separate inspection |
| scripts/check-fast.sh | Python guard tests, static core boundaries, prerequisites, checksum metadata, Spotless, every core module's JVM tests (music, training, audio, media, data), the app ViewModel tests, app debug assembly/lint |
| scripts/check-full.sh --automated-only | Fast/assets/native fixtures, fresh nine-image UI verification, debug/release/review build/lint/APK inspection, NDK probes, isolated connected tests and real 30-minute screen-off endurance; `--skip-endurance` keeps every other stage and records the exclusion (owner decision 2026-10-15, M11 endurance evidence cited instead) |
| scripts/qa.sh [--with-endurance] | One-command whole-project QA: runs check-full --automated-only --fresh (default adds --skip-endurance for the owner decision of 2026-10-15 and cites the recorded M11 evidence; `--with-endurance` runs the real 30-minute session instead and the report/device scan then require its PASS and memory-guard markers), the reviewed-asset gate, strict screenshot gate, light candidate report (zero pending required), dark review re-capture, packaged-APK audit and a device crash/ANR scan; writes QA-REPORT.md into its own timestamped evidence folder and always ends with the open human rows |
| scripts/check-apk.sh <apk>... | Inspects packaged permissions, assets, native ABIs/alignment and release diagnostics exclusion; rejects INTERNET/ACCESS_NETWORK_STATE |
| scripts/install-debug.sh | Builds/inspects/installs/launches selected device |
| scripts/record-dependencies.sh | Deliberate checksum update; not verification |
| scripts/check-native-tests.sh | Production-source host build/CTest and WAV fixtures/numeric assertions |
| scripts/check-audio-tools.sh | Python asset/audio-tool unit tests |
| scripts/device-smoke.sh --automated-only | Isolated review app/test APKs, launch/controller/transport/answers/timer/persistence/UI/focus/reliability tests, force-stop/relaunch and real 30-minute screen-off session; --smoke-only excludes endurance |
| scripts/check-ui.sh | Strict owner gate: manifest set contract plus fresh comparison of every case against its approved baseline; rejects overrides/recording and missing/extra images |
| scripts/capture-ui-candidates.sh [DIR] | Generate ignored candidates from the manifest; refuses the baseline directory; no approval |
| scripts/screenshot-report.py --candidates DIR [--manifest M] | Report mode: approved cases must be pixel-identical; a declared change must stay inside its declared region and is only PENDING_VISUAL_APPROVAL; missing/malformed/undeclared or stale declarations fail |

check-full inspects debug, release and review APKs. It installs only the review app/test
packages and isolated library test APKs. The NDK probe alone does not prove synthesis/JNI/alignment; the separate native
packaging/host-render checks provide their named evidence. A generic Studio build proves
neither the complete script suite nor device acceptance.
Static architecture checks supplement code review; they are not a Kotlin semantic
dependency analyzer. Current module tests cover substantial domain/audio/media/data rules,
but isolated rule tests do not prove their integration into the production playback path.

Missing/ambiguous devices make check-full nonzero/blocked. A host pass must not be
reported as a full pass. ANDROID_SERIAL chooses the intended device.

## Studio MCP evidence

Follow [ANDROID_STUDIO_MCP](../development/ANDROID_STUDIO_MCP.md). First verify Gradle import
and analyzable content roots. The earlier single generic module was repaired; all six
modules, dependency entries and app run configuration were rechecked on 2026-10-11.
Record SDK/JDK inspection separately. After import, use call/symbol traces to
review wiring and get_file_problems/lint_files on affected sources. Record missing/incomplete
results, not a fabricated clean inspection. SDK/Lint, strict Gradle checks and component tests
remain necessary; an IDE inspection is supplemental evidence.

Run targeted configurations only after discovery; ensure strict arguments/environment/device
selection. Capture final exit status and full logs even if MCP output is truncated or times out.
No dedicated Android screenshot/accessibility/audio/profiler API was exposed by this server.

## Required regression additions for the reopened integration

| Owner | Meaningful new coverage |
| --- | --- |
| WP3.1a | One Start automatically consumes multiple items; delayed consumption vs submission; silent paused navigation; selected identity after PCM eviction; live answer marker; EOF/Stop races |
| WP3.3a | No output submission before acknowledged focus grant; denial/delayed grant/Stop races; route recreation and owned-scope/native/output teardown |
| WP3.4a | Actual expiry effects, before-sound deadline checks, paused/preparing expiry, output fade reset/drain and service cleanup; command acceptance alone is insufficient |
| WP4.1c | Full saved/imported configuration reaches generator/recipe/stop execution; seeded preset use reproduces sequence; mixed/fixed-root feasibility and v1/v2 preservation |
| WP4.2a | Gain limits agree with renderer; current/old/future settings and explicit recovery; future-schema writes blocked; backup/extraction declarations |
| WP4.3f / WP4.4a | Real UI/controller/reconnect/navigation/draft state; named custom preset operations; document IO success only after close; picker cancellation/partial failure |
| WP4.3g | Fixed-environment UI rendering, answer semantics and human-reviewed first screenshot baselines; debug leak checks plus separate native/total memory evidence |

Add regressions with their repaired consumers, then targeted checks and check-fast; run full
at integration. Existing app device XML contains four tests and data XML four store tests.
Neither those counts nor the unchecked smoke checklist establish the added coverage above.

## Required coverage as the app expands

| Gate | Automated evidence | Physical/human evidence |
| --- | --- | --- |
| G1 | MIDI/interval tables, validation, PRNG bag fixtures, timeline fixtures | None required |
| G2 | Asset schema/hash/audio metrics, native lifecycle, same-renderer WAV checks, APK/ABI/alignment | Voice/instrument listening and phone PCM sequence |
| G3 | State/command races, timer virtual time, focus/routes, Media3 integration | Locked 30-minute session, controls, interruptions and actual expiry |
| G4 | Recipes, settings, migrations/import, navigation and reviewed UI baselines | Daily-use session, accessibility, export/restore |
| G5 | Independent musical formulas, providers, asset union and audio fixtures | New labels/instruments reviewed on phone |
| G6 | Feasibility/path generation, weighted cycles, cents checks, mixed seeds | Advanced/mixed session smoke |
| G7 | Idempotent accounting, retention, ratings/weights, migrations | Metric labels and privacy behavior review |
| G8 | Strict/minified/signed build, manifests/native checks, install/update/restore | Full route/power matrix and signing-key backup confirmation |

When adding modules, expand check-fast/full to execute their real tests/lint.
Preserve strict dependency verification. Every planned check command has an owner
in AGENT_WORKFLOW and must be created with its first consumer.

## Domain and deterministic fixtures

Use existing JUnit for exhaustive finite models and scripted random-source tests.
Use fixed-seed large invariant runs for generated exercises. The expected sequence
records normalized config, generator/RNG versions, seed, concrete pitches and cycle
position. Document random draw order.

The default fixtures include ascending/descending/harmonic intervals and unison.
Test impossible configurations by asserting a bounded failure, not waiting for a
timeout. Reject caller-mutable collection leakage.
Independent music21 fixtures supplement hand-reviewed theory tables from Stage 5.
Do not update goldens when an unexplained result changes.

For session/time tests use kotlinx-coroutines-test and Turbine with one coordinated
elapsed clock. Test just-before/exactly-at/after deadlines and completion-vs-stop
races. No real sleep is necessary for these rules.

## Audio metrics and limitations

Use a host harness built from the production renderer sources. Python analysis uses
NumPy/SciPy/soundfile and marker metadata; a second unrelated synth cannot validate
production timing. Keep assets fixed and record source hashes/settings.

| Measurement | Initial acceptance |
| --- | --- |
| Absolute event boundaries | Within one sample frame of rational conversion |
| PCM length | Exact scheduled frame count |
| Gaps | Zero samples outside explicitly allowed note envelopes |
| Float safety | No NaN/Inf, no samples outside [-1,1], no unexplained saturation |
| Production voice format | 48 kHz, mono PCM16 WAV, positive and <=8 s |
| Voice processing | Target -20 LUFS and <=-3 dBTP; short-clip fallback measured/reviewed |
| Sine frequency | <=1 cent on a sufficient steady-state window |
| Piano frequency | Initial <=15 cents on reviewed MIDI 48..84 fixtures using harmonics |
| PCM cache | <=48 MiB combined current/next; <=60 s each |
| Cancellation | No stale-session PCM submitted after accepted stop/token invalidation |

Numeric PCM results may vary slightly by architecture. Avoid binary WAV golden hashes
for generated synthesis; hashes are appropriate for fixed shipped assets.
A high harmonic may exceed a fundamental's FFT magnitude; do not misdiagnose pitch
from a single largest spectral bin. Intonation checks use pure tones.

Offline samples prove the renderer, not physical AudioTrack timing or Bluetooth
acoustic delay. Measure queued/consumed position, underruns and actual stop latency
on device. Report output timestamp confidence and fallback behavior.

## Native compatibility

For every shipped ABI inspect dependencies and ELF LOAD segment alignment, and run
SDK zipalign with the 16 KiB page-size check on the packaged APK.
Use NDK llvm-readelf, SDK zipalign/apksigner and official page-size instructions.
A successful NDK r28 compiler probe alone is insufficient.

Verify runtime on normal API 29/36 emulators and phone. Add a 16 KiB system-image/device
test at G8, record getconf PAGESIZE and ensure compatibility mode does not hide failure.
If no such runtime is available, mark runtime validation open while retaining static
alignment results.

## Physical background matrix

At G3 complete the core rows on the user's phone. At G8 repeat after all features and
release optimization. Record start/end, model/API/ABI, revision, config/seed, assets,
route and outcome for each row.

| Scenario | Expected outcome |
| --- | --- |
| Screen on/off, Home, another app | One continuous active session |
| Locked speaker, 30 minutes | Correct sequence, no recurrent underruns, timer completes |
| Bluetooth media play/pause/next/previous | Same command semantics as UI |
| Bluetooth disconnect/reconnect | Stop and flush; no automatic restart |
| Wired/USB unplug where available | Stop; no unintended speaker continuation |
| Other audio/focus denial/duck | Denial silent; loss stops; duck lowers gain; explicit Start after loss |
| Incoming call | Focus-loss policy stops; actual call behavior remains a human acceptance row |
| Paused for 30 minutes | No wake leak; expiry checked; service death restores stopped |
| Timer immediate/finish-current/fade | Stops according to policy; bounded queued tail |
| Rotation/recreate/rebind | No duplicate engine, changed seed or lost controls |
| Task swipe-away during playback | Stops and releases resources; relaunch stays stopped |
| Process kill and relaunch | Stopped defaults at G3, saved setup after G4; no old deadline resurrection |
| Battery saver/low battery | Record observed continuity/limitations and cleanup |
| Repeated start/stop/error | No stuck notes, focus/wake leaks or native growth |
| Airplane mode/no data connection | Complete session and local settings/presets work |
| Notification permission denied | Platform-consistent media controls and visible state |
| G8 two-hour mixed session | Bounded memory, correct sequence/accounting/stop |
| G8 signed APK upgrade | Presets/history retained; migrations succeed |

If the phone has no wired/USB route or another needed accessory is unavailable, label
that row untested and describe supported routes; never label it PASS.
Automated controller tests and manual route/phone actions are both necessary.

## UI, persistence and release

UI baselines: fixed emulator/Robolectric API, size, density, font scale, locale and
theme. Cover light/dark and 150%/200% text, basic/advanced forms, errors, player phases,
timer, presets, settings and later progress. First and changed baselines require
visual review. Do not auto-approve screenshots to make a build green.

The case list, baselines and declared pending differences live in
`app/src/test/screenshots/manifest.json`; scripts derive their expected set from it instead of
hard-coding image names. Two entry points exist: `scripts/check-ui.sh` is the strict gate and
fails on any pixel difference, while `scripts/capture-ui-candidates.sh DIR` followed by
`python3 scripts/screenshot-report.py --candidates DIR --report-only` is the automated report.
The report exits nonzero for undeclared differences, out-of-region pixels, stale declarations,
missing or malformed candidates; a correctly bounded declared change is reported
PENDING_VISUAL_APPROVAL and never approved. `check-full.sh --automated-only` uses the report
path with `EAR_UI_CANDIDATES_DIR` and records that a pending visual row remains open.

Preset-store tests freeze the v1 schema, run the migration chain from a frozen fixture and
compare a migrated database's shape with a fresh one; no destructive reset. Host tests run
the production schema and SQL through a real SQLite engine (sqlite-jdbc, test-only), and the
Android adapter gets its own connected test. DataStore tests include corruption/defaults and
singleton ownership. Preset tests bound bytes/counts before parsing, reject invalid
schema/music values, preview conflicts and verify atomic commit. Test fresh-app import
independently from an in-memory round trip.

First-baseline work includes implementing/pinning the harness and creating check-ui, then
rendering review candidates. It is not currently just an owner-approval task. Preserve separate
record and verify operations. Test restore with isolated test data; routine checks must not
clear the owner's saved app data. When comparing same-seed playback, carry the seed through
normal preset use and Start rather than comparing only decoded repository values.

Inspect both packaged manifests for INTERNET after every dependency integration.
Add dependency/license records, native source/asset notices and release signatures
to full/release checks. Check minified release JNI, serialization, DI and preset-store
paths, not only debug instrumentation.

## Evidence and completion

Local evidence: .local/verification/<package-or-gate>/. Commit a concise summary in
STATUS with commands, date, outcome, environment and any manual reviewer result.
Do not fabricate phone actions, listening quality or cold-bootstrap success.
When work is documentation-only, validate links/structure/consistency and source
integrity; do not run unrelated application builds to imply new feature verification.

Sources: [Android 16 KiB support](https://developer.android.com/guide/practices/page-sizes),
[Media3 background behavior](https://developer.android.com/media/media3/session/background-playback).

## Isolated automated review workflow (2026-10-11)

The review variant is `com.thomas.passiveeartraining.review`; its instrumentation ID/runner
and library test IDs are read from packaged manifests. Regular app installation and data are
untouched. Select the authorized phone explicitly with ANDROID_SERIAL when ambiguous.
Direct Gradle commands always use `--dependency-verification strict`; the review test build
selects `-Pear.device.variant=review`. No checksum or baseline recording occurs.

The driver rejects empty, failed, ignored, crashed or incomplete instrumentation results even
when ADB exits zero. Each successful suite produces XML plus raw runner output. Bounded
watchdogs stop review playback in cleanup. Evidence includes packaged IDs, APK/input hashes,
phone API/ABI/page size, app-UID logs/exit info, screenshots, cycle memory and endurance samples.
Regular app APK/private-file hashes are compared before/after. No global font/network setting,
personal app, call or accessory is manipulated. Review-only font configuration exercises 150%
and 200% semantics; rotation is unfrozen during test cleanup and initial display power restored.
Before UI tests, the platform wake/dismiss command removes a non-secure keyguard left by an
earlier screen-off run. A still-covering keyguard fails the precondition; credentials are never
bypassed. This changes no global lock or display-timeout setting.

Endurance runs a real 30-minute sleep deadline with Home and display off, without a test wake
lock. Samples check monotonically consumed progress, timer state, bounded PCM, service/power
state and memory; terminal output/renderer/focus/wake release and no stale restart are asserted.
After warmup, at least 25 display-off memory samples must span 24 minutes with total PSS
variation at most 64 MiB. Shutdown samples are excluded because resource release reduces PSS.
Injected route events are policy tests, not actual accessory disconnect evidence. Focus requests
are test-owned. Force-stop/relaunch restores saved setup and remains stopped.

Acoustic quality, real wired/USB/Bluetooth behavior, TalkBack usability and new visual approval
remain unverified. Automated semantics/layout/screenshot agreement cannot close those rows.
