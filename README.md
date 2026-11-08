# EarBy

EarBy is an offline Android ear-training app. It plays short generated musical examples and then
speaks the answer, so you practise recognition without tapping or scoring.

- Nine training modes: interval recognition, interval walk, chords, scales, note identification,
  scale degrees, chord progressions, intervals in context and melodic dictation.
- Recognition, Exposure and Association learning.
- Per-mode setup, five presets and a starter preset.
- No account, advertising, analytics, telemetry, cloud sync, runtime text-to-speech or INTERNET
  permission.
- Settings and presets stay in app-private storage.

EarBy is a release candidate for **1.0.0** (`versionCode` 100), package `com.earby.app`.

The reviewed source is published at <https://github.com/ThomasTZh/EarBy> as a one-commit source
snapshot; the identity stays 1.0.0 / `versionCode` 100.

## Download and release status

The public APK and Play listing are not published yet. The release checklist is in
[docs/release/PLAY_CHECKLIST.md](docs/release/PLAY_CHECKLIST.md); the G8 evidence is recorded in
[docs/release/G8_EVIDENCE.md](docs/release/G8_EVIDENCE.md). No release archive attaches an APK:
only a debug-key test build exists, so there is no upload-key-signed artifact to publish.

## Build

Requirements are documented in [docs/development/ENVIRONMENT.md](docs/development/ENVIRONMENT.md).
From a fresh checkout with JDK 17 and the Android SDK:

```bash
source scripts/env.sh
scripts/fetch-native-sources.sh
./gradlew --dependency-verification strict :app:assembleDebug
scripts/check-fast.sh
```

`fetch-native-sources.sh` downloads the pinned miniaudio source and verifies its SHA-256 value.
The app build never downloads it itself.

The project uses the Gradle wrapper. Full device checks require an authorized Android phone and the
review application; see [docs/testing/TEST_STRATEGY.md](docs/testing/TEST_STRATEGY.md).

## Privacy

EarBy collects no personal information and has no network permission. Read the
[privacy policy](docs/legal/PRIVACY_POLICY.md). The public copy is published from `site/privacy/` at
<https://thomastzh.github.io/EarBy/privacy/>.

## Licensing

- Source code: Apache License 2.0, see [LICENSE](LICENSE).
- Original project assets: CC BY 4.0, see [docs/legal/ASSET_SOURCES.md](docs/legal/ASSET_SOURCES.md).
  The hand-drawn launcher icon is the one exception: it is used with the artist's permission and is
  not relicensed under CC BY 4.0.
- Third-party components and voice datasets: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and
  [LICENSES/](LICENSES).

## Repository map

- `app` — Android entry point, Compose UI and release configuration.
- `core/music` — pitches, intervals, chords and scales.
- `core/training` — definitions, generators, recipes, sessions and settings ports.
- `core/audio` — native renderer, PCM pipeline and AudioTrack output.
- `core/media` — Media3 session service and playback ownership.
- `core/data` — DataStore settings, SQLite presets and document import/export.
- `docs` — architecture, release, legal and development records.
- `scripts` — verification, QA and release tools.

This public repository (<https://github.com/ThomasTZh/EarBy>) is a distributable source snapshot
with one initial commit; internal development records, private evidence and the pre-1.0 icon
drafts are not published. See [docs/release/PUBLIC_REPO.md](docs/release/PUBLIC_REPO.md).

## Contributing and security

See [CONTRIBUTING.md](CONTRIBUTING.md) and [SECURITY.md](SECURITY.md). Do not commit keystores,
passwords, raw voice candidates or device serials.

## Credits and acknowledgements

- **Piper** (MIT), with voices **Alba** and **Amy** (CC BY 4.0), **Cori** (public domain), and
  **Jenny (Dioco)** (custom license requiring attribution).
- **miniaudio** (MIT-0/Unlicense). The instrument is rendered by the in-project sample player, so
  the app ships no third-party synth or LGPL code.
- **AndroidX, Kotlin, kotlinx.serialization, Media3 and Jackson** (primarily Apache-2.0).
- **EarBy ear-and-note icon**, hand-drawn by **Dr Ham Hamster** and used with her permission.

Full texts and per-component details are in [LICENSES/](LICENSES),
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and
[docs/legal/ASSET_SOURCES.md](docs/legal/ASSET_SOURCES.md).
