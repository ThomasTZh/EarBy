# AI agent implementation workflow

This document defines the common contract for every work package in the roadmap.
The 2026-10-09 review changed documentation only. Execute packages only after an
implementation instruction from the user. The installed Studio MCP supplies additional
development tools; it does not change authorization or gate requirements.

## Read and establish scope

1. Read AGENTS.md, README, STATUS, ROADMAP and the selected package.
2. Read ARCHITECTURE, OPEN_SOURCE and relevant existing code/tests before editing.
3. Inspect git status and the recent history. The public snapshot has a single initial
   commit; the working tree is the source of truth. Preserve existing work; do not use reset,
   clean or force checkout to create an artificial clean baseline.
4. Select the earliest eligible incomplete package. Check each prerequisite gate.
5. Give a concise statement of its outcome, allowed paths and checks. Routine choices
   specified by this plan need no fresh approval.
6. Complete that package and its verification before starting another unless the
   user's implementation instruction explicitly covers multiple packages.

A package's allowed paths include its tests and documentation. It may also change
catalog/checksum entries and build/check integration needed for its named consumers.
It may not use that allowance to upgrade unrelated libraries or rewrite scripts.

For IDE-assisted work, read [ANDROID_STUDIO_MCP](ANDROID_STUDIO_MCP.md). Discover the
current tool schema, select this checkout with `projectPath`, verify import/indexing and
use semantic call/symbol tools when they answer a consumer question. The 2026-10-11
server exposes the imported Gradle modules; empty or incomplete results are not evidence of clean code or absent callers. CLI/source review remains available.
After code changes, inspect affected files through the usable IDE and run the required
project checks. Keep Spotless authoritative, strict verification enabled and scopes unchanged.

## Required fields inherited by every package

Each package below defines its goal, gate, scope, interfaces, requirements and tests.
The following apply to all of them:

- **Failure behavior:** reject invalid input with a typed/domain error or explicit
  precondition failure appropriate to a programmer API. Surface runtime errors to the
  session/UI. Never swallow exceptions or mark a failed operation successful.
- **Dependency step:** reuse OPEN_SOURCE choices; add exact compatible pins only when
  the consumer exists. Review license, provenance, minimum API, Kotlin/AGP compatibility
  and transitive manifests. Resolve checksums as an intentional dependency operation.
- **Forbidden changes:** no network/TTS in the app, speculative feature scaffolding,
  unrelated upgrades, global coroutine scopes, business logic in Compose, hidden
  fallback exercises, generic architecture rewrites or automatic golden replacement.
- **Verification:** run the package's targeted checks, then check-fast for code changes.
  Run check-full at an Android/native/persistence integration gate. Missing hardware
  yields OPEN, even if all host checks pass.
- **Documentation:** update STATUS with task outcome and evidence; update contracts,
  task details or ADRs if an implementation proves a plan adjustment necessary.
- **Done:** implementation, meaningful tests, passing available checks, no unexplained
  failures, documented deviations and an explicit result for every acceptance item.

Tests should detect domain, lifecycle and integration mistakes. Avoid repetitive tests
that merely restate a field assignment or enforce arbitrary test counts.

## Existing and planned verification entry points

Existing commands can run now on a host with the required permissions:

```bash
source scripts/env.sh
./scripts/check-fast.sh
./scripts/check-full.sh
./scripts/install-debug.sh
./scripts/check-apk.sh app/build/outputs/apk/debug/app-debug.apk
```

check-full invokes check-fast itself. During iteration use targeted tests; once a
final full check passes, do not immediately repeat the same suite without a reason.
Every direct Gradle verification command must use --dependency-verification strict.
Sourcing env.sh preserves pre-existing ANDROID_HOME/GRADLE_USER_HOME; check that they
refer to the intended SDK/cache.

Present and planned specialist entry points (verified against scripts on 2026-10-09):

| Owner | Command | State / responsibilities |
| --- | --- | --- |
| WP2.1 | scripts/check-assets.sh | PRESENT: asset integrity, vocabulary, reviews and provenance |
| WP2.2 | scripts/check-native.sh | PRESENT: packaged ABIs, dependencies and page alignment |
| WP2.2/2.3 | scripts/check-native-tests.sh | PRESENT: production-source host build, CTest, WAV fixtures and numeric analysis |
| WP2.1/2.3 | scripts/check-audio-tools.sh | PRESENT: validator/analysis Python tests |
| WP3.5 | scripts/device-smoke.sh | PRESENT: isolated automated phone suites; use --automated-only for real 30-minute endurance |
| WP4.3g | scripts/check-ui.sh | PRESENT: UI state/semantics and complete reviewed Roborazzi comparisons |
| WP5.1 | scripts/check-music-fixtures.sh | PLANNED: independent music21 fixtures |
| WP8.3 | scripts/build-personal-release.sh | PLANNED: signed/minified APK, hashes, manifest/native/signature checks |

Do not invoke the earlier proposed `check-audio.sh`: its responsibilities are implemented
by check-native-tests/check-audio-tools. Extend their fixtures for new production recipes.

The owner must add each command and hook it into check-fast/full where appropriate,
rather than merely documenting a command that never runs. Extend formatting targets,
architecture guards and dependency recording when introducing modules.
No routine check writes lockfiles, baselines or dependency-verification checksums.
Local logs/reports are evidence outputs, not approved baselines.

## Dependency introduction procedure

1. Use the existing pins if already present. For a new package, inspect the authoritative
   release page and choose a stable compatible version; never use '+' or 'latest'.
2. Record Maven pins in gradle/libs.versions.toml. Native sources/assets need a manifest
   containing immutable revision, download URL, SHA-256, license and build options.
   Python projects need pyproject.toml, uv.lock and an exact Python minor.
3. Resolve the selected configurations, explicitly record new checksums, inspect the
   diff and the source. Checksum recording is not a way to suppress an unexpected hash
   change. Update record-dependencies.sh for configurations it did not previously cover.
4. Test strict verification, then test an offline rebuild once downloads are cached.
   Ordinary compilation must not download CMake submodules or voice models.
5. Add notices and the consumer's tests. Document the resolved revision and compatibility
   result in STATUS. Proposed versions in OPEN_SOURCE are not build-verified promises.

## Review and handoff

Review the completed package against its specification: correctness, boundaries,
randomness/timing, concurrency, resource lifetime, errors, dependency necessity and
scope. Fix demonstrated issues and repeat affected checks.

Completion report:

```text
Package:
Outcome:
Files/modules changed:
Interfaces and behavior:
Packages/versions introduced:
Checks run and results:
Evidence paths:
Open host/device/human items:
Plan changes and reasons:
Next eligible package:
```

Store bulky local logs under .local/verification/<package>/ and put a concise portable
summary in STATUS. Record date, command, device/API/ABI, revision or source snapshot,
seed/configuration and asset versions. Do not commit device serials, keystores, raw
voice candidates or local absolute paths in runtime configuration.

Git commits, pushes, publishing and external communication follow the user's actual
authorization. A plan is not authorization to publish. If implementation is requested
in a restricted sandbox, distinguish a sandbox tool failure from a project failure;
use available authorized host execution or record the specific blocked check.

## Gate closure tasks

**G0-host:** already supported by preparation evidence; preserve the build baseline.

**G0-device:** on the physical phone, select ANDROID_SERIAL, record model/API/ABI,
install/launch the shell and run check-full. Record any ABI other than arm64-v8a before
native packaging. An emulator cannot close this gate.

**G0-IDE:** confirm Studio SDK and Gradle JDK match the checkout. This is a manual
setting check, not a claim inferred from successful command-line builds. Complete the
import/model/client-readiness steps in ANDROID_STUDIO_MCP; installation alone does not pass.

**Initial commit:** review the untracked scaffold, ignore rules and generated
checksums before a separately authorized commit. Normal package work can proceed
without a commit; parallel worktrees cannot be assumed to exist.
