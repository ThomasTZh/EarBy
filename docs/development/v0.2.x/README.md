# v0.2.x mode expansion: agent implementation plans

Status: **v0.2.x is final (2026-10-13)** at version 0.2.0. Milestones M0–M5 are implemented
and recorded in [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md); the remaining plans
(M6–M11) carry forward to **v0.3.x**. Prepared 2026-10-11 from the current source and the
owner's planning decisions.

## Purpose and reading order

Fill the seven existing mode placeholders through the existing offline player. Each mode
must include generation, validation, spoken answers, all three learning recipes, Play/Setup/
Presets integration, persistence and automated verification. A working generator alone is
not a completed mode.

Read the repository README and development STATUS first, then:

1. [Original overall plan](projectPlan_v0.2.md), preserved as supplied.
2. [Decisions and refinements](DECISIONS.md), including owner answers and proposed defaults.
3. [Shared implementation contracts](SHARED_CONTRACTS.md).
4. [Implementation status and handoff ledger](IMPLEMENTATION_STATUS.md).
5. The earliest unfinished milestone below, followed by its owning source and tests.

The owner's chord/scale procedure answers are resolved in the [ToneDear reference](REFERENCE_PROCEDURES.md).
Equivalent chord labels are retained; scale fragments are deferred. These refinements supersede
the relevant proposals in the preserved overall plan.

The detailed documents refine the overall plan. Recorded owner decisions take precedence.
The location requested by the owner is this directory: M0 must use these documents rather
than creating duplicate MODE_EXPANSION_PLAN/STATUS documents at the older proposed paths.

## Ordered milestones

| Milestone | Deliverable | Prerequisite | Detailed plan |
| --- | --- | --- | --- |
| M0 | Baseline, authorization scope and reproducible evidence | Explicit instruction to implement | [M0](M0_BASELINE_AND_HANDOFF.md) |
| M1 | Generic exercise pipeline with unchanged interval behavior | M0 | [M1](M1_SHARED_EXERCISE_PIPELINE.md) |
| M2 | Per-mode settings, five slots per mode, safe drafts/migrations | M1 | [M2](M2_MODE_STORAGE_AND_UI_STATE.md) |
| M3 | Mode changes after the current learning sequence | M2 | [M3](M3_SAFE_MODE_SWITCHING.md) |
| M4 | Musical fixtures, spelling and expandable voice vocabulary | M3 | [M4](M4_THEORY_FIXTURES_AND_VOICE_ASSETS.md) |
| M5 | Chord quality and inversion training | M4 | [M5](M5_CHORDS.md) |
| M6 | Complete scales and directions; fragments deferred by D09 | M5 | [M6](M6_SCALES.md) |
| M7 | Note identification, including legacy Perfect pitch selection | M6 | [M7](M7_NOTE_IDENTIFICATION.md) |
| M8 | Scale degrees and reusable tonal context | M7 | [M8](M8_SCALE_DEGREES_AND_CONTEXT.md) |
| M9 | Progressions, then intervals in context | M8 | [M9](M9_PROGRESSIONS_AND_CONTEXTUAL_INTERVALS.md) |
| M10 | Passive melodic dictation | M9 | [M10](M10_PASSIVE_MELODIC_DICTATION.md) |
| M11 | Complete automated integration and final handoff | M10 | [M11](M11_INTEGRATION_AND_ACCEPTANCE.md) |

Work sequentially. M9 has two ordered sub-deliverables, M9A then M9B. Do not start
unrelated roadmap work to satisfy an old dependency: weighted sequencing, interval walks,
contrast, intonation, mixed sessions, clusters, history statistics and adaptation are deferred.

## Authorization and acceptance

The request to save these plans authorizes documentation work only. Future explicit
implementation authorization for this plan covers M0–M11; it does not authorize publication,
commits, unrelated features or incidental dependency upgrades. At that point M0 records the
narrow exception to the old Stage 5 gate wording. Do not claim that saving this plan closes
G3/G4 or authorizes implementation now.

Keep automated delivery and human acceptance separate. New voice candidates and changed
screenshots may have passing machine checks with listening/visual approval still pending.
Never mark human review as accepted from a model's analysis or an automated substitute.

## Definition of a completed milestone

- Its subpackages and acceptance IDs have evidence, not just source changes.
- New behavioral regressions fail before their repair and pass afterwards where applicable.
- Required targeted checks and `scripts/check-fast.sh` pass for code changes.
- Migration and audio/service integration receive the appropriate full check; M11 always
  runs the complete isolated-phone suite and real 30-minute endurance session.
- All changes, exact commands/results and open limitations are recorded in the ledger.
- The next agent can identify what to do next without reading conversation history.

Use `AUTOMATED_COMPLETE / HUMAN_OPEN` when software checks pass and named human rows remain.
Use `BLOCKED` only for a concrete missing prerequisite; retain partial work/evidence. Do not
wait at every milestone for routine approval once implementation is authorized.

## Repository rules and reference documents

- [Agent workflow](../AGENT_WORKFLOW.md): package ownership, verification and reporting.
- [Development status](../STATUS.md): historical evidence and present limitations.
- [Architecture](../../architecture/ARCHITECTURE.md): existing ownership and lifecycle rules.
- [Expansion roadmap](../../product/STAGES_5_TO_8_TASKS.md): original musical vocabulary.
- [Open-source choices](../OPEN_SOURCE.md): pinned infrastructure and development tooling.
- [Testing strategy](../../testing/TEST_STRATEGY.md): current host/device entry points.

Existing source is largely untracked and there is no initial commit. Preserve it using
inventories and hashes; do not use git clean/reset, replace .git or infer a clean starting
tree. Preserve original fixtures, archived APKs, historical evidence and planDraft.md.

## Suggested implementation instruction

When implementation is wanted, the following instruction identifies this exact plan set:

> Implement docs/development/v0.2.x/README.md and its M0–M11 plans sequentially. Read the
> decisions, shared contracts and implementation ledger first. Preserve existing work, add
> targeted regressions, run the specified checks and update the ledger after each package.
> Continue between milestones; ask only for new pivotal decisions or concrete blockers.

This example is a handoff template, not an instruction already issued by this documentation.
