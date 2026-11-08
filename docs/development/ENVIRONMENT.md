# Nobara development environment

Workstation baseline recorded 2026-10-07; Studio MCP inspected 2026-10-09.
Reuse this setup; fresh-machine instructions are separate below.
Preparation build evidence is in [STATUS](STATUS.md).

## Observed workstation

| Item | Observed state |
| --- | --- |
| OS | Nobara Linux 44, KDE Plasma, Fedora family, x86_64 |
| CPU / memory | Intel i5-12600K; approximately 31 GiB RAM |
| Workspace / free space | <checkout>; approximately 376 GiB free |
| Studio launcher | $HOME/Applications/android-studio/bin/studio |
| Studio MCP | Rabbit 1 / 2026.2.1; JetBrains MCP Server 262.9437.214 responds on loopback; Gradle import/readiness OPEN |
| Project JDK | .local/jdk-17; Temurin 17.0.20.1 |
| SDK / cache | .local/android-sdk / .local/gradle-home by default |
| Host Python / uv | Python 3.14.7; uv 0.12.0 |
| Audio processing | FFmpeg 8.1.2; ffprobe present in preparation inventory |
| Other tools | Git, curl, unzip, GCC/G++, CMake, Ninja and pkg-config installed |
| GPU | nvidia-smi exists but could not communicate with driver here; GPU/CUDA unconfirmed |
| KVM | Host usability recorded in prior preparation; restricted process visibility may differ |
| Emulators | ear-api29 and ear-api36 created and previously verified |
| Physical phone | Recorded ASUS_AI2302 / Zenfone 10, Android 14 / API 34, arm64-v8a, 4 KiB pages; G0-device PASS 2026-10-09; reconnect before new runs |

GPU failure in a sandbox is not proof of a broken host installation. No GPU is needed
for Android builds or core tests. Use the CPU voice path unless host CUDA is verified
when WP2.1 is actually implemented.

System Java is not the project toolchain. Always select the installed project JDK
before Gradle; do not change the host's Java alternatives.

## Resume work in this checkout

```bash
cd <checkout>
source scripts/env.sh
./scripts/check-env.sh --build
./scripts/check-fast.sh
```

env.sh defaults ANDROID_HOME and GRADLE_USER_HOME only when not already set.
Check existing overrides if build tools appear inconsistent; use the intended
checkout paths. It selects .local/jdk-17 when a full compiler is present.

The official wrapper, checksum and dependency metadata already exist. Do not rerun
bootstrap-gradle or record-dependencies during routine work. record-dependencies
intentionally writes checksums and is reserved for reviewed dependency changes.

Recorded pins: AGP 9.4.0, Gradle 9.6.0, Kotlin JVM/Compose plugins 2.4.20, minSdk 29,
compileSdk/targetSdk 36, Build Tools 36.0.0, NDK 28.2.13676358, SDK CMake 3.22.1.
Android uses AGP built-in Kotlin; do not apply org.jetbrains.kotlin.android.
Native builds must use SDK CMake/NDK even though Linux CMake/GCC are installed.

## Studio settings to confirm

Open this repository with the installed Studio, without running a new-project wizard.
Set Android SDK to this checkout's .local/android-sdk and Gradle JDK to .local/jdk-17.
The IDE's own bundled runtime is separate. Avoid automatic project/toolchain upgrades.
G0-IDE remains open until these settings are inspected.

The newly installed JetBrains MCP Server is enabled, with brave mode disabled and an
enabled user-local Codex entry. A live handshake and 25-tool inventory succeeded. However,
module discovery returned only one generic module, dependencies/run configurations were
empty, and a Kotlin source was outside analyzable content roots. Reading local.properties
confirmed the SDK file path, not the displayed IDE setting or Gradle JDK.

Use [ANDROID_STUDIO_MCP](ANDROID_STUDIO_MCP.md) to complete Gradle import and client
readiness, then inspect source/consumers through the IDE. Keep endpoint/client configuration
and `.idea` untracked. Rediscover tools after client restart; this review used read-only local
protocol probes because native MCP bindings were absent from the running agent session.

## Emulator use

After sourcing env.sh:

```bash
emulator -list-avds
emulator -accel-check
emulator -avd ear-api29
```

Use the existing AVDs. Create them only if missing, using the installed package IDs:

```bash
avdmanager create avd --name ear-api29 --package 'system-images;android-29;default;x86_64'
avdmanager create avd --name ear-api36 --package 'system-images;android-36;google_apis;x86_64'
```

Run device checks once per API. With multiple devices, export ANDROID_SERIAL to the
specific adb-reported serial; never let an arbitrary connected device be selected.
KVM access is a host permission/virtualization check. Do not disable SELinux or
globally relax USB/device permissions to fix tooling.

A 16 KiB runtime is an additional Stage 8 target, not something the current API 36
AVD is assumed to provide. Confirm getconf PAGESIZE on the chosen runtime.

## Physical-phone gate

On an Android 10+ phone enable USB debugging and accept the host authorization.
Use host execution for USB/ADB when the restricted environment cannot access it.

```bash
source scripts/env.sh
adb devices -l
export ANDROID_SERIAL='actual-selected-serial'
adb shell getprop ro.product.model
adb shell getprop ro.build.version.sdk
adb shell getprop ro.product.cpu.abi
./scripts/install-debug.sh
./scripts/check-full.sh
```

Replace the serial placeholder. Record model/API/ABI and pass/failure; keep the serial
in local evidence. arm64-v8a is the planned phone ABI, not a fact about an unseen phone.
API 29/36 emulators use x86_64. A different phone ABI requires a packaging decision.

Phone launch has closed G0-device, and Stage 2 listening has recorded acceptance.
Stage 3/4 integration and physical acceptance remain open. Studio MCP discovery, a
successful import or an app launch cannot prove locked playback, routes, expiry or accessibility.

## Existing Python/audio tools and recovery

The isolated locked projects already exist under tools/voice-assets and tools/audio-analysis.
Use installed uv and their Python 3.11 pins when restoring their environments. Chatterbox's upstream tested environment
uses Python 3.11; the system's Python 3.14 is not assumed compatible.
Record wheel/model versions and download hashes; keep weights/cache under ignored
.local or tools raw/candidates directories. Never add Python dependencies to Gradle.

The existing voice pack was produced on CPU and reviewed; do not regenerate it during
routine work. For a future vocabulary addition, measure a small batch and inspect CUDA only if useful.
Do not change GPU drivers as part of app implementation. Reuse installed FFmpeg.
Review [OPEN_SOURCE](OPEN_SOURCE.md) before installing any new package.

## Automated runs inside the workspace

Automated checks must not depend on writes outside the checkout. `scripts/env.sh` therefore keeps
every tool cache inside `.local/` so a read-only home (sandbox, locked-down account or CI
container) cannot break a run:

| Tool | Variable(s) | Project location |
| --- | --- | --- |
| Gradle | `GRADLE_USER_HOME` | `.local/gradle-home` |
| uv (audio tools) | `UV_CACHE_DIR`, `UV_PYTHON_INSTALL_DIR` | `.local/uv-cache`, `.local/uv-python` |
| ccache (system compiler wrappers) | `CCACHE_DIR`, `CCACHE_CONFIGPATH` | `.local/ccache` |
| Kotlin daemon client marker | `XDG_DATA_HOME` | `.local/xdg-data` |
| Robolectric `android-all` runtime | `robolectric.dependency.dir` (set by `app/build.gradle.kts` when present) | `.local/robolectric-dependencies` |
| Debug signing store | `signingConfigs.getByName("debug")` in `app` and the core Android modules | `.local/debug.keystore` |

`scripts/download-toolchain.py` provisions the Robolectric runtime (byte-identical copy from the
local Maven cache or a download from Maven Central, pinned SHA-256
`3fdac2d463f431c6b8d83b9d0ca739e795de75fffedee648c6761bd4bd864701`). `.local/debug.keystore` is a
byte-identical copy of `~/.android/debug.keystore`, so debug/review builds and installs keep the
same signature; both files are untracked local state and are excluded from backups like every
other keystore.

## Fresh-machine recovery only

If an existing host prerequisite is missing, install the specific package with Nobara's
dnf, not apt. For a new host the base package set is:

```bash
sudo dnf install git curl unzip zip tar gcc gcc-c++ cmake ninja-build pkgconf-pkg-config python3 python3-pip
```

Reuse a working FFmpeg installation. The project setup scripts do not invoke sudo,
change system Java or edit shell startup files.

Fresh project-local bootstrap:

```bash
./scripts/setup-android.sh
source scripts/env.sh
./scripts/bootstrap-gradle.sh
./scripts/check-env.sh --build
./scripts/record-dependencies.sh
./scripts/check-fast.sh
```

Setup validates downloaded tool archives and records JDK/SDK provenance in .local.
It displays SDK licenses for review, installs platform 36, build tools, NDK, CMake,
platform tools/emulator and API 29/36 images. Existing complete installations are
reused; partial installations require a diagnostic rather than destructive overwrite.

Review any regenerated wrapper/checksum artifacts before accepting them.
A source checkout already containing the official wrapper does not need it replaced.
Studio's Linux archive can be obtained from [Android Studio](https://developer.android.com/studio);
verify its official checksum and keep the IDE in a user-owned directory.

## Restricted execution, Git and offline builds

Some Gradle/ADB operations require local sockets/device access unavailable inside the
sandbox. Record this as an execution restriction; use authorized host execution for
builds rather than changing app network permissions or weakening dependency checks.

The repository is initialized on main with no commits; the scaffold is untracked.
Review files/ignore rules/checksums before an authorized initial commit. Never replace
.git metadata. Keep SDK/JDK caches, raw voices, model weights and signing keys untracked.

Initial dependency/model downloads require development-time network access. Once
cached, direct Gradle --offline with --dependency-verification strict verifies the
build without new resolution. Native submodules must be staged too. A cached
clean-source build is not proof of a cold-download bootstrap.

Sources: [AGP compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes),
[built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin),
[Chatterbox environment](https://github.com/resemble-ai/chatterbox).
