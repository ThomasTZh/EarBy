# Implementation roadmap

Reviewed 2026-10-11 against the implemented Stage 0–4 app. G0-host/device/IDE and G1/G2
have historical acceptance; G3/G4 remain open for unsupported human/physical acceptance.
The authorized Stage 0–4 review and repairs are recorded in
[STATUS](../development/STATUS.md) and [the review ledger](../development/PROJECT_REVIEW_2026-10-11.md).

**Current gate note (2026-10-25):** the owner reports **G3 PASS** and **G4 PASS** after testing
0.9.33 / versionCode 42. Full QA passed on 2026-10-26 (1117 host tests); G8 remains open for the
2-hour lane, 16 KiB runtime, upload-key signing, separator provenance and publisher contact. G6 is
complete under the reduced scope; G5 and G8 remain eligible. G7 is dropped. No new implementation
scope has been authorized.

Current owner-approved scope: Play/Setup/Presets tabs, Start/Stop, recognition/exposure/
association, five user slots, live edits preserving current/backward items, and stopping on
task removal. Older recall/deep-repetition, pause/mini-player and document-picker UI requirements
are superseded. Existing data formats and migrations remain supported; do not reintroduce UI.

The owner's 2026-10-11 instruction authorizes the
[v0.2.x mode expansion](../development/v0.2.x/README.md) (M0–M11): the seven existing mode
placeholders filled through one shared pipeline, per-mode storage and presets, consumed-frame
mode switching, theory fixtures, voice assets and automated verification. This track supersedes
the earlier Stage 5 plan for these modes; unrelated Stage 5/6 work stays unauthorized. It may
proceed while G3/G4 human rows remain open; that does not close those gates.

## 1. Read this plan in order

| Document | Responsibility |
| --- | --- |
| [STATUS](../development/STATUS.md) | Evidence of completed work, open gates, next eligible task |
| [ENVIRONMENT](../development/ENVIRONMENT.md) | This Nobara workstation, tool paths, host/device commands |
| [AGENT_WORKFLOW](../development/AGENT_WORKFLOW.md) | Rules inherited by every work package and completion report |
| [ANDROID_STUDIO_MCP](../development/ANDROID_STUDIO_MCP.md) | Installed tool inventory, import readiness and IDE-assisted workflow |
| [ARCHITECTURE](../architecture/ARCHITECTURE.md) | Module ownership, public contracts, timing and failure behavior |
| [OPEN_SOURCE](../development/OPEN_SOURCE.md) | Selected reusable packages, introduction stages, provenance and pinning |
| [STAGE_1_TASKS](STAGE_1_TASKS.md) | Pure Kotlin interval domain and timelines |
| [STAGE_2_TASKS](STAGE_2_TASKS.md) | Voice/SoundFont assets, native rendering and audio proof |
| [STAGE_3_TASKS](STAGE_3_TASKS.md) | Session state, Media3, interruptions and sleep timer |
| [STAGE_4_TASKS](STAGE_4_TASKS.md) | Usable interval app, settings, five preset slots and retained data formats |
| [STAGES_5_TO_8_TASKS](STAGES_5_TO_8_TASKS.md) | Core/advanced exercises, history, reliability and release |
| [TEST_STRATEGY](../testing/TEST_STRATEGY.md) | Commands, evidence, tolerances and device matrix |

This roadmap controls scope and ordering; architecture controls shared contracts;
work packages control their concrete implementation; status records facts.
Update conflicting documents together. The original plan is preserved as historical context and is not part of this snapshot. Its milestone numbers are superseded by the
stage/work-package IDs here.

The user's 2026-10-11 instruction authorizes the dated Stage 0–4 review and repairs.
Package descriptions do not authorize unrelated future implementation or Stage 5.

## 2. Product contract

Build a personal Android 10+ application that generates musical exercises locally and
plays bundled English answers. Primary use is a continuous listening session with the
screen locked. Ship a locally signed APK installed with ADB.

Required behavior:

- Offline operation, with no account, backend, runtime AI/TTS, telemetry, ads or app
  network access. Neither debug nor release APK may request INTERNET permission.
- Piano first; simple intervals first; the first useful outcome is a 30-minute
  background interval session with working media controls and a sleep timer.
- Recognition defaults to example, thinking silence, spoken answer, final silence.
  Answer text stays hidden until its scheduled answer frame is consumed, including notifications,
  Bluetooth metadata, accessibility announcements and the mini-player.
- Basic controls remain simple. Advanced controls expose roots, range, timing, seed,
  presentation and, at their later stages, weighting and learning modes.
- Store settings, presets and optional listening history locally. Disable OS cloud
  backup. Five user slots are local; document import/export APIs remain for compatibility
  without a current document-picker UI.
- Report listening/exposure, with optional self-ratings. Do not claim measured accuracy
  or promise perfect pitch or passive-learning outcomes.

Out of scope: Play Store publication, billing, microphone quizzes, speech recognition,
MIDI hardware input, streaming, downloaded content in the app, iOS/web clients and sync.

## 3. Adopt the existing system

Use Nobara Linux 44, the existing repository and installed Studio. Do not reinstall the
OS, replace system Java, create another app scaffold or upgrade the toolchain to follow
the draft. The workspace catalog is the build source of truth:

| Setting | Existing baseline |
| --- | --- |
| Workspace | <checkout> |
| Studio | $HOME/Applications/android-studio |
| Gradle runtime | .local/jdk-17; Temurin 17.0.20.1 |
| Android SDK | .local/android-sdk |
| AGP / Gradle / Kotlin plugins | 9.4.0 / 9.6.0 / 2.4.20 |
| Android API | minSdk 29; compileSdk 36; targetSdk 36 |
| SDK build tools / NDK / CMake | 36.0.0 / 28.2.13676358 / 3.22.1 |
| Emulator coverage | ear-api29 and ear-api36; x86_64 |
| Existing modules | app, core:music, core:training, core:audio, core:media, core:data |

The host has an i5-12600K, approximately 31 GiB RAM and 376 GiB free at review.
System Python is 3.14.7; voice tooling uses a separate Python 3.11 environment managed
with installed uv. FFmpeg is installed. GPU access was unavailable from this execution
environment, so CUDA availability is unconfirmed; a CPU voice route is specified.

Keep the verified baseline. AGP's [compatibility table](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
supports this JDK/Gradle/tool choice. Supporting API 37 does not require compiling
this project against it.

## 4. Stage gates and task order

A gate is PASS with evidence or OPEN/PARTIAL with named missing evidence. A task may
finish its host portion while its device gate remains open. Do not label a whole stage
complete because only a build passed.

| Stage | Ordered work packages | Exit gate |
| --- | --- | --- |
| 0 Preparation | G0-host review; G0-device/IDE closure | Host scaffold verified; phone launch and IDE settings recorded separately |
| 1 Interval domain | WP1.1 model → WP1.2 generation → WP1.3 timeline | G1: deterministic JVM fixtures, boundaries, app build |
| 2 Audio proof | WP2.1 assets → WP2.2 native → WP2.3 PCM/output → WP2.4 diagnostics | G2: notes/silence/answer verified in WAV and on phone; asset review; ABI checks |
| 3 Background proof | WP3.1 state → WP3.2 Media3 → WP3.3 focus/routes → WP3.4 timer → WP3.5 reliability | G3: 30-minute locked-screen session, controls, interruptions, expiry and cleanup |
| 4 Intervals alpha | WP4.1 recipes → WP4.2 settings → WP4.3 UI → WP4.4 presets/export → WP4.5 acceptance | G4: reproducible daily-use interval session, persistence and accessibility |
| 5 Core training | WP5.1 provider seam → WP5.2 chords → WP5.3 scales → WP5.4 notes → WP5.5 degrees | G5: all providers share playback, validated vocabulary and musical fixtures |
| 6 Advanced training | **Reduced by owner 2026-10-25:** interval walks only; contrast and the other G6 features are dropped. Progressions/functional intervals and melodies already exist. | **G6 PASS in 0.10.0** for finite interval walks. No tuning/mixed/cluster gate. |
| 7 History/refinement | **DROPPED by owner 2026-10-25** (WP7.1 exposure → WP7.2 ratings/adaptation) | No G7. No user-facing history, ratings or adaptation. |
| 8 Release | WP8.1 reliability → WP8.2 optimization → WP8.3 signing/backup | G8: signed APK, device report, source/asset provenance and restore instructions |

G0-host, G0-device, G1 and G2 have recorded passes (see STATUS). G0-IDE has recorded acceptance and its imported model was rechecked on 2026-10-11.
Record incomplete inspections separately; IDE diagnostics supplement canonical checks. CLI/source work remains available; IDE connectivity never closes a phone gate.

The original stage table defines product milestones. Existing Stage 4 consumers were built
while G3's human rows remained open, and the review now finds integration gaps as well.
Repair these existing consumers in this explicit order; do not restart completed asset work:

| Order | Remaining package | Exit evidence |
| --- | --- | --- |
| 1 | G0-IDE setup in ANDROID_STUDIO_MCP | Displayed SDK/JDK settings, sync, imported model and usable inspections/client tools |
| 2 | WP3.1a → WP3.3a → WP3.4a | Continuous consumption-driven queue, silent paused navigation, focus-before-sound and actual timer effects |
| 3 | WP4.1c → WP4.2a → WP4.3f → WP4.4a → WP4.3g | Complete saved configuration, persistence/limits, screen controls, named presets and UI regression harness |
| 4 | WP3.5 → WP4.5 | Physical G3 and G4 reports plus reviewed visual baselines |
| 5 | WP5.1 onward | Begin expansion only after G3/G4 PASS |

Repair packages are defined in the Stage 3/4 task documents and inherit their original
scopes. This exception allows completion of existing Stage 4 consumers before G3 closure;
it does not waive acceptance or permit Stage 5 early.

Within a stage, work sequentially. Do not create empty future modules. An asset review
can remain pending while independent native work proceeds, but placeholder assets
never satisfy G2. Stages 5–7 are expansions after a usable G4 alpha.

## 5. Shared defaults and limits

These are initial product decisions, not performance claims.

| Item | Default / limit |
| --- | --- |
| Tuning | 12-tone equal temperament, A4 = 440 Hz |
| Interval configuration | C4..C5 inclusive, all root pitch classes, minor third/major third/perfect fifth, ascending |
| Instrument | Bank 0, program 0; one EARP1 instrument pack (V4 dark calibration) |
| Recognition timing | 700 ms notes, 100 ms melodic gap, 2 s thinking, 2 s final silence; owner-reviewed thinking default changed from 4 s on 2026-10-09 |
| Voice | One reviewed English pack; mono 48 kHz PCM16 WAV |
| Session | Unlimited until stopped; timer off until selected |
| Initial timer | Duration 1..180 minutes; immediate stop; optional 0..60 s fade |
| Numeric UI timing limits | Notes 100..4000 ms; gaps 0..30000 ms; repeat counts 1..8 |
| Compiled exercise cap | 60 s, including speech and all pauses; reject configurations exceeding it |
| PCM cache | Current and next exercise only; 48 MiB combined budget at supported 44.1/48 kHz stereo float32 |
| Replay history | Last 20 concrete exercise definitions; PCM regenerated if evicted |
| Voice clip bound | Positive length, at most 8 s per phrase; composed answers must fit exercise cap |
| User gains | Stored music/voice range 0..2; owner-approved centered sliders and defaults music 1.0 / voice 0.25 |
| Process death/reboot | Restore saved setup in stopped state; never start sound automatically |

The domain pitch model supports MIDI 0..127. Alpha UI defaults remain in the piano's
comfortable range; broad user selections must still be feasible and audible.
A low/high register may be mathematically valid but must pass audio review.

## 6. Corrections and traceability from the original draft

| Draft area | Decision / destination |
| --- | --- |
| Ubuntu setup, API 37 and blanket latest versions | Replaced by observed Nobara environment and existing verified pins |
| Complete theory engine before playback | Minimal interval slice in Stage 1; vocabulary expands in Stage 5 |
| Handwritten infrastructure | Adopt OPEN_SOURCE selections for synthesis, WAV decoding, resampling, persistence, navigation, tests and analysis |
| Third-party SoundFont synth plus independent voice player | Shared PCM timeline; one AudioTrack output; no independent clocks; the in-project sample player is the only engine since V4 |
| Continuous native state across pause | Cache one bounded exercise; resume its PCM position after flushing output |
| Implicit reproducibility | Version + normalized config + seed + algorithm + concrete fixtures |
| Perfect-fifth metadata during recognition | Hide answer until answer event; metadata follows the same rule |
| Missing voice silently skipped | Preparation fails with semantic ID; runtime failure stops safely in observable Error |
| Exposure/association/recognition | Recognition WP1.3; remaining recipes WP4.1; contrast WP6.1 |
| Mixed direction, fixed root, repeat controls | WP4.1/4.3 |
| Weighted labels, root anti-repeat | **DROPPED by owner 2026-10-25**; no weighted-cycle or anti-repeat policy |
| Instrument rotation, alternate voice packs | Deferred extension after G8; one piano/voice keeps asset review bounded |
| Chords, inversions, scales, notes, tonal context | Stage 5 |
| Progressions and melodies | Already implemented in v0.2.x |
| Intonation, mixed sessions, clusters | **DROPPED by owner 2026-10-25** |
| Timer duration / N exercises / current cycle | WP3.4 / WP4.1; a cycle is explicitly a sequence bag |
| Five navigation destinations | Train/Presets/Settings; Home/Progress were planned for G7, which the owner dropped on 2026-10-25 |
| Statistics, ratings, adaptation | Stage 7; actual consumed time and heard answer windows |
| No personal data stored | Corrected to local settings/history, no app transmission |
| Parallel worktrees | Sequential tasks by default; require a first commit and explicit parallel scope |
| Oboe, benchmarks and profiles | Introduce only in WP8.2 if measurement justifies them |
| Signing and release | WP8.3; keystore external to repository |

## 7. Decisions that remain evidence dependent

| Decision | Default route | Closure point |
| --- | --- | --- |
| Phone ABI and physical routes | arm64-v8a phone + x86_64 emulators; confirm actual device | G0-device / WP2.2 |
| Voice sound | Chatterbox English base model, CPU supported; Piper fallback if generation fails acceptance | WP2.1; human listening required |
| Instrument | EARP1 pack from the owner-generated custom additive string synth (V4 dark calibration, 2026-11-08); 88 one-shot 48 kHz mono samples for A0-C8; the SF2 is a tracked generator input only | Preserve descriptor/hash; the sample-player A/B passed; owner listening remains the human gate |
| Dependency revisions not already pinned | Choose a compatible stable release in the owning package and lock before use | Each package's dependency step |
| CPU wake ownership / buffer size | Scoped playback wake lock and measured bounded AudioTrack buffer | WP3.3/3.5 |
| Output replacement | Keep AudioTrack; Oboe only after a reproduced defect survives buffer/thread fixes | WP8.2 |
| Compound intervals | Deferred beyond G8; separate interval ID model and vocabulary required | Future scoped extension |
| Alternate solfège systems | Movable-do major and do-based minor first; degree-number answers default | WP5.5 |

Human listening, phone interactions and signing-key backup cannot be inferred from
automated logs. Record their actual results. A blocked acceptance item must name the
missing evidence and the work that can continue.
