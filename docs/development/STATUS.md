# Development status

## Current addendum — 2026-11-08 (V4 sample engine; FluidSynth and LGPL code removed)

The instrument engine is now the in-project EARP1 **sample player**
(`core/audio/src/main/cpp/sample_player.cpp`). FluidSynth 2.6.1, its GCEM and Signalsmith Audio
basics dependencies and the `ear.renderer` dual-engine switch are removed, so the app compiles and
ships **no LGPL code**; `LICENSES/LGPL-2.1-or-later.txt` and `docs/legal/LGPL_COMPLIANCE.md` are
deleted, and the notices name miniaudio as the only native third-party component
(`tools/native/sources.json` pins it alone).

- V4 dark calibration (selected 2026-11-08): slopeP 1.55, lowpass 1500 Hz for guitar / 900 Hz for
  bass at Q 0.5, body peaks scaled by 0.8; 88 one-shot 48 kHz mono samples at 0.120 RMS.
- Shipped asset: `app/src/main/assets/soundfont/earby-string-pack.bin`, 21,126,760 bytes, SHA-256
  `f2e14a32c252c5d94ec5f1b7f8cbf63da6ff7a2c08afb49470af591cc6c05462` (payload
  `295476119e0db682e793d1f36ca277d264c6f6fc9623682d9ed07fd96759d908`). The 21 MB SoundFont is no
  longer packaged.
- Generator input (not packaged): `synthNotes/StringSynth_A0-C8.sf2`, 21,126,646 bytes, SHA-256
  `f3326f4c53c52a521bce7e9e2932edb9e34807f85a96b391eb224a5a0b499705`.
- `piano.json` is pack-centric: `samplePack` records the pack plus the SF2 source provenance;
  `synthNotes/tools/build_sample_pack.py --check` passes and `scripts/check-assets.sh` validates the
  pack, the SF2 and the separator clip.
- Owner acceptance (2026-11-08): the owner accepted the V4 dark calibration and the sample-player
  playback by ear; `piano.json` sets `review.reviewedAt=2026-11-08` and keeps the earlier 0.9.28
  level-acceptance history in its notes. No audio bytes changed.
- Version stays **1.0.0 / versionCode 100**.

Verification: `:app:assembleDebug` and `:app:assembleRelease` PASS. `scripts/check-apk.sh` passes on
the debug APK (69,643,845 bytes, SHA-256
`eab617175b13100ff48a4ff5d9c3aba60519d5a7aacd002fdfc52e53a37a4d91`, reproducible across clean
builds) and the release APK (35,453,480 bytes): no network permission, the EARP1 pack and voice pack
are packaged, no stray SoundFont, 16 KiB alignment and no review-only code. The minified release APK
has no stable SHA-256: clean builds produced `7c7e0eb9e11e4ed8d903bff55a1af54c5b4ca5e4fae770f846f391084dfb3545`
here and `778c3cd2...` in verification (the earlier `e0ae7f70...` was a third run), all at the same
size with the same assets and a passing audit. The release hash can vary between runs
(R8/packaging nondeterminism); the debug hash is reproducible. `scripts/check-assets.sh` and
`--require-reviewed` pass, `scripts/check-docs.py` passes (49 documents) and
`scripts/check-architecture.py` passes.

Play assets and signed artifacts (2026-11-08): the listing copy is finalised in `store/listing.md`
and `docs/release/STORE_LISTING.md` (title EarBy, 72-character short description, 125-word full
description covering the nine modes, offline/no account/ads/network, spoken answers, presets and
sleep timer, Education category, contact and privacy URL). The upload-key-signed artifacts are
`dist/play/EarBy-1.0.0.aab` (30,240,377 bytes, SHA-256
`63e458398eb87e32785b0d2e932c2050c60f0421d9eefae622cae0a90e5b65bf`) and
`dist/release/EarBy-1.0.0.apk` (35,465,948 bytes, SHA-256
`eb983c34c4dc804a793799d587eb76753c1a818aa08c0c4c043cc2d868bdec36`; the minified hash can vary
per run). Both are built from commit `f634502` with the JNI keep rules and pass
`scripts/check-release.sh` against the upload certificate
`1d48d8d0bc148a1314f377dfcadfd9a2ec01feb530db2bb57dc98b6e6f5bb9d5`. The eight Play phone
screenshots were captured from the signed 1.0.0 release on 2026-11-08 (1080x2160 24-bit RGB PNG,
exact 2:1, status bar removed and letterboxed) and are in `store/screenshots/`; the rules are
recorded in `docs/release/STORE_LISTING.md`. Nothing was pushed or uploaded to Play.

## Addendum — 2026-11-08 (hand-drawn launcher icon)

The launcher and store icon is now **Dr Ham Hamster's hand-drawn ear-and-note mark**. She granted
the project permission to use the artwork freely, including shipping and distributing it with EarBy
(permission recorded 2026-11-08). The delivered artwork is `icons/EarByIconV6.png`.

- `tools/icon/generate_launcher_icons.py` generates every packaged size from that source: the five
  legacy `ic_launcher`/`ic_launcher_round` PNGs, the adaptive
  `ic_launcher_foreground`/`ic_launcher_background` layers and `store/icon-512.png` (full-bleed,
  RGB). The generator replaces the paper-white page and the drawn tile with the flat mint background
  the icon set uses; the drawing itself is unmodified. The mark is cropped to the drawing's own ink
  before placement, so the faint anti-aliased ring at the drawn tile's edge cannot pull it off
  centre, and it is centred by its drawing bounds, matching the artist's placement in the original
  tile. `--placement centroid` renders the ink-mass variant instead, which sits about 17 px lower at
  512 px; both use the same crop.
- The earlier drafts (`icons/EarByIconV1.png` to `EarByIconV4.png`), including the Qwen-Image-2.1 V4,
  are retained only in the private repository and are excluded from the public snapshot. The Play
  icon licence blocker is therefore closed.
- The separator clip's legal blocker is closed as well (2026-11-08): it is a C6/G6 render made by
  the owner on 2026-10-18 from the MIT-licensed MuseScore General v0.2 font, and rendering it again
  from the hash-verified upstream font reproduces the shipped bytes exactly. The record, the
  upstream acknowledgements and the licence text are in `docs/legal/ASSET_SOURCES.md`,
  `THIRD_PARTY_NOTICES.md` and `LICENSES/MuseScore_General-MIT.txt`. The asset is unchanged and the
  owner accepted it by ear on 2026-11-08.
- Attribution is recorded in `README.md`, `NOTICE`, `THIRD_PARTY_NOTICES.md`,
  `docs/legal/ASSET_SOURCES.md`, `design/README.md`, `store/README.md`, the new `icons/README.md`
  and the in-app Help page, which gained a **Credits** section.
- Version stays **1.0.0 / versionCode 100**, because 1.0.0 has not been published; the next build
  carries the new icon under the same identity.

Verification: `spotlessCheck`, `:app:testDebugUnitTest` (**138/0**, including the extended
`HelpScreenHostTest`), `:app:lintDebug`, `:app:assembleDebug` and `scripts/check-apk.sh` on the
rebuilt debug APK (`72,841,154` bytes, SHA-256
`fafcefddc52d796b7c95ca5b761d591b3573a01f91d70b3886c7ef5ada43f58f`) pass; the packaged
`res/mipmap-*/ic_launcher*` entries are byte-identical to the generated sources and the APK requests
no network permission. `scripts/check-docs.py` passes. No screenshot baseline changed: the launcher
icon is not part of the host-rendered screens, and the nine approved images still verify against
their manifest hashes. No full `scripts/qa.sh` run or device install was made for this resource-only
change.

The public repository is <https://github.com/ThomasTZh/EarBy>, a **single-commit** source snapshot
with no internal history; the reviewed tree is tagged `v1.0.0` and is produced only by
`scripts/export-public-snapshot.sh`. The snapshot excludes `AGENTS.md`, `planDraft.md`,
`local.properties`, candidate/raw audio, the V1-V4 icon drafts (including the non-commercial
Qwen-Image draft) and `docs/testing/notes4human.txt`; home-directory paths are replaced with generic
phrases, and the contact address is **thomas.t.zhang@outlook.com**. GitHub Pages serves the privacy
policy at <https://thomastzh.github.io/EarBy/privacy/>. The hardened CI workflow fetches the pinned
native sources and runs formatting, host tests, debug/release builds, lint, the packaged-APK audit,
screenshot verification and the documentation/architecture checks.

Snapshot close-out (2026-11-08): `store/feature-graphic.png` was restored to the pre-Qwen project
art from commit `cae1165` (SHA-256 `b2fa9b6fb2b8349132920f3e9825896873fc8ae838a0fb571dac932657ced888`).
The public tree ships only `icons/EarByIconV5.png` (CC BY 4.0 fallback) and `icons/EarByIconV6.png`
(hand-drawn, permission on record); `scripts/export-public-snapshot.sh` removes V1-V4 and fails if
one remains. The single image generator is `tools/icon/generate_launcher_icons.py`, which
regenerates every launcher size and `store/icon-512.png` byte-identically from V6;
`design/render-icons.py` renders only the V5 fallback. CI actions are upgraded to the node24 majors
(`actions/checkout@v7`, `actions/setup-java@v6`, `android-actions/setup-android@v4`,
`actions/setup-python@v7`, `gradle/actions/setup-gradle@v6` with the MIT `basic` cache provider,
`actions/upload-pages-artifact@v5`, `actions/deploy-pages@v5`); their run is pending the snapshot
push. The exported tree (598 files, 39 MB) passes `scripts/check-public-tree.sh`,
`scripts/check-docs.py` (49 documents), `scripts/check-architecture.py`, the 45 script tests,
`scripts/check-verification.py`, `scripts/check-voice-durations.py` and
`scripts/screenshot-contract.py`; re-running the export from a fresh clone reproduces it
byte-identically.

## Addendum — 2026-10-26 (1.0.0 release preparation)

The source now targets the public identity **`com.earby.app`**, version **1.0.0 / versionCode
100**. Debug builds use `.debug`, review builds `.review`; the old `com.thomas.passiveeartraining`
app remains separate and is not migrated.

Release preparation is implemented:

- The Qwen-Image-2.1 icon was restored at the owner’s request for the personal build. Its
  non-commercial license blocks Play/public distribution until a commercial license or replacement
  icon is provided. The original `design/` icon remains available.
- Preset export/import for all filled user slots, with JSON validation, preview/commit and tests.
- Apache-2.0 source license, CC BY 4.0 original-asset license, third-party notices, LGPL source
  compliance and asset provenance.
- Release R8/resource shrinking, external upload-key signing, APK/AAB build scripts and signature
  checks. A debug-key test build verified the configuration; the owner upload key is still required.
- Privacy policy, Data safety mapping, Play checklist, store listing assets and a public repo
  snapshot guide.
- GitHub CI, privacy site and community files.
- Full QA passed: **1117/0 host tests**, 61/61 review-device tests, 5 data-platform, 9 audio-platform,
  real 30-minute exercise (**1812 s**, PSS variation 32,401 KiB) and 0 crash/ANR. Evidence:
  `.local/verification/qa-2026-10-26-2115/QA-REPORT.md`.
- Human rows: none open; owner acceptance is recorded separately.

G8 is **not closed** yet: the 2-hour endurance lane, a 16 KiB runtime, the owner upload-key backup
and a final upload-key-signed AAB/APK remain. No GitHub push or Play upload has occurred.

## 0.10.4 addendum — 2026-10-25

Version **0.10.4 (versionCode 47)** updates the already-seeded interval-walk starter. Changing the
starter constant to three steps did not rewrite the built-in row created by earlier installs, so the
Presets screen still showed four steps. Preset schema 13 now removes the old built-in starter row;
the next store open seeds the current three-step starter under the same fixed identity. User slots
are untouched, and a database that never had the starter simply seeds three steps.

The migration has a focused regression test. The phone's Presets detail now reads
"Direction: ascending · 3 steps".

Full QA passed: **1113/0 host tests** before the migration regression test was added; 61/61
review-device tests, 5 data-platform and 9 audio-platform tests, strict nine-image comparison, APK
audit and 0 crash/ANR. Evidence: `.local/verification/qa-2026-10-25-2050/QA-REPORT.md`. The default QA run skips the live 30-minute endurance and
cites the recorded M11 evidence.

The APK is `dist/EarBy-0.10.4.apk` (71,408,841 bytes, SHA-256
`4faaf12afa924d75effa6e86b8a5df700e9a4070170ecb40614bff9755508432`) and is installed on the testing phone (versionCode 47). A minimal backup is at
`an external backup`.

Owner acceptance on 2026-10-25: acoustic quality, actual earbuds (including removal stopping
playback), TalkBack, and the interval-walk/starter/Stopping chooser are all good. No open human
row remains.

## 0.10.3 addendum — 2026-10-25

Version **0.10.3 (versionCode 46)** changes the interval-walk default from four steps to **three
steps**, so the default walk plays **four notes**. The setup chooser still allows 2–8 steps, and a
saved setup keeps its own chosen length. The in-memory starter definition is now three steps (existing seeded rows are handled by 0.10.4), and the Play
summary, host all-modes matrix and review-device walk test expect three steps.

Full QA passed: **1113/0 host tests**, 61/61 review-device tests, 5 data-platform and 9
audio-platform tests, strict nine-image comparison, APK audit and 0 crash/ANR. Evidence:
`.local/verification/qa-2026-10-25-2029/QA-REPORT.md`. The default QA run skips the live 30-minute endurance and cites the recorded M11 evidence.

The APK is `dist/EarBy-0.10.3.apk` (71,408,841 bytes, SHA-256
`651db0973cacb960b0c9dc9365cccbee51f579d2bcb3eb8d66abf97500464d5e`) and is installed on the testing phone (versionCode 46); its Play summary shows
"Direction: ascending · 3 steps". A minimal backup is at `an external backup`.

## 0.10.2 addendum — 2026-10-25

Version **0.10.2 (versionCode 45)** fixes the interval-walk **Stopping** section. It now has a
heading and a proper choice between "Until stopped" and "After N items"; selecting the latter shows
the existing completed-items chooser, so N is editable instead of being fixed at 10. A host Compose
regression opens the chooser and asserts it is present.

Full QA passed again: **1112/0 host tests**, 61/61 review-device tests, 5 data-platform and 9
audio-platform tests, strict nine-image comparison, APK audit and 0 crash/ANR. Evidence:
`.local/verification/qa-2026-10-25-1949/QA-REPORT.md`. The QA suite's own review tests now wait for the reset default selection before starting a
session, removing a settings-propagation race that surfaced during the first two runs.

The APK is `dist/EarBy-0.10.2.apk` (71,408,841 bytes, SHA-256
`7f86add2f3d9a6abee887ca4701b37c285d18473f240a40b416f1b3c67527c86`) and is installed on the testing phone (versionCode 45). A minimal backup is at
`an external backup`.

Owner feedback on 2026-10-25: interval walk is functionally good; actual earbuds are good, including stopping when they are put down; TalkBack is good; acoustic quality is good. The only reported issue was the Stopping section, fixed in 0.10.2. The interval-walk, starter and Stopping chooser are owner-accepted on 0.10.4.

## 0.10.1 addendum — 2026-10-25

Version **0.10.1 (versionCode 44)** updates the full QA automation for the ninth mode and the
earlier UI changes. The all-modes host matrix now renders nine Play tabs, nine setup screens and
nine preset surfaces at 150% and 200% text. The isolated device suite has an interval-walk
select/start/stop row; the device Help check expects nine modes; the endurance warmup and schedule
include interval walk; the QA report no longer claims G3/G4 are untouched, while noting that the
0.10.x walk surfaces have not had a separate human gate pass. The drawer baseline now contains the
ninth mode.

Full QA passed on 2026-10-25: **1111/0 host tests**, 61/61 review-device tests, 5 data-platform and
9 audio-platform tests, strict nine-image comparison, APK audit and 0 crash/ANR. Evidence:
`.local/verification/qa-2026-10-25-1841/QA-REPORT.md`. The default QA run skips the live
30-minute endurance and cites the recorded M11 evidence.

The APK is `dist/EarBy-0.10.1.apk` (71,408,837 bytes, SHA-256
`057f7eae9e04764edd5a45880472622450bc637de3904b4b80cfb57d076209c8`) and is installed on the
testing phone (versionCode 44). A minimal 107 MiB backup is at
`an external backup`; `sha256sum -c SHA256SUMS` passes.

## 0.10.0 addendum — 2026-10-25

Version **0.10.0 (versionCode 43)** adds the reduced G6: **Interval walk** is the ninth mode. A
walk is a finite chain of 2-8 intervals (3-9 sounding notes); each step starts on the previous
step's end note. The setup selects intervals, direction (ascending, descending or mixed), length,
starting-root range or a fixed start, and one of the three learning modes. Only the starting root is
restricted by the range; every later note stays inside MIDI 21-108. Answers are spoken as a directed
interval sequence.

The mode has its own provider, generator, setup screen, DataStore keys, five preset slots and one
starter, **Ascending thirds and fifths**. The database migration adds the five slots without
touching another kind. Contrast drills and the previously dropped G6 features are not implemented.
G6 is complete under this reduced scope.

Verification: focused domain/provider/setup/preset tests pass; `scripts/check-fast.sh` passes; the
APK audit passes; 0.10.0 was installed and a walk was started on the phone with a five-note chain.
The Help page lists nine modes. Full QA has **not** run for 0.10.0.

The APK is `dist/EarBy-0.10.0.apk` (71,688,710 bytes, SHA-256
`fad87aaafa8c7cc0dce89fbb2ce22b9346c8f77844ab9609ef77094b048a4095`) and is installed on the
testing phone (versionCode 43).

## 0.9.33 addendum — 2026-10-25

Version **0.9.33 (versionCode 42)** updates the in-app Help page. The single long introduction is
now split into short sections: how to use the app, the three learning modes, one card per mode, the
root/tonic range rules, and presets/sound. The mode cards keep the shared drawer summaries and
audio descriptions, and the page still shows the packed build version. The section copy covers the
current behavior: roots/tonics only in multi-note modes, perfect-pitch exceptions, always-open
Advanced controls, fresh sequences on preset application, and the 0-100% sliders with a 50% normal.

The APK is `dist/EarBy-0.9.33.apk` (71,212,233 bytes, SHA-256
`9e14e638d3be2d7413d36f767d2ad96d7976f75b739b647d9ab0bced694b43f9`) and is installed on the
testing phone (versionCode 42). The updated page was reviewed in the phone's dark theme at
1080x2400. `scripts/check-fast.sh` passes, including the expanded Help host test. Full QA passed
on 2026-10-25: 1087/0 host tests, 60/60 review-device tests, 5 data-platform and 9 audio-platform
tests, strict 9-image comparison, APK audit and 0 crash/ANR. Evidence:
`.local/verification/qa-2026-10-25-1549/QA-REPORT.md`. The QA harness still lists G3/G4 as
non-automated human rows; the owner-reported PASS is recorded separately above. A minimal 107 MiB
backup is at `an external backup`; checksums verify.

**Gate update (owner-reported, 2026-10-25):** after testing the current **0.9.33 / versionCode 42**
build, the owner reports **G3 PASS** and **G4 PASS**. This records owner acceptance of the G3/G4
physical and daily-use rows; it is not a new automated `qa.sh` result. Detailed per-row notes and
refreshed screenshot baselines were not supplied in this session. **G7 is dropped** by owner
decision on 2026-10-25: no listening history, Home/Progress history screens, ratings or adaptation.
**G6 is reduced** to contrast drills and interval walks; weighted practice, intonation, mixed
sessions and clusters are dropped. G5, G6 and G8 are eligible, but no new implementation scope has
been authorized.

## 0.9.32 addendum — 2026-10-25

Version **0.9.32 (versionCode 41)** makes the Sound sliders on the main Settings page track the
finger immediately. Slider positions now live in Compose state, so rendering no longer waits for a
repository round trip. Writes are coalesced with an 80 ms debounce during a drag and flushed
immediately when the slider is released; changes made elsewhere still update the sliders when they
are not being dragged. The displayed percentage follows the local position during the drag.

Coverage added: `gainWritesAreCoalescedAndTheFinalReleaseIsFlushed` exercises the debounce and the
release flush. `scripts/check-fast.sh` passes, including all host suites, lint and docs checks.

The APK is `dist/EarBy-0.9.32.apk` (71,179,469 bytes, SHA-256
`0f4016a2308aa12fc6a3b0924106cfcfd27c86cb3db6ac5f218031c45eadafd3`) and is installed on the
testing phone (versionCode 41). Full QA has **not** run; the owner explicitly deferred it.

## 0.9.31 addendum — 2026-10-25

Version **0.9.31 (versionCode 40)** implements the owner-approved root/tonic range semantics across
all eight modes. The setup range is now the final restriction on **root notes or key tonics only**;
non-root interval, chord, scale, context, progression and melody notes may sound outside it but
remain inside the packaged MIDI 21-108 samples. Perfect pitch keeps the labels
**Lowest note / Highest note**; every other setup screen uses **Lowest root note / Highest root
note**. Perfect-pitch classes with no playable note remain selected but are excluded with a visible
warning; Start is blocked only when no selected class is playable. Roots and keys that cannot fit
are skipped, and a fixed root that no longer fits is cleared. The Advanced switch is removed; the
Advanced header remains and its controls are always open. Existing preset numbers are
reinterpreted as root bounds with no silent migration, and validation happens on import/use.

The APK is `dist/EarBy-0.9.31.apk` (71,179,465 bytes, SHA-256
`746b270a1904e74b7db367d3c9bf34024a8eb8006bddff97f8a9e766a3705033`) and is installed on the
testing phone (versionCode 40). `scripts/check-fast.sh` passes, including all host suites and lint;
`spotlessCheck`, the debug build and `check-apk.sh` also pass. Full QA has **not** run; the owner
explicitly deferred it.

## 0.9.30 addendum — 2026-10-25

Version **0.9.30 (versionCode 39)** stops presets from carrying a run seed. Applying any preset
writes `seed = null` into the active mode setup, even when the stored preset has an old seed.
Saving into a slot writes `seed = null` at the preset level, even when the current setup has the
last run's seed. The next Start draws a fresh seed, so applying the same preset repeatedly gives
different sequences.

Legacy preset rows may still contain a seed, but it is ignored and overwritten with null on the
next save. No schema migration was required.

The APK is `dist/EarBy-0.9.30.apk` (71,179,465 bytes, SHA-256
`4e98a30e888256e0396c9d0e3fecb0f601099a84d53ccfcf13fdba87f6c21842`) and is installed on the
testing phone (versionCode 39). Focused `ViewModelTest` coverage for the apply and save paths
passes, along with `spotlessApply`, the debug build and `check-apk.sh`. Full QA has **not** run;
the owner explicitly deferred it.

## 0.9.29 addendum — 2026-10-25

Version **0.9.29 (versionCode 38)** renames the bundled custom font from
`assets/soundfont/MuseScore_General.sf2` to `assets/soundfont/StringSynth_A0-C8.sf2`. The content is
unchanged: 21,126,646 bytes, SHA-256
`c3c3ff7da62bf0ad7d306657d168783f75bd8a894dfa7c2cdb27bb4b7a7cd27c`. The owner acceptance still
applies because the bytes, level and timbre are identical.

All references were updated: descriptor, playback service, native host test, APK audit, device/QA
identity lists, restore script, `.gitignore`, notices and current docs. Historical 0.9.22 references
retain the old name for the actual upstream/hybrid file.

The APK is `dist/EarBy-0.9.29.apk` (71,179,465 bytes, SHA-256
`51725337376f9a9c21f6b67ca0de00e0f903520278d20e4bd59f945f179947fa`) and is installed on the
testing phone (versionCode 38). Full `scripts/qa.sh` passed on 2026-10-25: 1085/0 host tests, 60/60
review-device tests, 5 data-platform and 9 audio-platform tests, both force-stop checks, 0 crash/ANR,
strict 9-image screenshots and packaged-APK audit. Evidence:
`.local/verification/qa-2026-10-25-1243/QA-REPORT.md`.

The 0.9.28 minimal backup predates the rename; no 0.9.29 backup was requested.

## 0.9.28 addendum — 2026-10-24

Version **0.9.28 (versionCode 37)** adds a uniform **1.13x** final-mix trim, about **+1.06 dB**, to
the music, voice and separator. It is applied once after mixing, so balance is unchanged and there
is no compression, limiting or per-note gain. The loudest note peak becomes about 0.943, below the
0.95 clean target. The 0-100% sliders keep their 50% reviewed normal.

The APK is `dist/EarBy-0.9.28.apk` (71,180,285 bytes, SHA-256
`644db947741495afa353ef88bb6fd9b5b25498814194b11093023c7204b8810e`) and is installed on the
testing phone (versionCode 37). The 0.120 synth level is accepted:
`review.humanAccepted=true`, owner, 2026-10-24.

Automated checks: the output-trim unit test, focused settings/ViewModel tests, `spotlessApply`,
debug build, `check-apk.sh`, `check-assets.sh --require-reviewed` and installation pass. The
settings/Play 50%/50% baselines are approved and `check-ui.sh` passes. Full `scripts/qa.sh` passed
on 2026-10-25: 1085/0 host tests, 60/60 review-device tests, 5 data-platform and 9 audio-platform
tests, both force-stop checks, 0 crash/ANR, strict 9-image screenshots and packaged-APK audit.
Evidence: `.local/verification/qa-2026-10-25-1159/QA-REPORT.md`.

## 0.9.27 addendum — 2026-10-24

Version **0.9.27 (versionCode 36)** makes both volume sliders span **0-100%** with the reviewed
normal at **50%** by default. The 50% point is the unchanged near-perfect 0.9.25 volume; 100% is
twice that level. Music 100% maps to gain 2.0 and voice 100% to 0.5. Stored gains and the synth
level are unchanged, so no settings or preset migration is required.

The APK is `dist/EarBy-0.9.27.apk` (71,179,497 bytes, SHA-256
`be10664d5bbf3cf6e79b0dfc78b5065eed922ae12cd2bfd6722948525bc225b0`) and is installed on the
testing phone (versionCode 36). The 0.120 synth level remains `review.humanAccepted=false` pending
volume acceptance.

Automated checks: focused settings/ViewModel tests pass, `spotlessApply` and the debug build pass,
`check-apk.sh` passes, and installation succeeded. Full QA is deferred until the volume is accepted.
The settings/Play screenshots now show 50% and need a new owner-approved baseline; the old
baselines were not overwritten.

## 0.9.26 addendum — 2026-10-24

Version **0.9.26 (versionCode 35)** changes the music gain scale to **4x full scale**. The reviewed
normal remains at the slider centre and now displays **25%**, matching the voice gain's 25%. The
stored music gain, synth samples and actual output are unchanged; the default is still the
near-perfect 0.9.25 volume. The upper half of the music slider can raise the output up to 4x the
normal. No settings or preset migration was needed.

The APK is `dist/EarBy-0.9.26.apk` (71,179,497 bytes, SHA-256
`8ca6eef4163a16782ea4204b9e59be5897251c5acc77aed87cc6fbe352803b3c`) and is installed on the
testing phone (versionCode 35). The 0.120 synth level remains `review.humanAccepted=false` pending
volume acceptance.

Automated checks: the focused settings/ViewModel tests pass, the debug build and `spotlessApply`
pass, and `check-apk.sh` passes. Full QA is deferred until the volume is accepted. The
settings/Play screenshots now show 25% and need a new owner-approved baseline; the old baselines
were not overwritten.

## 0.9.25 addendum — 2026-10-24

Version **0.9.25 (versionCode 34)** doubles the synth output level at the existing centre gain
position. The original MATLAB scripts were run with `rmsTarget = 0.120`, regenerating 176 candidates
and 88 final notes (twice the first-audition 0.060 RMS; peaks 0.44-0.83). The SF2 was rebuilt and
the gain settings, slider mapping and voice level are unchanged. The separator clip remains the
original level.

The bundled font is 21,126,646 bytes, SHA-256
`c3c3ff7da62bf0ad7d306657d168783f75bd8a894dfa7c2cdb27bb4b7a7cd27c`. The APK is
`dist/EarBy-0.9.25.apk` (71,180,274 bytes, SHA-256
`26747b824b649582667e8b7803bd639a25f71cdc80d0a60b47dc146d62975712`) and is installed on the
testing phone (versionCode 34). The descriptor is `review.humanAccepted=false` because the 0.120
level is **pending owner listening**; the 0.9.23 timbre acceptance remains recorded in its notes.

Automated checks so far: MATLAB `run_synthesis` and `build_final(59)` passed, the SF2 tool tests
passed 6/6, the app's native render fixtures passed 9/9 with the louder font (chord peak 0.655),
`check-apk.sh` passed, and installation succeeded. **Full `scripts/qa.sh` has not been run for
0.9.25**, by the owner's instruction to defer it until the volume is accepted. The 0.9.24 build had
passed full QA (1083/0 host tests, 60/60 review device tests, strict screenshots and APK audit).
G3/G4 remain open.

## 0.9.24 addendum — 2026-10-24

Version **0.9.24 (versionCode 33)** adds the Play `Current setup` debug `Notes:` line. While a
session is PLAYING it shows the current item's notes in letter+octave form: one name for a
single-note item, several for a chord. The value comes from `SessionSnapshot.currentNotes` through
the app-private state query; it is not added to media metadata or the lockscreen. This is an
intentional owner-requested exception to the hidden-answer rule (decision D44) and exposes the
generated pitches before the spoken answer.

The version bump is commit `4f7446c`; the feature is `b5c3f0a`. The daily-phone APK is
`dist/EarBy-0.9.24.apk` (71,179,377 bytes, SHA-256
`bbf07bba34033af0035f6b39a051c7c925e50920348718c597b1d07c07dabdfe`) and was installed on the
testing phone; the package reports versionCode 33 and versionName 0.9.24. The 0.9.23 custom synth
and its acceptance are unchanged.

Automated evidence: the focused controller and Play tests pass, `scripts/check-fast.sh` passes, and
the full `scripts/qa.sh` run on 2026-10-24 passed all 10 stages: 1083/0 host tests, 60/60 review
device tests, 5 data-platform and 9 audio-platform tests, both force-stop checks, 0 crash/ANR,
strict 9-image screenshots and the packaged-APK audit. Evidence:
`.local/verification/qa-2026-10-24-1611/QA-REPORT.md`. The 30-minute endurance stage was excluded by
the default owner decision. No screenshot baseline changed. G3/G4 remain open.

## 0.9.23 addendum — 2026-10-24

The owner's custom additive string synth replaced the 0.9.22 Osiris/MuseScore D3 hybrid and is now
bundled as **0.9.23 (versionCode 32)**. One bank 0/program 0 preset, `String Synth A0-C8`, covers
MIDI 21-108 with 88 one-shot mono 48 kHz PCM16 samples; bass timbre covers MIDI 21-59 and guitar
timbre MIDI 60-108. The font is 21,126,646 bytes, SHA-256
`fcef5e8869cd42f6b3bc4df495a02fc021c04b3b6d4fa71276fd838380f8efee`. The C6/G6 separator and the
renderer transition fixes are unchanged.

The instrument commit is `225aff6`; the acceptance is `5e3a24c` and the review-harness fix is
`abb3d5f`. The accepted daily-phone APK is `dist/EarBy-0.9.23.apk` (71,162,997 bytes, SHA-256
`47cec73c56b5342066112c016ba6284762b0ca6990b7faffbb91a7682bbbb043`), the exact debug APK built and
audited by the passing QA run. The testing phone still has 0.9.22; 0.9.23 was not installed there.
The owner accepted the custom synth on 2026-10-24, and `scripts/check-assets.sh --require-reviewed`
passes. QA-F02 is closed. The 0.9.23 instrument is the latest accepted instrument; the 0.9.21 D6
hybrid was the previous one.

Automated evidence for 0.9.23: the synth's own SF2 validator passed; the app's native render
fixtures passed 9/9 with the bundled font; `scripts/check-fast.sh` exited 0; and
`scripts/check-apk.sh` passed with no network permission and the expected asset. A full
`scripts/qa.sh` run on 2026-10-24 finished **PASS** on all stages: 1081/0 host tests, 60/60 review
device tests, 5 data-platform and 9 audio-platform tests, both force-stop checks, 0 crash/ANR
markers, strict 9-image screenshots and the packaged-APK audit. The earlier QA-F01 focus failure was
fixed in the test harness by keeping a real `MainActivity` resumed with `ActivityScenario`; the two
affected tests then passed 2/2 and the full rerun passed. The 30-minute endurance stage was excluded
by the default owner decision. No screenshot baseline changed. Evidence:
`.local/verification/qa-2026-10-24-1514/QA-REPORT.md`.

A post-0.9.23 source change adds the Play `Current setup` debug `Notes:` line for the notes of the
current item (decision D44). It deliberately exposes generated pitches before the spoken answer,
is absent from media metadata and the lockscreen, and is not in the accepted 0.9.23 APK.
`scripts/check-fast.sh` passed; that release was 0.9.24.

Resume state, exact paths, commands and next steps: [HANDOFF.md](HANDOFF.md).

## Previous addendum — 2026-10-23

The piano boundary was lowered from D6 to **D3 (MIDI 50)** after the owner found a key-clash sound
in a few mid/high notes of the accepted 0.9.21 D6 hybrid. Osiris now covers B0 to C#3 only; the
original MuseScore General covers D3 to C8. The original C6/G6 separator and the renderer
transition fixes are unchanged. Version 0.9.22 (versionCode 31) is installed on the testing
phone; the daily-phone APK is `dist/EarBy-0.9.22.apk` (SHA-256
`b77b916f3f80d68e015813a2304975e41c9e5e41a8369fb366ba46e7273b5c5b`). The D3 asset review is
pending.

The instrument commit is `cde34f0`; the docs-only wrap-up commit follows it. A minimal continuity
backup is at `an external backup`. The 0.9.21 D6 hybrid remains the last
owner-accepted instrument. The technical D3 boundary check passed (B0/C#3 against Osiris, D3/G4/C6/
C7 against MuseScore, correlations 0.99851-1.00000), but this addendum does not claim a new human
acceptance. The historical M11/G3/G4 rows remain recorded as before. No full QA has run since
0.9.6.

Resume state, exact paths, commands and next steps: [HANDOFF.md](HANDOFF.md).

## Current status — 2026-10-15

The v0.2.x mode expansion is implemented: all eight modes (interval recognition, chords, scales,
note identification, scale degrees, chord progressions, intervals in context and melodic
dictation) ship through the shared pipeline, and every drawer entry is live. The app is version
0.9.1 (versionCode 10); continuity backups v0.2 through v0.9 live under
`an external backup`, and M0–M10 are AUTOMATED_COMPLETE in the implementation ledger
([v0.2.x/IMPLEMENTATION_STATUS.md](v0.2.x/IMPLEMENTATION_STATUS.md)). M11 (integrated
verification and agent handoff) is AUTOMATED_COMPLETE; G3/G4 human rows remain open. The
v0.3.x line closed at 0.8.0, and the v0.9.x line is now active: its first release was 0.9.0 and it continues at 0.9.15
(brand theme, V4 icon, drawer tagline, dark-text controls) with a continuity backup at
`an external backup`; v0.9.x work continues. An owner-created automated QA harness (`scripts/qa.sh`) drove a
revision pass on 2026-10-16: environment/keystore/ccache/uv repairs, a device-test race fixed,
the long-standing SwitchModeTest flake root-caused, dead code removed, and a warm-index Android
Studio MCP review; the closing run (`scripts/qa.sh --with-endurance`) passes every stage
including the real 30-minute endurance (evidence
`.local/verification/qa-2026-10-16-1253/`).

A whole-project QA pass on 2026-10-15 added `scripts/qa.sh` as the one-command automation entry
point and is PASS on all ten stages: 1087 fresh host tests (music 73, training 600, audio 59,
media 24, data 205, app 126; 0 failures), review-short 58 plus data 5, audio 9 and force-stop
1+1 device tests with 0 crash/ANR in the window, strict 9/9 screenshot baselines and zero
pending visual cases, the dark review set re-captured, and no INTERNET or review-only code in
the production APKs; the endurance stage is excluded by the owner decision with the M11
evidence cited. No repairs were needed, the version stays 0.9.1 and no backup changed.
Evidence: `.local/verification/qa-2026-10-15-2135/QA-REPORT.md`. Human rows stay open.

The dated review record below is kept as history: its test counts and state descriptions describe the
2026-10-11 snapshot, not the current tree.

## Current review — 2026-10-11

The user authorized a sequential Stage 0–4 source/MCP review, repairs and automated phone
verification. Implementation spans all six modules. The accepted app has a mode sidebar
(interval recognition is implemented; the other ToneDear modes are placeholders), Play/Setup/
Presets tabs, Start/Stop, recognition/exposure/association, five editable preset slots and live
editing.
Recall/deep-repetition modes, pause controls, mini-player and document import/export UI are
retired owner decisions; their older descriptions below are historical. Existing data import/
export APIs and migration fixtures remain for compatibility. No Stage 5 work is authorized.

The owner's 2026-10-11 instruction authorizes the [v0.2.x mode expansion](v0.2.x/README.md)
(M0–M11): the seven existing mode placeholders filled through one shared pipeline, per-mode
storage and presets, consumed-frame mode switching, theory fixtures, voice assets and
automated verification. This track supersedes the earlier Stage 5 plan for these modes;
unrelated Stage 5/6 work stays unauthorized. It may proceed while G3/G4 human rows remain
open; it does not close those gates.

Packages B–E: duration/fixed-root validation, typed identity, bounded item/timeline retention,
immutable history gains/recipes, hidden-answer publication, settings/error handling, asset
hashes, output-head accounting, independent fade/duck, screenshot and document guards are
implemented. Canonical fast checks and targeted regressions passed in the dated review logs.
Package F adds an isolated review application and automated phone tests. New phone findings
are repaired. Fresh host execution passes **450 tests**. `check-full.sh --automated-only`
passes, including **33 connected tests** and the real 30-minute display-off session, with terminal
resource release and no stale restart. Post-warmup PSS variation is 2,952 KiB; the regular app's
APK and four private-file hashes are unchanged. Exact evidence is in `check-full-review-6.log`
and `device-final-5/` under the dated review directory.

Evidence and exact per-package results: [PROJECT_REVIEW_2026-10-11](PROJECT_REVIEW_2026-10-11.md).
Raw evidence: `.local/verification/project-review-2026-10-11/`. Never substitute the historical
records below for newly executed tests.

Post-review verification, 2026-10-11: the workspace cleanup was audited (1,913 protected inputs
verified, only generated targets removed, archived APKs and reports hash-checked) and the reviewed
source was re-verified from the cleaned tree. `check-fast` passes with **450 host tests**;
`check-ui.sh` compares the nine approved baselines (after fixing one cleanup interaction: the
harness smoke test captured under `app/build/`, which the cleanup deleted, so verification failed;
the scratch capture is now skipped while verifying). Native host contracts (2 CTest, 7 render
fixtures) pass, and an isolated device smoke passes **32 tests** with the regular app's hashes
unchanged. The reviewed state is frozen at `an archived build`.

| Gate | Current interpretation |
| --- | --- |
| G0-host / G0-device / G0-IDE / G1 / G2 | Historical accepted evidence retained; host/phone/MCP readiness rechecked in this review |
| G3 / G4 | **PASS, owner-reported 2026-10-25** on 0.9.33 / versionCode 42. Detailed row notes and refreshed visual baselines remain pending. |
| G5, G6, G8 | G5 is largely superseded by v0.2.x. **G6 PASS in 0.10.0** for interval walks only; contrast, weighted practice, intonation, mixed sessions and clusters are dropped. **G7 dropped by owner 2026-10-25.** G8 remains. |

IDE limitation R28: MCP builds pass, but the imported semantic model retains Gradle/catalog and
new JSON-library resolution errors and excludes the review source set. The MCP connection exposes
no synchronization action. These inspections remain incomplete; CLI compilation/lint/device
coverage passes. No clean IDE inspection or human acceptance is claimed.

Current verification entry points:

- `scripts/check-fast.sh`: read-only guards, all six modules' host tests, debug build/lint and native packaging.
- `scripts/check-ui.sh`: complete nine-image approved-baseline comparison; recording is refused.
- `scripts/capture-ui-candidates.sh [DIR]`: ignored candidate generation; cannot target the baseline directory.
- `scripts/device-smoke.sh --smoke-only`: isolated review APK, bounded automated short phone tests.
- `scripts/check-full.sh --automated-only`: assets/native fixtures, UI comparison, debug/release/review APKs and lint, isolated connected tests, real 30-minute screen-off endurance.
- `scripts/record-dependencies.sh`: explicit dependency update only; never a routine check.

## Workspace cleanup — 2026-10-11

Safe tidy removed 17 allowlisted targets: generated build/cache/temp files and the redundant
Studio installer, reclaiming approximately **5.42 GiB net** after archiving the six tested APKs and 790
build/test/lint report files. The APK archive is `.local/dist/project-review-2026-10-11/`.
Source, assets, approved screenshots, historical releases/evidence, voice candidates, installed
tools and dependency/model caches are preserved. No application behavior or dependencies changed.

The removal manifest, protected-input hashes, archived reports and cleanup verification are in
`.local/cleanup/2026-10-11T095330Z/`. Protected-input comparison, environment and document
checks passed; application and phone tests were not rerun. Prior review results above remain evidence.

## v0.2.x mode-expansion planning — 2026-10-11

Detailed, sequential M0–M11 agent implementation plans are saved in
[v0.2.x](v0.2.x/README.md), alongside the preserved original overall plan, shared contracts,
decision record and a separate implementation ledger. They cover all seven current placeholders,
per-mode storage/presets, safe mode switching, musical fixtures/assets and automated acceptance.

The owner directed chord and scale procedures to follow ToneDear. The inspected reference
accepts equivalent chord interpretations; this passive app will speak the valid alternatives.
Scale fragments are deferred in favor of complete scales. See the
[reference and decision details](v0.2.x/REFERENCE_PROCEDURES.md).

This work creates documentation only; every feature milestone remains PLANNED. Application,
asset, dependency and device changes are not part of this planning task. Documentation integrity,
link and preservation-check evidence is under `.local/verification/v0.2.x-planning/`;
app/phone tests are not rerun or claimed as new results. G3/G4 human rows above remain open.

Documentation checks passed: 39 maintained documents have valid local links/text; the twelve
milestone files define 100 unique acceptance/regression IDs, and their eleven command references
resolve or are explicitly planned. Hash comparison preserves every previous document except
this STATUS update, including the original overall plan and planDraft.md.

## Historical records below

These dated records retain prior owner decisions, evidence and superseded work lists.
The current interpretation above and the dated review ledger control this review.

# Historical development records

**0.1.x finished, 2026-10-11 (build `11c`).** The owner accepted build `11c` as the finish of the
0.1.x line; every checklist row passed except TalkBack, which is still untested. A minimal,
restorable backup is frozen at `an archived build` (accepted APK +
complete source + verification evidence + `RESTORE.md` + source checksums). The nine screenshot
baselines are recorded under `app/src/test/screenshots/`, `scripts/check-ui.sh` now passes
(verified against a deliberately drifted baseline, which fails as it should), and a review run can
redirect the tests to a candidates folder with `-Pear.screenshots.dir=...`. The workspace version
is bumped to 0.2.0 for the next feature line. Remaining human rows: TalkBack.

**First ready version, 2026-10-10 (build `10x`).** The owner accepted the current app as the first
ready version. The milestone APK is archived at
`.local/dist/PassiveEarTraining-0.1.0-debug-2026-10-10x.apk` and in `an archived build`; its SHA-256 is
`8a2260c55b09ae77b0b1fbbbd5f4dee5861c0e48b092b32f6ea52f799d64adf8`. Note spelling was implemented
right after (build `10y`, 420 tests, archived at
`.local/dist/PassiveEarTraining-0.1.0-debug-2026-10-10y.apk`). Remaining before wide use: the
owner's visual-baseline approval (eight screenshots) and the physical G3/G4 acceptance rows
(locked-screen timer, TalkBack, fixed root, notification behavior). Everything else in the
owner's checklist is fixed or accepted.

Reviewed 2026-10-09 against source, existing verification artifacts and the live Android Studio
MCP. Stages 1 and 2 have recorded acceptance (G1/G2), and G0-device has passed. G0-IDE now passes
after the import was repaired. The 2026-10-09 integration repairs WP3.1a, WP3.3a and WP3.4a are
implemented and host-verified; the physical G3 rows remain. Stage 4's WP4.1c/WP4.2a data and
start wiring are implemented; the Setup/Player controls, named presets, preset document editing,
UI regression harness and WP4.5 acceptance remain; **G4 is OPEN**.

See [plan review findings](PLAN_REVIEW_2026-10-09.md) and
[Android Studio MCP workflow](ANDROID_STUDIO_MCP.md). Implementation for the reviewed repair
sequence is authorized by the 2026-10-09 instruction; G3/G4 acceptance still requires physical
and human evidence.

## Historical authorization and next work (2026-10-09)

The 2026-10-09 implementation instruction authorizes the reviewed repair sequence. Application
source is being changed again; existing historical evidence stays under the dated headings below.

The physical phone's recorded identity is ASUS_AI2302 / Zenfone 10, Android 14 / API 34,
arm64-v8a, 4 KiB pages. Reconnect/authorization and ANDROID_SERIAL must be checked before a new
device run; the phone is required for G0-device re-verification and the G3/G4 acceptance rows.

**Verification commands, in the order to run them**

| Command | What it covers | Last result |
| --- | --- | --- |
| `scripts/check-fast.sh` | guard/audio-tool tests, boundaries, Spotless, JVM tests for all six modules, debug build/lint and native packaging | PASS, 2026-10-11: 424 JVM tests, 0 failures/errors/skips, including the Robolectric/Roborazzi UI harness; `.local/verification/check-fast-11c.log` |
| `scripts/check-full.sh` | fast plus asset/native host-render checks, both APK inspections, lint, connected app/data tests and device smoke report | PASS 2026-10-09 on ASUS_AI2302 (Android 14): connected app 4 + data 4 tests, 0 failures; instrumentation PASS; no network permission packaged; human rows unchecked. `.local/verification/wp3.1a-3.4a/check-full.log`, `.local/verification/device-smoke/report.md` |
| `scripts/device-smoke.sh` | the repeatable device reliability rows plus a human checklist | instrumentation PASS 2026-10-09; the 12 human rows remain unchecked |
| `scripts/record-dependencies.sh` | recording checksums after an *intentional* dependency addition | 1,275 checksum entries now reported by the verifier; new entries are the intentional IDE classifier records |

**Next work, in order**

1. **WP4.3f → WP4.4a → WP4.3g**: expose the already-wired configuration and the live Player
   state (partly done through the live session query and the Play tab); live apply of an edited
   setup to a running session (implemented 2026-10-10: the next pair uses the edit without a
   pause or restart); named custom preset create/update/rename (implemented 2026-10-10); and the
   Robolectric/Roborazzi UI harness (integrated 2026-10-10: Robolectric 4.17 + Roborazzi 1.76.0,
   checksums recorded, smoke and Play-tab candidates rendered). The nine baselines are recorded
   and `scripts/check-ui.sh` passes; a review run redirects the tests to a candidates folder with
   `-Pear.screenshots.dir=...`. WP4.2a's explicit
   backup/extraction rules are packaged (`allowBackup=false`, `backup_rules.xml`,
   `data_extraction_rules.xml`) and `scripts/check-apk.sh` now fails when they are absent.
2. **WP3.5 → WP4.5 acceptance** on the physical phone: locked 30-minute session, timer expiry,
   routes/focus, daily-use save/export/import/airplane-mode/process-death and
   TalkBack/150%/200% checks. Shared sessions may supply both reports when the build,
   configuration, route and observations are recorded exactly. The prepared request is
   `.local/verification/g3-g4-human-request.md`.
3. **WP5.1 onward** only after G3/G4 pass.

**Limitations and decisions**

- **Hilt / KSP2 and Room:** rejected by the recorded 2026-10-09 compatibility probe. The implemented
  path is manual composition, Preferences DataStore and SQLite with tested migrations.
- **Studio import:** repaired this session; the IDE now imports all Gradle modules and exposes
  usable Kotlin diagnostics and run points. See the G0-IDE section below.
- **Human rows:** listening, route/accessory actions, TalkBack and visual-baseline approval remain
  with the device owner.
- **IDE-only classifier artifacts:** Android Studio's sync resolves source/javadoc classifiers
  regardless of the source-download setting; their SHA-256 entries were recorded in
  `gradle/verification-metadata.xml` as an intentional IDE-metadata operation, not a dependency
  change.

## 2026-10-09 implementation session: G0-IDE, WP3.1a, WP3.3a, WP3.4a

**G0-IDE PASS.** The failed sync was `Dependency verification failed for configuration
'classpath'`: Studio requests distribution and dependency sources, which strict verification had
no checksums for. `gradle/verification-metadata.xml` now records the Gradle 9.6.0 source zip and
the Android Studio classifier artifacts (`origin="Recorded for IDE source attachment"`), the IDE
Gradle JDK is the project `temurin-17` (`.local/jdk-17`), the Gradle service directory is
`.local/gradle-home`, and an IDE-generated `gradle-daemon-jvm.properties` that requested a JDK 25
daemon was removed after preserving a copy at
`.local/verification/g0-ide/gradle-daemon-jvm.properties.ide-generated`. The IDE log records
`onSuccess(RESOLVE_PROJECT:0)` and a 9.9 s sync; `get_project_modules` returns all six Gradle
modules plus source sets, `get_project_dependencies` 36 JPS entries, `get_run_configurations` the
`app` launch configuration, and `get_file_problems` analyzes both JVM and Android Kotlin sources.
Evidence: `.local/verification/g0-ide/`.

**WP3.1a: continuous queue, silent navigation, PCM identity.** `DefaultSessionController` now
runs playback on a worker off the command thread, advances only when the device has fully
consumed an item (submission alone never advances), reports live consumed position and answer
visibility through a suspend callback, and keeps commands responsive while rendering or writing.
All queue mutation stays on the command dispatcher: exactly one item is drawn ahead, backward
navigation pushes the already-drawn follower onto the retained forward path, and Repeat/Previous
select without submitting sound. `AudioPort` gained an identity-keyed bounded (current/next) PCM
cache contract with idempotent `prepare` and a play outcome carrying submitted/consumed/completed;
`RendererAudioPort` implements the cache, waits for the consumed tail, and keeps per-item position
across flushes. `SessionControllerTest` was rewritten around a faithful one-current/next fake:
29 tests cover automatic continuation, submission-vs-consumption, paused navigation silence,
history re-render of an evicted identity, seeded sequence preservation, live answer/duration
updates and interrupted-write/stop/close paths. Evidence:
`.local/verification/wp3.1a-3.4a/check-fast.log`.

**WP3.3a: focus/readiness before sound.** `AudioPort.requestPlaybackPermission()` is called before
the writer is opened on Start, Resume and audible navigation; a denial pauses with
`PauseReason.Focus` and submits no PCM. `PlaybackReadiness` (core:media) is implemented by the
service over `AudioFocusCoordinator`, which is now idempotent while focus is held, so item
boundaries do not churn focus. The phase observer only releases focus: user pause, route change,
completion and error abandon it; a `PauseReason.Focus` pause keeps a transient-loss grant for
explicit resume. Route/noisy interruptions set a recovery flag; the port reopens the output device
before the next sound and clears it. `RendererAudioPort.configure` reuses the engine instead of
leaving a replaced renderer open, and rendering/release are serialised by the port's render lock.
Evidence: `.local/verification/wp3.1a-3.4a/check-fast.log`.

**Defect found on the phone and fixed (2026-10-09): no audio after Start.**
A tap on Start published metadata and a PLAYING state but produced no sound. Device logcat showed
Media3's play path calling `prepare()` and failing with `Missing implementation to handle
COMMAND_PREPARE`: `TrainingPlayer` advertised `COMMAND_PREPARE` without overriding
`handlePrepare`, so `Start` never ran. `handlePrepare` now returns an immediate no-op future
(the session prepares inside Start), and `PlaybackServiceDeviceTest` gained the regression
`aPlayRequestStartsTheSessionWithoutFailingOnPrepare`. The same pass made
`AudioFocusCoordinator.request()` idempotent while focus is held and cleared its flag on a
permanent loss, so item boundaries do not re-request focus (the log had shown repeated
requests). Verified on the phone: the session reaches PLAYING with an advancing position, focus
is granted, and the connected suite passes (app 5 + data 4 tests, 0 failures).

**Owner phone session (2026-10-09 evening).** After the fix, the owner started a session, set a
5-minute sleep timer and locked the screen: playback continued and stopped about five minutes
later without reappearing. On request, the Player now shows a live sleep-timer countdown
(`formatCountdown`) fed by timer metadata extras (`SLEEP_REMAINING_MS_EXTRA`,
`SLEEP_DRAINING_EXTRA`); verified on the phone as "Sleep timer: 4:56" after arming five minutes.
The first version only moved when the service republished metadata, which was about every item
(seven seconds); the ViewModel now runs a local one-second ticker resynchronized by every
metadata update, and six consecutive phone reads two seconds apart showed 4:42, 4:40, 4:38,
4:36, 4:34, 4:32, so the display changes every second. Evidence:
`.local/verification/g3-g4-human-results.md`. Wired/Bluetooth route rows, pause/Next/
Previous checks, battery saver, swipe/relaunch, TalkBack, large text and visual baselines remain
open.

**Owner feedback batches (2026-10-10).** The owner's list is recorded at
`.local/verification/owner-feedback-2026-10-10.md`. Implemented: consumed-answer display with the
answer appearing when the voice starts (the port now reports progress while the writer blocks);
one status box on the Play tab; sound sliders only in Settings, centered on the reviewed defaults;
thinking time with two decimals; learning-mode explanations; repeats hidden for answer-first
recall; fixed-root control moved out of advanced; random up/down presentation; setup autosave;
named preset create/update/rename; optional pair separator cue persisted through settings and
presets (SQLite v5); combined Pause/Stop transport; association mode now honours the thinking
time; live apply of an edited setup to a running session (the audible pair keeps its rendered
timeline, the one-ahead follower is redrawn and every later pair uses the new selection, recipe,
gains and stop policy; a sequence edit continues the running random stream); and the harmonic-mode
stop (the frame timeline required every sounding window to follow the previous one, but a harmonic
pair shares one start frame; a same-start chord is now valid while any other overlap is still
rejected). Notification ducking lowers the volume and continues; another media app still pauses.
Remaining: the fixed-root phone check (owner) and notification testing (owner). Evidence for each
step is in the feedback file and the `.local/verification/` logs.

**Checklist results and repairs (2026-10-10, 10n build).** The owner marked
`.local/verification/test-checklist-2026-10-10.md`. Repaired: the stop policies never reached a
session because the setup's `stopPolicy` was written nowhere and read from a different field (the
policy now round-trips with the setup; a live edit redraws its follower so the cycle boundary is
exact); the "After a duration" option was removed because the sleep timer owns durations; Deep
repetition and Answer-first recall were removed and the preset schema migrates stored rows to
Recognition; association now honours "repeats after the answer"; an empty interval or root
selection is a valid "all options included" state (the chips stay unselected and the surfaces say
so); "Hide on screen" now hides the answer; the countdown ticks smoothly; an applied preset
refreshes the setup page and the Play seed line shows the setup's seed; preset export/import was
removed; the sleep chips/switches hold at the largest text and display sizes; and a swipe-away now
stops the session. Still owner-run: notification duck/pause, TalkBack and the visual baseline
review; note spelling still does not change the answer text.

**Preset slots (2026-10-10, 10s build).** Per the owner's request, the Presets tab hides each
card's details behind a toggle, shows exactly five replaceable user slots created by schema v8,
renames them and never deletes them, sorts the slots by A-Z or most recent, and drops Duplicate,
Delete and free preset creation. A custom saved before the slot upgrade is adopted into an empty
slot once; the one-shot marker lives in migration 8 because migration 7 had already run on the
test phone. A follow-up (10t) tinted custom cards differently from the starters, made the sort
apply to the whole list, removed the standalone save button, and renamed each slot's action to
Replace. The redesigned `presets.png` needs the owner's review again.

**Wheel pickers and one-page Play (2026-10-10, 10u build).** The Play page no longer scrolls: the
sleep timer is a vertical wheel (Off and 15-180 minutes, no custom value), and the Current setup
card is compact. Every setup slider became a wheel through `VerticalPicker` (thinking time,
additional repeats after the answer, fixed note, lowest and highest note, note length, the two
gaps, and the after-N-items count). The Advanced row toggles from anywhere on it, the Save now and
Close buttons are gone, and the setup page shows the bottom navigation instead; leaving the page
flushes a pending autosave. Verified on the ASUS test phone: the sleep wheel arms the timer, a
thinking-time change reached the stored settings, and the Advanced row expands from its label.

**Picker dialogs (2026-10-10, 10v build).** The inline wheels were replaced with the standard
Android pattern the owner asked for: each setting shows a value button, and tapping it opens a
modal with the scroll wheel plus OK/Cancel. `ChooserButton`/`ChooserRow`/`PickerDialog` in
`VerticalPicker.kt` implement it for the sleep timer and every setup wheel. Verified on the ASUS
test phone: the sleep dialog selected 45 minutes and armed the timer; the thinking-time dialog
selected 2.25 s and stored 2250 ms. Two new Roborazzi candidates capture the dialogs
(`play-sleep-picker.png`, `setup-picker.png`), and the test count is 420.

**Mode sidebar and tab rebuild (2026-10-10, 10z build).** The shell now has a
`ModalNavigationDrawer` opened from a top-left hamburger and titled with the selected mode. The
drawer lists the ToneDear modes (Intervals implemented; Chords, Scales, Chord progressions,
Perfect pitch, Scale degrees, Intervals in context, Melodic dictation marked "Soon" and showing a
placeholder), with Settings pinned at the bottom. The bottom tabs are Presets | Play | Setup;
Setup is a tab and the Play page's Setup button is gone, and `lastMode` persists in the settings
store. Verified on the ASUS test phone: the drawer opens, Chords switches the title and shows its
placeholder and survives a restart, and the tabs reach Setup/Presets/Settings.

**Checklist 10k fixes (2026-10-11, 11a build).** System back on any tab returns to Play instead of
exiting; the picker popup became a narrow card sized to the wheel; and the fixed-root failure was
repaired: the switch had defaulted to the lowest note, which descending cannot play, so the whole
draft failed validation silently and reached neither the run nor presets. The toggle now picks a
feasible middle note, the picker offers only feasible notes, and presentation or range edits snap
the fixed root to the nearest playable one. Verified on the ASUS test phone with a descending run
and a stored `setup_fixed_root = C4`.

**No pause; bottom-aligned Play (2026-10-10, 10w build).** Per the owner, pause and resume were
removed: the Play tab shows Setup | Start | Stop in one row above the sleep timer, with the title
and status at the top and the Current setup card above the row. `TrainingTransport` maps a
play-when-ready false request to Stop and restarts from PAUSED by stopping and starting fresh;
audio-focus interruptions now end the session instead of pausing, so the next Start draws a new
seed and a new random order. Notification ducking still lowers the volume and continues. Verified
on the ASUS test phone: two consecutive starts drew different seeds, and the layout matches the
requested order.


A tap on Start published metadata and a PLAYING state but produced no sound. Device logcat showed
Media3's play path calling `prepare()` and failing with `Missing implementation to handle
COMMAND_PREPARE`: `TrainingPlayer` advertised `COMMAND_PREPARE` without overriding
`handlePrepare`, so `Start` never ran. `handlePrepare` now returns an immediate no-op future
(the session prepares inside Start), and `PlaybackServiceDeviceTest` gained the regression
`aPlayRequestStartsTheSessionWithoutFailingOnPrepare`. The same pass made
`AudioFocusCoordinator.request()` idempotent while focus is held and cleared its flag on a
permanent loss, so item boundaries do not re-request focus (the log had shown repeated
requests). Verified on the phone: the session reaches PLAYING with an advancing position, focus
is granted, and the connected suite passes (app 5 + data 4 tests, 0 failures).

**WP4.1c/WP4.2a (data and start wiring, 2026-10-09).** The saved configuration now reaches
playback: `LastSetup`/`SetupDraft` carry the full enabled presentation set (nullable, so a single
presentation keeps the version 1 draw order and a mixed set is version 2), deep-repetition counts,
melodic gap, final silence and an optional seed. `StartRequest` carries the one selected
`SessionStopPolicy` and a nullable seed; the controller resolves an absent seed exactly once,
passes the resolved value to the source and publishes it in the snapshot. A `SessionStopTracker`
counts only full consumed traversals (count policy stops with `CompletedItemsReached`; cycle
policy stops at the exact bag boundary; `PauseReason.StopPolicyReached` and `endingCause` are
published). Duration policies arm the same authoritative `SleepTimer`; `FINISH_CYCLE` keeps
playback through the current cycle. The settings DataStore persists presentations, counts, gaps,
seed and a real duration stop (the previous write silently downgraded a duration to
until-stopped), refuses writes over a newer stored schema instead of overwriting it, re-reads
out-of-range gains as a reported fallback, and the gain limit is now the reviewed 0..1.
`PresetPayloadV1` carries the same fields with defaults so frozen documents still load; SQLite
moved to schema v4 with an additive `ALTER TABLE` migration. `PresetsViewModel.use` preserves a
preset's seed, and export success is reported only after the provider stream is written and
closed. Remaining for these packages: the Setup/Player controls that expose mixed presentation,
counts/duration, seed and preview (WP4.3f), the Robolectric/Roborazzi harness (WP4.3g), and
named custom preset create/update/rename (WP4.4a); the data layer they consume is in place.
Evidence: `.local/verification/wp4.1c/check-fast.log` (PASS, 384 JVM tests, 0 failures/errors/skips).

**WP3.1a/3.3a/3.4a check-full (2026-10-09).** `scripts/check-full.sh` passed on the connected
ASUS_AI2302: connected app 4 + data 4 tests with 0 failures, the instrumented launch/controller/
metadata/timer surface PASS, no packaged network permission, and the 12-row human checklist
produced unchecked as required. Evidence: `.local/verification/wp3.1a-3.4a/check-full.log` and
`.local/verification/device-smoke/report.md`. The physical G3/G4 rows are requested in
`.local/verification/g3-g4-human-request.md`.

**WP3.4a: timer execution.** The session controller owns one authoritative `SleepTimer` built on
the injected `MonotonicClock` and a service-owned `TimerScheduler` (the app's scope is cancelled
in `onDestroy`). `SessionCommand.SleepTimer(minutes)` arms/cancels/replaces and is stored for the
next Start; `StartRequest.sleepPolicy` is the saved-default seam for WP4.1c. Ticks actually
evaluate: `FadeTo` scales the output master gain (`ExerciseOutputWriter.masterGain`, cached PCM
untouched), `StopNow` completes silently with `PauseReason.TimerExpired`, and `DrainCurrent` lets
the audible item finish, prevents the next item's generation, and stops with
`EndingCause.DurationReached(drained=true)`. The timer is checked before every sound start and
before resume, so a late tick cannot let audio reappear; expiry while paused prohibits resume.
Snapshot now publishes `sleep` state (policy/deadline/remaining/draining/expired) and
`endingCause`. `SleepTimer.evaluate` during a drain keeps returning `DrainCurrent` (with the
after-expiry fade gain) instead of stopping early. Evidence:
`.local/verification/wp3.1a-3.4a/check-fast.log`.

## 2026-10-09 review corrections

The source review reopened completion claims because `StartRequest` omits the stored stop policy,
the production service arms but never evaluates/applies timer effects, first-item completion
does not automatically advance, paused navigation still calls play, and history preparation
flags do not match the one-current-PCM cache. Focus grant is not acknowledged before sound.
Stage 4 also lacks custom-preset create/update/rename, complete consumed answer/Player state,
seed preservation on preset use and the promised UI regression suite. These are static findings,
not newly reproduced device failures; the detailed repair requirements are in Stage 3/4 tasks.

README, roadmap, workflow, environment, architecture, infrastructure/asset choices, Stage 3/4
packages and testing now distinguish implemented components from gate acceptance. Stage 7 uses
the selected SQLite path. The owner-reviewed 2-second thinking and music 1.0/voice 0.25 defaults
replace stale plan defaults; old Stage 1 worked fixtures remain unchanged.

Review verification: **PASS** (documentation-only). Checked 19 Markdown documents, 58 local links,
eight repair definitions and their references, five present specialist commands and three marked
planned commands, and the 25-tool MCP inventory. SHA-256/inventory comparison found no changes
to 7,147 protected source/build/script/asset inputs, including AGENTS and planDraft.
Evidence: `.local/verification/plan-review-2026-10-09/document-checks.json` plus the MCP schemas
and read-only responses alongside it. Application fast/full suites and device/human acceptance
were not rerun; no dependency or visual baseline was rewritten.

## Historical handover notes

These cost time to learn. Read them before editing.

1. **`javap` before guessing an API.** `$JAVA_HOME/bin/javap -classpath <jar> '<class>'` against the
   extracted artifact answered questions about Media3, DataStore and Jackson in one step each. The
   jars are under `.local/gradle-home/caches/9.6.0/transforms/*/transformed/`.
2. **Re-read a file before patching it.** Spotless reformats on every `spotlessApply`, so a text
   replacement written from memory silently misses and the build keeps an old behaviour while the
   source appears correct. Three separate "fixes" failed this way and looked like product bugs.
3. **Check the exit code, not a filtered log.** Piping a build through `grep` hides the failure and
   a stale APK then gets installed and tested, which produced two rounds of misleading evidence.
4. **`check-full.sh` must rebuild the debug APK** before installing it; that gap existed and is now
   fixed in the script. Keep it.
5. **Do not wrap Kotlin lines with a regex.** An automated wrapper corrupted a test file twice with
   stray commas. Use `spotlessApply`.
6. **Assertions come after the arithmetic.** Several test failures were my expectations, not the
   code: a fade clamp, a frame-to-millisecond rounding, a replay budget inside the 60 s cap, an
   octave range that was feasible after all. Measure first, then assert.
7. **Device evidence beats reasoning.** The two most serious defects of the whole project (every
   voice clip failing to decode, and the screen feeding its own seed back in) were invisible to 100+
   host tests and obvious within one phone run.
8. **A released migration is frozen.** `PresetSchemaTest` pins the v1 preset statements and compares
   a migrated database with a fresh one. Change the preset schema only by adding a migration and a
   frozen-fixture test; editing migration 1 breaks installed databases silently. The same applies to
   the JSON document: add fields compatibly, keep the old fixture, never renumber.
9. **A connected IDE is not an imported project.** Discover modules/dependencies and require an
   analyzable Kotlin source before trusting empty IDE inspection results. Use scripts for final
   verification and inspect timer/queue consumers, not only isolated rules or command responses.

## Historical implementation evidence

The dated sections below preserve the original package reports and diagnostic decisions.
Counts and descriptions such as "not yet consumed" describe that point in development.
They do not override the current ledger or the reopened integration findings above.

### Stage 1 implementation evidence

Implemented packages and their results:

| Package | Outcome | Evidence |
| --- | --- | --- |
| WP1.1 pitch/interval model | MidiNote 0..127 with checked transposition, PitchClass with sharp/flat display, full 13-entry simple-interval table, three presentations | `core/music/src/main/kotlin/.../MidiNote.kt`, `PitchClass.kt`, `SimpleInterval.kt`; `IntervalModelTest.kt` |
| WP1.2 reproducible generation | Normalized immutable IntervalDefinition, GeneratorIdentity v1 (`java-random-48-v1`), feasibility rejection, canonical bag with Fisher-Yates and boundary correction | `core/training/src/main/kotlin/.../IntervalDefinition.kt`, `GeneratorIdentity.kt`, `IntervalExercise.kt`, `IntervalGenerator.kt`, `RandomSource.kt`; `IntervalGeneratorTest.kt` |
| WP1.3 recipe/timeline compiler | Recognition recipe, pure compiler, timeline events with markers, VoiceDurationCatalog, MonotonicClock port plus test fake | `core/training/src/main/kotlin/.../RecognitionRecipe.kt`, `Timeline.kt`, `TimelineCompiler.kt`, `MonotonicClock.kt`; `TimelineCompilerTest.kt`; `core/training/src/test/resources/timelines/recognition-v1.timeline` |

Verification actually run on 2026-10-07 (host, project JDK 17, strict dependency verification):

| Check | Command | Result |
| --- | --- | --- |
| Fast verification suite | `scripts/check-fast.sh` | PASS: 60 Gradle tasks, Python guard tests OK, static module boundaries PASS, debug assembly and debug lint PASS (log: `.local/verification/g1/check-fast.log`) |
| core:music unit tests | `./gradlew --dependency-verification strict :core:music:test` | PASS: 23 tests, 0 failures |
| core:training unit tests | `./gradlew --dependency-verification strict :core:training:test` | PASS: 58 tests, 0 failures |
| Deterministic invariants | `IntervalGeneratorInvariantTest` in the suite above | PASS: 10,000-item seeded run with cycle coverage, boundary rule and range invariants |
| Worked timeline fixture | `TimelineCompilerRecognitionTest.theRecordedFixtureMatchesTheCompiler` | PASS: ascending 0/800 ms/5500 ms/8400 ms, harmonic 0/0/4700 ms/7600 ms, descending and harmonic unison cases |
| Formatting | `spotlessCheck` via check-fast | PASS |

Total Stage 1 JVM tests: 81, all passing. The app still assembles and lints; core modules
remain free of Android/UI dependencies (static guard passes).

### Emulator versus phone for the audio proof

The emulator is usable for wiring, UI and command-surface evidence, and it is what this session
used. It is **not** a substitute for the phone at G2, and one measured limit makes that concrete.

Measured on 2026-10-09: the engine end-to-end device test did **not** reach the READY phase within
15 minutes on the API 36 x86_64 emulator (software CPU emulation, TCG; KVM was not available to the
Android emulator in this environment). The same font load and render completes in about 14 seconds
on the host with the same renderer sources, so the emulator penalty for this operation is roughly
two orders of magnitude. That is a property of the test environment, not of the app: FluidSynth
parsing a 206 MiB sample table is CPU-bound work.

Consequences recorded rather than worked around: the engine test stays marked `@LargeTest` and is
invoked explicitly; it is a phone task, not an emulator task. The screen, ViewModel, asset staging
and command surface are still verified on the emulator. The phone remains the only environment
that can produce the WP2.4 listening evidence, and the emulator number above is the recorded
reason it was not produced here.

Two real defects were found and fixed while getting the proof onto the emulator:

- the debug entry point called `onClose()` from its own composition, so the diagnostics screen
  displayed for one frame and immediately returned to the shell;
- the bundled SoundFont was packaged deflate-compressed (205.6 MiB stored as 167.6 MiB), so
  staging it on-device inflated the archive on every read. `sf2` is now stored uncompressed and
  staging uses a streamed transfer;
- during preparation the screen still reported IDLE, so a long engine load looked like a hang.
  `start()` and `regenerate()` now publish an explicit PREPARING phase first;
- the host harness wrote each WAV before applying the envelope and tail clearing, so its fixtures
  contained audio the app would never produce. The write now happens after processing, which is
  also what exposed the release-tail violation above.

### Emulator performance: measured contributor

A local LLM session running alongside this work uses about 95% of one CPU core, and the host had
about 7 GB of its 42 GB swap in use, with roughly 1.2 GB of the emulator's own 4.5 GB resident set
swapped out. Combined with software CPU emulation (no hardware acceleration for the Android
x86_64 image here), that makes the 206 MiB SoundFont load impractical on this emulator. The load is
expected to be fast on a phone; that remains unverified until a phone runs it.

### Newly enforced audio contract

Audible note windows now end with a 5 ms envelope and the instrument's release tail is cleared
between a note and the next audible event. The offline analysis proved why this was necessary:
before the change, a SoundFont piano kept sounding for roughly 0.5 s after note-off, so the
100 ms melodic gap in an interval fixture contained real audio (peak 0.02) instead of silence.
Specified silence is a product contract, so it takes precedence over the sample's natural decay.

## Stage 4 recorded progress (before this integration review)

The device owner approved adding Jackson Core and adjusting tool use; Jackson Core is added and
recorded. The Room question was settled by experiment rather than assumption: a probe module showed
that KSP refuses to run with AGP's built-in Kotlin, and that disabling built-in Kotlin in favour of
`kotlin-android` 2.2.21 fails against AGP 9 with a class-cast error. Room therefore needs a
coordinated AGP and Kotlin downgrade, which this package does not carry. The probe was removed and
the toolchain restored, so nothing in the build depends on an unreproducible state.


**WP4.1 (recipes and interval controls)** is implemented in `core/training`: the five templates are
built from named steps rather than script expressions, so validation can measure a recipe and a
screen can preview it. Two deliberate choices:

- A nested `Duration` stop policy would have shadowed `kotlin.time.Duration`, so the time type is
  imported under an alias and the policy keeps its natural name.
- The longest replay the bounds allow still fits inside the 60-second exercise cap, which is asserted
  so the product's longest recipe is known to be playable rather than assumed to be.

**WP4.4 (preset store)** is implemented in `core:data` on plain SQLite
(`android.database.sqlite`) with hand-written migrations, because Room/KSP is infeasible on this
toolchain. What the package now provides:

- **Schema v1 with tested migrations.** Creation is migration 0 -> 1, so the fresh path and the
  upgrade path are the same code; `PresetSchemaTest` freezes the v1 statements, asserts a contiguous
  migration chain, refuses a newer stored schema, and compares a migrated database's shape with a
  fresh one. A future v2 adds one migration and one frozen-fixture test; that is the only sanctioned
  way to change the schema.
- **Repository and CRUD.** `PresetRepository` with create/update/rename/duplicate/delete, UUID
  identities, trimmed and bounded names, edited-time recording, a 100-editable-preset bound, and
  per-preset seeds, gains and stop policy. Updates keep the creation time and never change identity.
- **Immutable starters stored separately from user copies.** Minor/major thirds, consonant intervals
  and all simple intervals are seeded once with fixed UUIDs under `built_in = 1`; every write path
  is restricted to `built_in = 0`, so rename/delete/update of a starter fails with a typed error and
  an attempted overwrite rolls the whole transaction back.
- **Import preview and one-transaction commit.** Per-preset validation rows, conflicts split into
  existing-id and duplicate-in-file, default re-identification, explicit replacement only, and a
  re-check at commit so a conflict that appeared after the preview is still handled. The batch runs
  in one SQLite transaction; a failure or an over-limit batch changes nothing.
- **Export selection.** Export writes exactly the selected presets in the v1 document format; the
  stop policy now travels in that document, validated strictly on read.

The host tests run the production schema and SQL through a real SQLite engine (sqlite-jdbc, a
test-only dependency), so migrations and transactions are tested without an emulator; the phone run
covers the Android adapter itself (`SQLiteOpenHelper`, cursor mapping, the transaction boundary).
Four connected tests pass on the device.

Backup rules: an export is a `.earpreset` JSON file written through the Storage Access Framework;
the chosen provider may itself be cloud-backed, which the app neither controls nor requires. The
database is app-private (`presets.db`); clearing app data removes user presets and re-seeds the
built-in starters. The app still ships no INTERNET permission.

Evidence: `.local/verification/wp4.4/check-full-final.log` (exit 0; it runs `check-fast.sh` first)
and `.local/verification/wp4.4/device-test-results.xml` (4 tests, 0 failures).

**WP4.3a (the setup reaches the session)** closes the gap that made a setup UI impossible: the
packaged service built every Start from the built-in defaults, and the session never applied the
request's seed, recipe or gains. What changed:

- `StartRequest` carries the selected `RecipeDefinition` and the music/voice gains. On an accepted
  Start the controller configures the exercise source (definition, seed, recipe) and the audio port
  (gains) before any item is drawn.
- The production source is `ConfiguredIntervalExerciseSource` in core:training, host-tested: its
  sequence equals a directly constructed seeded generator, a new Start restarts the sequence, and
  timelines compile under the accepted recipe.
- `LastSetup` gained note/answer-gap/repeat-gap durations plus `toRecipe()`/`toStartRequest()`.
  Settings and presets persist them; the SQLite store moved to schema v2 with an explicit
  `ALTER TABLE` migration, and the frozen-v1 test now upgrades real rows and checks the defaults.
- The app composes one settings store and one preset store in a new `PassiveEarTrainingApp`
  (manual composition, because Hilt is blocked). The service's Start reads the cached settings and
  draws a fresh seed; a Start before the settings flow emits uses the product defaults.
- `RecipeExerciseCompiler` fixes found while wiring it: an example's step window was one note long
  while two notes were scheduled, so the answer could start while the example still sounded; a
  harmonic example was rendered as two melodic notes; and the compiled timeline had lost the
  `AnswerStartedEvent` marker. The layout is now presentation-aware, the 100 ms melodic gap is
  restored to the reviewed default, and every template emits exactly one answer marker.

Known remaining for WP4.3: the UI screens and the Navigation 3 pin decision; the domain behind
every control now exists.

Evidence: `.local/verification/wp4.3a-check-full.log` (exit 0; it runs `check-fast.sh` first and
the device suites).

**WP4.3b (a fixed note through the configuration layer)** completes the advanced panel's domain
support. `LastSetup` gained an optional fixed root and `toPolicy()`; a fixed root makes the policy
version 2, so the draw order cannot silently fall back to version 1. `SetupDraft` and
`SetupValidator` report a fixed note that is outside the range, does not match a selected root
class, or cannot play an interval in the chosen direction, each against the fixed-note field.
`StartRequest` now carries the whole `SequencePolicy`, and `ConfiguredIntervalExerciseSource`
selects the version 1 or version 2 generator from the policy itself. Settings re-check a stored
setup through the policy, so an unusable fixed note falls back with a diagnostic; presets carry
`fixed_root` in the v1 document, and the SQLite store moved to schema v3 with the frozen v2 step
and a v2-to-v3 upgrade test. Evidence: `.local/verification/wp4.3b-check-full.log` (exit 0, phone).

**WP4.3c (navigation shell and screens)** implements the Material 3 flow on Navigation 3 1.0.1. The
pin is recorded: 1.2.0 would pull Compose 1.10.0 above the pinned BOM 1.9.0, while 1.0.1 pulls
Compose 1.9.5, a patch inside the same line. What landed:

- Typed, serializable destinations and a saveable back stack; a bottom bar for Train, Presets and
  Settings, with Setup and Player pushed as their own destinations.
- Train starts from the saved setup and shows it; Setup edits the basic controls (intervals,
  presentation, mode, stopping, thinking, repeats, gains) and the advanced panel (root classes,
  range, note/gap lengths, fixed note), rendering every shared-validator issue with its field.
- Player owns transport and the sleep timer. The MediaController connection now lives in a
  ViewModel, and no screen names an answer before it has been consumed.
- Presets lists the starters and user copies with use, duplicate and delete (import/export still to
  come); Settings edits theme, dynamic color, spelling, answer display and gains, with the explicit
  reset and the local-data description.
- The theme follows the stored preference, and the advanced-panel expansion preference persists.

Implementation notes: Hilt remains blocked, so the application composes the repositories manually.
The controller connection is released when its ViewModel clears, so a screen leaving never stops
playback. ViewModel host tests now run inside check-fast, and the app's unit-test configuration is
part of the dependency-recording script.

**WP4.3d (preset import/export through the document picker)** completes the WP4.4 UI flow. The
Presets screen exports a checked selection through `CreateDocument` and imports through
`OpenDocument`; the read is bounded to 1 MiB plus one byte regardless of what the provider reports.
An import is staged first: the dialog lists every entry, shows per-preset rejections with their
reason, and offers an explicit "replace the stored preset" choice for each conflict; without that
choice a conflicting id keeps a new identity. The commit runs atomically through the store.

**WP4.3e (mini-player and accessibility pass)** adds the one-line live session summary above the tab
bar, so pause and open-player work without leaving the current tab, and the first accessibility
sweep: a minimum 48 dp target on the summary, TalkBack labels for every slider, navigation icons
kept out of the announcement so each tab label is read once, and no answer text on any surface. The
TalkBack and 150%/200% font-scale pass on the phone, and the Roborazzi baselines, need the device
owner and stay explicitly open. Evidence: `.local/verification/wp4.3e-check-full.log` (exit 0,
phone).

**WP4.1's remaining generator work** is implemented. `SeededIntervalGeneratorV2` preserves the
version 1 shuffle exactly and adds the specified draw order: after the bag label, one presentation
from the sorted enabled set via `nextInt` (even for a singleton), then one feasible pair for that
label and presentation. An optional fixed root narrows the pair list; every label × presentation
combination must be feasible or the policy is rejected before the first draw. The identity records
the enabled presentations and the fixed root, and `IntervalExercise` now carries the concrete
presentation so one mixed sequence can vary it. A recorded golden fixture pins the version 2
sequence, and `SequencePolicy.generatorVersion` correctly reports 2 for a fixed root even with one
presentation. Evidence: `.local/verification/wp4.1b-check-fast.log` (exit 0).

## Stage 3 recorded progress (before this integration review)

**WP3.3 (focus, routes, power)** and **WP3.4 (sleep timer)** are implemented; the device gate passes
with 4 connected tests. Platform facts worth recording:

- Requesting focus needed the app to be visible or foreground, so focus is requested as the session
  enters Playing, and a denial pauses with a recoverable reason instead of playing over another app.
- `SessionCommands` are negotiated per controller in `onConnect`. The first attempt also narrowed the
  player's commands, which removed play/pause from the advertised surface; only session commands are
  narrowed now, so configuration cannot change transport.
- The sleep timer keeps no wake lock and uses no alarm: it re-checks its deadline before every
  command and before any sound, so a session that is already silent stays silent even if a platform
  callback is late. Deadlines are elapsed realtime, so device sleep counts and wall-clock changes do
  not.


**WP3.2 (media service)** is implemented. `core:media` holds the service, the player and the mapping;
the app composes the real audio pipeline (bundled SoundFont, voice pack, native renderer, device
output) behind an `AudioPipelineFactory` seam, and the screen now holds only a `MediaController`
connection, so leaving or rotating a screen never stops an active session.

Notable decisions and findings:

- Media3's own manifest contributes `ACCESS_NETWORK_STATE`. The product is offline by contract, so
  the merged permission is removed with `tools:node="remove"` and `scripts/check-apk.sh` now fails on
  **any** network permission rather than only `INTERNET`.
- Serialization in `DefaultSessionController` moved from a channel to single-thread confinement: a
  released session could still receive a late command (a disconnecting screen, a destroyed service)
  and threw instead of reporting unavailability. Commands now return an unavailable result after
  release, which is covered by a test.
- `scripts/check-full.sh` did not rebuild the debug APK before installing it on the device, so the
  device step could test stale sources. The gate now builds the debug APK explicitly.

## Stage 3 original controller report

**WP3.1 (session state and exercise queue)** is implemented in `core:training` with no Android
dependency. The controller owns one coroutine, so commands cannot interleave and an asynchronous
render or write result cannot act on a newer session. Queue rules are enforced and tested: one item
generated ahead, at most 20 retained definitions, Repeat and backward navigation draw no randomness,
and moving back then forward traverses retained history rather than regenerating.

Two decisions are worth recording:

- The session reads its position from the audio port rather than tracking it internally, because the
  device is authoritative. Tests therefore state a position through the fake instead of simulating a
  device, which keeps every assertion about the session.
- Turbine was considered for Flow testing and deliberately not added: the tests assert on
  `StateFlow` values, and an unused dependency would only add verification surface.

A defect found while writing the tests is recorded in the defect log: a fake audio port that always
completed its write made the controller treat every write as interrupted.

## Defects found and fixed during Stage 2

The two most serious defects of the whole stage were found only when a physical phone ran the
engine, and neither was reachable from host or emulator work:

6. **Every bundled voice clip failed to decode on Android.** miniaudio returns `MA_AT_END`, a
   success code, when the decoder reaches the end of a file; the decode loop treated any
   non-`MA_SUCCESS` result as fatal, so decoding a clip always failed with "wav decode or
   conversion failed". On a device this made every exercise unplayable while all host tests passed,
   because the host fake supplies samples and the host harness does not decode voice clips. The loop
   now treats `MA_AT_END` as a normal end and only other results as errors.
7. **`NativeAudioFiles` never loaded the native library.** Only `MusicRenderer.create()` did, so any
   WAV decode or WAV write before a renderer existed failed with `UnsatisfiedLinkError`. The native
   library is now loaded through a single `MusicRenderer.ensureLoaded()` used by both.
8. The diagnostic screen polled the device position from the UI on a 250 ms timer, which starved the
   Compose test framework of idle frames and made UI-driven device tests hang instead of failing.
   The ViewModel now samples the device on its own worker and pushes state; the UI only reads it.
   Device tests for the screen use UiAutomator, which drives the app the way a person does.


1. `SimpleInterval`-style wording aside, the native renderer initially compared `void`-returning
   FluidSynth calls against `FLUID_OK`, and the Android build set `-fno-exceptions -fno-rtti`,
   which FluidSynth 2.6.1 requires. Both were caught by the first native build.
2. The RIFF writer in the SF2 subsetter emitted an odd-sized `INAM` chunk, which makes FluidSynth
   reject the file as structurally defective.
3. `ExerciseRenderer` scheduled notes only when a note started exactly on a block boundary, so a
   note beginning mid-block was skipped entirely (its start frame was never rendered). The loop is
   now boundary-driven: it stops at every event boundary and never renders more than one block per
   call, which keeps note starts and ends frame-exact.
4. The asset review gate `scripts/check-assets.sh --require-reviewed` returned success while a
   review was pending, because the flag was passed to the wrong validator.
5. `ExerciseOutputWriter` re-sent audio after a short device write: the retry passed the whole
   block again instead of continuing after the frames already accepted, so the same audio would
   be queued two or three times. The output contract now carries a sample offset and the retry
   advances past what the device took; a test asserts the device receives every frame exactly
   once and in order.

Several test-side defects were also found and fixed rather than weakening checks: a test recipe
helper that silently produced zero-duration gaps and a zero-length voice clip (Int division before
widening to `Long`), a fake renderer that reported scheduling frames one block late, and three
expectations that contradicted the cache's documented replacement semantics. Two of those
"failures" were the tests being wrong about the product contract, which is recorded here so a
later reader does not re-derive it.

## Defects found and fixed during Stage 1

Three real defects were found by tests and fixed in production code rather than by weakening
expectations:

1. `MidiNote.pitchClass` returned the raw MIDI value instead of the pitch class, so any note
   outside 0..11 threw during construction of its own pitch class.
2. `SimpleInterval.ofId` returned the list index instead of the semitone count, so identifiers
   such as `perfect_fifth` mapped to the wrong interval. Every bag order and pair count was
   affected; the fix is covered by the interval table test and the generator fixtures.
3. `SeededIntervalGenerator` enumerated descending examples from an *out-of-range* root, so a
   descending fifth could pick root 75 in a 60..72 configuration. The generator now enumerates
   roots inside the configured range and validates the target as well.

Also corrected: the Spotless `kotlin` target used `core/**/src/**/*.kt`, which made Spotless
depend on compiled module outputs; it is now `core/*/src/**/*.kt`.

Deliberate hardening beyond the package text: `IntervalDefinition` exposes unmodifiable
collections, so a caller that tries to mutate a definition fails with
`UnsupportedOperationException` instead of silently changing it.

## Stage 2 implementation evidence

### Phone listening review (first pass) and what it changed

The device owner listened on the phone and reported: the sequence note -> real silence -> spoken
answer -> silence is correct; the middle silence was a little long; the voice was too loud or the
piano too low; Start worked only once and then failed or appeared to hang; Pause did not pause
(though mid-note pause is not wanted).

Each item was measured and fixed:

| Report | Finding | Change |
| --- | --- | --- |
| Middle silence too long | default thinking silence was 4 s | default is now 2 s, and the timings live in a named `RecognitionRecipe.PROFILES` table (`spacious` 4 s, `default` 2 s, `quick` 1 s) that a future settings screen can present and persist |
| Voice too loud / piano too low | measured: the spoken clip is **12 dB above** a rendered piano note | a voice gain was implemented (the plan always had separate music and voice gains; only the music side existed). Default 0.25 (-12 dB), independent of the music gain and tested for independence |
| Start worked only once | `start()` refused an open session and `stop()` left playback finished, so a repeat Start either errored or replayed from a fully consumed position | `start()` is re-entrant, `play()` restarts a finished exercise, `stop()` keeps the session usable. Covered by unit tests and by an on-phone repeat-Start assertion |
| Preparing stuck until playback finished | `start()` prepared, rendered and wrote the whole exercise as one blocking command | preparation and playback are separate steps, so Ready is published before the output is written and the display moves Preparing -> Ready -> Playing |
| Play/Resume reported an error | redundant presses were surfaced as failures | a press that is not valid in the current phase is ignored rather than reported |

The reviewing owner also asked that the silence length stay user-adjustable, which is why the
profiles table exists; a user-facing control over these values belongs to the settings work.

### Pause semantics (agreed with the device owner)

Pause during a pair is acknowledged immediately ("Pause waits for the current interval pair to
finish.") and lands as soon as the write loop stops, which is why the paused position can sit
inside a note. Resuming replays the pair from its beginning. The owner reviewed this and accepted
it as a good behaviour, so it is the documented contract rather than a defect: resuming a partially
played interval pair would be a worse experience than restarting it.

Three defects had to be fixed for this to work at all, all reported from the phone:

1. the pause request was queued behind the blocking write, so it arrived after the exercise;
2. AudioTrack resets its playback head on flush, so the consumed position read zero and the UI
   waited forever;
3. the completion signal was "all frames consumed", which an interrupted write never reaches.

They are fixed by requesting the pause off the worker, accumulating the position across flushes, and
keying completion on the writer stopping rather than on the device position.

### Second phone listening pass

The owner reported three further items, two of which were real defects:

9. **Every exercise was the same interval** unless the seed field was edited by hand. The cause was
   a wiring mistake rather than the generator: the controller's "fresh seed" callback was
   constructed as `{ seedText.toLongOrNull() ?: 42 }`, reading the very field that displays the
   drawn seed. Every draw therefore returned the value the field had just been given, so the proof
   replayed one exercise forever while the generator, the seeds and the draw logic were all correct
   (a device test shows six distinct exercises from six draws). The controller now owns the draw,
   and the field only displays it. Verified on the phone: three consecutive Starts produced three
   different seeds.
10. **Pause did not stop anything mid-exercise.** `play()` writes the whole exercise with blocking
    writes, so a pause press queued behind the write loop and took effect only once every frame had
    been handed to the device. Both reported symptoms - "does nothing" and "pauses after the pair" -
    were this one cause. The owner also said mid-note pause is not required.

Agreed pause behaviour: a press during playback is recorded and applied at the end of the current
interval pair, so a note is never cut short. The screen shows that a pause is waiting. This is
deliberate: an exercise is one indivisible timeline, and the boundary-level pause the owner
described belongs to the exercise queue rather than to a single exercise.



The WP2.4 engine test passes on the phone in 9.196 s. Measured: font staged 0.004 s, all assets
0.020 s, engine prepare (load the 206 MiB font and render the first exercise) **0.785 s**, track
accepted 48000 Hz mono with a 7688-frame buffer, 409657 frames written and 403200 played,
**0 underruns and 0 short writes**. The same test on the API 36 emulator did not reach the ready
phase within 15 minutes, which is the recorded reason the emulator is unusable for this operation.

| Item | Outcome | Evidence |
| --- | --- | --- |
| WP2.1 voice pack | 13 English answer clips generated locally with Chatterbox on CPU, postprocessed to mono 48 kHz PCM16 and bundled with a schema v1 manifest | `app/src/main/assets/voice/voice-pack.json`, `app/src/main/assets/voice/wav/`, `tools/voice-assets/` |
| WP2.1 asset tooling | Deterministic read-only validators for the voice manifest and the SoundFont descriptor, with 26 Python tests | `tools/audio-analysis/validate_assets.py`, `validate_soundfont.py`, `speech_metrics.py`, `tests/test_validate_assets.py`, `scripts/check-assets.sh` |
| WP2.2 native renderer | Pinned FluidSynth 2.6.1 plus its two submodule dependencies and miniaudio, built from a staged source manifest with a reduced feature set | `tools/native/sources.json`, `scripts/fetch-native-sources.sh`, `core/audio/src/main/cpp/` |
| WP2.2 JNI/Kotlin owner | Typed error codes, checked JNI surface, Kotlin `MusicRenderer` with idempotent `close` and use-after-close failure | `error.h`, `renderer.h/.cpp`, `wav_io.h/.cpp`, `jni_library.cpp`, `core/audio/src/main/kotlin/.../MusicRenderer.kt` |
| native check | `scripts/check-native.sh` (wired into check-fast) | See the check table below |
| WP2.4 diagnostic proof | Debug-only screen over a serialized controller: generate, play, pause, resume, repeat, stop, reseed, with seed/configuration/exercise/voice/timeline/output-metric readouts and an explicit Error state. The release variant compiles a no-op entry point, so no diagnostic code ships | `app/src/debug/kotlin/.../`, `core/audio/src/main/kotlin/.../DiagnosticAudioSession.kt`, `ForegroundDiagnosticSession.kt`, `AssetPreparer.kt`; `DiagnosticControllerTest` |
| WP2.4 device evidence | The diagnostic screen and its full command surface are verified on the API 36 x86_64 emulator; the engine end-to-end test is marked LargeTest | `app/src/debugAndroidTest/.../DiagnosticsDeviceTest.kt` |
| WP2.3 output seam | Device output contract with an Android `AudioTrack` adapter, a cancellation-aware writer that handles short writes and device loss, resume ramping, and position/metrics accounting, tested with a fake output | `core/audio/src/main/kotlin/.../pcm/AudioOutput.kt`, `AudioTrackOutput.kt`; `ExerciseOutputWriterTest` |
| WP2.3 frame/cache layer | Frame-exact timeline conversion, bounded current/next PCM cache and block-bounded render scheduling, with 21 JVM tests in core:audio | `core/audio/src/main/kotlin/.../pcm/`, `core/audio/src/test/kotlin/.../PcmPipelineTest.kt` |
| WP2.3 host proof | The host harness renders seven fixtures with the production renderer sources and the bundled font - C4, A4, G4, ascending C4-G4, descending G4-C4, a harmonic third and a unison - and exports frame-marker sidecars. All seven pass the numeric checks: exact frame count, finite and unclipped, no audio before the first note or past the declared end plus a bounded release, declared gaps silent, single-note pitch within 15 cents, and one marker pair for the unison | `.local/verification/wp2.3-wav/`, `scripts/check-native-tests.sh`, `tools/audio-analysis/check_render_fixtures.py` |
| host harness | `core/audio/src/main/cpp/host_render_main.cpp` builds as an optional host target from the same renderer sources and writes WAV fixtures for numeric analysis | `scripts/check-native-tests.sh` (see below) |

Measured generation facts (host, CPU only, no GPU used): model load 7.19 s, three-phrase batch
33.2 s, peak RSS 6.9 GiB, torch 2.6.0 with 10 threads. The full 13-phrase run plus
postprocessing completed well inside the WP2.1 CPU budget; no Piper fallback was needed.
Generated clips are 0.70-1.06 s, 48 kHz mono, no clipped samples, RMS 0.088-0.095, active
speech fraction 0.70-0.79 - numeric sanity only, not a listening result.

Verification run on 2026-10-07 (host, project JDK 17, strict dependency verification):

| Check | Command | Result |
| --- | --- | --- |
| Fast verification suite | `scripts/check-fast.sh` | PASS: Python guards, audio-tool tests, static boundaries, Spotless, all JVM tests, debug assembly/lint, native packaging (log: `.local/verification/check-fast-stage2.log`) |
| Native packaging | `scripts/check-native.sh` | PASS: `libear_audio.so` for arm64-v8a and x86_64, no unexpected shared dependency, LOAD align 0x4000, zipalign 16 KiB clean |
| Strict build of the new module | `./gradlew --dependency-verification strict --offline :core:audio:assembleDebug :core:audio:assembleDebugAndroidTest` | PASS |
| Asset validators | `scripts/check-audio-tools.sh` | PASS: 26 tests |
| Asset bundle | `scripts/check-assets.sh` | PASS: 13 voice entries and the piano descriptor validate; size waiver and reviews reported as PENDING |
| Host render fixtures | `scripts/check-native-tests.sh` | PASS: 3 fixtures rendered and numerically checked (log: `.local/verification/check-native-tests.log`) |
| Dependency checksums | `scripts/record-dependencies.sh` | PASS: 799 artifacts, no trusted-artifact exclusions (4 new entries for the Android library plugin and test libraries) |

`core:audio` is built but not yet consumed by `app`; that happens in WP2.4 when the diagnostic
proof screen exists. The device conformance tests in
`core/audio/src/androidTest/.../AudioRendererTest.kt` are written but cannot run until a device
is attached; the note/chord assertions additionally require the reviewed SoundFont.

Python environments are separate locked `uv` projects: `tools/voice-assets` (Python 3.11,
Chatterbox 0.1.7, numpy <2, pinned setuptools for the upstream watermarker) and
`tools/audio-analysis` (Python 3.11, numpy/scipy/soundfile/jsonschema). Machine-local caches
(`.local/uv-cache`, `.local/uv-python`, `.local/huggingface`, `.local/native-sources`) stay
untracked.

## Prepared scaffold (unchanged from Stage 0)

- Git initialized on main, no initial commit; current work is untracked.
- Compose app shell and pure Kotlin music/training module boundaries.
- Version catalog and official Gradle 9.6.0 wrapper with distribution SHA-256.
- Project JDK 17; SDK platform 36; Build Tools 36.0.0; NDK 28.2.13676358;
  SDK CMake 3.22.1, emulator and API 29/36 system images.
- Studio installed at $HOME/Applications/android-studio.
- Environment/bootstrap/dependency/verification scripts.

## Preparation verification evidence (Stage 0, not rerun during Stage 1)

| Check | Recorded result |
| --- | --- |
| Dependency recording | Passed; 795 artifact checksum entries; no blanket trusted-artifact exclusions |
| Debug/release/test APK compilation | Passed |
| Python guard tests and static module boundaries | Passed |
| NDK compiler probes | Passed for arm64-v8a and x86_64 |
| Packaged APK offline policy | Debug/release APKs have no INTERNET permission |
| API 29 and API 36 emulators | Installed/launched; connected test passed |
| Visual shell review | API 36 light/dark and 150% text reviewed |

Existing local evidence lives under .local/verification, including connected results,
installation/test logs, clean-source build log and shell screenshots. Those screenshots are
not regression baselines.

## Historical gate ledger

| Gate | State | Missing evidence |
| --- | --- | --- |
| G0-host | PASS, preparation evidence | None for scaffold/JVM development |
| G0-device | **PASS** (2026-10-09) | ASUS_AI2302 (Zenfone 10), Android 14 / API 34, arm64-v8a, 4 KiB pages. Install and launch verified; engine end-to-end passes in 9.2 s with 0 underruns. Evidence: `.local/verification/g0-device/device-report.md` |
| G0-IDE | **PASS** (2026-10-09) | Import repaired, `temurin-17` Gradle JDK and `.local/gradle-home` service directory recorded, sync `onSuccess`, 24 IDE modules, 36 dependencies, `app` run configuration, Kotlin inspections and run points usable. Evidence: `.local/verification/g0-ide/` |
| G1 | PASS on host | WP1.1-WP1.3 implemented; 81 JVM tests pass; check-fast passes. No device or human evidence is required for G1 |
| G2 | **PASS** (2026-10-09) | Assets accepted (13/13 answers, piano at music gain 1.0). Engine verified on the phone: prepare 0.785 s, 0 underruns, 0 short writes. The device owner listened through the fixed build and confirmed the sequence (note -> real silence -> spoken answer -> silence), the balance, varied intervals per Start, and Pause/Resume. Evidence: `.local/verification/g0-device/device-report.md`, phone tests in `app/src/debugAndroidTest/` |
| G3 | **PARTIAL; host repairs implemented; owner timer pass recorded** | WP3.1a/3.3a/3.4a implemented and host-verified; the owner confirmed a locked-screen session with a 5-minute timer stopped on time and stayed silent. Missing: wired/Bluetooth unplug, pause/Next/Previous on the phone, battery saver, swipe/relaunch, and the full WP3.5 report |
| G4 | **OPEN; substantial implementation** | Screens, settings/store/import/export/mini-player exist. Missing WP4.1c configuration, WP4.2a persistence/limits, WP4.3f screen state, WP4.4a named presets/document completion, WP4.3g regression harness/review and WP4.5 physical daily-use acceptance; requires G3 PASS |
| G5–G7 | NOT STARTED | Provider expansion, advanced sequencing, history/adaptation |
| G8 | NOT STARTED | Final device matrix, signed release, upgrade/restore and key backup |

G2 has the recorded 2026-10-09 owner listening pass. G3/G4 remain incomplete for implementation
and acceptance reasons. Current cumulative counts and reopened findings are at the top of this file.

### Historical component report snapshots

These rows preserve the original per-package results/counts. They are not present-day
claims of complete production wiring and do not override the current gate ledger.

| Recorded component | Reported outcome | Evidence at that package |
| --- | --- | --- |
| WP4.2 settings model | Immutable `UserSettings` and `LastSetup` with validation, a versioned schema, a `SettingsSnapshot` carrying recovery diagnostics, and a `SettingsRepository` whose updates are described as a change rather than a read-modify-write. 8 host tests | `core/training/.../settings/UserSettings.kt` |
| WP4.2 storage | `core:data` with a `DataStore`-backed repository: atomic updates applied inside the transaction, a versioned schema, per-field validation with reported fallbacks, a newer-schema diagnostic instead of silent overwrite, an explicit reset, and an unusable stored setup rejected rather than loaded. 10 host tests against an in-memory store | `core/data/.../DataStoreSettingsRepository.kt` |
| WP4.3 validation | `SetupValidator` and an unvalidated `SetupDraft`: the UI and the domain share one rule set, every issue names the field it belongs to and explains itself in the user's terms, all problems are reported at once, and an infeasible interval/range/direction combination is refused before Start. 11 host tests | `core/training/.../settings/SetupValidator.kt` |
| WP4.4 preset documents | `PresetDocumentV1`/`PresetPayloadV1` with stable field names and explicit units, seeds exported as signed decimal strings so a 64-bit value cannot be rounded by a JSON reader, strict decoding that rejects unknown fields and future schemas, a 100-preset and 1 MiB bound, and import-time validation including musical feasibility. 13 host tests | `core/data/.../preset/PresetDocument.kt` |
| WP4.4 import preflight | Jackson Core streaming preflight with `STRICT_DUPLICATE_DETECTION`, a nesting bound of 32 and a string bound of 16 KiB, run before the typed decode so a malformed document cannot reach it. 7 host tests, including duplicate keys, deep nesting, truncated and trailing content | `core/data/.../preset/PresetPreflight.kt` |
| WP4.4 blocker: Room 2 | **SETTLED as infeasible on this toolchain.** KSP is required and the newest KSP targets Kotlin 2.2.21, while AGP 9.4's built-in Kotlin is 2.4.20. Disabling built-in Kotlin and applying `kotlin-android` 2.2.21 fails outright: `ApplicationExtensionImpl cannot be cast to BaseExtension`, so a KSP-compatible Kotlin cannot be applied against AGP 9. Room would need both AGP and Kotlin downgraded, which is out of scope for this package. The probe was run and reverted; the toolchain is back on the verified 2.4.20/AGP 9.4 pair. Recorded as a decision with its evidence rather than left open | probe reproduced 2026-10-09; KSP latest 2.3.12 |
| WP4.4 preset store | Plain SQLite with hand-written migrations instead of Room; CRUD with UUID identities and a 100-editable-preset bound; immutable built-in starters stored under `built_in = 1`; per-entry import preview with existing-id and in-file conflicts; default re-identification and explicit replacement; single-transaction commit with rollback; selected export. 83 host tests in core:data and 4 connected tests on the phone | `core/data/.../preset/PresetSchema.kt`, `SqlitePresetRepository.kt`, `PresetStarters.kt`, `AndroidPresetDatabase.kt`; `core/data/src/androidTest/.../AndroidPresetStoreDeviceTest.kt` |
| WP4.2 blocker: Hilt/KSP2 | **BLOCKED** on a toolchain incompatibility, not skipped. KSP's latest release is 2.3.12, and no KSP release exists for the project's Kotlin 2.4.20, so Hilt's KSP2 path cannot be added without changing the Kotlin/AGP toolchain, which the project rules forbid incidentally. Recorded with the version evidence; the settings layer therefore needs no code generation, and dependency injection is deferred until a compatible KSP exists | verified against Maven Central metadata, 2026-10-09 |
| WP4.1 recipes and controls | `RecipeDefinition` with five named templates (recognition, exposure, association, deep repetition, answer-first recall), a step-based compiler with duration preview and cap validation, `SequencePolicy` with version 2 mixed presentation and fixed roots, and one selected stop policy (duration/count/cycle) with a bounded drain. 27 host tests | `core/training/.../recipe/` |
| WP3.5 device acceptance | `scripts/device-smoke.sh` runs the repeatable reliability rows on one device, records asset hashes, the packaged service type and permissions, and prints a 12-row human checklist. Amber rows are reported UNTESTED, never skipped | `scripts/device-smoke.sh`, `.local/verification/device-smoke/report.md` |
| WP3.3 focus, routes, power | Pure `InterruptionPolicy` and `WakeLockPolicy` with 12 host tests, plus `AudioFocusCoordinator` (request before sound, keep across transient loss and ducking, abandon on permanent loss, `ACTION_AUDIO_BECOMING_NOISY`) and `WakeLockCoordinator`. The service applies them from session phases, so acquire and release cannot drift | `core/media/.../InterruptionPolicy.kt`, `FocusAndPower.kt` |
| WP3.4 sleep timer | `SleepPolicy`, `SleepTimerState`, `StopReason` and `SleepTimer` with 15 host tests on virtual time: immediate and finish-current expiry, fade clamped to duration and to 60 s, sticky silence after expiry, replacement deadlines, and cancel restoring the unfaded gain. A controller command arms it, validated and bounded | `core/training/.../timer/SleepTimer.kt`, `core/media`, app screen timer control |
| WP3.2 media service | New `core:media`: a `MediaSessionService` that owns the session, the player, the MediaSession and the audio lifetime, a `SimpleBasePlayer` advertising only supported operations, and a snapshot-to-player mapping with 12 host tests. The app connects through a `MediaController`; the temporary activity-owned proof is retired | `core/media/`, `app/src/main/kotlin/.../app/TrainingPlaybackService.kt`, `SessionScreen.kt` |
| WP3.2 device evidence | 3 connected tests on the phone: the launcher shows the training screen, a controller connects to the packaged service and reports play/pause without seek or speed, and metadata never names an answer before it is consumed | `app/src/androidTest/` |
| WP3.1 session state and queue | Immutable snapshot contracts, a command surface, and a controller that serializes every command on one coroutine; lazy one-ahead generation, bounded history, navigation that never redraws, position from consumed audio, cumulative played duration, answer visibility on consumption. 17 tests with a fake audio port and virtual time, no sleeps | `core/training/src/main/kotlin/.../session/`, `SessionControllerTest` |

## Historical remaining preparation actions

1. Complete Studio import/readiness and inspect .local/android-sdk / .local/jdk-17 settings.
2. Before the next physical run, reconnect/confirm the already recorded phone's authorization,
   select ANDROID_SERIAL and record the exact build/device/configuration. Do not rerun acceptance
   merely to make this documentation review appear tested.
3. Review untracked source/ignore rules/dependency checksums before an authorized
   initial commit. Do not replace protected Git metadata.

Emulator results cannot close a physical audio/background gate. Missing devices,
human voice review or signing-key backup must remain explicit acceptance items.
