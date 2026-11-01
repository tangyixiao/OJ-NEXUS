# Phase 127C Typed Import Storage Errors Implementation Plan

**Goal:** import storage failures (RV004–RV006) return typed categories, leave no orphan
staging evidence behind, and reach the user as stable uppercase copy instead of the
export-failure string.

**Architecture:** pure classification in the protocol, full staging injectability on the
existing `RestoreFileOperations` boundary, typed outcome threaded through
BackupRepository → SettingsViewModel → SettingsScreen.

**Tech Stack:** existing Kotlin, Robolectric real-file fault harness, Compose strings only.

**Spec:** [binding design](../specs/2026-10-29-phase-127c-import-storage-errors-design.md).

**Execution:** completed for the Phase127C boundary at `ed7e873`;
spec `3b66151`, plan `ff0c6a3`, classification `6d65852`, typed surfacing `545aa77`,
review fixes `ed7e873`; [evidence](../../verification/phase-127c-import-storage-errors.md).
Execution notes, recorded rather than hidden: the Task 1 RED was demonstrated at the
coordinator level (4 failing tests including the RV004 crash path); the Task 2 typed-surfacing
test is a pin added with the behavior it covers because the Boolean collapse had no
observable typed surface to assert against — the underlying category defect was RED-proven
in Task 1. One test-side correction (an unarmed fault flag) occurred during GREEN and is
retained in the run log. Two connected runs aborted when the emulator process died under
host memory pressure from two idle Gradle daemons; after stopping them the clean run passed
35/35 at the final head.

## Global Constraints

- Base: Phase127B delivery tip on `codex/phase-127-recovery-safety`; deliver as a stacked
  scoped slice extending PR #24's branch line (new branch, PR against the 127B branch when
  that is the reviewed head).
- RV001–RV003 validation behavior unchanged; no schema/dependency/version change; no release.
- Every commit builds green; a boundary-extension commit with default behavior may precede
  the behavioral RED if needed, and the RED is recorded against the pre-fix behavior.
- The known double-stage edge (a second import before restart overwrites a staged pending
  file under the old journal) is pre-existing behavior; this slice must not worsen it and
  records it as a documented limitation.

## Task 1 — Typed classification, staging injectability and retryable cleanup

**Files:** `app/src/main/java/com/ojnexus/core/data/restore/RestoreProtocol.kt` (enum values
+ pure classifier), `RestoreFileOperations.kt` (add `createStagingTemp` and
`copyStreamAndSync` with system defaults), `DatabaseRestoreCoordinator.kt` (route staging
through the boundary; catch IOException → typed; best-effort owned-artifact cleanup);
tests: new `app/src/test/java/com/ojnexus/core/data/restore/ImportStorageFaultTest.kt`
(Robolectric, injected boundary decorators, real files).

**Interfaces:** `RestoreFailure.STORAGE_FULL` / `STORAGE_IO` appended;
`classifyImportIoFailure(Throwable): RestoreFailure?`; existing public coordinator API
unchanged. Cleanup policy: staging temp and journal temp always deleted on failure; the
staged pending file deleted only when no journal existed before the run.

- [x] Extend the boundary with default-system behavior; `prepareImport` routes staging-temp
      creation and the input copy through it; suite stays green.
- [x] RED: fault tests prove the present defects — journal-write failure and staged-copy
      failure return `FILE_REPLACEMENT_FAILURE` and leave pending/journal evidence that
      would block the next start.
- [x] Implement typed classification and owned-artifact cleanup; GREEN with retry
      convergence, pre-existing-journal preservation and the INVALID_SQLITE pin.

## Task 2 — Typed outcome through the repository, ViewModel and settings UI

**Files:** `app/src/main/java/com/ojnexus/core/data/repository/BackupRepository.kt`
(`importFrom` returns `RestoreOutcome`), `app/src/main/java/com/ojnexus/feature/settings/SettingsViewModel.kt`
(`BackupResult` carries `RestoreFailure?`), `app/src/main/java/com/ojnexus/feature/settings/SettingsScreen.kt`
(typed import-failure mapping), `res/values/strings.xml` + `res/values-zh-rCN/strings.xml`
(three new keys, real zh translations);
tests: ViewModel-level test for the typed `BackupResult` (RED first against the current
Boolean-only result).

- [x] RED: a rejected import today surfaces `restoreFailure == null`.
- [x] Thread the outcome through and map the three UI cases; export failure keeps its copy.
- [x] GREEN: ViewModel test passes; resource keys exist in both catalogs with parity.

## Task 3 — Gates, review, evidence and scoped PR

**Files:** `docs/verification/phase-127c-import-storage-errors.md`; roadmap Phase127
substage update; DATA_SAFETY open-items refresh; handoff addendum; stacked branch + PR.

- [x] Fresh `testDebugUnitTest --rerun test assembleDebug assembleRelease`; connected suite
      on the dedicated AVD when the host allows; record exact totals and warnings.
- [x] Independent scoped review of the diff against the spec; fix real findings and reverify.
- [x] Diff/secrets check, scoped commits, push, PR; update evidence and handoff without
      claiming the whole phase.
