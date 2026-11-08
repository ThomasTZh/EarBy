# Android Studio MCP workflow

Reviewed 2026-10-09 against the installed server, its live tool schemas and
[JetBrains' MCP documentation](https://www.jetbrains.com/help/idea/mcp-server.html).
This is a development tool. It adds no dependency or permission to the Android app.
Follow [AGENT_WORKFLOW](AGENT_WORKFLOW.md) and the selected work package.

## Observed installation and readiness

Updated 2026-10-09 after the import repair (G0-IDE PASS).

| Check | Result |
| --- | --- |
| IDE | Android Studio Rabbit 1 / 2026.2.1; build 262.9437.185.2621.16467767 |
| Plugin | JetBrains `com.intellij.mcpServer`, version 262.9437.214 |
| Settings | Server enabled; brave mode unchanged (disabled); no dependency or permission added to the app |
| Client configuration | User-local Codex entry `android-studio`, enabled, optional; HTTP stream transport on loopback |
| Live protocol | Initialize succeeded using MCP 2025-03-26; server identifies itself as Android Studio MCP Server |
| Exposed tools | 25 tools returned by `tools/list`; inventory below |
| Checkout access | `read_file` reads project files; SDK path points to `.local/android-sdk` |
| IDE module model | Root plus the six Gradle modules and their main/unitTest/test/androidTest source sets (24 modules) |
| Dependencies / run configurations | 36 JPS dependency entries; the `app` Android launch configuration; Kotlin test run points discovered |
| Kotlin inspection | `get_file_problems` analyzes JVM and Android Kotlin sources (warnings returned, no `notAnalyzedReason`) |
| Gradle JDK | Project `temurin-17` (`.local/jdk-17`); the IDE service directory is `.local/gradle-home` |

The server and import are usable for semantic work. The repair recorded Studio's classifier-artifact
checksums in `gradle/verification-metadata.xml` (IDE source attachment only) and removed an
IDE-generated `gradle-daemon-jvm.properties` that requested a JDK 25 daemon. CLI builds remain the
verification source of truth. Evidence: `.local/verification/g0-ide/`.

MCP sessions end when Studio restarts; reconnect and rediscover schemas rather than guessing.
Local evidence: `.local/verification/plan-review-2026-10-09/studio-tools.json` and
`.local/verification/g0-ide/`. These are installation observations, not app test results.

## G0-IDE completion package

**Goal:** a correctly imported checkout and usable IDE diagnostics without changing pins.
**Prerequisites:** installed Studio/server; an instruction covering IDE setup or implementation.
**Scope:** local IDE/client settings, import diagnosis and evidence documentation. Keep
`.idea`, SDK/JDK paths, terminal environment and client configuration untracked.

1. Open the existing checkout as its Gradle project. Inspect SDK and Gradle JDK in Studio:
   checkout `.local/android-sdk` and `.local/jdk-17`. The IDE's bundled runtime is separate.
   Record the displayed settings; a successful CLI build or reading `local.properties`
   does not establish the IDE's Gradle JDK.
2. Sync with the existing wrapper and catalog. Capture the complete sync outcome/log.
   If it fails, identify the actual import error before making a scoped fix. Preserve
   AGP built-in Kotlin and all toolchain pins; decline incidental upgrade suggestions.
3. Rediscover tools with the client. Supply `projectPath` on every project operation.
   `get_project_modules` must show the imported app/music/training/audio/media/data
   content, accounting for Studio's qualified/source-set module names. Compare with
   `settings.gradle.kts`; do not require exactly six IDE entries.
4. Check `get_project_dependencies` against representative catalog entries and build
   consumers. Confirm Kotlin sources are content roots with `get_file_problems` on
   one JVM source and one Android source. Neither an empty list nor an inspection
   failure is a successful check. Record indexing or partial-result limitations.
5. Discover `get_run_configurations`. Create local configurations only when their
   consumers exist: app launch and targeted domain/app tests. If running project
   check scripts through Studio, use the existing script and its environment setup.
   Check required Gradle arguments and device selection before execution.
6. Record SDK/JDK inspection, sync, module model, usable inspections and client tool
   access in STATUS. G0-IDE passes only when all of those have evidence. Run/build
   results remain separate from physical and human acceptance.

## Tool inventory and use

These are names returned by the installed server, not promises that every operation
works before import/indexing completes. Inspect the current input schemas each session.

| Work | Installed tools | Project use |
| --- | --- | --- |
| IDE model | `get_project_modules`, `get_project_dependencies` | Check import and resolved consumer dependencies; catalog and strict verification remain authoritative |
| Semantic navigation | `search_symbol`, `get_symbol_info`, `analyze_calls` | Trace Start, timer effects, queue completion, answer publication and repository calls |
| Inspections | `get_file_problems`, `lint_files` | Inspect changed Kotlin/build files and affected consumers after import |
| Source reading/search | `read_file`, `search_file`, `search_text`, `search_regex` | Read source/dependency declarations; use `rg` for ordinary filesystem searches under the shared workflow |
| IDE context | `list_directory_tree`, `get_all_open_file_paths`, `open_file_in_editor` | Confirm project/editor context; open review targets |
| Scoped mutations | `apply_patch`, `create_new_file`, `rename_refactoring`, `reformat_file` | Implement only the selected authorized package; review touched files and preserve Spotless as formatter of record |
| Execution | `get_run_configurations`, `execute_run_configuration`, `build_project`, `execute_terminal_command` | Targeted iteration and supplemental build diagnostics; execute canonical verification scripts for gate evidence |
| Repository observation | `get_repositories`, `git_status` | Review existing changes without requiring a clean checkout or writing Git metadata |
| Router | `execute_tool` | Access a tool only through its advertised router schema when direct bindings are absent |

Example read-only requests, using the discovered tool schema:

```text
get_project_modules(projectPath=<absolute checkout>)
get_run_configurations(projectPath=<absolute checkout>)
get_file_problems(filePath=<project-relative Kotlin file>, errorsOnly=false,
                  timeout=15000, projectPath=<absolute checkout>)
lint_files(files=[<changed files>], min_severity="warning",
           timeout=30000, projectPath=<absolute checkout>)
```

Locate a callable with `search_symbol` before `analyze_calls`. Pass its callable FQN
or exact returned signature, `analysisKind` (`INCOMING_CALLS` / `OUTGOING_CALLS`) and
bounded depth/node counts. A missing symbol or failed index is not evidence of no callers.
Review actual data transfer as well: a call graph cannot prove a field reaches playback.

## Execution and evidence rules

- Continue sequentially. MCP does not create authorization for implementation, baseline
  approval, commits, toolchain upgrades or release actions.
- Run configurations come from discovery. Pass launch overrides only if that configuration
  reports `supportsDynamicLaunchOverrides=true`; otherwise use an explicit local setup.
- Direct Gradle checks include `--dependency-verification strict`. Prefer the existing
  wrapper/scripts over a generic `build_project` result for final verification.
  `build_project` does not prove scripts, APK permissions, assets, native checks or device rows.
- A Studio terminal has its own environment. Confirm working directory, source `scripts/env.sh`
  and select `ANDROID_SERIAL` when required. Keep serials in local evidence. Capture exit status
  and full logs; truncated output or a still-running command is not PASS.
- Bound synchronous execution/inspection waits to at most 60 seconds. Resume a running task
  using the supported mechanism; do not start duplicate builds after a timeout. Report each
  `timedOut`, `notAnalyzedReason`, truncation or incomplete result explicitly.
- Missing native tool bindings or an unavailable IDE do not block independent CLI/source work.
  Record the exact limitation. Do not install another integration or change server settings
  merely to make a plan review appear successful.
- Keep brave mode at its observed value. If Studio asks for execution confirmation, describe
  that IDE prompt and the exact operation. A project script run never approves visual goldens.

## Capabilities that still require other tools or people

The discovered tools contain no dedicated Android device, Compose preview, screenshot,
TalkBack, acoustic measurement or profiler API. Use existing ADB/instrumentation scripts,
Roborazzi when introduced, Studio's human-operated tools and physical listening as appropriate.
Opening or building a screen through MCP proves none of those acceptance rows.

At G3/G4, MCP can trace lifecycle/state wiring and run existing tests after setup. The owner
still performs listening, route unplug, TalkBack and visual-baseline review. At G8, it can
assist investigation of measured defects; Perfetto, memory measurements, signed/minified
APK checks, a 16 KiB runtime and signing-key backup remain their specified evidence.
