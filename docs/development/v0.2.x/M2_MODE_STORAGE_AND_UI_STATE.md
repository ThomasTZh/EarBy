# M2 — Per-mode storage, drafts and preset slots

State: PLANNED. Prerequisite: M1 automated acceptance. Next: [M3](M3_SAFE_MODE_SWITCHING.md).
Read [shared contracts](SHARED_CONTRACTS.md), especially SC6–SC7, and decisions D03/D10/D11.

## Outcome and scope

Each selected mode has independent saved settings, editable drafts and five stable preset
slots. Legacy interval data survives an atomic migration. Mode selection alone cannot start
playback. Unsupported providers remain unavailable until their later milestones register them.

Own `core/training/.../settings/`, `core/data/.../DataStoreSettingsRepository.kt`, the preset
repository/document/schema files, `app` setup/preset/root ViewModels and screens, their host/
device tests, screenshot scripts/guards and affected architecture/storage documentation.
Read the current `PresetSchema` in `PresetDatabase.kt`, `AndroidPresetDatabase`,
`JdbcPresetDatabase`, `SqlitePresetRepository`, app `SetupViewModel` and settings observer
before changing interfaces.
No new database library, import/export UI, preset count increase beyond five slots per mode,
or persistence of live playback is included.

## M2.1 — Versioned settings contract

1. Add explicit wire DTO schema 2: global preferences, selected kind, and a map of stable kind
   IDs to `{revision, configVersion, providerPayload, recipe, seed, stopPolicy}`. Use immutable
   domain values and explicit serializers; never serialize enum ordinals or class names.
2. Separate global theme, Scientific/Solfège naming, answer visibility and gains from each
   mode's musical setup. Preserve existing values, defaults and gain limits.
3. Migrate schema 1 `lastSetup` to intervals without changing seed, timings, presentation,
   stop policy, root normalization or advanced panel state. Map legacy `lastMode`, including
   `PERFECT_PITCH`. Preserve selection of a known mode whose provider is not yet available.
4. Create missing per-mode defaults lazily when its real provider is registered. An absent
   new setup is distinguishable from a malformed existing setup. No interval payload may
   silently stand in for another mode. Known unavailable modes can own empty slots now.
5. Add atomic update operations carrying kind and expected revision. Increment only the
   changed mode's revision; guard overflow. A preset/draft race returns an explicit conflict
   and reloads saved state rather than silently applying an outdated write.
6. Preserve schema recovery diagnostics. Unknown future settings/config versions remain
   protected from automatic writes, including autosave and migration. Do not reset them to
   defaults as part of normal launch. Keep explicit recovery separate from save success.

The repository operation for selecting a mode commits valid outgoing edits and selected kind
in one settings transaction. Invalid drafts stay available for correction but do not block
viewing another mode. A failed write leaves persisted selection and acknowledged UI selection
unchanged and exposes retryable error state. Never report a successful Start/switch first.

## M2.2 — Preset document schema 2

- Add a discriminated payload with `kind`, provider `configVersion`, typed musical setup,
  recipe, seed and stop policy. Preserve existing preset gain capture/application behavior.
- Decode schema 1 through the existing strict interval decoder, then adapt to the new domain
  value. Export new documents as schema 2. Freeze v1 import fixtures unchanged.
- Keep decimal-string signed 64-bit seeds, exact units, duplicate-key preflight, 1 MiB total,
  100 entries per document, 80-character names, 64-character IDs, depth 32 and string limits.
- Reject mismatched kinds, unknown fields/versions, missing required tokens and invalid
  musical/duration constraints before any database mutation. Preserve import rollback.
- Applying a preset atomically saves the destination mode's setup plus captured global gains.
  It does not arm/reset the service's already-running elapsed sleep timer.
- Do not restore retired import/export UI. Existing repository APIs remain compatible through
  adapters and tests. A v2 payload for an unavailable provider is refused, not misdecoded.

## M2.3 — SQLite migration 9 and slot ownership

Keep migrations 1–8 byte-for-byte. Introduce version 9 using a generic payload table with
ID, kind, nullable slot number, name, payload JSON/version, empty/built-in flags and timestamps.
Enforce unique `(kind, slot_number)` and slot numbers 1–5 when non-null. Filled rows require
a payload; empty rows cannot masquerade as a starter preset.

Migration sequence, all inside one transaction:

1. Read every version-8 row and adoption metadata through the migration's supplied database
   driver. Validate the old representation using the existing compatibility rules.
2. Create the new table/indexes. Convert every existing filled payload to a v2 interval
   payload; preserve IDs, names, creation/update times, gains, built-in flags and empty state.
3. Preserve the five current interval slot IDs exactly. Allocate the other 35 deterministic
   IDs as `mode:<stable-kind-id>:slot:<1..5>`, frozen in constants and bounded below 64 chars.
4. Preserve legacy custom rows outside slots with null slot numbers and the legacy adoption
   marker. Do not adopt them twice or discard them to make room for new modes.
5. Verify converted counts, payloads, uniqueness and metadata before replacing old tables.
   Commit schema version only after success. On any conversion/write failure, roll back all
   changes and leave the old database readable by its existing code.

Use one transformation shared by Android and JDBC tests. Android `onUpgrade` must use its
already-open SQLiteDatabase, not recursively request `writableDatabase` or the repository.
Fresh creation must produce the same final schema/constraints as upgrade. Reserved slots and
built-ins must not consume a legacy-custom creation limit or cause row loss; keep the separate
100-entry document bound. Test a maximally populated legacy database explicitly.

Slot operations are kind-scoped: list, fill/save, rename, clear and apply. Clearing retains ID
and slot position; built-ins remain read-only. Never use a title or row order as identity.
Later providers add their starter idempotently without another SQL version solely for content.

## M2.4 — UI ownership and asynchronous writes

Key Setup/Presets ViewModels and saveable draft state by kind. Every scheduled autosave captures
kind, base revision and edit generation. On newer edit, preset apply, mode disposal or recreation,
cancel or invalidate the earlier job; a late completion still must pass revision validation.
Do not rely on cancellation alone to prevent a database write that has already begun.

Keep invalid text/ranges across rotation in SavedStateHandle, including field errors; never
coerce a blank numeric field into a playable default. Load completion must not overwrite edits
made after initialization. Disable operations requiring an unresolved initial snapshot and
represent loading/write/conflict failures explicitly. Test dialogs remaining attached to the
mode/preset that opened them even if selection changes underneath.

The drawer uses stable IDs while preserving order. The owner kept the display label
"Perfect pitch" (2026-10-11), so the rename in D10 is withdrawn; the stable kind id is unchanged. Before
M3, playing mode-change requests must be unavailable or explicitly refused; do not approximate
safe switching by stopping/restarting audio. Stopped navigation/storage is fully functional.

## M2.5 — Visual verification without automatic approval

Extend the screenshot case manifest as screens become available; do not hard-code “nine” in
multiple scripts. Keep the nine existing approved files intact. Introduce an explicit automated
report mode alongside strict verification; document the exact new flag/entry point when added.
The existing `check-ui.sh` strict behavior remains a nonzero exit on missing baseline/mismatch.

For the deliberate drawer-label change, declare the case, baseline hash, reason and bounded
expected difference region. Generate candidates into ignored output. Compare all unchanged
cases strictly, require every expected candidate, and test pixels outside declared change
regions plus semantics/layout inside them. New cases and declared differences are reported
`PENDING_VISUAL_APPROVAL`; malformed files, missing candidates, undeclared differences and
render failures still fail automation. Never broaden an allowance just to pass a failed check.
Wire this reporting into automated-only full verification without claiming strict goldens pass.

## Regression matrix

| ID | Required evidence |
| --- | --- |
| M2-T01 | Settings v1→v2 preserves every field; old lastMode aliases map; invalid/future versions stay protected. |
| M2-T02 | SQL versions 1–8→9 and fresh schema agree; all rows/slot IDs/adoption state survive. |
| M2-T03 | Fault after each migration phase rolls back tables, rows and user_version; retry succeeds once. |
| M2-T04 | Forty unique slots; mode-scoped rename/clear/apply cannot touch another mode; legacy customs survive. |
| M2-T05 | v1/v2 document round trips; future kind/version, duplicate keys, limits and malformed seed fail atomically. |
| M2-T06 | Delayed save, preset application, A→B→A, loading race and recreation cannot cross-write modes. |
| M2-T07 | Invalid drafts remain editable; failed writes never falsely acknowledge selection, preset or Start. |
| M2-T08 | Screenshot guards reject missing/malformed/undeclared cases, protect baseline hashes and report pending cases honestly. |
| M2-T09 | Android/JDBC migration parity and process restart restore selected setup stopped, with all slots intact. |

Use injected failing database/store adapters and controlled dispatchers. Add regressions before
repairing observed races. Do not simulate migration success only by decoding hand-built v2 data.

## Commands and completion

Future implementation checks, from the repository root after `source scripts/env.sh`:

```bash
./gradlew --dependency-verification strict :core:data:testDebugUnitTest :app:testDebugUnitTest
./scripts/check-fast.sh
./scripts/capture-ui-candidates.sh .local/verification/mode-expansion/M2/ui-candidates
EAR_NATIVE_EVIDENCE_DIR=.local/verification/mode-expansion/M2/native EAR_REVIEW_OUT=.local/verification/mode-expansion/M2/device ./scripts/check-full.sh --automated-only
```

Full verification depends on the explicit pending-visual report implemented in M2.5. Record
strict visual comparison separately; its expected drawer failure is not a visual approval.
Complete when M2-T01–T09 pass on host/isolated phone as applicable, all preserved-data hashes
are accounted for, and storage/UI/verification contracts plus the implementation ledger are
updated. A storage blocker prevents M3; a named human visual row remains open without stopping
other authorized work. Do not delete old data or regenerate migration fixtures to unblock tests.
