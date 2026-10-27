# Phase 127B Strict Worker Generation Implementation Plan

**Goal:** every background task is admitted only on a captured generation that exactly
matches the resolved process generation; refusals are auditable; the ready-start bootstrap
replaces the periodic queue with freshly stamped entries.

**Architecture:** the frozen Phase126 classifier stays authoritative; the three workers and
the backup scheduler consume it strictly, and one dispatcher entry records refusals.

**Tech Stack:** existing Kotlin, Room, WorkManager 2.11.2, Compose untouched, JUnit/Robolectric.

**Spec:** [binding design](../specs/2026-10-27-phase-127b-worker-generation-design.md).

## Global Constraints

- Base `5c6a6e7` on `codex/phase-127-recovery-safety`; deliver as a stacked scoped slice,
  keeping PR #20 scoped to 127A. Push/PR cadence is pre-authorized; merging is not.
- Classifier values and `contracts/recovery/v1` projections unchanged. Native dependencies,
  schema, credentials and app version unchanged.
- No release, no version bump, no full Phase127 VERIFIED claim. 127C typed import storage
  errors and old-engine export compatibility stay open.
- Full local gates are re-run fresh for this slice; inherited 829/35 evidence is baseline,
  not acceptance.

## Task 1 — Strict admission in the sync and Luogu workers with auditable refusal

**Files:** `app/src/main/java/com/ojnexus/judge/sync/JudgeSyncWorker.kt`,
`app/src/main/java/com/ojnexus/judge/luogu/open/LuoguOpenResultWorker.kt`,
`app/src/main/java/com/ojnexus/judge/JudgeSyncDispatcher.kt`;
tests `app/src/test/java/com/ojnexus/core/data/restore/RestoreWorkGenerationTest.kt`
(unchanged expectations stay), `app/src/test/java/com/ojnexus/judge/SyncOperationRecordingTest.kt`,
new `app/src/test/java/com/ojnexus/RecoveryGenerationAdmissionTest.kt` with a Ready counting
probe application.

**Interfaces:** `recordStaleGeneration(judge, accountId, staleGeneration: String?)` gains
null/blank support (empty-generation ledger row, same `STALE_GENERATION` status and
`STALE_DATA_GENERATION` marker); account-missing and same-generation guards unchanged.
Workers normalize the stamp (trim, blank = missing) before `decideRestoreWorkGeneration` and
map STALE → recorded skip with `STALE_DATA_GENERATION`, LEGACY → recorded skip with
`UNSTAMPED_DATA_GENERATION`; only PROCEED reaches the dispatcher/result poll. Luogu refusals
write no Room state. Existing public signatures remain source-compatible.

- [ ] RED: focused admission tests prove the present defect — unstamped judge sync reaches
      the dispatcher (no refusal marker, no empty-generation ledger row) and an unstamped
      Luogu poll proceeds into the result path.
- [ ] Implement worker normalization, refusal markers and dispatcher recording.
- [ ] GREEN: admission tests pass with exactly one auditable ledger row per refused sync and
      zero coordinator operations; classifier tests still pass unchanged; backup worker in
      Task 2 keeps its current behavior until its own slice lands.

## Task 2 — Scheduled backup stamping, admission and queue replacement

**Files:** `app/src/main/java/com/ojnexus/core/data/backup/ScheduledBackupWorker.kt`,
`app/src/main/java/com/ojnexus/core/data/backup/BackupCadence.kt` (scheduler),
`app/src/main/java/com/ojnexus/judge/sync/JudgeSyncWorker.kt` (periodic policy);
tests in `RecoveryGenerationAdmissionTest.kt` plus a focused scheduler stamp assertion.

**Interfaces:** `ScheduledBackupWorker` reads `DATA_GENERATION` after the readiness gate and
refuses STALE/LEGACY before preferences or export with the same two markers; successful
"schedule cancelled after queued" stays a plain success. `ScheduledBackupScheduler.update`
stamps the Ready container's current generation into the request input and keeps UPDATE.
`JudgeSyncWorker.enqueuePeriodic` switches `ExistingPeriodicWorkPolicy.KEEP` → `UPDATE`.

- [ ] RED: backup worker with stale/unstamped input must return the marker before any
      preferences read (DataStore file stays absent); scheduler request must carry the stamp.
- [ ] Implement stamping, backup admission and the judge periodic UPDATE replacement.
- [ ] GREEN: admission tests cover all three workers' stale/unstamped refusals; the plain
      "schedule cancelled" success shape is unchanged.

## Task 3 — Full gates, evidence and scoped delivery

**Files:** `docs/verification/phase-127b-worker-generation.md`; roadmap Phase127 substage
update; DATA_SAFETY open-items paragraph refresh; PR branch `codex/phase-127b-generation-admission`
stacked on the 127A head, PR against `codex/phase-127-android-base`.

- [ ] Fresh `testDebugUnitTest --rerun test assembleDebug assembleRelease`; collect XML
      totals and note real warnings, not pristine claims.
- [ ] Connected suite on the dedicated Phase127 test AVD when the host allows emulator
      startup; record the exact serial and totals, or the blocker honestly.
- [ ] Diff/secrets check, scoped commits, push stacked branch, open the PR; update evidence
      and roadmap without claiming the whole phase.
