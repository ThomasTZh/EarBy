# v0.2.x implementation status

Status: v0.2.x is FINAL (version 0.2.0 / versionCode 2, kept as the backup). v0.3.x
development continues at version 0.8.0 (versionCode 8): **M11 is AUTOMATED_COMPLETE /
HUMAN_OPEN** (2026-10-14) — the canonical integrated gate and the real 30-minute endurance
passed with all eight modes, and acoustic quality, actual accessories, TalkBack usability and
G3/G4 remain explicitly open. All eight providers are production-wired.
M9A and M9B are AUTOMATED_COMPLETE and their integration gate (native fixtures, APK checks, the
isolated phone with force-stop checks, the 30-minute endurance and the memory bound) passed on
2026-10-14; M9A's harmonic-function clips and both drawer badge removals were approved by the
owner (2026-10-14). M8 is AUTOMATED_COMPLETE with the
scale-degrees drawer badge removal approved by the owner (2026-10-14); M7 is AUTOMATED_COMPLETE
with the note drawer badge removal approved (2026-10-14); M6 is AUTOMATED_COMPLETE with the
scales drawer badge removal approved (2026-10-14). M5 is AUTOMATED_COMPLETE with the owner
listening row closed (2026-10-12) and the chord drawer change approved (2026-10-13). The owner's
2026-10-11 instruction to continue from `docs/development/v0.2.x/` is the implementation
authorization. Historical 450-host-test/33-phone-test results belong to the earlier review and
are not v0.2.x feature acceptance. Owner direction 2026-10-14: the v0.3.x line closes at 0.8.0
and the next development line is v0.9.x; no v0.9.x scope is authorized yet.

| Milestone | State | Evidence | Next action |
| --- | --- | --- | --- |
| M0 | AUTOMATED_COMPLETE | `.local/verification/mode-expansion/M0/` | M1 may start. |
| M1 | AUTOMATED_COMPLETE | `.local/verification/mode-expansion/M1/` | M2.1 may start. |
| M2 | AUTOMATED_COMPLETE | M2.1–M2.5 complete; full gate passed incl. endurance; owner kept the drawer label | M3 complete; M4 continues. |
| M3 | AUTOMATED_COMPLETE | Host + native/UI + isolated phone with endurance | M4 continues (M4.1/M4.2 done; M4.3 next). |
| M4 | AUTOMATED_COMPLETE | M4.1–M4.4 complete; owner accepted all 107 new voice clips (2026-10-12); checks green | M5 may start. |
| M5 | AUTOMATED_COMPLETE | M5.1–M5.3 complete; chord pack bundled; 499 host tests, device 20/5/9 + 30-minute endurance pass; drawer screenshot approved by the owner (2026-10-13) | M6 may start. |
| M6 | AUTOMATED_COMPLETE | M6.1–M6.3 complete; 42-entry pack; 563 host tests, device 25/5/9 + force-stop pass; drawer screenshot approved (2026-10-14) | M7 may start. |
| M7 | AUTOMATED_COMPLETE | M7.1–M7.3 complete; 82-entry pack; 634 host tests, device 30/5/9 + force-stop pass; drawer screenshot approved (2026-10-14) | M8 complete. |
| M8 | AUTOMATED_COMPLETE | M8.1–M8.3 complete; 99-entry pack; 714 host tests, device 37/5/9 + 30-minute endurance pass; drawer screenshot approved (2026-10-14) | M9 may start. |
| M9A | AUTOMATED_COMPLETE | M9A.1–M9A.3 complete; 803 host tests, device 44/5/9 + force-stop pass; harmonic-function clips bundled and drawer screenshot approved (2026-10-14); 105-entry pack | M9B complete. |
| M9B | AUTOMATED_COMPLETE | M9B.1–M9B.3 complete; 880 host tests, device 51/5/9 + force-stop checks + 30-minute endurance + memory bound pass; M9 integration gate green; drawer screenshot approved (2026-10-14) | M10 may start. |
| M10 | AUTOMATED_COMPLETE | M10.1–M10.4 complete; 980 host tests, native 9/9, device 57/5/9 + force-stop checks; drawer screenshot approved (2026-10-14); M11 supplies the final full/endurance acceptance | M11 may start. |
| M11 | AUTOMATED_COMPLETE / HUMAN_OPEN | Integrated gate 2026-10-14: 1083 host tests, native 2+9, review 58/5/9, endurance 30 m + memory guard, strict screenshots, no INTERNET; findings M11-F01/F02 repaired | No further authorized work. |

## Per-package record template

Append actual records here; do not replace history or mark an unexecuted command PASS.

```text
Milestone/subpackage and date:
State: IN_PROGRESS / AUTOMATED_COMPLETE / BLOCKED
Intent and user-visible result:
Baseline input manifest and changed-file hashes:
Files and contracts changed:
Schema/generator/asset compatibility:
Regression demonstrated before repair:
Commands, exit codes, fresh vs cached task results:
Automated acceptance IDs passed/failed/unexecuted:
Raw evidence directory:
Human acceptance: accepted evidence / OPEN with named limitation
Unexpected findings and resolution:
Current source state and next exact action:
```

### M0 record

```text
Milestone/subpackage and date: M0 — baseline and agent handoff, 2026-10-11
State: AUTOMATED_COMPLETE
Intent and user-visible result: no app change; a frozen input snapshot and fresh
  baseline so later milestones can be judged against real, current evidence.
Baseline input manifest and changed-file hashes: .local/verification/mode-expansion/M0/
  inputs-before.json (278 files, 220,220,416 bytes, SHA-256 each), git-status.txt
  (275 untracked entries, unborn main), inventory.md, mcp.md, tools.txt, device.txt,
  baseline.md and the eight check logs/status files.
Files and contracts changed: documentation only — README, STATUS, ROADMAP authorization
  notes plus this ledger. No training/schema/asset/dependency change.
Schema/generator/asset compatibility: untouched; no checksums or baselines regenerated.
Regression demonstrated before repair: none (clean baseline).
Commands, exit codes, fresh vs cached task results: check-env --build 0, check-fast 0
  (450 host tests freshly executed after clearing test-results), check-assets 0,
  check-ui 0 (9 baselines compared under -Pear.ui.fresh=true). Task-level
  executed/up-to-date counts are recorded in baseline.md.
Automated acceptance IDs passed/failed/unexecuted: M0-A01..A05 pass as recorded in
  baseline.md; no human rows claimed.
Raw evidence directory: .local/verification/mode-expansion/M0/
Human acceptance: existing G3/G4 and visual/acoustic/TalkBack rows remain OPEN and
  unchanged by this milestone.
Unexpected findings and resolution: R28 still applies in the IDE model (catalog alias
  and kotlinx.serialization.json unresolved); CLI remains canonical. No other finding.
Current source state and next exact action: start M1.1 — freeze interval adapter
  compatibility tests, then M1.2 models/registration, M1.3 variable compile, M1.4
  service/controller migration.
```

### M1 record

```text
Milestone/subpackage and date: M1 — shared exercise pipeline with an interval adapter, 2026-10-11
State: AUTOMATED_COMPLETE
Intent and user-visible result: no audible change; the accepted interval app now runs on the
  provider-independent pipeline every later mode will use.
Baseline input manifest and changed-file hashes: M0 inputs-before.json; M1 evidence folder
  holds the fresh check-fast/check-ui/full logs and APK SHA-256 (debug 9348f0a6…, review
  e7d4414d…).
Files and contracts changed: new core/training provider package (TrainingKind, ProviderConfig,
  ModeSetup, ProviderValidation, ConcreteExercise, CompiledExercise, ExerciseProvider/
  ExerciseGenerator, ProviderRegistry, IntervalExerciseProvider, ExerciseCompiler); StartRequest,
  ExerciseSource and AudioPort generalized; ConfiguredIntervalExerciseSource replaced by
  ConfiguredExerciseSource; controller Item/compiled/cycle/answer-label migrated; app
  RendererAudioPort + review seams updated; legacy generator/compiler classes retained as
  fixture oracles; ARCHITECTURE.md documents the pipeline.
Schema/generator/asset compatibility: no persisted schema, asset or dependency change; interval
  v1/v2 draw order frozen; compatibility suite proves byte-identical legacy content and timelines
  across golden seeds, fixed roots, mixed presentation, every recipe, timing control and replay.
Regression demonstrated before repair: none; the new path was written against the legacy oracle
  and matched from the first green run.
Commands, exit codes, fresh vs cached task results: check-fast 0 (225 training tests among the
  suite), check-ui 0, native tests PASS, check-full.sh --automated-only 0 — review-short 17,
  data-platform 4, audio-platform 9, before/after force-stop 1 each, endurance-30m 1 test/1808 s
  with bounded memory, regular app hashes unchanged.
Automated acceptance IDs passed/failed/unexecuted: M1-T01..T09 pass (compat suite, compiler
  shapes, hidden-answer gate unchanged, identity collisions, Previous/Repeat, failure cleanup,
  bounded retention); no human rows claimed.
Raw evidence directory: .local/verification/mode-expansion/M1/
Human acceptance: acoustic quality, accessories, TalkBack, visual approval and G3/G4 remain OPEN.
Unexpected findings and resolution: SC2's proposed NoteGroup relativeStart/duration fields were
  refined to slot-position semantics (list order = scheduling slot; empty group = rest slot) so
  recipe timing stays authoritative; ProviderValidation.Valid carries measured content bounds
  (longest example/answer) re-timed by the shared evaluator. A stale Spotless lint cache was
  cleared and the migration cleaned; no source behavior issue.
Current source state and next exact action: begin M2.1 — versioned settings contract (schema 2,
  selected kind + per-kind setups, revisions, atomic mode-selection update).
```

### M2.1 record

```text
Milestone/subpackage and date: M2.1 — versioned settings contract (schema 2), 2026-10-11
State: AUTOMATED_COMPLETE for the settings contract; M2.2–M2.5 continue in M2.
Intent and user-visible result: each mode owns its saved setup plus a revision; schema-1
  settings migrate exactly into intervals (including empty selections and advanced-panel state);
  a known-but-unavailable mode can stay selected and simply cannot start.
Files and contracts changed: core/training settings (ModePayload/SavedModeSetup/ModeEdit/
  ModeWriteResult, UserSettings.selectedKind/modeSetups with derived temporary lastSetup/
  lastMode accessors, schema 2, NewerModeConfig diagnostic, SettingsRepository.update/
  updateMode/saveAndSelect/selectKind contract); core/data DataStoreSettingsRepository
  (selected_kind + intervals_revision + intervals_config_version keys, v1 fallback, atomic
  revision-checked writes and a guard that update() never carries mode-setup changes); app
  consumers migrated (SetupViewModel autosave, PresetsViewModel apply, AppRoot selection,
  TrainingPlaybackService Start routing by selected kind); test fakes and device tests updated.
Schema/generator/asset compatibility: schema 1 remains readable; writes upgrade to
  schema_version 2 and remove the legacy last_mode alias; interval payload fields are unchanged;
  no asset/generator change.
Regression demonstrated before repair: none; revision conflicts and protected future config
  versions were added as new demonstrated tests before any UI depended on them.
Commands, exit codes, fresh vs cached task results: :core:data:testDebugUnitTest 107 tests 0
  failures; :app:testDebugUnitTest pass; :app:assembleReviewAndroidTest compiles; both
  spotlessApply/spotlessCheck and scripts/check-fast.sh exit 0.
Automated acceptance IDs passed/failed/unexecuted: M2-T01, M2-T04(settings side), M2-T06(store
  side), M2-T07(storage side) demonstrated; the remaining M2 rows belong to M2.2–M2.5.
Raw evidence directory: .local/verification/mode-expansion/M2/ (logs copied as steps run).
Human acceptance: none claimed; visual rows unchanged and still strict.
Unexpected findings and resolution: none functionally; the derived lastSetup/lastMode accessors
  are the documented temporary adapter until M2.4 re-keys the screens by stable kind ID.
Current source state and next exact action: begin M2.2 — preset document schema 2 (discriminated
  kind/configVersion payload, schema-1 read via the strict interval decoder, limits preserved).
```

### M2.2 record

```text
Milestone/subpackage and date: M2.2 — preset document schema 2, 2026-10-11
State: AUTOMATED_COMPLETE
Intent and user-visible result: exports and imports now carry a stable kind discriminator and
  configuration version; schema-1 files still read exactly and re-export as schema 2.
Files and contracts changed: PresetDocument.kt — PresetDocument sealed interface (V1/V2), the
  envelope decoder dispatches per element schema version, V2 carries {kind, config_version,
  payload: JsonObject}, interval payloads keep the unchanged schema-1 field set, unknown
  kinds/config versions are refused instead of guessed, encode writes schema 2; repository
  ImportEntry/Planned document types widened to PresetDocument; tests updated and extended.
Schema/generator/asset compatibility: schema-1 fixtures read through the strict interval decoder;
  seeds stay decimal strings; gains/stop/timing fields unchanged; no database change yet.
Regression demonstrated before repair: none; new refusals (foreign kind, future config) were
  demonstrated by tests before any UI depends on them.
Commands, exit codes, fresh vs cached task results: :core:data:testDebugUnitTest 109 tests 0
  failures; :app:assembleReviewAndroidTest compiles; spotlessApply/Check and check-fast exit 0.
Automated acceptance IDs passed/failed/unexecuted: M2-T05 demonstrated (v1/v2 round trips, future
  kind/version refusal, duplicate keys/limits preserved from the existing suite).
Raw evidence directory: .local/verification/mode-expansion/M2/ (logs copied as steps run).
Human acceptance: none claimed.
Unexpected findings and resolution: the codec's internal Json enables special floating point
  values so a non-finite gain is refused by preset validation with a readable message instead of
  a raw serializer error; validated documents can never reach exported text with such a value.
Current source state and next exact action: begin M2.3 — SQLite migration 9 with the generic
  kind-scoped payload table and slot ownership, preserving every version-8 row.
```

### M2.3 record

```text
Milestone/subpackage and date: M2.3 — SQLite migration 9 and kind-scoped slots, 2026-10-11
State: AUTOMATED_COMPLETE
Intent and user-visible result: the preset store becomes one generic kind-scoped payload table
  with forty stable slots (five per kind); every version-8 row converts in place and no preset,
  slot or custom is lost.
Files and contracts changed: PresetDatabase.kt (PresetMigration statements/transform steps,
  TRANSFORMS, migrationsToUpgrade, CURRENT_VERSION 9); new PresetMigration9.kt (frozen table
  SQL, driver-level Kotlin transformation, payload conversion and verification, table swap);
  PresetSlots (kind-scoped deterministic ids); SqlitePresetRepository rewritten onto the payload
  table with kind-scoped reads, membership refusal for other modes and slotsOf; codec storage
  helpers (intervalPayloadJson/storedPreset, non-feasibility load path); AndroidPresetDatabase
  uses the migration's already-open database handle; host and Android tests extended.
Schema/generator/asset compatibility: migrations 1–8 byte-frozen; the intervals payload keeps
  its field set; the five historical interval slot ids are unchanged; later kinds use
  `mode:<kind>:slot:<n>`; legacy customs keep a null slot and adoption is idempotent.
Regression demonstrated before repair: the Android 8→9 upgrade failed on the device because
  onUpgrade re-entered writableDatabase (a recursive open the platform refuses); the new
  Android upgrade test reproduced it before the fix, and the fix was re-verified.
Commands, exit codes, fresh vs cached task results: :core:data:testDebugUnitTest 112 tests 0
  failures; scripts/check-fast.sh exit 0; spotless clean; isolated device smoke exit 0 with
  review-short 17, data-platform 5 (including the on-device 8→9 upgrade), audio-platform 9,
  force-stop 1+1, regular app hashes unchanged.
Automated acceptance IDs passed/failed/unexecuted: M2-T02 (frozen v1/v2/v8 fixtures, shape
  parity, row/slot preservation), M2-T03 (failed migration phase rolls back every phase;
  retry succeeds), M2-T04 (forty unique kind-scoped slots, cross-mode access refused, legacy
  customs survive incl. a sixth one) demonstrated; M2-T09 host+device parity demonstrated.
Raw evidence directory: .local/verification/mode-expansion/M2/ (device smoke logs)
Human acceptance: none claimed.
Unexpected findings and resolution: Android `onUpgrade` must use the database it was given;
  the driver now exposes that handle for the migration and clears it afterwards.
Current source state and next exact action: M2.4 UI ownership and stable kind IDs.
```

### M2.4 record

```text
Milestone/subpackage and date: M2.4 — mode-keyed UI, stable drawer IDs and safe selection, 2026-10-11
State: AUTOMATED_COMPLETE (full gate passed: review-short 19, data-platform 5 incl. the on-device 8→9 upgrade, audio-platform 9, force-stop 1+1, endurance 1809 s)
Intent and user-visible result: the drawer, top bar and Play page use stable kind IDs; the
  display label stays "Perfect pitch" (owner decision 2026-10-11, withdrawing the planned
  rename); a mode change during playback is refused or switched per M3; an unimplemented kind
  can be selected while stopped and shows its placeholder in Play, Setup and Presets.
Files and contracts changed: TrainingModes.kt keyed by TrainingKind with the shared
  ModeUnavailableScreen; ModeDrawer.kt iterates TrainingKind.ALL; AppRoot selects
  settings.selectedKind, refuses selection while playbackState.active, and gates Setup/Presets
  on kind.implemented; PlayScreen typed by kind; SetupViewModel takes its kind and keeps the
  revision-guarded autosave; review device tests added for refusal-during-playback and
  stopped selection of an unavailable kind.
Schema/generator/asset compatibility: no schema or asset change; the temporary lastSetup/
  lastMode view remains only for the interval editor and playback reads selectedKind.
Regression demonstrated before repair: none new.
Commands, exit codes, fresh vs cached task results: scripts/check-fast.sh exit 0; spotless
  clean; review instrumentation compiles; the two new device tests passed in the full gate
  (check-full.sh --automated-only exit 0, endurance 1809 s, memory bounded).
Automated acceptance IDs passed/failed/unexecuted: M2-T06/M2-T07 UI-side demonstrated by the
  device tests; M2-T08 belongs to M2.5.
Raw evidence directory: .local/verification/mode-expansion/M2/
Human acceptance: the drawer label change is the declared pending visual case (M2.5).
Unexpected findings and resolution: none.
Current source state and next exact action: M2.5 visual report (implemented) and full gate.
```

### M2.5 record

```text
Milestone/subpackage and date: M2.5 — visual verification without automatic approval, 2026-10-11
State: AUTOMATED_COMPLETE (no pending row)
Intent and user-visible result: screenshot verification is manifest-driven; automated full
  runs report a declared pending difference honestly instead of approving it, and the strict
  owner gate still fails until the owner approves.
Files and contracts changed: app/src/test/screenshots/manifest.json (nine cases, baseline
  hashes, the declared drawer change with reason and bounded region); scripts/screenshot-report.py
  (pixel comparison: approved cases identical, declared changes bounded to their region,
  missing/malformed/undeclared/stale cases fail); screenshot-contract.py reads the manifest
  instead of a hard-coded list; check-full.sh generates candidates and runs the report path
  with EAR_UI_CANDIDATES_DIR; TEST_STRATEGY and ARCHITECTURE document both entry points.
Schema/generator/asset compatibility: no baseline was regenerated; drawer.png keeps its hash.
Regression demonstrated before repair: none; the report tests demonstrate out-of-region,
  undeclared, stale and missing candidates failing before integration.
Commands, exit codes, fresh vs cached task results: script tests 25 tests 0 failures;
  capture + report exit 0 with exactly the drawer case PENDING; the eight other cases verified
  identical; strict scripts/check-ui.sh remains the owner gate and is expected to fail on the
  drawer until approval.
Automated acceptance IDs passed/failed/unexecuted: M2-T08 demonstrated (missing/malformed/
  undeclared fail; declared case reported pending; baseline hashes guarded by tests).
Raw evidence directory: .local/verification/mode-expansion/M2/ui-candidates/
Human acceptance: CLOSED for this case — the owner reviewed the declared drawer change on
  2026-10-11 and chose to keep the "Perfect pitch" label; the rename was withdrawn, the manifest
  restored drawer as approved with its original hash, and the strict comparison passes with all
  nine cases unchanged. The decision is recorded at
  .local/verification/mode-expansion/M2/ui-review/DECISION.md.
Unexpected findings and resolution: none.
Current source state and next exact action: M3 after the full M2 gate returns.
```

### M3 record (in progress)

```text
Milestone/subpackage and date: M3 — switch after the current learning sequence, 2026-10-11
State: AUTOMATED_COMPLETE
Intent and user-visible result: a mode request preserves the audible learning sequence and
  commits the destination at a safe boundary; stopped sessions only remember the selection.
Files and contracts changed: SessionCommand.SwitchMode + snapshot active/pending kind and
  revision fields; DefaultSessionController unit-wise playback (mandatory unit, each optional
  replay, trailing tail), boundary authorization, pending-switch state, commit with activation
  ordinal and mode-seed-v1 derivation, A->A cancellation, navigation refusal, pending clears on
  stop/timer/failure; AudioPort.play segments with boundaries and a synchronous boundary
  authorization callback; ExerciseOutputWriter end boundary and stoppedAtBoundary; adapter
  converts compiled boundaries to frames; ModeSeedDerivation implemented with frozen vectors;
  media transport/service switch command with settings resolution; PlaybackViewModel switch
  state and command; drawer sends the switch; Play shows the pending destination.
Schema/generator/asset compatibility: no persisted schema or asset change; interval behavior
  without a pending switch is unchanged (one authorized segment per item).
Regression demonstrated before repair: two integration defects were found and fixed during
  implementation - the writer's trailing-lambda parameter order broke cancellation tests, and
  the fake port refused a boundary retroactively after it had already been crossed.
Commands, exit codes, fresh vs cached task results: :core:training:test 236 tests 0 failures
  (7 new boundary tests incl. derived/fixed seeds, replay completion, pause/resume, A->B->A,
  navigation refusal, timer priority); scripts/check-fast.sh exit 0; spotless clean; native
  host contracts and the nine-case visual report already evidenced from the cancelled bundled
  run; isolated device automated run exit 0 - review-short 19, data-platform 5, audio-platform
  9, force-stop 1+1, endurance-30m 1 test/1808 s with bounded memory (PSS variation 3722 KiB)
  and the regular app's hashes unchanged. A first automated attempt failed only on the
  physical 'display stays off' precondition (screen woke at ~9 min); the retry on the same
  APKs passed, and the failure log is kept.
Automated acceptance IDs passed/failed/unexecuted: M3-T01-T08 demonstrated (new boundary
  suite, existing failure/retention suites, writer/accounting suites); M3-T09 demonstrated on
  the isolated phone (active/pending state through the review tests and the endurance run).
Raw evidence directory: .local/verification/mode-expansion/M3/ (device, device-full,
  device-full2, native, ui-candidates, mode-seed-v1-vectors.json)
Human acceptance: none claimed; the M2 drawer visual row is closed (owner kept "Perfect
  pitch").
Unexpected findings and resolution: see regression line; the boundary protocol never submits
  past an unauthorized boundary, so there is no buffered-unit revocation path to exercise.
Current source state and next exact action: M4 continues; M4.3 voice vocabulary is the next
  code step, and M4.4 voice generation pauses for the owner's listening review.
```

### M4 record (in progress)

```text
Milestone/subpackage and date: M4 — theory fixtures, spelling and voice vocabulary, 2026-10-11
State: IN_PROGRESS (M4.1 music theory, M4.2 fixtures and the M4.3 voice registry are complete;
  tool-facing catalog complete; harmony phrases deferred to M9; M4.4 asset batches running)
Intent and user-visible result: no app change yet; the theory and fixture foundations every
  later mode consumes are frozen and independently checked.
Files changed: core/music SpelledPitch/Letter (SC5 conversion), Key/Tonality (theoretical
  letter degrees), ChordQuality/ChordVoicing (eleven qualities, closed-position inversions,
  theoretical root vs bass, double-accidental spellings), ScaleKind/ScaleDirection/
  ScaleTraversal (thirteen formulas, complete octaves including the repeated tonic);
  tools/music-fixtures (pinned music21 10.5.0 project, generator/independent oracle, NOTICE);
  scripts/check-music-fixtures.sh wired into check-fast; checked-in fixtures under
  core/music/src/test/resources/music-fixtures plus a host fixture test.
Compatibility: existing interval APIs untouched; no dependency or asset change in the app.
Regression demonstrated before repair: the music21 oracle caught one real formula error - sus2's
  second member is D (letter step 1), not E - fixed in the Kotlin catalog, the tool and the
  frozen evidence; A# whole-tone is recorded as an explicit -2..2 unrepresentable case.
Commands, exit codes: :core:music:test 49 tests 0 failures (M4-T01/M4-T02 rows); the fixture
  generator regenerates byte-identical files; scripts/check-fast.sh exit 0 with the new
  check-music-fixtures step.
Raw evidence: .local/verification/mode-expansion/M4/ (vocabulary.json, plan.md).
Human acceptance: M4.4's new voice clips will need the owner's listening review; existing
  thirteen interval clips stay frozen.
Current source state and next exact action: M4.3 is complete for every template that exists -
  tools/voice-assets/vocabulary.json (120 entries, 13 bundled) is consumed by
  generate_voice_pack.py and validate_assets.py, and VoiceCatalogTest proves it matches
  VoiceVocabulary. The harmony namespace stays reserved until M9 specifies its phrases.
  M4.4 generation is complete: all 120 catalog clips exist under tools/voice-assets/candidates
  (107 new + 13 frozen intervals), every clip verified 48 kHz mono PCM16 with 0<seconds<=8 and
  non-silent, the packaged interval hashes still match their candidates, and full provenance is
  the packaged interval hashes still match, and full provenance is merged at
  candidates/generation-summary-all-v1.json. Owner decision 2026-10-12: the v0.2.x voice is
  Piper en_GB-alba-medium (CC BY 4.0 attribution); the Chatterbox new clips are archived under
  candidates-superseded-chatterbox-2026-10-12/ and the 13 interval clips stay frozen. The 107
  piper-alba clips were regenerated, verified (0.53-2.13 s, 48 kHz mono PCM16, non-silent) and
  the refreshed owner listening sheet (107 rows, markdown and HTML with players) is at
  .local/verification/mode-expansion/M4/listening-sheet.{md,html}. Nothing is bundled or marked
  humanAccepted; M4-T05/T07/T08 and the listening row stay open until the owner accepts clips,
  after which each mode milestone bundles its accepted set through build_voice_pack.py.

M4.3 registry slice (added after the original record): VoiceVocabulary with the stable
namespaces (interval, chord, scale, note, fixeddo, degree, connector, harmony reserved),
entries derived from the frozen music catalogs (13 intervals, 11 chord qualities, 4 inversion
labels, 13 scale names, note names with accidentals, octaves -1..9, fixed-do, degree numbers
and major/natural-minor syllables, or/ascending/descending/unison), duplicate-by-meaning
syllables deduplicated to one clip, and exact/spoken text pinned. Tests: 244 training tests
0 failures; spotless, check-fast and the strict nine-case UI gate exit 0.
```

### M4.4 record (completion)

```text
Milestone/subpackage and date: M4.4 — voice assets and owner listening acceptance, 2026-10-12
State: AUTOMATED_COMPLETE
Intent and user-visible result: every v0.2.x answer vocabulary has a spoken clip the owner
  accepted by ear; no app/UI/asset-manifest change (the shipped interval pack is untouched).
Files and contracts changed: tools/voice-assets/vocabulary.json (per-entry ttsText/ttsVoice/
  lengthScale; bundled set unchanged at 13); generate_piper_voice_pack.py (per-voice
  provenance, ttsText/lengthScale/ttsVoice support); new dev-only tools variants.py,
  phoneme_variants.py, cut_syllables.py, vowel_probe.py, audition_words.py;
  reviews-vocabulary.json (107 owner acceptances); NOTICE.md, OPEN_SOURCE.md and DECISIONS.md
  D24 (voice attribution and choice); candidate summary regenerated
  (generation-summary-piper-alba-full-v5.json, all 107 hashes match disk).
Schema/generator/asset compatibility: no packaged asset, manifest or Kotlin contract changed;
  the thirteen interval WAVs, their manifest entries and reviews.json are untouched.
Regression demonstrated before repair: the owner's first listening pass flagged 27 rows
  (letter A/B/E, solfege do/re/mi/sol/la/le/me/te/ti, augmented, diminished family, four,
  whole tone, two/three, fa/la). Failed approaches kept as evidence: respelling-only fixes
  where the phonemes were already correct, phrase-cut takes and raw-phoneme takes.
Commands, exit codes: check-music-fixtures 0; check-audio-tools 0; check-assets 0 and
  --require-reviewed 0; strict gradle music/training/audio tests first run 1 intermittent
  SwitchModeTest failure then 3 isolated + 2 full reruns 0; check-fast 0; native tests 0
  (7 rendered fixtures under M4/native); device smoke first run failed on cold start then an
  identical-APK rerun 0 (review 19, data 5, audio 9, before/after force-stop 1 each).
Automated acceptance IDs passed/failed/unexecuted: M4-T01/T02 (49 music tests + independent
  fixtures), T03 (byte-identical regeneration), T04 (VoiceVocabulary/VoiceCatalogTest),
  T05 (frame metadata vs WAVs via check-assets and bounds tests), T06 (frozen interval hashes),
  T07 (80 ms token gaps, 44.1/48 kHz conversions, duration cap), T08 (four-note simultaneity,
  native fixture peaks/rms). Device renderer instrumentation of future multi-token answers
  stays with the mode milestones that bundle them.
Raw evidence directory: .local/verification/mode-expansion/M4/ (m43-notes.md, round folders
  review-2026-10-12{-annotated,-r3,-r4,-r5,-final}, pronunciation-references.md,
  owner-flags-2026-10-12.md, gate-2026-10-12/, device/ and device-rerun/, native/).
Human acceptance: CLOSED - the owner listened through all rounds and accepted the final
  38-clip folder on 2026-10-12 ("all good"); reviews-vocabulary.json records the per-id
  acceptance.
Unexpected findings and resolution: alba mispronounces word-final /a/ (r-coloured
  "fah"/"lah"), the standalone "dough" vowel and the isolated letter A; resolved with
  per-clip voices (jenny/amy/cori, decision D24) after respelling, cutting and raw-phoneme
  routes failed. Two intermittent machine flakes recorded with reruns: SwitchModeTest (host)
  and the post-install cold-start review run (device).
Current source state and next exact action: M4 is complete. M5 (chords) may start; when a
  mode bundles its accepted set through build_voice_pack.py it regenerates
  BundledVoiceDurations and the manifest/checks for that subset.
```

### M5 record (in progress)

```text
Milestone/subpackage and date: M5 — chord quality and inversion training, 2026-10-12/13
State: IN_PROGRESS (M5.1 complete; M5.2 host bounds complete; M5.3 partly integrated)
Intent and user-visible result: a Chords mode with validated setup, deterministic
  closed-position voicings, equivalent spoken answers and phone playback; the visible app
  surface lands together with codec, validation, required voices and screens.
Files and contracts changed so far: core/music (ChordEquivalence, ChordPresentation,
  ChordInversions, ChordQuality.englishAnswer); core/training (ChordDefinition,
  ChordExerciseProvider with SC3 bag draws and D08 alternatives, GeneratedChord provenance,
  AnswerDisplay.Chord, RecipeDefinition.totalDurationOf, ChordSetup/ChordSetupDraft/
  ChordSetupValidator, ModePayload.Chords + CHORDS_CONFIG_VERSION, UserSettings.chordSetup);
  core/data (chord preference keys, decode/write/validation); tools/voice-assets
  (catalog-driven build_voice_pack.py, bundled chord vocabulary); tools/audio-analysis
  (generalized id/text keys — fixed a real interval/chord label collision); voice pack
  rebuilt to 29 reviewed entries (packId english-answers 2.0.0) with the thirteen interval
  clips byte-identical; BundledVoiceDurations regenerated; docs/audio/ASSETS.md updated;
  core/audio host render harness generalized to four-note windows with a `chord_cmaj7`
  fixture, and tools/audio-analysis/check_render_fixtures.py checks it.
  One reviewed dependency-verification entry added (jackson-base-2.19.4.pom parent POM,
  SHA-256 cross-checked with repo1.maven.org); no version or toolchain change.
Regression demonstrated before repair: the validator collision would have refused the chord
  pack on a wrong expected text; the chord-default root representation differed between a
  fresh and a decoded default.
Commands, exit codes: :core:music:test 58 (incl. 9 new); :core:training:test 276 (incl. 30
  new); :core:data:testDebugUnitTest 116 (incl. 4 new); all 0 failures; check-assets
  --require-reviewed 0 (29 entries, 0 pending); check-audio-tools 0; check-voice-durations 0;
  spotlessCheck 0; check-native-tests 0 under M5/native with 8 rendered fixtures, including the
  new four-note `chord_cmaj7` (peak 0.3715, finite PCM, one onset).
Raw evidence directory: .local/verification/mode-expansion/M5/ (grows with the phone gate).
Human acceptance: the owner accepted the chord clips inside the M4.4 listening pass
  (reviews-vocabulary.json). M5's own phone Start/Stop and switching rows stay open until the
  M5 device gate.
Unexpected findings and resolution: the pack validator's label-keyed text table collided
  between "interval.major_seventh" and "chord.major_seventh" (now keyed by full id);
  ChordSetup.default() keeps an empty root selection (= all roots) so decoded defaults compare
  equal.
Current source state and next exact action: preset kind-scoping for chords is the next
  coherent edit (codec, five slots, chord starter, clear), then provider registration with
  Setup/Play/Presets screens and TrainingPlaybackService routing, then M5-T01..T09 evidence
  and the device gate.
```

### Interval voice refresh (2026-10-14)

The owner approved replacing the thirteen frozen Chatterbox interval clips with Piper
`en_GB-alba-medium` takes (the same voice and postprocessing as the v0.2.x vocabulary clips).
The old takes and their manifest entries are archived under
`tools/voice-assets/candidates-superseded-chatterbox-intervals-2026-10-14/` (archived files
verified byte-identical to the previously packaged ones). The new takes were listened to and
accepted by the owner, and the approval is recorded in
`tools/voice-assets/reviews-intervals.json`. The pack was rebuilt with
`build_voice_pack.py --refresh` (all thirteen packaged files match their candidates
byte-for-byte; the manifest now carries Piper provenance and the new hashes), and
`BundledVoiceDurations` was regenerated. Verified: `check-voice-durations.py` PASS (29
entries), `check-assets --require-reviewed` PASS, `check-audio-tools.sh` PASS,
`check-fast.sh` PASS. `SetupValidatorTest` now reads the measured bundled clip for its 60 s
cap boundary instead of a hardcoded duration (the new, slightly longer Major seventh take had
moved that boundary). The signed release APK and the backup were updated the same day.

### Icon refresh (2026-10-14)

The app icon was refreshed to the owner-supplied teal artwork (`icons/EarByIconV3.png`): the
legacy square and round mipmaps, and the adaptive icon, were regenerated with an adaptive
background flattened from the artwork's own colour so the launcher mask shows a seamless
field. Built and checked with `:app:assembleDebug` and `:app:lintDebug`.

### v0.2.x release close-out (2026-10-13)

v0.2.x is frozen at version 0.2.0 (versionCode 2) and a continuity backup lives at
`an external backup/` (739 MiB; the owner moved it there
from the original location): the full source tree (7013 files, verified file-for-file against
the workspace), the mode-expansion evidence, the bundled SoundFont, and `apk/EarBy-0.2.0.apk`
(release build, zipaligned and signed with the machine's debug key). The APK was refreshed with
the icon V2 artwork on 2026-10-13; its current SHA-256 is
`f6b3bb1038c5ebb183ee27a96b5d33f9b54a1b50b242f88b9221cf73736f81ec` (the first build's
`ec78ed1c...` is superseded) and the backup README records the current value. The workspace was
tidied afterwards: build outputs, Python virtual environments, the uv/Hugging Face/Gradle
caches, obsolete 0.1.0 APK snapshots, raw voice takes and the redundant SoundFont source
download were removed (45 GiB -> 18 GiB); the Android SDK, Gradle home, JDK, TTS models and all
evidence remain for continued work.

### Owner-requested identity change (2026-10-13)

The launcher name is "EarBy" (main app, and the isolated review app label "EarBy (Review)").
Two icon artworks were supplied the same day: V1, the three-dimensional rendering
(`icons/EarByIconV1.png`, sha256 `554fdd01292e...`), and then V2, the flat simplified version
(`icons/EarByIconV2.png`, sha256 `d72525af5a06...`), which is current. Both are packaged the
same way: legacy square and round mipmaps at all densities plus an adaptive icon whose
blurred, edge-filled background continues the artwork's gradient.

The V2 refresh regenerated every launcher asset, rebuilt and re-signed the release APK
(`EarBy-0.2.0.apk`, version 0.2.0/versionCode 2, zipaligned, debug-key signed, SHA-256
`f6b3bb1038c5ebb183ee27a96b5d33f9b54a1b50b242f88b9221cf73736f81ec`), and updated the backup at
`an external backup/` (APK replaced, README hash refreshed,
icon artwork, mipmap resources and this ledger synced). Packed verification: the adaptive
background layer is pixel-identical to the source, the foreground's alpha shape matches exactly
(aapt2 crunches RGB at transparent edges), and the legacy and round icons are pixel-identical.
`:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug` and `spotlessCheck` pass;
aapt2 badging reports `application-label:'EarBy'` and the adaptive
`mipmap-anydpi-v26/ic_launcher.xml`. In-app screen titles and the documentation's project name
are unchanged, so no screenshot baseline moves.

### M5.3/M5.4 record (completion)

```text
Milestone/subpackage and date: M5.3/M5.4 — product integration and gates, 2026-10-13
State: AUTOMATED_COMPLETE (drawer visual change pending owner approval; acoustic, accessory and
  TalkBack rows stay with G3/G4)
Intent and user-visible result: Chords is a complete offline mode: a setup editor with quality
  and inversion choices (inapplicable inversions disabled with explanations), presentation and
  answer-format choices, roots/fixed root and range; a chord Play summary; kind-aware presets
  with the built-in chord starter and a Clear action; Start/Stop and interval<->chord switching
  on the phone.
Files and contracts changed: core/data presets (PresetConfiguration, chords payload, five
  isolated slots, chord starter, clear); core/training registry registration; app
  TrainingModes/TrainingPlaybackService per-kind dispatch/ChordSetupScreen and ViewModel/
  PlayScreen/PresetsScreen/AppRoot session-running gate; review device tests; screenshot
  manifest declares the drawer change pending.
Regression demonstrated before repair: the drawer's switch path gated on a state flag that only
  updated on the periodic session poll, so a switch requested while playback was audibly running
  could be skipped; AppRoot now treats a running session (active/playing/paused) as switchable.
Commands, exit codes: check-music-fixtures 0; check-assets 0 and --require-reviewed 0; strict
  fresh Gradle run music 58 / training 276 / data 123 / app 42 = 499 tests, 0 failures;
  check-fast 0; check-native-tests 0 (8 fixtures including the four-note chord_cmaj7);
  check-full --automated-only 0 with review-short 20, data-platform 5, audio-platform 9,
  before/after force-stop 1 each and the 30-minute endurance (memory variation 3.0 MiB against
  the 64 MiB bound); regular package/private-file hashes unchanged.
Automated acceptance IDs passed/failed/unexecuted: M5-T01..T09 pass; T08 through the review
  device suite (chord selection, Start/Stop, interval<->chord switching at the boundary, hidden
  answers in snapshots/metadata/semantics); T09 through the reliability and force-stop suites
  plus endurance.
Raw evidence directory: .local/verification/mode-expansion/M5/ (gate-2026-10-13/, device/,
  native/, notes.md).
Human acceptance: pronunciation CLOSED (owner accepted the chord clips in the M4.4 pass); the
  drawer visual change is CLOSED — the owner accepted the candidate on 2026-10-13, the
  baseline app/src/test/screenshots/drawer.png was re-recorded (sha256 3cdbe02dc58d...), and
  scripts/check-ui.sh (strict, fresh) passes with all nine screenshots unchanged.
Unexpected findings and resolution: the switch race above; the contract/tests otherwise held.
Current source state and next exact action: M5 is complete; M6 (scales) may start and bundles
  its accepted scale clips through build_voice_pack.py before its gate.
```

### M6 record (completion)

```text
Milestone/subpackage and date: M6 — complete scales and modes, 2026-10-14
State: AUTOMATED_COMPLETE (drawer visual change pending owner approval; the scale-name clips
  were accepted in the M4.4 listening pass; acoustic and TalkBack rows stay with G3/G4)
Intent and user-visible result: Scales is a complete offline mode: the thirteen canonical
  formulas as complete-octave traversals (ascending, descending, both with the turning tonic
  once), a setup editor with direction, roots/fixed root and range plus the shared recipe
  controls, a Play summary, kind-aware presets with builtin:scales:starter:v1 and Clear,
  Start/Stop and interval<->scale and chord<->scale switching at whole-traversal boundaries.
Files and contracts changed: core/training ScaleDefinition, ScaleExerciseProvider (SC3 bag of
  scales, uniform tonic), GeneratedScale provenance and AnswerDisplay.Scale;
  ScaleSetup/ScaleSetupDraft/ScaleSetupValidator, ModePayload.Scales/SCALES_CONFIG_VERSION,
  UserSettings.scalesSetup; core/data scale persistence keys and decode/write validation;
  PresetConfiguration.Scales, PresetPayloadScalesV1, the scales starter and kind-scoped slots;
  app ScaleSetupScreen/ViewModel, Play/Presets kind-awareness, TrainingModes,
  TrainingPlaybackService dispatch; ProviderRegistry registration; the voice catalog now
  bundles the thirteen scale names (42-entry pack, all reviewed); the host render harness and
  checker gained the fifteen-note both-direction traversal fixture; review device tests.
Regression demonstrated before repair: a drawer selection made while a session was polling
  state could hang inside the drawer close animation, silently skipping the mode switch; the
  selection/switch now runs independently of the drawer animation (found by the new device
  tests for scale switching).
Commands, exit codes: check-music-fixtures 0; check-assets 0 and --require-reviewed 0
  (42 entries, 0 pending); strict fresh gradle music 58 / training 318 / data 136 / app 51 =
  563 tests, 0 failures; check-fast 0; check-native-tests 0 (9 fixtures including
  scale_c_major_both); device smoke 0 (review-short 25, data-platform 5, audio-platform 9,
  before/after force-stop 1 each); capture-ui-candidates 0 with the drawer declared pending.
Automated acceptance IDs passed/failed/unexecuted: M6-T01..T08 pass; T07/T08 through the
  review device suite (scale selection, Start/Stop, interval<->scale and chord<->scale
  switching at the boundary, hidden answers in snapshots/metadata/semantics).
Raw evidence directory: .local/verification/mode-expansion/M6/ (gate-2026-10-14/,
  device-rerun2/, native/, ui-candidates/, notes.md).
Human acceptance: the thirteen scale-name clips were accepted by the owner in the M4.4
  listening pass and are bundled. The drawer change from removing the Scales "Soon" badge is
  CLOSED: the owner accepted the candidate on 2026-10-14, the baseline
  app/src/test/screenshots/drawer.png was re-recorded (sha256 8b83dbd4a6eb...), and
  scripts/check-ui.sh (strict, fresh) passes with all nine screenshots unchanged.
Unexpected findings and resolution: the switch/animation issue above; the previously
  documented SwitchModeTest host flake remains intermittent and unrelated.
Current source state and next exact action: the version is now 0.3.0 (versionCode 3), the
  first v0.3.x build. M7 (note identification) may start; it will bundle its accepted
  note/octave clips through build_voice_pack.py before its gate.
```

### M7 record (completion)

```text
Milestone/subpackage and date: M7 — note identification ("Perfect pitch"), 2026-10-14
State: AUTOMATED_COMPLETE (drawer visual change pending owner approval; the note/octave/
  fixed-do clips were accepted in the M4.4 listening pass; acoustic and TalkBack rows stay
  with G3/G4)
Intent and user-visible result: the Perfect pitch placeholder is a complete mode — pick pitch
  classes, a range and an optional fixed octave, sharp/flat spelling and optional octave
  speech; hear one note and its spoken name in Scientific or fixed-do Solfège naming.
Files and contracts changed: core/training NoteDefinition and NoteExerciseProvider (SC3 bag of
  pitch classes, one uniform note per draw, naming-aware tokens), GeneratedNote provenance and
  AnswerDisplay.Note; NoteSetup/NoteSetupDraft/NoteSetupValidator with the global
  Scientific/Solfège mapping; ModePayload.Notes/NOTE_IDENTIFICATION_CONFIG_VERSION and
  UserSettings.noteSetup; core/data note persistence (naming-aware decode), 
  PresetConfiguration.Notes, PresetPayloadNotesV1 and the "All twelve notes" starter; app
  NoteSetupScreen/ViewModel, Play/Presets kind-awareness, TrainingModes and service dispatch
  with the global naming; ProviderRegistry registration; the voice pack now bundles the note
  naturals, sharp/flat chromatic names, spoken octaves and fixed-do syllables (82 entries, all
  reviewed); review device tests.
Regression demonstrated before repair: the mode setup validators re-timed answer content but
  not the example window, so a long note duration could pass the 60 s cap check and then fail
  at Start; the chord, scale and note validators now re-time the example with the draft's own
  timing, each with a regression test.
Commands, exit codes: check-music-fixtures 0; check-assets 0 and --require-reviewed 0
  (82 entries, 0 pending); strict fresh gradle music 58 / training 366 / data 149 / app 61 =
  634 tests, 0 failures; check-fast 0; device smoke 0 (review-short 30, data-platform 5,
  audio-platform 9, before/after force-stop 1 each); capture-ui-candidates 0 with the drawer
  declared pending.
Automated acceptance IDs passed/failed/unexecuted: M7-T01..T08 pass; T07/T08 through the
  review device suite (note selection, Start/Stop, interval<->note and scale<->note switching,
  hidden answers in snapshots/metadata/semantics).
Raw evidence directory: .local/verification/mode-expansion/M7/ (gate-2026-10-14/, device/,
  ui-candidates/).
Human acceptance: the note-name, octave and fixed-do clips were accepted by the owner in the
  M4.4 listening pass and are bundled. The drawer change from removing the Perfect pitch
  "Soon" badge is CLOSED: the owner accepted the candidate on 2026-10-14, the baseline
  app/src/test/screenshots/drawer.png was re-recorded (sha256 473740e742a2...), and
  scripts/check-ui.sh (strict, fresh) passes with all nine screenshots unchanged.
Unexpected findings and resolution: the example re-timing gap above; the fixed-do catalog stays
  sharp-based (M4.3 design), documented in the screen and the validator.
Current source state and next exact action: version 0.4.0 (versionCode 4). M8 (scale degrees
  and context) may start; it bundles its degree/tonality clips through build_voice_pack.py
  before its gate, and its completed mode gets the next version and backup.
```

### M8 record (completion)

```text
Milestone/subpackage and date: M8 — scale degrees and reusable tonal context, 2026-10-14
State: AUTOMATED_COMPLETE (drawer visual change pending owner approval; the degree clips were
  accepted in the M4.4 listening pass; acoustic and TalkBack rows stay with G3/G4)
Intent and user-visible result: Scale degrees is a complete mode — choose keys and tonalities,
  target degrees 1..7, one of five tonal contexts (tonic note, tonic triad, ascending octave
  scale, tonic–dominant–tonic, subdominant–dominant–tonic), context gap, range and one of four
  answer formats (degree, note name, movable-do, degree + syllable).
Files and contracts changed: core/training TonalContext/ContextBuilder (pure context values,
  CONTEXT-role groups, minor cadences use a major V while targets stay natural minor),
  ScaleDegreeDefinition and the provider with SC3 child streams (labels/context/target via
  ModeSeedDerivation), GeneratedScaleDegree provenance and AnswerDisplay.ScaleDegree;
  ScaleDegreeSetup/Draft/Validator with the context gap mirrored into RecipeTiming;
  ModePayload.ScaleDegrees/SCALE_DEGREES_CONFIG_VERSION and UserSettings.scaleDegreeSetup;
  core/data degree persistence (naming-aware), PresetConfiguration.ScaleDegrees +
  PresetPayloadScaleDegreesV1 + the "C major scale degrees" starter; app
  ScaleDegreeSetupScreen/ViewModel, Play/Presets kind-awareness, TrainingModes and service
  dispatch; ProviderRegistry registration; the voice pack now bundles the degree numbers and
  both movable-do syllable sets (99 entries, all reviewed); review device tests.
Regression demonstrated before repair: the review session/UI tests updated the saved kind and
  started immediately, racing the app's settings flow so the service could build the Start from
  the previous kind; the tests now wait for the saved and running kind (M8 device gate).
  The example-window re-timing regression from M5–M7 also applies here via the shared fix.
Commands, exit codes: check-music-fixtures 0; check-assets 0 and --require-reviewed 0
  (99 entries, 0 pending); strict fresh gradle music 58 / training 422 / data 162 / app 72 =
  714 tests, 0 failures; check-fast 0; check-native-tests 0 (9 fixtures);
  check-full --automated-only 0 with review-short 37, data-platform 5, audio-platform 9,
  before/after force-stop 1 each and the 30-minute endurance (memory variation 3.9 MiB against
  the 64 MiB bound).
Automated acceptance IDs passed/failed/unexecuted: M8-T01..T09 pass; T07/T08 through the
  review device suite (degree Start/Stop, context/target/answer phases, Association unit
  before a switch, interval<->degree and note<->degree switching, hidden answers).
Raw evidence directory: .local/verification/mode-expansion/M8/ (gate-2026-10-14/, device/,
  device-rerun/, device-ui/, ui-candidates/, native/, notes.md, domain/).
Human acceptance: the degree clips were accepted by the owner in the M4.4 listening pass and
  are bundled. The drawer change from removing the Scale degrees "Soon" badge is CLOSED: the
  owner accepted the candidate on 2026-10-14, the baseline
  app/src/test/screenshots/drawer.png was re-recorded (sha256 0d059a812c6e...), and
  scripts/check-ui.sh (strict, fresh) passes with all nine screenshots unchanged.
Unexpected findings and resolution: the settings-race test repairs above.
Current source state and next exact action: version 0.5.0 (versionCode 5). M9A/M9B
  (progressions and contextual intervals) may start and must consume this context builder
  rather than duplicating its cadence/minor rules; each completed mode gets its version and
  backup.
```

### M9A record (completion)

```text
Milestone/subpackage and date: M9A — chord progressions, 2026-10-14
State: AUTOMATED_COMPLETE (both human rows closed on 2026-10-14: the three harmonic-function
  clips were accepted by the owner, bundled and reviewed; the Chord progressions drawer badge
  removal was approved and the baseline re-recorded)
Intent and user-visible result: Chord progressions is a complete mode — the four frozen
  templates over compatible keys and tonalities, root-position or globally optimal
  nearest-inversion voicing, and three answer formats (Roman sequence, directed root motion,
  harmonic functions).
Files and contracts changed: core/music Progression/ProgressionVoicing (four templates,
  HarmonicFunction, minor V raised leading tone reusing the M8 rule, root-motion intervals
  with V->I a descending fifth, DP voicing with lexicographic tie-break and no RNG);
  core/training ProgressionDefinition and the provider (bag of templates, canonical
  realization order), ProgressionSetup/Draft/Validator, ModePayload.Progressions/
  CHORD_PROGRESSIONS_CONFIG_VERSION, UserSettings.progressionSetup, ProviderConfig and the
  registry registration; core/data progression persistence, PresetConfiguration.Progressions,
  PresetPayloadProgressionsV1 and the "Major chord progressions" starter; app
  ProgressionSetupScreen/ViewModel, Play/Presets kind-awareness with the silent intervals
  fallback removed, TrainingModes and service dispatch; the voice catalog gained the three
  owner-accepted harmony function entries and bundles the direction connectors (105-entry
  pack; 102 at gate time, before the function clips were approved); review device tests.
Regression demonstrated before repair: none new beyond the carried example-window re-timing
  fix; the mode's integrality tests prove a live edit or pending switch never splits a
  progression between chords.
Commands, exit codes: check-music-fixtures 0; check-assets 0 and --require-reviewed 0
  (102 entries, 0 pending at gate time; 105 after the function clips were bundled); strict fresh gradle music 73 / training 470 / data 174 / app 86 =
  803 tests, 0 failures; check-fast 0; device smoke 0 (review-short 44, data-platform 5,
  audio-platform 9, before/after force-stop 1 each); capture-ui-candidates 0 with the drawer
  declared pending. After both approvals the same day, the three clips were bundled (105-entry
  pack), the affected test expectations updated and the drawer baseline re-recorded: a fresh
  --rerun-tasks sweep passes with music 73 / training 509 / data 174 / app 86 = 842 tests and
  strict check-ui.sh exits 0 (nine screenshots unchanged). The full gate with native fixtures
  and the 30-minute endurance is the M9 integration gate after M9B, per the milestone document.
Automated acceptance IDs passed/failed/unexecuted: M9A-T01..T05 pass; the FUNCTIONS
  pronunciation row closed on 2026-10-14 with the owner's acceptance of the three clips
  (review record tools/voice-assets/reviews-m9a.json, listeners under
  .local/verification/mode-expansion/M9A/listening/).
Raw evidence directory: .local/verification/mode-expansion/M9A/ (gate-2026-10-14/,
  device-short/, ui-candidates/, listening/).
Human acceptance: the Roman sequence composes already-accepted number and chord-quality
  clips; root motion composes accepted interval phrases with accepted direction connectors.
  The owner accepted the three new function clips on 2026-10-14; they are bundled (105-entry
  pack) with review record tools/voice-assets/reviews-m9a.json. The drawer change is CLOSED:
  the owner approved the candidate on 2026-10-14, the baseline
  app/src/test/screenshots/drawer.png was re-recorded (sha256 c64daa3de363...) and the manifest case
  is approved again.
Unexpected findings and resolution: the PresetsScreen use() path had a silent intervals
  fallback for unimplemented kinds, replaced with explicit branches and an error.
Current source state and next exact action: version 0.6.0 (versionCode 6). M9B (intervals in
  context) started on 2026-10-14: its domain layer (ContextualIntervalDefinition and
  ContextualIntervalExerciseProvider) is implemented and under verification; it reuses the M8
  context builder and child streams. The M9 integration gate (native + full review with
  endurance) runs after M9B.
```

### M9B record (completion)

```text
Milestone/subpackage and date: M9B — intervals in context, 2026-10-14
State: AUTOMATED_COMPLETE (the drawer badge removal row closed on 2026-10-14: the owner
  approved the candidate, the baseline was re-recorded and strict check-ui passes)
Intent and user-visible result: Intervals in context is a complete mode — the target interval
  sounds inside an M8 tonal context (five kinds, major or natural minor), ascending,
  descending or harmonic, answered by interval phrase, degree pair or both.
Files and contracts changed: core/training ContextualIntervalDefinition and
  ContextualIntervalExerciseProvider (child streams labels/context/target, finite pair
  enumeration per selected interval, no interval classification from context pitches,
  harmonic unison dedupe, the minor-context raised leading tone never enters the
  natural-minor target pool), ContextualIntervalSetup/Draft/Validator,
  ModePayload.ContextualIntervals and INTERVALS_IN_CONTEXT_CONFIG_VERSION,
  UserSettings.contextualIntervalSetup, ProviderConfig registration; core/data
  contextual_intervals_* persistence, PresetConfiguration.ContextualIntervals,
  PresetPayloadContextualIntervalsV1 and the "Intervals over the tonic triad" starter; app
  ContextualIntervalSetupScreen/ViewModel, Play summary and Presets kind-awareness, review
  tests. Both M9 providers are production-wired.
Regression demonstrated before repair: two stale expectations still asserted the three
  harmonic-function clips were unbundled (ProgressionExerciseProviderTest and the progression
  host screen test); both were updated to the accepted, bundled state while their
  synthetic partial-catalog missing-clip cases stay covered.
Commands, exit codes: check-music-fixtures 0; check-assets and --require-reviewed 0; fresh
  gradle sweep music 73 / training 523 / data 186 / app 98 = 880 tests, 0 failures; check-fast
  0; device smoke 0 (review-short 51, data-platform 5, audio-platform 9, before/after
  force-stop 1 each); native fixtures 9/9; the M9 integration gate check-full --automated-only
  exit 0 (builds, lint, APK checks, native toolchain, the phone stage above, endurance-30m
  1 test in 1809 s and the memory bound 3743 KiB PSS variation over 26 samples), with the
  drawer declared pending and the regular app package/private-file hashes unchanged.
Automated acceptance IDs passed/failed/unexecuted: M9B-T01..T03 pass (domain and provider
  suites incl. frozen child-stream vectors); M9B-T04 passes (recipes, full context replays,
  token timing, duration bounds); M9B-T05 passes on host and the isolated phone (storage, live
  edits, progression<->contextual-interval switches, hidden metadata); M9B-T06 passes on the
  phone (lifecycle/timer/Stop, missing-asset and render-failure handling, the endurance and
  memory rows).
Raw evidence directory: .local/verification/mode-expansion/M9B/ (gate-2026-10-14/,
  device-short/, ui-candidates/, notes.md) with the integration evidence under
  .local/verification/mode-expansion/M9/ (native/, device/).
Human acceptance: no new recordings are needed — every spoken token is from the accepted
  105-entry pack. Acoustic quality, actual accessories and TalkBack usability remain open
  human rows. The drawer change is CLOSED: the owner approved the candidate on 2026-10-14,
  the baseline app/src/test/screenshots/drawer.png was re-recorded (sha256 d27ac0dddf4f...) and strict
  check-ui.sh passes.
Unexpected findings and resolution: the first integration-gate attempt stopped at the
  undeclared drawer difference (the fresh "Soon" badge removal); the change was declared with
  its region and the full gate then passed end to end. The M9B domain agent also handed back
  before its background check-fast finished; the session waited, re-ran the suites and fixed
  the two stale expectations named above.
Current source state and next exact action: version 0.7.0 (versionCode 7). M10 (passive
  melodic dictation) may start after the owner approves the drawer screenshot; each completed
  mode keeps its version bump and continuity backup (v0.6 for M9A, v0.7 for M9B).
```

### M10 record (completion)

```text
Milestone/subpackage and date: M10 — passive melodic dictation, 2026-10-14
State: AUTOMATED_COMPLETE (the drawer badge removal row closed on 2026-10-14: the owner
  approved the candidate, the baseline was re-recorded and strict check-ui passes)
Intent and user-visible result: Melodic dictation is a complete mode — bounded, reproducible
  tonal phrases over the M8 context, from two notes to sixteen, with fixed or free endpoints,
  selected signed steps, a maximum leap and a three-choice direction bias, answered by degree,
  movable-do or directed-interval speech. Passive only: no entry, scoring or marks.
Files and contracts changed: core/training MelodicDictationDefinition and MelodyGraph (scale
  nodes in range, signed-step edges bounded by the leap, zero only when allowed, canFinish DP
  bounded to 128 nodes x 16 rows), MelodicDictationExerciseProvider (context and target
  streams only, one context draw, one start draw and one weighted per-position draw with
  singleton draws counted; no label cycle and CyclePosition null), the exact reachable answer
  DP (node clips for degree/syllable answers, per-edge phrase clips with explicit clip-count
  connection gaps for interval answers, checked integers), MelodicDictationSetup/Draft/
  Validator, ModePayload.MelodicDictation and MELODIC_DICTATION_CONFIG_VERSION, the new
  SetupField values LENGTH/ENDPOINTS/STEPS/LEAP/BIAS, UserSettings.melodicDictationSetup,
  ProviderConfig and the eighth production registration; core/data melodic_dictation_*
  persistence (nullable endpoints stored as 0 = any), PresetConfiguration.MelodicDictation,
  PresetPayloadMelodicDictationV1 and the "Tonic-to-tonic four-note phrases" starter with
  cycle policies refused on validation, fallback and import; app MelodicDictationSetupScreen/
  ViewModel, the Play summary with a bounded scroll region for long answers, Presets
  kind-awareness and review tests.
Regression demonstrated before repair: the frozen replay test caught a real direction-bias
  inversion (the weighted bucket used the reversed step sign) before the vectors were frozen;
  fixed and re-recorded. The first device run exposed a stop-to-start race around the
  repository edit (an item could be compiled from the previous mode) and one summary-string
  expectation that missed the "bias" word; both were corrected in-suite and the re-run is
  fully green.
Commands, exit codes: check-music-fixtures 0; check-assets and --require-reviewed 0 (105
  entries); fresh gradle sweep music 73 / training 593 / data 201 / app 113 = 980 tests, 0
  failures; check-fast 0; native fixtures 9/9; device smoke 0 (review-short 57, data-platform
  5, audio-platform 9, before/after force-stop 1 each). The 30-minute endurance and the
  complete integration run belong to M11 per the milestone document.
Automated acceptance IDs passed/failed/unexecuted: M10-T01..T04 pass in the definition, graph
  and provider suites (lengths 2/16, fixed and octave endpoints, zero-leap gating, exact
  maximum leap, no chromatic nodes, exhaustive tiny-graph reachability and duration checks,
  frozen seed vectors with exact weighted and singleton draws). M10-T05 passes (10,000 phrases
  across a fixed seed/config corpus with invariants and a bounded generation time). M10-T06
  passes (all answer modes and recipes, 16-note and 15-interval speech, the cap below/at/above
  60 s, maximum replay rejection). M10-T07 passes (cycle policies rejected before persistence
  and Start; count/time policies and switch deadlines). M10-T08 and M10-T09 pass on host and
  the isolated phone (persistence and failed saves, live edits and history, stopped
  recreation, switching in every phase, hidden semantics/media, focus/route/Stop cleanup and
  bounded memory).
Raw evidence directory: .local/verification/mode-expansion/M10/ (gate-2026-10-14/,
  native/, device/, ui-candidates/, notes.md).
Human acceptance: no new recordings are needed — every spoken token is from the accepted
  105-entry pack. Acoustic quality, actual accessories and TalkBack usability remain open
  human rows. The drawer change is CLOSED: the owner approved the candidate on 2026-10-14,
  the baseline app/src/test/screenshots/drawer.png was re-recorded (sha256 f742b1246328...) and strict
  check-ui.sh passes.
Unexpected findings and resolution: a server restart interrupted the product integration and
  corrupted two half-written screen files to NUL bytes; they were deleted and rebuilt from
  the surviving wiring. The device-run failures above are regression evidence, not open items.
Current source state and next exact action: version 0.8.0 (versionCode 8). M11 (integration
  and acceptance) may start after the owner approves the drawer screenshot; the final full
  gate with the 30-minute endurance closes the release.
```

### Whole-project QA pass and one-command entry point (2026-10-15)

Owner-authorized whole-project QA over the delivered 0.9.1 tree, with a new repeatable entry
point. `scripts/qa.sh` (no arguments) runs the complete automated QA and writes a timestamped
report folder: fresh `check-full.sh --automated-only --fresh --skip-endurance`,
`check-assets.sh --require-reviewed`, strict `check-ui.sh`, the light candidate report (zero
pending required), the dark review re-capture, `check-apk.sh` over debug/release/review and a
crash/ANR/terminal-resource scan, then `QA-REPORT.md`. `check-full.sh` gained the narrowly
scoped `--skip-endurance` option (maps to `device-smoke.sh --smoke-only`) under the owner
endurance decision of 2026-10-15; the M11 endurance evidence (real 30-minute screen-off,
1818 s, memory guard 23,209 KiB PSS variation) is cited in the report instead. First full pass
(`.local/verification/qa-2026-10-15-2135/`, `QA-REPORT.md`): PASS on all ten stages — 1087
fresh host tests (music 73, training 600, audio 59, media 24, data 205, app 126; 0
failures/errors/skips), music-fixtures/docs/voice-durations/boundaries and 1393 dependency
checksum artifacts pass, native CTest and 9 rendered fixtures pass, debug/release/review
assembled and linted, review-short 58 device tests plus data 5, audio 9 and force-stop 1+1,
0 crash/ANR inside the run window, regular-app package/private hashes unchanged, strict 9/9
screenshot baselines, zero pending/undeclared visual cases, dark review set re-captured, no
INTERNET permission and no review-only classes in the production DEX. No repair was needed
(findings.md: none), so no version bump and no backup change. The evidence folder also holds
the IDE MCP review record (lint of all 131 main-source files, bounded call analysis of the
answer-publication and Start/switch paths; one stale-index IDE error documented as IDE-only)
and the M11-matrix coverage mapping. README and TEST_STRATEGY now document `scripts/qa.sh`.
Human rows (acoustic quality, actual accessories, TalkBack, G3/G4) stay OPEN.

### v0.9.1 — brand palette on upgraded installs (2026-10-14)

Owner report: both phones still showed wallpaper (dynamic) colours after 0.9.0. Cause: the old
settings default stored `dynamicColor = true`, so an upgraded install kept it even though the
brand palette is now the theme default. Fix: settings schema 3 (`UserSettings.CURRENT_SCHEMA_VERSION`)
turns dynamic colour off once for stores written before it; the Settings switch still allows
opting back in, and a schema-3 store keeps an explicit choice. Tests:
`SettingsSchemaThreeTest.aSchemaTwoStoreAdoptsTheBrandPaletteOnceAndKeepsAnExplicitChoice` and
`.aSchemaThreeStoreKeepsTheStoredDynamicChoice`; host suites green (training 600, data 205,
app 126). Version 0.9.1 (versionCode 10); the v0.9 backup was refreshed with this build.

### v0.9.0 release and v0.9 backup (2026-10-14)

First release of the v0.9.x line at version 0.9.0 (versionCode 9). Contents: the brand palette
(D28), the V4 launcher icon (D29) and the drawer header (D30), with all nine light screenshot
baselines re-recorded after owner approval and a dark review set captured. A minimal
continuity backup lives at `an external backup/` (source tree plus
bundled assets, verification evidence and a signed release APK) and the release APK is
installed on the testing phone. The v0.9.x line continues after this backup.

### v0.9.x QA revision (2026-10-16)

The owner-created automated QA harness (`scripts/qa.sh`; helpers `qa-report.py`,
`qa-device-scan.py`, self-tests in `scripts/tests`) drove a full revision pass. The first runs
exposed environment and harness findings (QA-F01..F03, F05..F07) and two test-order races
(F-08 and the long-standing SwitchModeTest flake); every repair carries its own verification
and is recorded with exact commands in `.local/verification/qa-2026-10-16-1253/repairs.md`:
workspace-local uv/ccache/XDG/Robolectric caches, a workspace-local byte-identical debug
keystore for the app and core modules, loud QA-stage failure propagation, the recipe-loop
300 ms settle, the prepare-count assertion moved to a deterministic point plus a 50x
repetition guard, the Jackson deprecation fixed, and dead code removed
(`SetupField.MODE/SEED/DIRECTION/OCTAVE_SPEECH/ANSWER_FORMAT`, `darkControlTextColors`). The
harness gained `--with-endurance` (scanner/report require the real endurance markers when
set). The closing run passes all ten stages: 1088 fresh host tests, review-short 58, data 5,
audio 9, force-stop checks, strict/light/dark visuals with zero pending cases, the APK audit,
the device crash/ANR scan and the real 30-minute endurance (1818 s; memory guard 21,996 KiB
PSS variation against the 64 MiB bound). A warm-index Android Studio MCP review (12 files,
0 errors; incoming-call trace confirms the production compile path) is embedded in the QA
report. Version stays 0.9.1; the human rows remain open. A new continuity backup,
`an external backup/`, captures this revised state (its own signed
APK, workspace and all QA evidence) and does not replace the v0.9 line-start backup. An
independent owner-run `scripts/qa.sh` at 13:39 also passed every stage.

### v0.9.15 — more shared voice clips (2026-10-17)

Owner instruction ("all with the recommended takes") under D31/D41. Ten further duplicate voice
files now share their canonical takes: the perfect-pitch octave words one, three, four, five, six
and seven use the degree takes; the chord major seventh and minor seventh use the interval takes;
the connector unison uses the interval take; and the scale "major" uses the chord-quality take.
The manifest points both ids at one WAV each, the duplicate files are removed (1,023,360 bytes),
the vocabulary records the sharing and the review records note the owner's approval. The pack
remains 83/83 accepted; the asset validator allows shared files and still checks every entry.
Host checks pass, and the built APK carries 72 voice WAVs for the 83 entries (8,180,262 bytes);
the ten removed files save 1,023,360 bytes. Version 0.9.15 (versionCode 24); installed on the
testing phone, and the phone suite passes (review-short 60 in 535 s, data 5, audio 9 and the
force-stop checks).

### v0.9.14 — the perfect-pitch “two” reuses the approved clip (2026-10-17)

Owner request under D31/D40: the perfect-pitch octave "two" (`note.octave.2`) now plays the
same owner-approved Cori carrier take as `degree.two`. Both manifest entries point to one WAV
(`wav/degree_two.wav`) and the old `note_octave_2.wav` is removed, saving its 51,970 bytes. The
asset validator now permits a shared physical clip while still checking each entry's format,
hash and duration; the pack remains 83/83 accepted. The vocabulary marks the sharing and the
review record carries the owner's request. Host checks pass, including the reviewed asset
validation and the duration map. Version 0.9.14 (versionCode 23); installed on the testing
phone, and the phone suite passes (review-short 60 in 536 s, data 5, audio 9 and the force-stop
checks). The shared clip removes the 51,970-byte octave file from the package.

### v0.9.13 — the owner's chosen “two” take (2026-10-17)

Owner pick under D31/D39: the “two” answer clip is the Cori carrier take, cut from “the number
is . . . two” with Piper phoneme alignments (length scale 1.6, 0.904 s), then the standard pack
postprocessing. The vocabulary (Cori, length scale 1.6, carrier metadata), the pack manifest and
`BundledVoiceDurations` record the new voice, hash and duration; the review record marks it
accepted on the owner's 2026-10-17 listening, so the pack is 83/83 accepted. The isolated and
carrier alternatives remain under `.local/verification/voice-two-2026-10-17/` as the audition
evidence. Host checks pass, including the fully reviewed asset validation. Version 0.9.13
(versionCode 22); installed on the testing phone, and the phone suite passes (review-short 60 in
535 s, data 5, audio 9 and the force-stop checks).

### v0.9.12 — a polished “two” answer clip (2026-10-17)

Owner request under D31/D39: the spoken "two" used in scale-degree and Roman numeral answers is
regenerated with the same Jenny voice at length scale 1.4 (0.729 s instead of 0.650 s). The clip
keeps the pack pipeline (silence trim, 80/120 ms pads, atempo 0.85, two-pass loudnorm -20 LUFS /
-3 dBTP, mono 48 kHz PCM16). The vocabulary records the length scale, and the pack manifest and
`BundledVoiceDurations` carry the new frame count, hash and duration. The review record marks the
clip pending the owner's listening pass; the other 82 clips stay accepted. A listening sheet with
the current take, two slowed variants, the new Jenny take, a longer Jenny variant and Cori/Alba
comparisons is at `.local/verification/voice-two-2026-10-17/listen/`. The owner rejected all of
those takes and asked for a carrier-phrase route; a second sheet cuts the word "two" out of
"one, two, three" (and a period variant) with Piper phoneme alignments in Jenny, Cori, Alba, Amy
and Southern English, at `.local/verification/voice-two-2026-10-17/phrase-listen/`. The installed
clip stays the Jenny 1.4 take until the owner picks a phrase take. After another rejection, a third sheet adds longer input gaps (sentence-separated “one . . . two . . . three”, semicolon, dash and line variants) and sentence-final carriers (“the number is . . . two”) in the same voices at `.local/verification/voice-two-2026-10-17/gap-listen/`. Host checks pass, including
the voice-duration and asset validators; suites are 1081 tests, 0 failures. Version 0.9.12
(versionCode 21); installed on the testing phone, and the phone suite passes (review-short 60 in
527 s, data 5, audio 9 and the force-stop checks). The new take remains pending the owner's
listening review; the asset validator reports that one pending entry.

### v0.9.11 — the owner's interval starter presets (2026-10-17)

Owner instruction under D31/D38. The three interval-recognition starters shipped since M5 are
replaced by the four presets the owner built on the testing phone: Major Pentatonic, Minor
Pentatonic, Major Triad and Minor Triad. Each keeps the reviewed configuration: C4..C5, the fixed
C4 root, a 3 s thinking pause, the separator cue and seed 776655. Preset schema 11 deletes the old
built-in interval rows once, so an upgraded store shows only the new four; the five user slots and
every user preset are untouched, and the four custom copies on the review app remain. The starter
identity and migration are covered by a focused upgrade test (old built-in removed, new four
seeded, a user preset and the five slots preserved). The host screenshot fake now shows Major
Pentatonic; the owner approved the `presets` screenshot on 2026-10-17, so all nine baselines are
approved and strict check-ui.sh passes. At the owner's request every custom preset was then
cleared in every mode of both the regular and review apps: the regular app had two filled
interval slots and the review app four, no non-slot customs existed, and both stores now have
zero filled user rows, 11 starters and empty slots with default slot names. The databases were
verified before and after the rewrite and on screen; `.local/verification/preset-defaults-2026-10-17/`
holds the before/after copies and the Presets screenshot.
Host suites: music 69, training 582, audio 59, media 24, data 218, app 129 (1081 tests, 0
failures). Version 0.9.11 (versionCode 20); installed on the testing phone. Its regular app
store upgraded to schema 11, lost the three old interval starters and gained the four new ones
before its custom presets were cleared; the review app upgraded the same way and its four custom
copies were cleared too. The phone suite passes (review-short 60 in 533 s, data 5, audio 9 and
the force-stop checks). A minimal continuity backup at
`an external backup/` (760 MB: the source tree, the two latest
evidence folders and the signed release APK `2442ed87…`) captures this state; the earlier
backups are untouched.

### v0.9.10 — progression opening and Play-card polish (2026-10-17)

Owner clarifications under D31/D36/D37. Progression keys are no longer selectable: every key is
used unless the fixed root pins one, and the stored key list is ignored (the preset payload's
`key_values` stays a legacy field and is written empty). Every exercise now opens on the plain I
triad, never Imaj7 and never a pool draw; the selected chords only fill the following three
slots, with repeats. Placements are drawn only from keys and anchors that realize the opening I
and the whole pool, so a key that cannot fit a selected seventh is skipped instead of making the
setup unusable; a selection with no feasible placement is still rejected. The Play status and
spoken answer no longer name the mode and use the page-title text size. The Current setup card
names the mode and uses one text size for every line, including the seed. The bottom bar uses
24 dp vector icons for Presets, Play and Setup, because the note font glyph rendered smaller than
its siblings on the testing phone.

Tests: updated progression definition, provider, validator, persistence, preset, host-screen and
review-device coverage for the fixed I opening and the removed key selection. Host suites: music
69, training 582, audio 59, media 24, data 216, app 129 (1079 tests, 0 failures). The four Play
screenshots are re-declared changes pending the owner's visual approval (status size, the mode
line in the card, the uniform card text); the other five images are unchanged. Version 0.9.10
(versionCode 19); installed on the testing phone. The owner approved the four Play screenshots
on 2026-10-17, the baselines were re-recorded and strict check-ui.sh passes. The first phone run
hit the known transient
Compose/Espresso measure race in `scaleDegreesCanBeSelectedStartedAndStopped`; the test passed
three targeted reruns and the full suite passed on the rerun (review-short 60 in 535 s, data 5,
audio 9 and the force-stop checks). Evidence: `.local/verification/progression-opening-2026-10-17/`
with the Play and progression-setup device screenshots.

### v0.9.9 — chord-progression rebuild and play polish (2026-10-17)

Owner batch under D31/D36/D37. The chord-progression mode was rebuilt as a four-chord draw:
the setup picks any of the fourteen major-key diatonic chords (seven triads and their seventh
chords), the engine always opens on a selected tonic (I or Imaj7, drawn at random when both are
selected) and then draws three chords with replacement from the full pool, so a chord may
repeat. Answers speak the drawn Roman sequence, its adjacent root motions or its harmonic
functions, and the display reads `I ii V7 ...`. A fixed root pins the tonic MIDI note. The
frozen template catalog and natural-minor progression templates are gone; stored progression
setups and presets without a chord pool read as the seven default triads (`template_ids` is
ignored, and the new `chord_ids`/`fixed_root` keys and payload fields are additive), and
AfterCycle stopping is refused because the mode has no label cycle. The setup screen groups the
pool into Triads and Seventh chords, adds the fixed-root switch, allows inversion 3 for seventh
chords, and explains an impossible chord/key/range pair. The music layer gained the diatonic
pool, seventh-chord Roman labels (Imaj7, ii7, V7, viiø7), a mixed triad/seventh voicing search
and the fixed function mapping. The Play tab's bottom-bar note glyph now matches the star and
gear at 24sp, the Play status shows the current mode on its own line at the normal body size,
and the perfect-pitch "Fix the octave" option was removed (old stored values are ignored).

Tests: new pool/label/motion tests in core:music; definition, provider, setup-validator,
settings-persistence, preset round-trip, host-screen and review-device updates. Host suites:
music 69, training 582, audio 59, media 24, data 216, app 129 (1079 tests, 0 failures). Four
Play screenshots are declared changes pending the owner's visual approval (play-answer,
play-idle, play-large-text and play-sleep-picker: the added mode line and the status size); the
other five images are unchanged. The new mode line made four review UI tests tap an ambiguous
duplicate of the mode name; those taps now select the clickable tab node, and the progression UI
test asserts the new Play summary. Version 0.9.9 (versionCode 18); installed on the testing
phone. The phone suite passes (review-short 60 in 534 s, data 5, audio 9 and the force-stop
checks). The progression setup screen was also checked on the device and captured at
`.local/verification/progressions-2026-10-17/device-progression-setup.png`.

### v0.9.8 — a melodic unison sounds twice (2026-10-17)

Owner report under D31/D35: in interval recognition a unison played one note; it now plays the
same pitch twice, one melodic stride apart, so both example slots are audible. The root cause
was the shared `IntervalPresentation.notes()` collapsing an equal target and the intervals
provider turning the remaining note into a note-plus-rest example; both now keep the two
melodic entries. A harmonic unison stays one note, because two simultaneous copies of one
pitch cannot be told apart, and the intervals-in-context mode already kept its two sequential
groups. Timing, feasibility, durations and answer voices are unchanged. Tests: the model,
generator and adapter tests pin the two onsets; the new adapter test also proves that the
legacy and shared compilers produce the identical two-onset timeline one stride apart. Host
suites: music 72, training 597, audio 59, media 24, data 213, app 128 (1093 tests, 0 failures).
No UI or screenshot change. The phone suite passes (review-short 60 in 507 s, data 5, audio 9
and the force-stop checks). Version 0.9.8 (versionCode 17); installed on the testing phone.

### v0.9.7 — preset recency follows last use (2026-10-16)

Owner request under D31/D34: the preset page's "Most recent" order now follows the preset the
user applied last, not the one saved last. The store records the moment in a new
`last_used_at_epoch_ms` column (preset schema 10); existing rows read as never used. A save
and a rename keep the stored value (update preserves it), a cleared slot forgets it, and a
duplicate starts unused. Applying a preset marks it used only after the mode write succeeds;
built-in starters are markable too, and the kind-aware screens now read their starter row from
the store, so it takes part in the same order. The moment is local: it is not part of the
preset export document. Tests: the data store proves that marking a use records the moment,
leaves the saved time alone and survives a save and a reload; the app sort test proves that
saved times never reorder the list, that equal use times break A to Z, and that applying a
preset moves it to the front. Host suites: music 72, training 596, audio 59, media 24, data
213, app 128 (1092 tests, 0 failures). All nine screenshots render unchanged, so no new visual
approval is needed. Version 0.9.7 (versionCode 16); installed on the testing phone, whose own
preset store migrated to schema 10 with every existing row reading as never used. The phone
smoke suite passed on its second run (review-short 60 in 506 s, data 5, audio 9 and the
force-stop checks); its first run hit a one-off Compose/Espresso race in
`ReviewUiDeviceTest.notesCanBeSelectedStartedAndStopped` (`performMeasureAndLayout called
during measure layout` inside waitForIdle) - the test passed three targeted reruns and the
full suite passed immediately after. A minimal continuity backup at
`an external backup/` (758 MB: the source tree with the bundled
assets, the closing QA evidence and this version's evidence, and the signed release APK
`b03d87c0…`) captures this state; the earlier backups are untouched.

### v0.9.6 — the sleep timer remembers its choice (2026-10-16)

Owner request under D33: the sleep timer keeps its last value across sessions. The choice is a
saved setting (`sleepTimerMinutes`, zero means Off); the Play screen seeds its chooser from it
and persists every change, and the media transport sends the stored value to the controller
before each fresh Start, so starts from the screen, a notification or a reconnect all arm the
same timer. Off is remembered too, and presets never change the timer (global preference).
Tests: the data store round-trips the value and falls back to Off for an impossible stored
value; `UserSettingsTest` pins the default; the new device test
`theSleepTimerRemembersTheLastChoiceAcrossSessions` proves the armed value across two sessions.
Host suites: music 72, training 596, data 212, app 127 (1007 tests, 0 failures); phone suites:
review-short 60, data 5, audio 9, before/after force-stop checks. Two device tests were
adjusted for the closed drawer staying composed off-screen (they now assert the visible title
and the unique page lines). Version 0.9.6 (versionCode 15); installed on the testing phone. The owner approved the three
pending visual candidates on 2026-10-16 (drawer, presets and settings); the baselines were
re-recorded, strict check-ui.sh passes, and the full QA run (`scripts/qa.sh --with-endurance`)
started on this build.

The first full QA run (`qa-2026-10-16-1716`) failed only at the endurance stage: the new
transport resend of the stored timer sent zero and cancelled the harness's explicit 30-minute
arm, and the controller did not consume the one-shot pending timer, so a later session could
re-arm it. Repairs: the transport arms the remembered value only when it is positive (an
explicit arm is never cancelled), and the controller consumes the pending timer when it
applies it. Regression: the device test now proves that an explicit arm survives a Start while
the stored choice is Off. The second full QA run (`qa-2026-10-16-1745`) passes all ten stages
on build 0.9.6: 1090 fresh host tests (music 72, training 596, audio 59, media 24, data 212,
app 127), review-short 60 plus the platform and force-stop suites, strict/light/dark visuals
with zero pending, the APK audit, the device scan and the real 30-minute endurance (1819 s;
26 memory samples; PSS variation 21,699 KiB against the 64 MiB bound).

### v0.9.5 — settings, help and navigation polish (2026-10-16)

Owner UI requests under D31. The Settings page lost its Local data section (heading, storage
card and the Open presets button; the Presets tab remains in the bottom bar) and the duplicate
in-page Settings heading, so only the top bar titles the page. The top bar now titles the
Settings and Help pages with their own names instead of the audible mode. The Help
page heading is "About EarBy". The preset list defaults to Most recent, with A to Z as the
second choice and as the tiebreaker for equal times. The bottom bar glyphs grew to about 1.5x
(the star and gear to 24sp, the note glyph to 32sp, 1.2x their size, so the three look alike).
App suite 127 tests, 0 failures; the review test asserts the two page titles and the About
heading. The settings, presets and drawer screenshots are declared changes pending owner
approval (`.local/verification/ui-polish-2026-10-16/`). Version 0.9.5 (versionCode 14);
installed on the testing phone.

### v0.9.4 — solfege removed (2026-10-16)

Owner request under D31/D32: remove solfege and its voice assets to shrink the app. Removed:
the settings Scientific/Solfege naming preference and `toNoteNaming()`, `NoteNaming` and
`NoteName.Solfege`, `DegreeAnswerFormat.MOVABLE_DO`/`DEGREE_AND_SYLLABLE`,
`MelodicAnswerFormat.MOVABLE_DO_SEQUENCE`, the `VoiceVocabulary` fixed-do/movable-do builders,
the Note spelling row on Settings, the syllable chips in the scale-degree and melodic-dictation
editors, and the 22 `fixeddo.*`/`degree.syllable.*` catalog entries with their packaged WAVs.
The pack is now **83 entries** (voice WAVs 10.76 MiB -> 8.80 MiB, about 2 MiB saved; the
206 MiB SoundFont is unchanged). Settings schema 4 ignores the stored spelling key and maps
stored removed answer formats to `DEGREE`/`DEGREE_SEQUENCE` on both the DataStore and preset
decode paths, so upgraded installs and saved presets keep their other choices. Host suites:
music 72, training 596, data 211, app 127 (1006 tests, 0 failures); `check-voice-durations` and
`check-assets --require-reviewed` pass at 83. The `settings` screenshot is a declared change
pending owner approval (the drawer Help candidate from v0.9.2/0.9.3 also remains pending).
Version 0.9.4 (versionCode 13); installed on the testing phone.

### v0.9.3 — audio explanation on the Help page (2026-10-16)

Owner request under D31. Each mode on the Help page now carries a second, muted sentence that
explains how the audio plays out (for example, two notes play and the answer names the
interval), and the introduction explains Recognition, Exposure and Association. The sentences
live beside the shared summaries in `TrainingModes.kt` (`TrainingKind.audioDescription`), so
the Help page and the mode definitions cannot drift. The copy follows the simple English
rules (short sentences, active voice, no contractions). `HelpScreenHostTest` asserts every
audio line and the recipe sentences; host suite 127 tests, 0 failures. Version 0.9.3
(versionCode 12); the updated Help render is
`.local/verification/help-2026-10-16/help/help.png`, and the phone runs this build.

### v0.9.2 — Help page in the drawer (2026-10-16)

Owner request under the new version rule (D31: every change from 2026-10-16 raises the
version, starting here). The drawer now reads EarBy, the tagline, the eight modes, Settings and
Help; Help opens a page with the app's purpose, the eight mode labels plus the shared
one-sentence summaries, and the packed build version (BuildConfig). The standard Material help
glyph ships as a local vector (`res/drawable/ic_help.xml`) so no new dependency is needed.
Tests: `HelpScreenHostTest` (purpose, all eight modes, version) and the review-UI test
`helpOpensFromTheDrawerAndShowsTheModesAndTheBuild` (drawer navigation plus system back to
Play). Host suite 127 tests, 0 failures; review variant compiles. The drawer screenshot is a
declared change pending owner approval (candidate
`.local/verification/help-2026-10-16/candidates/drawer.png`; the Help page review render is
`help/help.png`). Version 0.9.2 (versionCode 11); installed on the testing phone the same day.

### First v0.9.x-line change — theme colours (2026-10-14)

Owner request on the day the v0.9.x line starts: the light theme colour is `#89E2C5` and the
dark theme colour keeps the same hue and saturation with a darker lightness (45%, about
`#2DB88B`). Implemented in `app/src/main/kotlin/com/thomas/passiveeartraining/app/Theme.kt`
(one brand hue drives primary, on/container, secondary, tertiary, surface tint and inverse
primary); decision D28. A first cut left the nine screenshot baselines byte-identical, but the
owner then noticed the app still looked blue: Dynamic colour defaulted on, so Android 12+
wallpaper colours replaced the brand palette entirely, and the neutral surfaces still carried
Material's lavender tint. Follow-up the same day: `dynamicColor` now defaults off
(`UserSettingsTest` pins it), the neutral surface/outline/inverse roles are mint-tinted from
the brand hue, and every screen is recoloured. `BrandThemeTest` pins both anchors; the host
suites stay green. A second follow-up then removed the light-mint label text: the Material
outlined/text buttons, value choosers and dialogs had been painting their labels with the light
primary, so the app's text-bearing controls now use the dark secondary (`darkOutlinedButtonColors`
and `darkTextButtonColors` in Theme.kt). All nine candidate screenshots are declared changes
pending the owner's re-record approval; the owner approved all nine, the baselines were
re-recorded with their new hashes on 2026-10-14 and the strict check-ui.sh passes (nine
unchanged). A dark-mode review set of the same screens was then captured with the temporary
dark-theme harness (`dark-candidates/dark-*.png` in the same evidence folder); it is a review
artifact, not a new baseline set. The earlier swatches remain under
`.local/verification/theme-review-2026-10-14/`.

### Post-M11 addendum — note-letter voice refresh (2026-10-14)

Owner-requested after M11: the single-letter clips A..G for Perfect pitch were reviewed again.
Owner verdict: A and C keep the current clips, B, D, F and G adopt the split of one alba
"A. B. C. D. E. F. G." take (tools/voice-assets/split_letter_list.py), and E adopts the
accepted cori candidate (tools/voice-assets/probe_letter_e.py); the review record is
tools/voice-assets/reviews-note-letters.json and the provenance is documented in
docs/audio/ASSETS.md. Effects: the voice pack manifest hash moved from d2e10ffe251822b2 to
1fa600ec5a9f6209 (still 105 entries, all human-accepted), BundledVoiceDurations regenerated,
and the host suites re-run green (training 600, app 124, 0 failures); check-voice-durations
and check-assets --require-reviewed pass. Nothing else in the delivered feature line changed;
the M11 record and its evidence stand as recorded before this refresh.

### M11 record (completion)

```text
Milestone/subpackage and date: M11 — integrated verification and agent handoff, 2026-10-14
State: AUTOMATED_COMPLETE / HUMAN_OPEN (human rows named below; no publication authorized)
Intent and user-visible result: final integrated verification of the delivered v0.2.x feature
  line (0.8.0): all eight modes through one service, saved settings, five slots per mode and
  three recipes, verified by the canonical automated workflow and the real 30-minute
  screen-off endurance.
Files and contracts changed (tests/scripts/docs only; no production behavior changed):
  new host suites ProviderRecipeMatrixTest, OrderedSwitchPairMatrixTest, SessionHistoryBoundTest,
  PresetSchemaUpgradeMatrixTest, HiddenAnswerSemanticsTest, AllModesTabsTest; new 44.1 kHz
  render cases; SwitchModeTest phase extensions; extended ReviewEnduranceDeviceTest (warmup +
  declared 3-minute mode schedule with request/commit evidence) and a reconnect lifecycle test;
  scripts/check-fast.sh and check-full.sh gained a documented --fresh option; STATUS banner and
  README/ledger updates.
Regression demonstrated before repair: M11-F01 — the endurance schedule's settings-only edits
  never reach a running session; the test now sends SessionCommand.SwitchMode with the
  destination StartRequest resolved exactly as the app dispatch does. M11-F02 — the measured
  run's phase assertion rejected the legitimate PREPARING phase after a committed switch; the
  assertion now accepts PLAYING or PREPARING and tolerates an instant boundary commit. Both
  failing runs are preserved under finding-M11-F01/ and finding-M11-F02/.
Commands, exit codes: canonical command above, exit 0 (gate-full-3.log). Fresh host sweep
  music 73 / training 600 / audio 59 / media 24 / data 203 / app 124 = 1083 tests, 0 failures;
  scripted Python suites 25 + 35 OK; assets 105/105 and --require-reviewed 0; native CTest 2/2
  + 9 fixtures + NDK; debug/release/review builds, lint and check-apk pass; strict check-ui 0
  with nine byte-identical baselines; review-short 58 tests; data-platform 5; audio-platform
  9; before/after force-stop 1 each; endurance-30m 1818 s; memory 26 samples, PSS variation
  23,209 KiB <= 65,536 KiB; regular app package/private hashes unchanged.
Automated acceptance IDs passed/failed/unexecuted: M11-A01..A06 pass (see
  .local/verification/mode-expansion/M11/final-report.md). The new host/device suites close
  the audited M11.2 gaps (all 56 ordered mode pairs, provider x recipe matrix, intermediate
  schema upgrades, history bound, semantics-level hidden answers, 150%/200% all-tab renders,
  reconnect). Unusable tool rows are recorded: Android Studio MCP get_file_problems /
  search_symbol / analyze_calls timed out on cold indexes and are not claimed as inspection.
Raw evidence directory: .local/verification/mode-expansion/M11/ (final-report.md, findings.md,
  finding-M11-F01/, finding-M11-F02/, input-inventory.md, gate-full-3.log, native/, device/,
  ui-candidates/, ide-review.md, check-ui-strict.log, check-assets-reviewed.log).
Human acceptance: acoustic quality, actual accessory behaviour, TalkBack usability and
  G3/G4 remain OPEN and are not claimed. No new pronunciation rows: all 105 bundled clips
  were previously owner-accepted and M9B/M10 added no recordings; every drawer visual
  candidate was owner-approved (M8, M9A, M9B, M10) and no pending visual candidate remains.
Unexpected findings and resolution: the two test-side findings above; the IDE analysis
  timeouts recorded in ide-review.md; the documented bundled-piano size waiver.
Current source state and next exact action: version 0.8.0 (versionCode 8); backups v0.2-v0.8
  under an external backup. No further work is authorized; any new track needs fresh
  owner authorization and must keep G3/G4 open.
```

## Carry-forward inventory (high level; detail belongs to M2/M4)

- Semantic voice ID namespaces will be kind-prefixed and independent: existing
  `interval.*` (13 clips) stays; chords, scales, note identification, scale degrees,
  progressions, contextual intervals and melodic dictation each add their own namespace
  and kind-specific catalog entries in M4–M10. Voice IDs remain semantic asset IDs, never
  display strings or file paths.
- Schema transitions owned later: settings wire schema 1→2 (M2), preset document 1→2
  (M2), SQLite version 8→9 (M2). All are single-transaction, preserve legacy rows and
  keep future versions write-protected.
- D16 seed derivation `mode-seed-v1` and generator provenance fields are specified in
  SC3; test vectors are frozen when first implemented (M1/M4).

## Resuming in a fresh agent context

Read the index, shared contracts, decisions and latest actual package record. Inspect the
workspace and source again. Verify the most recent evidence belongs to the present input
hashes; do not rerun a completed long suite without a change or unresolved concern. Resume
the first incomplete subpackage. Keep failed attempts and explain superseded evidence.

## Documentation-only preparation evidence

The creation/consistency check for these plans is recorded separately under
`.local/verification/v0.2.x-planning/`. It is not an implementation or app-test result.

On 2026-10-11, documentation verification passed: local links/text integrity in 39 maintained
documents; all twelve M0–M11 files present; 100 unique acceptance/regression IDs; eleven command
references checked, with the new music-fixture command explicitly planned. Direct Gradle examples
retain strict verification and native/full examples redirect evidence outside historical folders.

Hash comparison confirms 17 new documents in this folder and only one changed pre-existing
document, development STATUS. The original overall plan, planDraft.md, README, AGENTS and all
other prior documents remain byte-identical. Logs and before/after manifests are in
`.local/verification/v0.2.x-planning/2026-10-11T102753Z/`. No application or phone tests were run
for this documentation task. D08 and D09 are resolved and recorded with inspected references.


### Post-0.9.x addendum — Osiris D3 hybrid and separator (2026-10-23)

The 0.9.21 D6 hybrid was accepted by the owner, but a few mid/high notes still had a key-clash
sound. The production boundary was therefore lowered to D3 (MIDI 50): Osiris covers B0-C#3 and
the original MuseScore General covers D3-C8, with the original two-note separator clip unchanged.
The renderer keeps its 5 ms onset ramp, 20 ms raised-cosine final envelope once per unique chord
window, and exact back-to-back release continuity.

Version 0.9.22 / versionCode 31 was the main-line build at commit `cde34f0`; a docs-only
wrap-up commit followed it. The technical boundary test passed: B0 and C#3 match the previous Osiris
renditions, while D3, G4, C6 and C7 match the original MuseScore General (correlations
0.99851-1.00000). Owner listening was still pending, so no new human acceptance is claimed. The
0.9.23 custom synth supersedes this candidate.

- Testing phone: 0.9.22 installed; not yet listened to after the D3 change.
- Daily phone APK: `dist/EarBy-0.9.22.apk`, SHA-256
  `b77b916f3f80d68e015813a2304975e41c9e5e41a8369fb366ba46e7273b5c5b`.
- Continuity backup: `an external backup`.
- Handoff: [HANDOFF.md](../HANDOFF.md).
- Next: owner listening; set `review.humanAccepted` only after acceptance; run full QA before
  closing the gate. The historical full-QA counts above remain the last full QA record, from
  0.9.6. The earlier accepted 0.9.21 D6 build is at commit `9fd9945` with backup
  `an external backup`.

### Post-0.9.23 addendum — custom additive string synth (2026-10-24)

The owner replaced the 0.9.22 Osiris/MuseScore D3 hybrid with a custom additive string synth
generated in MATLAB and exported as one SoundFont. The production bank is one preset,
`String Synth A0-C8`, at bank 0/program 0: 88 one-shot mono 48 kHz PCM16 samples covering MIDI
21-108, with a bass timbre for MIDI 21-59 and a guitar timbre for MIDI 60-108. Samples are 2.5 s,
normalized to 0.060 RMS with peaks 0.22-0.42, and the app's longest note is 2.0 s. The C6/G6
separator and renderer transition fixes are unchanged.

Version 0.9.23 / versionCode 32 is the current main-line build at instrument commit `225aff6`;
the acceptance is `5e3a24c` and the review-harness fix is `abb3d5f`. The font is 21,126,646 bytes,
SHA-256 `fcef5e8869cd42f6b3bc4df495a02fc021c04b3b6d4fa71276fd838380f8efee`.

- Validation: `synthNotes/tools/validate_sf2.py` passed; the app's native render fixtures passed
  9/9 with the bundled font; `scripts/check-fast.sh` exited 0; `scripts/check-apk.sh` passed with
  no network permission and the expected packaged asset.
- The fast run was incremental, so the instrument-specific evidence is the native fixture run and
  the descriptor validation.
- Full `scripts/qa.sh` run, 2026-10-24: **PASS** on all stages. 1081/0 host tests, 60/60 review
  device tests, 5 data-platform and 9 audio-platform tests, both force-stop checks, no crash/ANR,
  strict screenshots and the packaged-APK audit. The earlier focus failure was fixed by keeping a
  real `MainActivity` resumed with `ActivityScenario` in the session tests; the two affected tests
  passed 2/2 before the full rerun. The 30-minute endurance stage was excluded by the default owner
  decision. Evidence: `.local/verification/qa-2026-10-24-1514/QA-REPORT.md`.
- Review: the owner accepted the custom synth on 2026-10-24; `review.humanAccepted=true`, and
  `scripts/check-assets.sh --require-reviewed` passes. QA-F02 and QA-F01 are closed. The 0.9.21 D6
  hybrid was the previous accepted instrument.
- Testing phone: 0.9.22 remains installed; 0.9.23 was not installed in this session.
- Daily phone APK: `dist/EarBy-0.9.23.apk`, 71,162,997 bytes, SHA-256
  `47cec73c56b5342066112c016ba6284762b0ca6990b7faffbb91a7682bbbb043`.
- No 0.9.23 backup was created; the last continuity backup is the 0.9.22 one.
- Next: install the accepted APK for ordinary use; optional end-to-end endurance run; keep G3/G4
  open. Handoff: [HANDOFF.md](../HANDOFF.md).

### Post-0.9.24 addendum — live Play note readout (2026-10-24)

Version 0.9.24 / versionCode 33 adds the Play `Current setup` debug `Notes:` line. While the
session is PLAYING it shows the current item's notes in letter+octave form: one name for a
single-note item and several for a chord. The source is `SessionSnapshot.currentNotes`, delivered
through the app-private state query only. This is an owner-requested exception to the hidden-answer
rule (D44) and intentionally exposes the generated pitches before the spoken answer; the answer
text, media metadata and lockscreen are unchanged.

The feature commit is `b5c3f0a` and the version bump is `4f7446c`; a docs-only wrap-up follows. The
APK is `dist/EarBy-0.9.24.apk`, 71,179,377 bytes, SHA-256
`bbf07bba34033af0035f6b39a051c7c925e50920348718c597b1d07c07dabdfe`, installed on the ASUS
testing phone (versionCode 33). The 0.9.23 custom synth and its acceptance are unchanged.

- Focused controller and Play UI tests passed; `scripts/check-fast.sh` passed.
- Full `scripts/qa.sh` run, 2026-10-24: **PASS** on all stages. 1083/0 host tests, 60/60 review
  device tests, 5 data-platform and 9 audio-platform tests, both force-stop checks, no crash/ANR,
  strict screenshots and the packaged-APK audit. The 30-minute endurance stage was excluded by the
  default owner decision. Evidence: `.local/verification/qa-2026-10-24-1611/QA-REPORT.md`.
- No 0.9.24 backup was created; the last continuity backup is the 0.9.22 one.
- Next: use the installed build and confirm the note line; optional endurance run; G3/G4 stay open.
  Handoff: [HANDOFF.md](../HANDOFF.md).

### Post-0.9.25 addendum — louder synth level (2026-10-24)

Version 0.9.25 / versionCode 34 doubles the synth output at the existing centre gain position. The
original MATLAB scripts were run with `rmsTarget = 0.120`; they regenerated 176 candidates and 88
final notes at twice the first-audition 0.060 RMS (peaks 0.44-0.83). The SF2 was rebuilt by the
project's Python writer, the gain settings and voice level are unchanged, and the separator clip
remains at its original level.

The bundled font is 21,126,646 bytes, SHA-256
`c3c3ff7da62bf0ad7d306657d168783f75bd8a894dfa7c2cdb27bb4b7a7cd27c`; the APK is
`dist/EarBy-0.9.25.apk`, 71,180,274 bytes, SHA-256
`26747b824b649582667e8b7803bd639a25f71cdc80d0a60b47dc146d62975712`, installed on the ASUS
testing phone.

- MATLAB `run_synthesis` and `build_final(59)` passed; the synth tool tests passed 6/6; the app's
  native render fixtures passed 9/9 with the louder font; `check-apk.sh` passed; installation
  succeeded.
- The descriptor remains `review.humanAccepted=false`; the 0.120 level is pending owner listening.
  The 0.9.23 timbre acceptance remains in the descriptor notes.
- Full `scripts/qa.sh` for 0.9.25 was **not run**, by the owner's instruction to defer it until the
  volume is accepted. The 0.9.24 build previously passed the full gate.
- No 0.9.25 backup was created; the last continuity backup is the 0.9.22 one.
- Next: listen, then mark acceptance and run QA, or adjust `rmsTarget` and rebuild. Handoff:
  [HANDOFF.md](../HANDOFF.md).

### Post-0.9.26 addendum — 25% music gain scale (2026-10-24)

Version 0.9.26 / versionCode 35 changes the music gain scale to 4x. The reviewed normal stays at
the slider centre and now reads **25%**, matching the voice gain's 25%. The stored gain, synth
samples and actual output are unchanged; this is a scale/label change, not a level change, and no
settings or preset migration was required. The upper half of the music slider now reaches 4x the
normal.

The APK is `dist/EarBy-0.9.26.apk`, 71,179,497 bytes, SHA-256
`8ca6eef4163a16782ea4204b9e59be5897251c5acc77aed87cc6fbe352803b3c`, installed on the ASUS
testing phone.

- Focused settings/ViewModel tests passed; `spotlessApply` and the debug build passed;
  `check-apk.sh` passed; installation succeeded.
- Full QA is deferred until the volume is accepted. The 0.9.24 build previously passed the full
  gate.
- The settings/Play screenshots now show 25% and are stale relative to the approved baselines; they
  need owner review before a strict UI comparison.
- The 0.9.25 0.120 level remains pending acceptance and the descriptor stays
  `review.humanAccepted=false`.
- Next: confirm the 25% labels and volume, approve the screenshots, then accept and run QA.
  Handoff: [HANDOFF.md](../HANDOFF.md).

### Post-0.9.27 addendum — 50% volume sliders (2026-10-24)

Version 0.9.27 / versionCode 36 makes both the music and voice sliders span **0-100%**, with the
reviewed normal at **50%**. The 50% point is the unchanged near-perfect 0.9.25 volume; 100% is
twice that level. Music 100% maps to gain 2.0 and voice 100% to 0.5. Stored gains and the synth
level are unchanged, so no settings or preset migration is required.

The APK is `dist/EarBy-0.9.27.apk`, 71,179,497 bytes, SHA-256
`be10664d5bbf3cf6e79b0dfc78b5065eed922ae12cd2bfd6722948525bc225b0`, installed on the ASUS
testing phone.

- Focused settings/ViewModel tests passed; `spotlessApply`, the debug build, `check-apk.sh` and
  installation passed.
- Full QA is deferred until the volume is accepted. The 0.9.24 build previously passed the full
  gate.
- The settings/Play screenshots now show 50% and are stale relative to the approved baselines; they
  need owner review before a strict UI comparison.
- The 0.9.25 0.120 level remains pending acceptance and the descriptor stays
  `review.humanAccepted=false`.
- Next: confirm the 50% labels and volume, approve the screenshots, then accept and run QA.
  Handoff: [HANDOFF.md](../HANDOFF.md).

### Post-0.9.28 addendum — uniform output trim (2026-10-24)

Version 0.9.28 / versionCode 37 applies a uniform **1.13x** trim to the completed PCM after music,
voice and separator mixing. This is about **+1.06 dB**, chosen because the loudest current note
peaks at 0.835 and 0.943 remains below the 0.95 clean target. It is not compression, limiting or
per-note normalization, so consistency and the instrument's waveform are preserved. The 0-100%
sliders keep their 50% reviewed normal.

The APK is `dist/EarBy-0.9.28.apk`, 71,180,285 bytes, SHA-256
`644db947741495afa353ef88bb6fd9b5b25498814194b11093023c7204b8810e`, installed on the ASUS
testing phone.

- The output-trim unit test, focused settings/ViewModel tests, `spotlessApply`, debug build,
  `check-apk.sh`, `check-assets.sh --require-reviewed` and installation passed.
- The owner accepted the 0.9.28 level on 2026-10-24; the descriptor is
  `review.humanAccepted=true`.
- The settings/Play 50%/50% screenshot baselines were approved for this accepted state and
  `check-ui.sh` passes.
- Full `scripts/qa.sh` passed on 2026-10-25: 1085/0 host tests, 60/60 review-device tests, 5
  data-platform and 9 audio-platform tests, both force-stop checks, 0 crash/ANR, strict 9-image
  screenshots and packaged-APK audit. Evidence:
  `.local/verification/qa-2026-10-25-1159/QA-REPORT.md`.
- Next: create the minimal 0.9.28 backup. Handoff:
  [HANDOFF.md](../HANDOFF.md).

### Post-0.9.29 addendum — SoundFont rename (2026-10-25)

Version 0.9.29 / versionCode 38 renames the bundled custom font from
`app/src/main/assets/soundfont/MuseScore_General.sf2` to
`app/src/main/assets/soundfont/StringSynth_A0-C8.sf2`. The bytes, 21,126,646-byte size, SHA-256
`c3c3ff7da62bf0ad7d306657d168783f75bd8a894dfa7c2cdb27bb4b7a7cd27c`, level and timbre are
unchanged, so the owner acceptance continues to apply.

All references were updated: descriptor, playback service, native host test, APK audit, device/QA
identity lists, restore script, `.gitignore`, notices and current docs. Historical 0.9.22 references
retain the old name because that was the actual upstream/hybrid file.

The APK is `dist/EarBy-0.9.29.apk`, 71,179,465 bytes, SHA-256
`51725337376f9a9c21f6b67ca0de00e0f903520278d20e4bd59f945f179947fa`, installed on the ASUS
testing phone.

- Native host fixtures 9/9 and `check-apk.sh` passed with the new path.
- Full `scripts/qa.sh` passed on 2026-10-25: 1085/0 host tests, 60/60 review-device tests, 5
  data-platform and 9 audio-platform tests, both force-stop checks, 0 crash/ANR, strict 9-image
  screenshots and packaged-APK audit. Evidence:
  `.local/verification/qa-2026-10-25-1243/QA-REPORT.md`.
- The first QA attempt failed only because the regular app's `profileInstalled` marker was rewritten
  after the fresh install; it stabilized and the rerun passed.
- The 0.9.28 minimal backup predates the filename-only rename; no 0.9.29 backup was requested.
- Next: no required work; G3/G4 stay open. Handoff: [HANDOFF.md](../HANDOFF.md).

### Post-0.9.30 addendum — presets no longer store seeds (2026-10-25)

Version 0.9.30 / versionCode 39 stops presets from carrying a run seed. Applying any preset writes
`seed = null` into the active mode setup, even when the stored preset has an old seed. Saving into a
slot writes `seed = null` at the preset level, even when the current setup has the last run's seed.
The next Start draws a fresh seed, so applying the same preset repeatedly produces different
sequences.

Legacy preset rows may still contain a seed, but it is ignored and overwritten with null on the
next save. No schema migration was required.

The APK is `dist/EarBy-0.9.30.apk`, 71,179,465 bytes, SHA-256
`4e98a30e888256e0396c9d0e3fecb0f601099a84d53ccfcf13fdba87f6c21842`, installed on the ASUS
testing phone.

- Focused `ViewModelTest` coverage for every mode's apply/save path passes; `spotlessApply`, debug
  build, `check-apk.sh` and installation passed.
- Full QA was explicitly deferred by the owner and has **not** run for 0.9.30.
- No new backup was requested; the 0.9.28 backup predates the rename and this seed change.
- Next: owner test of repeated preset application. Handoff: [HANDOFF.md](../HANDOFF.md).

### Post-0.9.30 addendum — root/tonic ranges across all modes (0.9.31, 2026-10-25)

Version 0.9.31 / versionCode 40 implements D46. The setup range is the final restriction on roots
or key tonics only; non-root notes may leave it but stay inside MIDI 21-108. Perfect pitch keeps its
"Lowest note / Highest note" labels and excludes unavailable selected classes with a warning.
Every other mode uses "Lowest root note / Highest root note". Infeasible roots/keys are skipped,
fixed roots that no longer fit are cleared, the Advanced switch is removed while its controls stay
open, and existing preset numbers are reinterpreted as root bounds with validation at import/use.

- `scripts/check-fast.sh` passes: all host suites, lint, docs/verification/architecture checks and
  the debug build. Core training is 583/0, app unit/Compose is 132/0 and core data is 218/0 after
  migrating the range assertions and reframing the old hard-bound/frozen expectations as
  playable-range invariants.
- The new focused coverage includes root-only ranges, MIDI 21/108 boundaries, perfect-pitch class
  exclusion, fixed-root clearing, preset preflight and the top-edge infeasible cases.
- `dist/EarBy-0.9.31.apk`, 71,179,465 bytes, SHA-256
  `746b270a1904e74b7db367d3c9bf34024a8eb8006bddff97f8a9e766a3705033`, passed `check-apk.sh` and is
  installed on the ASUS testing phone as versionCode 40.
- Full QA was not run for 0.9.31; the owner deferred it.
- Next: owner device check; G3/G4 remain open. Handoff: [HANDOFF.md](../HANDOFF.md).

### Post-0.9.31 addendum — responsive Settings sliders (0.9.32, 2026-10-25)

Version 0.9.32 / versionCode 41 keeps the Settings music and voice slider positions in Compose
state, so the thumb and percentage follow the finger without waiting for a settings repository
round trip. Gaining writes are coalesced with an 80 ms debounce during a drag and flushed
immediately on release; external settings changes still update the thumb when that slider is not
being dragged.

- Added `gainWritesAreCoalescedAndTheFinalReleaseIsFlushed`, covering the debounce and final flush.
- `scripts/check-fast.sh` passes, including all host suites, lint and docs checks.
- `dist/EarBy-0.9.32.apk`, 71,179,469 bytes, SHA-256
  `0f4016a2308aa12fc6a3b0924106cfcfd27c86cb3db6ac5f218031c45eadafd3`, passed `check-apk.sh` and
  is installed on the ASUS testing phone as versionCode 41.
- Full QA was not run for 0.9.32; the owner deferred it.

### Post-0.9.32 addendum — Help page refresh (0.9.33, 2026-10-25)

Version 0.9.33 / versionCode 42 reorganizes the in-app Help page into readable sections: how to use
the app, the three learning modes, one card per mode, root/tonic range rules, and presets/sound. The
copy now reflects the current behavior, including root-only ranges in multi-note modes,
perfect-pitch exceptions, always-open Advanced controls, fresh preset sequences, and the 0-100%
sliders with a 50% normal. The mode cards reuse the drawer summaries and audio descriptions.

- `HelpScreenHostTest` asserts the new sections, range guidance, volume guidance and all eight mode
  cards.
- `scripts/check-fast.sh` passes, including all host suites, lint and docs checks.
- The page was reviewed from a 1080x2400 dark-theme phone capture; no screenshot baseline was
  changed. Full QA passed on 2026-10-25: 1087/0 host, 60/60 review-device, 5 data-platform and
  9 audio-platform tests, strict screenshots, APK audit and 0 crash/ANR. Evidence:
  `.local/verification/qa-2026-10-25-1549/QA-REPORT.md`.
- Minimal backup: `an external backup` (107 MiB; source archive, APK,
  QA report, commit, README and checksums; `sha256sum -c` passes).
- `dist/EarBy-0.9.33.apk`, 71,212,233 bytes, SHA-256
  `9e14e638d3be2d7413d36f767d2ad96d7976f75b739b647d9ab0bced694b43f9`, passed `check-apk.sh` and
  is installed on the ASUS testing phone as versionCode 42.
- Full QA passed; the minimal 0.9.33 backup was created afterwards.


### Owner gate update — G3/G4 PASS (2026-10-25)

After testing the current **0.9.33 / versionCode 42** build, the owner reports **G3 PASS** and
**G4 PASS**. This records owner acceptance of the physical and daily-use rows; it is not a new
automated `scripts/qa.sh` run. Detailed per-row observations and refreshed screenshot baselines
were not supplied in this session. G5-G8 are now eligible, but no new implementation scope has
been authorized.

- G5: core-training gate; largely superseded by the v0.2.x eight-mode expansion.
- G6: reduced to contrast drills and interval walks. Weighted practice, intonation, mixed sessions and clusters are dropped. Progressions and melodies are already implemented.
- G7: **dropped** by owner decision on 2026-10-25; no history, ratings or adaptation work.
- G8: reliability, optimization, signed APK, backup/restore; outstanding.


### Post-0.9.33 addendum — reduced G6 interval walk (0.10.0, 2026-10-25)

Version 0.10.0 / versionCode 43 adds **Interval walk** as the ninth mode. The walk is a finite chain
of 2-8 intervals (3-9 notes); each step starts at the previous end note. Only the starting root is
restricted by the setup range; all sounding notes stay inside MIDI 21-108. The spoken answer is the
directed interval sequence. Recognition, Exposure and Association remain the three learning modes.

- New domain/provider: `IntervalWalkDefinition`, `SeededIntervalWalkGenerator`,
  `IntervalWalkExerciseProvider`, graph reachability and seeded uniform edge sampling.
- New setup/model: `IntervalWalkSetup`, draft, validator and ViewModel; `ModePayload.IntervalWalk`,
  DataStore keys and the Play summary.
- New mode surface: drawer entry, setup route, Help page (nine modes), five preset slots and the
  **Ascending thirds and fifths** starter.
- Preset document schema 2 gains the interval-walk payload; database schema 12 adds five empty
  slots without touching any other kind.
- Contrast drills, weighted practice, intonation, mixed sessions and clusters are not implemented.
- `scripts/check-fast.sh` passes; the new domain/provider/setup/preset tests pass; the APK audit
  passes. Manual phone check: the mode appears, the setup renders and starting a walk produces a
  five-note chain.
- `dist/EarBy-0.10.0.apk`, 71,688,710 bytes, SHA-256
  `fad87aaafa8c7cc0dce89fbb2ce22b9346c8f77844ab9609ef77094b048a4095`, installed as versionCode 43.
- Full QA has not run for 0.10.0.


### Post-0.10.1 addendum — full QA coverage for the ninth mode (2026-10-25)

Version 0.10.1 / versionCode 44 updates the full QA automation for the features since the last
full-QA gate. The host all-modes matrix renders nine Play tabs, nine setup editors and nine preset
surfaces at 150% and 200% text. The review device suite adds an interval-walk select/start/stop
test; the Help device assertion expects nine modes; the endurance warmup and three-minute schedule
include interval walk; and the QA report records the owner-reported G3/G4 pass while noting that
the 0.10.x walk surfaces still need a separate human gate pass. The approved drawer image was
updated for the ninth row.

- Full `scripts/qa.sh` passed on 2026-10-25: 1111/0 host tests, 61/61 review-device tests, 5
  data-platform, 9 audio-platform, strict nine-image comparison, APK audit, 0 crash/ANR.
  Evidence: `.local/verification/qa-2026-10-25-1841/QA-REPORT.md`.
- The live 30-minute endurance was skipped by default; the report cites the recorded M11 evidence.
- `dist/EarBy-0.10.1.apk`, 71,408,837 bytes, SHA-256
  `057f7eae9e04764edd5a45880472622450bc637de3904b4b80cfb57d076209c8`, installed as versionCode 44.
- Minimal backup: `an external backup` (107 MiB; source archive, APK,
  QA report, commit, README and checksums; `sha256sum -c` passes).


### Post-0.10.2 addendum — interval-walk stopping chooser (2026-10-25)

Version 0.10.2 / versionCode 45 fixes the interval-walk setup **Stopping** section. The two
filter chips now sit under a heading and selecting "After N items" exposes the shared
completed-items chooser, so the count can be changed from the fixed 10. A host Compose regression
opens the chooser and asserts "Completed items" is present. Manual phone check confirmed the
chooser dialog opens with the scrollable count wheel.

- Full `scripts/qa.sh` passed: 1112/0 host tests, 61/61 review-device tests, 5 data-platform,
  9 audio-platform, strict nine-image comparison, APK audit, 0 crash/ANR.
  Evidence: `.local/verification/qa-2026-10-25-1949/QA-REPORT.md`.
- The review tests now wait for the reset default selection before starting a session, removing a
  settings-propagation race observed in two earlier QA runs.
- `dist/EarBy-0.10.2.apk`, 71,408,841 bytes, SHA-256
  `7f86add2f3d9a6abee887ca4701b37c285d18473f240a40b416f1b3c67527c86`, installed as versionCode 45.
- Minimal backup: `an external backup`.


### Post-0.10.3 addendum — three-step default walk (2026-10-25)

Version 0.10.3 / versionCode 46 changes `IntervalWalkDefinition.DEFAULT_LENGTH` from 4 to 3. A
fresh or reset interval walk therefore has **three interval steps and four sounding notes**. The
setup chooser still permits 2–8 steps, and an already saved setup keeps its chosen length. The
starter preset, Play summary, host all-modes matrix and review-device walk test were updated to
three steps. A focused domain test asserts three steps and four notes.

- Full `scripts/qa.sh` passed: 1113/0 host tests, 61/61 review-device tests, 5 data-platform,
  9 audio-platform, strict nine-image comparison, APK audit, 0 crash/ANR.
  Evidence: `.local/verification/qa-2026-10-25-2029/QA-REPORT.md`.
- `dist/EarBy-0.10.3.apk`, 71,408,841 bytes, SHA-256
  `651db0973cacb960b0c9dc9365cccbee51f579d2bcb3eb8d66abf97500464d5e`, installed as versionCode 46. The phone Play summary shows "Direction: ascending · 3 steps".
- Minimal backup: `an external backup`.


### Post-0.10.4 addendum — three-step seeded starter (2026-10-25)

Version 0.10.4 / versionCode 47 updates the interval-walk starter that older installs had already
seeded. Preset schema 13 deletes the old `builtin:interval_walk:starter:v1` row; the next store open
seeds the current three-step starter under the same fixed identity. User slots are untouched. A
migration regression test inserts the old row at schema 12, upgrades, and asserts it is gone.

- Phone Presets detail now reads "Direction: ascending · 3 steps".
- Full `scripts/qa.sh` passed: 1113/0 host tests before the migration regression test was added,
  61/61 review-device tests, 5 data-platform, 9 audio-platform, strict nine-image comparison, APK
  audit, 0 crash/ANR. Evidence: `.local/verification/qa-2026-10-25-2050/QA-REPORT.md`.
- `dist/EarBy-0.10.4.apk`, 71,408,841 bytes, SHA-256
  `4faaf12afa924d75effa6e86b8a5df700e9a4070170ecb40614bff9755508432`, installed as versionCode 47.
- Minimal backup: `an external backup`.


### Post-1.0.0 release-preparation addendum (2026-10-26)

The working tree now targets public package `com.earby.app`, version 1.0.0 / versionCode 100.
Apache-2.0/CC BY licensing, original icon, release signing/minification, privacy, Play and GitHub
preparation are in place. Preset export/import covers every filled user slot with versioned JSON and
host tests.

- Full `scripts/qa.sh --with-endurance` passed: 1117/0 host tests, 61/61 review-device, 5
  data-platform, 9 audio-platform, endurance 1812 s and PSS variation 32,401 KiB, 0 crash/ANR.
  Evidence: `.local/verification/qa-2026-10-26-2115/QA-REPORT.md`.
- G8 remains open for the 2-hour lane, 16 KiB runtime, owner upload-key backup and final signed
  AAB/APK. No push or Play upload has occurred.
