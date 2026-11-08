# ADR 0003: Local operation, prerecorded answers, explicit persistence

Status: accepted.

No production INTERNET permission, runtime TTS, account, telemetry or cloud dependency.
Voice generation is development-only. App-private data can include settings, presets
and listening history. No data is transmitted. Disable OS cloud backup; provide explicit
versioned preset export later. Do not claim that no local data exists.

Implemented choice (reviewed 2026-10-09): Preferences DataStore with typed domain mapping,
atomic edits and versioned keys holds settings. Plain SQLite holds presets and later history.
Manual application/service/ViewModel composition preserves lifetimes without code generation.
Proto DataStore and Hilt were not introduced. Signing keys stay outside Git.

Update 2026-10-09: Room cannot be introduced on the current toolchain. It requires KSP,
the probed KSP release did not support the project's Kotlin 2.4.20, and its compatible Kotlin could not
be applied against AGP 9. Preset/history storage is therefore plain SQLite through
`android.database.sqlite` with hand-written, tested migrations; the settlement evidence
is in docs/development/STATUS.md.

These are recorded compatibility findings, not a claim about all future releases. Any future
Room/Hilt adoption needs a scoped, verified decision and data-preserving migrations. It is not
a prerequisite for completing G3/G4. Preset document and SQLite schema versions remain separate.
