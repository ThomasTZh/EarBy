# M0 — Baseline and agent handoff

Status: PLANNED. Prerequisite: explicit implementation instruction for this plan.
Next: [M1](M1_SHARED_EXERCISE_PIPELINE.md). Shared rules: [contracts](SHARED_CONTRACTS.md).

## Goal and completion evidence

Establish the exact input snapshot and tools against which mode expansion will be judged.
Do not reinterpret historical passes as a fresh baseline. The recent tidy removed generated
outputs; rebuilding them is expected and does not justify redownloading existing toolchains.

M0 is complete when protected inputs are recorded, current checks have concrete outcomes,
the expansion authorization is documented, and another agent can resume from this folder.

## Owning files and allowed changes

Read README, AGENTS, STATUS, AGENT_WORKFLOW, ARCHITECTURE, OPEN_SOURCE, TEST_STRATEGY and
the existing project review. Scope is evidence/scripts only where a demonstrated baseline
check needs repair, plus documentation of the authorized feature track. No training behavior,
schema, asset or dependency changes belong to M0.

Preserve `projectPlan_v0.2.md`; the detailed documents are its refinements, not a replacement
copy in another folder. Keep `.local/dist/project-review-2026-10-11/` and existing cleanup/
verification archives. Preserve planDraft.md and the Git directory.

## Ordered work packages

### M0.1 — Establish workspace identity

1. Capture `git --no-optional-locks status --porcelain=v1 --untracked-files=all`.
2. Inventory production/test sources, build/catalog/lock files, scripts, manifests and docs.
3. Hash all project inputs, including ignored bundled SoundFont, existing voice clips,
   screenshot baselines and generator/timeline fixtures. Exclude generated outputs, caches,
   installed tool binaries and ignored raw candidates from the source-input manifest.
4. Separately inventory existing evidence/releases; do not copy multi-gigabyte caches.
5. Record that an untracked scaffold is existing owner work. Never use clean/reset as a
   shortcut for defining the baseline or create a commit without an instruction.

Write `inputs-before.json`, `git-status.txt` and `inventory.md` under the M0 evidence folder.

### M0.2 — Recheck tools and IDE readiness

1. Source `scripts/env.sh`; record JDK, Gradle wrapper, SDK/NDK/CMake and uv versions/paths.
2. Confirm the wrapper/dependency-verification files are the recorded inputs.
3. Recheck Android Studio MCP modules, dependencies and run configuration against Gradle.
4. Record the known semantic model issue R28, then inspect whether it still applies.
5. Try usable semantic/source inspections once. If the model remains incomplete, record
   exact failures and continue CLI work; do not spend the milestone inventing a new toolchain.
6. Read ADB device state. Select the authorized device explicitly for later runs; record
   serial/model/API/ABI/page size only in ignored evidence. Do not alter phone settings here.

### M0.3 — Execute the current baseline

Run from repository root:

```bash
source scripts/env.sh
./scripts/check-env.sh --build
./scripts/check-fast.sh
./scripts/check-assets.sh
./scripts/check-ui.sh
```

Capture full output and exact exit status separately for each command. `check-fast` includes
host tests and builds; record executed/cached task distinctions. Do not claim the historical
450/33 counts as the new result. Do not regenerate approved screenshots to fix a mismatch.

If a baseline failure is environmental, fix the narrow environment issue and rerun its check.
If a source failure predates feature work, assign a finding ID, demonstrate it, repair it in
its owner and keep the evidence. Do not proceed as if a failing baseline had passed.

### M0.4 — Establish authorization and reporting

After explicit implementation authorization exists, add a current note to README/STATUS/
ROADMAP linking this index and identifying M0–M11 as the authorized expansion. Preserve old
records as history. State that this track may proceed while G3/G4 human rows remain open;
this does not close those gates or authorize all remaining Stage 6 work.

Set M0's actual state in IMPLEMENTATION_STATUS and append its package record. Inventory
expected semantic voice IDs and schema transitions at a high level; detailed work belongs
to M2/M4. Carry forward the resolved D08/D09 ToneDear procedures; ask only if new evidence
requires a pivotal change to the recorded decisions.

## Acceptance checks

| ID | Required evidence |
| --- | --- |
| M0-A01 | Source/asset/fixture/baseline hashes and untracked-state inventory exist. |
| M0-A02 | Fresh environment/fast/asset/UI command outcomes are recorded, with failures resolved or concretely blocking. |
| M0-A03 | MCP readiness is observed; incomplete analysis is not described as clean. |
| M0-A04 | Regular phone app, prior APKs, assets and historical evidence are untouched. |
| M0-A05 | Authorization note is accurate and scoped; all later milestones remain PLANNED. |

## Handoff and failure boundaries

Record exact baseline manifest paths, check logs, tool versions, device readiness and the
first M1 subpackage. No new runtime public interfaces or schema changes should exist.
Human acoustic/accessibility gaps remain named; they do not turn automated checks into passes.
