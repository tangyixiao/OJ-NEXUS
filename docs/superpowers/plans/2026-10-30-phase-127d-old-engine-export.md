# Phase 127D Old-Engine Export Compatibility Implementation Plan

**Goal:** backup export works on every minSdk 26 engine: VACUUM INTO where available, a
lock-protected checkpoint fallback below SQLite 3.27, both validated before publishing.

**Architecture:** version-gated snapshot creation inside `BackupRepository`; the fallback
uses one dedicated framework connection under `BEGIN EXCLUSIVE` + `wal_checkpoint(TRUNCATE)`
and reuses the restore-side validation contract.

**Tech Stack:** existing Kotlin, framework SQLiteDatabase, Robolectric real files, connected
tests on two dedicated AVDs (API 37.1 and API 27).

**Spec:** [binding design](../specs/2026-10-30-phase-127d-old-engine-export-design.md).

## Global Constraints

- Base: Phase127C tip on the `codex/phase-127-recovery-safety` line; deliver as a stacked
  scoped slice extending PR #25's branch line.
- VACUUM INTO path unchanged for modern engines; no schema/dependency/version change; no
  release. Never copy main without a proven successful TRUNCATE checkpoint.
- The API 27 image download decides the device-verification leg: if unavailable on this
  host, record BLOCKED with the exact command instead of claiming old-engine evidence.

## Task 1 — Version gate, checkpoint fallback and validation

**Files:** `app/src/main/java/com/ojnexus/core/data/repository/BackupRepository.kt`
(`createSnapshotFile` gains the gate and fallback; an internal version-seam constructor
parameter for tests; validation of the staging file on both paths);
tests `app/src/test/java/com/ojnexus/core/data/repository/BackupEngineExportTest.kt`
(new, real SQLite files via framework connections).

**Interfaces:** public API unchanged (`exportTo`/`exportSnapshot` stay Boolean). Internal:
`engineSupportsVacuumInto` seam; the fallback helper returns only after a validated copy.

- [ ] RED: with the seam forced below 3.27, the present code fails the export; the new test
      requires a complete snapshot whose WAL-only row is present (stale-main prohibition).
- [ ] Implement the gate + exclusive-checkpoint fallback + staging validation on both paths.
- [ ] GREEN: WAL-only row captured under the forced-old seam; modern seam keeps VACUUM INTO;
      the two existing snapshot repository suites pass on the default engine.

## Task 2 — Connected export test and real old-engine run

**Files:** `app/src/androidTest/java/com/ojnexus/core/data/repository/BackupEngineExportTest.kt`
(new connected test: build a WAL-mode database, commit a row, export through
BackupRepository, read the row back from the archive — self-selects the engine path);
dedicated AVD runs.

- [ ] Connected test passes on the API 37.1 AVD (modern path).
- [ ] API 27 image + dedicated AVD (`OJNexus_Phase127_OldEngine`): run the connected suite;
      record the engine version (`SELECT sqlite_version()`) and totals per engine, or the
      download blocker honestly.
- [ ] Emulators shut down afterwards; no user device or data touched.

## Task 3 — Gates, review, evidence and scoped PR

**Files:** `docs/verification/phase-127d-old-engine-export.md`; roadmap + DATA_SAFETY
updates; handoff addendum; stacked branch + PR.

- [ ] Fresh `testDebugUnitTest --rerun test assembleDebug assembleRelease` at the final head.
- [ ] Independent scoped review; fix real findings and reverify changed paths.
- [ ] Diff/secrets check, scoped commits, push, PR; roadmap/phase status updated without
      claiming the full Phase127 release gate.
