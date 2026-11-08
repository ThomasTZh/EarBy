# G8 release evidence

Fill this file after the release candidate is frozen. Do not include device serials or personal
paths.

## Build

- Source commit: V4 tree at `81fceb1`; published 2026-11-08 as `ca315c9` with the public
  `v1.0.0` tag moved to that commit. A later documentation commit requires a republish and a tag
  move.
- Version: 1.0.0 (100)
- Package: `com.earby.app`
- Icon: hand-drawn by Dr Ham Hamster, used with her permission; launcher/store assets generated
  from `icons/EarByIconV6.png`
- AAB: `dist/play/EarBy-1.0.0.aab`, 30,240,377 bytes, SHA-256
  `63e458398eb87e32785b0d2e932c2050c60f0421d9eefae622cae0a90e5b65bf`, upload-key signed.
- APK: `dist/release/EarBy-1.0.0.apk`, 35,465,948 bytes, SHA-256
  `eb983c34c4dc804a793799d587eb76753c1a818aa08c0c4c043cc2d868bdec36`, upload-key signed. The
  minified release APK hash can vary between runs (R8/packaging); this is the rebuilt artifact
  with the JNI keep rules.
- Upload certificate SHA-256:
  `1d48d8d0bc148a1314f377dfcadfd9a2ec01feb530db2bb57dc98b6e6f5bb9d5` (verified on the AAB and APK).
- Minified release build: PASS (`:app:assembleRelease`)
- `scripts/check-release.sh`: PASS on `dist/play/EarBy-1.0.0.aab` and
  `dist/release/EarBy-1.0.0.apk` with `EARBY_UPLOAD_CERT_SHA256` set (2026-11-08)
- Full QA: PASS; private QA evidence is not part of the public snapshot
- Host tests: 1123/0
- Review device: 63/63 review-short, data 5/5, audio 9/9, force-stop 1/1 before and after
- 30-minute endurance: PASS, 1821 s, PSS variation 27,419 KiB <= 65,536 KiB
- Preset export/import: PASS (all filled slots, validation/preview/commit tests)
- Upload-key-signed release: PASS (AAB and APK; hashes above)

## Physical matrix

| Scenario | Device/API/route | Result | Evidence |
| --- | --- | --- | --- |
| 30-minute screen off | ASUS_AI2302, API 35, arm64-v8a, 4 KiB pages | PASS (1821 s, 0 crash/ANR) | private QA record |
| Release clean install/launch | ASUS_AI2302, API 35, arm64-v8a, 4 KiB pages | PASS (home Ready) | `t27b/10-home.png`, `t27b/11-home.xml` |
| Release start/playback | same device | PASS (PLAYING, 48 kHz AudioTrack, 0 crash/ANR) | `t27b/12-media-session-playing.txt`, `t27b/13-audio-playing.txt`, `t27b/15-playing.png`, `t27b/16-play-logcat.txt` |
| 2-hour locked mixed playback | same device, screen off | PASS (7200 s, 25 samples, 0 crash/ANR, same media session) | `t27b/51-lock-start.txt`, `t27b/samples/timeline.tsv`, `t27b/55-media-session-after-2h.txt` |
| Bluetooth controls | ASUS_AI2302 | PASS (owner-reported, 2026-11-08, physical device) | owner observation |
| Earbud removal | ASUS_AI2302 | PASS (owner-reported, 2026-11-08, physical device) | owner observation |
| Incoming call/focus loss | ASUS_AI2302 | PASS (owner-reported, 2026-11-08, physical device) | owner observation |
| Battery saver | same device, `low_power=1`, battery unplugged | PASS (PLAYING, 0 crash/ANR) | `t27b/40-battery-saver-state.txt`, `t27b/41-media-session-saver.txt`, `t27b/42-battery-saver-playing.png`, `t27b/43-battery-saver-logcat.txt` |
| Process death/relaunch | same device | PASS (force-stop -> relaunch Ready; Presentation=descending retained) | `t27b/30-before-force-stop.xml`, `t27b/31-after-relaunch.xml`, `t27b/32-relaunch-retained.png` |
| Notification/lock-screen media controls | same device | PASS (notification and lock-screen cards; pause -> STOPPED, play -> PLAYING) | `t27b/20-notification-shade.png`, `t27b/22-lock-screen.png`, `t27b/24-media-session-paused.txt`, `t27b/26-media-session-resumed.txt` |
| API 29 | AVD `ear-api29` exists (x86_64, API 29) but host has no KVM and AVD writes outside the workspace are denied | BLOCKED (host capability) | `t27b/60-emulator-blockers.txt` |
| API 36 | AVD `ear-api36` exists (x86_64, API 36) but host has no KVM and AVD writes outside the workspace are denied; software start failed with a pending-snapshot lock | BLOCKED (host capability) | `t27b/60-emulator-blockers.txt` |
| 16 KiB page runtime | physical ASUS page size 4096 B; no 16 KiB AVD/system image available | BLOCKED (no 16 KiB runtime) | `t27b/60-emulator-blockers.txt` |

## Install, update and restore

- Clean install: PASS. The previously installed test-signed (debug-key) `com.earby.app` was
  uninstalled first; Android deleted `/data/user/0/com.earby.app`, so all settings, presets,
  statistics and staged assets were lost. The upload-key release installed and launched; the
  direct update path is blocked by the signing-certificate mismatch (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
- 1.0.0 → 1.0.1 update: not exercised here; the release is a clean install after the required
  debug/test-signature uninstall, so there is no 1.0.1 artifact yet.
- Settings and presets retained: PASS across process death/relaunch (Presentation changed to
  descending, force-stop, relaunch still descending). NOT retained across uninstall, by Android
  package semantics; this t27 install therefore starts from default settings.
- Preset export/import: implemented in the Presets screen with versioned JSON, preview/commit and tests.
- Upload-key backup confirmed by owner: yes, on 2026-11-08 the upload keystore and its password
  were backed up in two secure places.

## Release JNI crash resolved before this run

The earlier signed artifact (SHA-256 `bfcdf8b8...`) aborted on Start because R8 renamed
`NativeAudioException` while `JNI_OnLoad` looked it up by name. The fix is in `f634502`
(`app/proguard-rules.pro` keeps both exception classes; `jni_library.cpp` clears a failed
`FindClass` and falls back to `java/lang/RuntimeException`; `scripts/check-apk.sh` fails a release
APK whose DEX loses those names). This signed APK
(`eb983c34c4dc804a793799d587eb76753c1a818aa08c0c4c043cc2d868bdec36`) contains both kept class
names and passes `check-apk`; Start/playback and the 2-hour run completed with no
`ClassNotFoundException`, `SIGABRT` or crash/ANR.

## Open items

- [x] Bluetooth controls: PASS (owner-reported, 2026-11-08, physical ASUS_AI2302); headset
      play/pause/next followed the notification controls and the session state stayed in sync.
- [x] Earbud removal: PASS (owner-reported, 2026-11-08, physical ASUS_AI2302); removing the earbuds
      paused/stopped playback and the route change behaved correctly.
- [x] Incoming call/focus loss: PASS (owner-reported, 2026-11-08, physical ASUS_AI2302); the
      session paused on focus loss and resumed/stopped according to the app's policy.
- [x] 2-hour mixed locked playback: PASS (7200 s, 25 samples, 0 crash/ANR, screen asleep).
- [x] Owner upload-key backup: the upload keystore and its password were backed up in two secure
      places on 2026-11-08.
- [x] Separator clip provenance or replacement: the shipped C6/G6 cue was rendered from the MIT
      MuseScore General v0.2 font and reproduced bit-exactly from the hash-verified upstream font;
      recorded in `docs/legal/ASSET_SOURCES.md`, no replacement needed. The owner accepted the
      shipped cue by ear on 2026-11-08.
- [x] Publisher contact email, public privacy URL and GitHub Pages deployment: the listing records
      thomas.t.zhang@outlook.com and https://thomastzh.github.io/EarBy/privacy/ (Pages live).
- [x] Play screenshots re-encoded within the 2:1 limit and without alpha: eight 1080x2160 24-bit
      RGB captures of the signed 1.0.0 release (2026-11-08) are in `store/screenshots/`; the rules
      are recorded in `docs/release/STORE_LISTING.md`.
- [x] Final upload-key-signed AAB/APK: both signed artifacts are recorded above and pass
      `scripts/check-release.sh`.

## Environment blockers

- 16 KiB page-size runtime: BLOCKED; the physical device has 4 KiB pages and no 16 KiB device or
  system image is available in this environment.
- API 29 and API 36: BLOCKED by host capability (no KVM and AVD writes outside the workspace are
  denied; the API 36 software start failed on a pending-snapshot lock). Recorded in the matrix
  above; these are not owner actions.
