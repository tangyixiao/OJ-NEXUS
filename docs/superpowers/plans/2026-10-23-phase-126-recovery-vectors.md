# Phase 126 Recovery Fault Vectors Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development or superpowers:executing-plans
> to execute task-by-task with the already authorized subagent method.

**Goal:** define a shared, executable recovery-vector corpus without overstating native recovery coverage.

**Architecture:** JSON v1 is the single test input. Test-only Kotlin DTOs validate it and invoke the
existing production recovery/generation decisions; native file implementations remain independent.

**Tech Stack:** JSON, Markdown, existing Kotlin serialization/JUnit and Gradle; no new dependency.

**Spec:** [design](../specs/2026-10-23-phase-126-recovery-vectors-design.md).

## Global Constraints

- Baseline `becc0e3` on the evidence stack; Android production recovery functions match main.
- 32 synthetic cases RV001–RV032; only 9 Android projections, all strictly decision/classification level.
- No product storage engine, credentials, native schema version, absolute host path or backup-format change.
- Keep original worktrees and all published assets intact; no automatic merge or version bump.
- Scope/semantics/enums and known gaps are fixed by the spec. Structural validity is not fault recovery.

## Review Focus

1. RV015 generation repair and RV017 failed rollback must remain unclaimed until real consumers exist.
2. RV004/005/006 split temp creation, copy and journal ENOSPC; a missing file is not injected ENOSPC.
3. RV022 LEGACY classification cannot authorize stale/unknown worker writes.
4. RV029/030 distinguish valid empty REPLACE/UPSERT from RV031 missing/invalid response.
5. A malformed, unsupported-version or duplicated-ID corpus must fail before any production decision runs.

## Task 1 — Canonical corpus and strict test-only loader

**Files:** create `contracts/recovery/v1/vectors.json`, `contracts/recovery/v1/README.md`,
`app/src/test/java/com/ojnexus/core/data/restore/RecoveryVectorFixtures.kt`,
`app/src/test/java/com/ojnexus/core/data/restore/RecoveryVectorCorpusTest.kt`;
modify test resources in `app/build.gradle.kts` to include root `contracts/`.

**Interfaces:** `RecoveryVectorFixtures.decode(text: String): RecoveryVectorCorpus` and
`RecoveryVectorFixtures.load(): RecoveryVectorCorpus`; test-only serializable DTOs expose
`vectors`, `id`, `expected`, `retry`, nullable typed `android` projection. Reject unsupported
version, missing/unknown fields, invalid enums, duplicate IDs and broken invariants.

- [ ] Add the fixture tests before the corpus/loader. Run the focused class and record a meaningful RED.
- [ ] Encode exactly the 32 spec cases and nine projections; document each key and symbolic old/new meaning.
- [ ] Load the single classpath resource `recovery/v1/vectors.json`; no second copied corpus.
- [ ] Assert exact IDs/projection set; no-write cases have writes=false, blocked cases preserve artifacts,
  aborted imports keep OLD/OLD, valid empty cases use distinct semantics, and denied retry uses after=NONE.
- [ ] Mutation negatives: version2, duplicate ID, missing expected, unknown enum/field, old worker writes=true,
  blocked cleanup=false, identical replacement/upsert empty result, retry allowed with no prerequisite.
- [ ] Run `:app:testDebugUnitTest --tests '*RecoveryVectorCorpusTest'`; require all positives/negatives pass.
- [ ] Commit only this coherent corpus/loader/resource change after diff/secrets check.

## Task 2 — Actual decision consumption and honest platform adoption

**Files:** create `app/src/test/java/com/ojnexus/core/data/restore/RecoveryVectorDecisionTest.kt`,
`docs/verification/phase-126-recovery-vectors.md`; update `docs/DATA_SAFETY.md` and roadmap status.

**Interfaces:** consume Task1 DTOs. For RECOVERY_DECISION construct actual `RestoreJournal?` /
`RestoreFileState` and call `decideRestoreRecovery`. For GENERATION_DECISION call
`decideRestoreWorkGeneration`. No duplicate decision implementation or simulated storage engine.

- [ ] Add parameterized tests for each of the nine projections with IDs in JUnit display names.
- [ ] Assert RV015/RV017 and all unbound cases are explicitly not covered by this consumer.
- [ ] Run the focused corpus/decision classes; retain XML counts and exact executed IDs.
- [ ] Record platform source/atomic-unit/adoption matrix and 127–130 handoff gaps from the design/audits.
- [ ] Correct DATA_SAFETY export wording with explicit reviewed-branch/main/release identity; do not
  advertise unmerged 122 source as the already-published v1.0.3 behavior.
- [ ] Run fresh `testDebugUnitTest --rerun test assembleDebug` using process-local JBR/SDK variables.
  Record BUILD SUCCESSFUL and actual XML totals; no emulator/device-fault claim from these pure tests.
- [ ] Independently review all vectors/projections/relational negatives and the coverage matrix.
- [ ] Check links/diff/secrets, commit, push and create a scoped PR based on
  `codex/phase-121-platform-baseline`; attach it. Mark 126 VERIFIED only for this contract scope.

Phase127 then consumes these exact IDs plus the Phase122 accepted implementation; Windows128,
Linux129 and Apple130 keep their existing runtime/integration prerequisites.
