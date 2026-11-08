# ADR 0001: Native Android and a minimal foundation

Status: accepted; host and API 29/36 emulator preparation verified.
Physical-phone launch passed on 2026-10-09; IDE settings/import remain OPEN. The installed
Studio MCP is reachable but does not yet expose a usable Gradle project model.

Use Kotlin, Compose Material 3, AGP built-in Kotlin, Gradle Kotlin DSL and a version
catalog. The original scaffold had app, core:music and core:training; audio/media/data
have since been introduced with consumers. Keep the core JVM modules Android-free.

Retain minSdk 29, compileSdk/targetSdk 36, AGP 9.4.0, Gradle 9.6.0, JDK 17 and
Kotlin JVM/Compose compiler plugins 2.4.20. The installed SDK includes platform 36;
preparation did not use API 37. No API 37 feature is required.
Do not install standalone Gradle or replace system Java.

Consequences: fast JVM domain tests and a small initial dependency graph.
Host build evidence is in STATUS; new dependencies still need integration checks.
A compatibility failure requires a coherent documented pin adjustment, not disabling
lint or silently opting out of built-in Kotlin.

Review clarification (2026-10-07): split G0-host from G0-device/G0-IDE. The verified
host gate permits pure JVM Stage 1 work once implementation is requested. The phone
gate must close before G2 audio acceptance.
