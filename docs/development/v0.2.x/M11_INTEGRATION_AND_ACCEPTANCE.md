# M11 — Integrated verification and agent handoff

State: PLANNED. Prerequisite: all M0–M10 automated acceptance, including M9A and M9B.
This milestone verifies delivery; it does not authorize commits, signing/publication or a
Stage 7 feature. Read [implementation status](IMPLEMENTATION_STATUS.md) and all unresolved
findings before running another suite. Historical results do not count as this integration.

## Outcome and scope

All eight modes work through one service, saved settings, five slots per mode and three learning
recipes. New musical fixtures, asset checks, migrations, UI checks and isolated-phone tests are
part of canonical verification. The final report accounts for every input change and separates
automated results from human acoustic/pronunciation/accessory/TalkBack/visual acceptance.

Own integration tests/scripts and documents plus narrowly scoped repairs demonstrated by them.
Fix defects in their owning subsystem with failing regressions; do not introduce an unrelated
framework or dependency upgrade at the finish. Preserve all original fixtures, assets, evidence
and planDraft.md. Do not silently amend a milestone's expected behavior to accommodate a bug.

## M11.1 — Source and MCP review

1. Inventory provider registration, codecs, defaults, required voices, Play/Setup/Presets routes
   and starter IDs. Exactly eight production kinds must be playable; no completed mode routes
   to a placeholder or “Soon” control. No test provider may be registered in production.
2. Recheck imported modules/dependencies/run configurations against Gradle. Use Android Studio
   MCP `get_file_problems`, symbol search and bounded call analysis where actually available.
   Trace Start, saved revision→switch, live edits, answer publication, timer and teardown.
3. Inspect actual field transfers, not just matching type names. Revisit all concrete models
   for mutable collections, root/bass confusion, context/target confusion and answer leakage.
4. Audit coroutine cancellation, single-writer ownership, output segment races, stale callbacks,
   focus/route handling, wake locks, PCM/timeline retention and failed persistence/reconnect.
5. Inspect final debug/release/review manifests for INTERNET and unintended permissions; check
   backup declarations, native ABIs/page alignment, license notices and no runtime Python/TTS.
6. Record unusable MCP calls/import gaps with exact evidence. CLI compilation does not prove
   IDE semantics are clean. Continue independent source/tests; do not claim an inspection pass
   from an empty tool result or spend the milestone upgrading the IDE/toolchain incidentally.

## M11.2 — Mandatory automated matrix

| Area | Minimum scenario coverage |
| --- | --- |
| Providers | All eight × Recognition/Exposure/Association; defaults plus maximum-complexity valid configurations. |
| Theory | Frozen interval fixtures unchanged; all chord/scale/key/context templates independently checked; melodic graph oracle cases. |
| Duration | Below/exactly/above 60 s, both rates, every answer format, equivalent chord alternatives, longest melody speech, repeats/cues. |
| Identity/history | Colliding generator indices across modes, rapid Stop/Start, live recipe/seed/gain edits and internal history replay; original item sound preserved. |
| Switching | All 56 ordered distinct-mode pairs at least once; exhaustive phase/race tests on controllable host output; representative phases on phone. |
| Storage | Settings 1→2, SQL 1–8→9, fresh schema, rollback faults, future schemas, legacy adoption, all 40 slots and revision races. |
| Visibility | Hidden before first token and after preference toggle across UI, accessibility semantics, snapshots and Media3 metadata. |
| Lifecycle | Launch, rotate/recreate, Home/rebind, task removal, force-stop/relaunch stopped, controller disconnect/reconnect and failed futures. |
| Failures | Generation, compile, asset, render, short/error output write and save/migration boundaries; error observable and resources released. |
| Limits | 20 historical items, two PCM entries, 48 MiB PCM, no globally retained timelines, bounded multi-token/graph/asset allocations. |
| UI | Every mode's three tabs, selectors/dialogs/errors/pending switch, 150%/200% text, complete candidates and semantic/layout assertions. |

Prefer host virtual time for exhaustive concurrency/timer permutations. Do not replace elapsed
phone endurance with accelerated virtual time or replace acoustic checks with an audio waveform
claim. Parameterized tests should assert behavior, not duplicate implementation statements.

## M11.3 — Isolated phone harness

Extend existing review sources/tests and `review-device.py`; do not create a second ad-hoc ADB
runner. Discover packaged application/instrumentation IDs from built artifacts. Use only the
isolated `com.thomas.passiveeartraining.review` target and its associated test packages. Preserve
the regular app's installation/data, user applications and global network settings.

Preflight records authorized explicit ANDROID_SERIAL locally, model/API/ABI/page size, tool
versions, APK hashes, config/seed/provider versions and asset manifest hashes. Device serials
remain in ignored evidence. Install/update review artifacts and assert every named suite actually
ran with nonzero expected test counts; a successful empty instrumentation selection is failure.

Short suite must cover the matrix above, including all mode/recipe starts and ordered switches.
Implement controlled phone synchronization against service phase/consumed frames with bounded
timeouts, not arbitrary sleeps that assume a phase. Keep test hooks restricted to review builds;
verify they cannot be exported/called in normal production packages.

Use the existing test-owned audio-focus requester for denial/loss/ducking and recovery, plus
injected route events. Label these simulated policy tests separately from physical accessory
disconnection. Record focus/wake-lock/output ownership on both success and error cleanup.

Capture instrumentation XML, logcat, crash/ANR buffers, rendered screenshots/layout results,
memory/service/timer samples and terminal cleanup. Restore font/display or other device settings
changed by the harness in a finally block, including partial setup failure. Keep all tests bounded
by watchdogs, and stop review playback on timeout, assertion failure and interruption.

## M11.4 — Real 30-minute screen-off endurance

Before the measured run, warm all provider/asset paths through automated short exercises so
one-time initialization is distinguishable from retention. Record warmup separately; it does not
count toward the 30 minutes. Start the measured session with a real elapsed-time 30-minute sleep
deadline and screen off, with no additional test wake lock keeping the session alive.

Configure valid representative modes, including four-note chords, both-direction scales,
context cadences, nearest-voiced progressions and a long valid melody. During the measured run,
request mode changes on a declared schedule without resetting the timer, e.g. every three
minutes through all eight modes, then alternate progressions/melody for the remaining time.
Record requested/committed elapsed timestamps, consumed progress and pending transitions. The
same original elapsed deadline must remain visible across the entire run.

Sample progress, active/pending kind, service/timer state and memory using the existing external
harness mechanism; sampling must not acquire an app wake lock or repeatedly wake the display.
Check the production sleep deadline's terminal stopped state, no pending mode, silent/released
output, no held focus/wake lock and no stale restart during the bounded post-deadline window.
Document the existing timer tolerance and watchdog actually used; do not move them after failure.

Keep the current endurance memory guard: at least 25 samples within elapsed 180–1740 seconds,
spanning at least 24 minutes, with display-off evidence and PSS variation ≤65,536 KiB before
shutdown, as implemented in `review-results.py`. Report baseline,
peak, final, range and sample times, not only PASS. Also assert explicit controller/PCM counts
through review-only diagnostics; PSS alone cannot prove the 48 MiB cache bound. Repeat bounded
start/stop/error cycles and check resources return to the expected plateau. A sustained upward
trend needs investigation even if it narrowly fits the coarse PSS range guard.

## M11.5 — Screenshot and asset acceptance accounting

Run strict screenshot comparison and retain its result. New candidates and specifically declared
changed cases use M2's explicit report mode; pending visual approval is not a strict screenshot
pass. Require complete case sets, valid images, no undeclared differences and baseline hashes
unchanged. Do not record/replace a golden as part of `check-fast`, `check-full` or a repair retry.

Asset machine checks must cover every bundled semantic ID, duration/provenance hash and WAV
constraint. Keep original approved interval speech evidence. New speech pronunciation/listening
rows remain pending unless actual owner review has occurred; automated-only completion does not
set humanAccepted=true or make `--require-reviewed` pass by removing required entries.

## Canonical execution and failure handling

After source review and targeted fixes, use the environment from `scripts/env.sh`. All direct
Gradle commands retain `--dependency-verification strict`. Final integration command:

```bash
EAR_NATIVE_EVIDENCE_DIR=.local/verification/mode-expansion/M11/native EAR_REVIEW_OUT=.local/verification/mode-expansion/M11/device ./scripts/check-full.sh --automated-only
```

The extended workflow must include fast checks, independent music fixtures, assets/native tests,
debug/release/review builds, lint, packaging/manifests, strict/pending visual accounting, all
connected suites and the real endurance test. Log exactly what ran fresh versus UP-TO-DATE/
FROM-CACHE. For a final execution claim, ensure the relevant test tasks/harness run fresh using
the existing script's freshness controls or add a documented narrowly scoped rerun option.

On failure, preserve original logs/APKs/configs, create a stable finding ID, add a regression,
repair the owning code, and rerun the affected checks. Repeat the final full suite when a code/
asset/config change invalidates its prior result. Do not repeatedly rerun a passing 30-minute
session merely to inflate evidence. Missing device/tool prerequisites are OPEN, never inferred
passes from old logs. An incomplete full run prevents automated completion.

## Completion and final report

| ID | Exit requirement |
| --- | --- |
| M11-A01 | All provider and shared acceptance IDs have current evidence; every placeholder has a production consumer. |
| M11-A02 | Final canonical automated workflow and real elapsed endurance pass; no unexplained crash/ANR/failure. |
| M11-A03 | All old input changes/new inputs accounted for by final hash inventory; old fixtures/assets/baselines preserved as specified. |
| M11-A04 | Architecture, storage/provider/seed/voice contracts, roadmap, testing, dependencies, STATUS and implementation ledger agree. |
| M11-A05 | Human/unsupported rows explicitly listed; no claim of closed G3/G4 or publication/release authorization. |
| M11-A06 | Review playback stopped, device settings restored, regular app/data preserved, no debug hook or INTERNET in production. |

Report feature availability, exact automated pass/fail/unexecuted counts, migration compatibility,
evidence paths and source/APK/asset identities. Mark delivered software `AUTOMATED_COMPLETE /
HUMAN_OPEN` when appropriate. Name acoustic quality, pronunciation, actual accessory behavior,
TalkBack usability and visual approval separately. Save a concise resume point for any remaining
work; never describe an unexecuted plan or historical result as completed implementation.
