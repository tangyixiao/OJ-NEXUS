# Phase 127B — Strict worker generation admission and queue replacement

Status: implementation design under the authorized Phase 121–220 roadmap. One delivery
boundary within the still-ACTIVE Phase 127; it does not close typed import-storage errors
or old-engine export compatibility.

## Baseline and purpose

Branch `codex/phase-127-recovery-safety` at `5c6a6e7` (Phase127A acceptance closed, PR #20
open/unmerged against integration base `codex/phase-127-android-base`). Fresh full gates at
`690b5f5`: 829 unit tests / 181 suites and 35 connected tests, zero failures; they are the
inherited baseline, re-run fresh before this slice's evidence.

RV022 of `contracts/recovery/v1` requires that resumed work without a captured generation has
no write authorization (`originating_work_may_write: false`, next action "Reconcile and
requeue with explicit current generation"). Current Android behavior contradicts this: the
generation classifier correctly labels a missing stamp `LEGACY`, but JudgeSyncWorker and
LuoguOpenResultWorker still proceed on that label, and ScheduledBackupWorker carries no
generation stamp or admission check at all. This slice makes admission strict and makes the
ready-start bootstrap reconcile (re-stamp) the queue, so strict refusal cannot permanently
wedge background work after an upgrade or a restored generation.

## Contracts and invariants

- `decideRestoreWorkGeneration` and the `RestoreWorkDecision` values are frozen Phase126
  projections (RV020 STALE, RV021 PROCEED, RV022 LEGACY). This slice changes only the
  consuming policy; it must not reinterpret the classifier or the contract corpus.
- Only `PROCEED` authorizes ordinary work. `STALE` is refused and recorded in the sync
  operations ledger. `LEGACY` (missing or blank captured stamp) is refused without any
  business write; the ledger records it with an empty generation so refusals stay auditable.
- A refusal never mutates judge profiles, submission jobs, backup documents, backup status
  preferences, or any network call. Output markers are stable strings, not raw errors.
- Every worker keeps the 127A readiness gate ahead of admission: Starting retries, Blocked
  fails with `result=RECOVERY_BLOCKED`, and neither touches preferences, ledger or WorkManager
  resolution. Admission runs after the Ready snapshot is taken.
- Queue replacement: every ready start re-stamps the periodic queue. Periodic judge sync and
  scheduled backup enqueue with `ExistingPeriodicWorkPolicy.UPDATE` so the replacement carries
  the current generation; Luogu pending jobs are re-enqueued by the existing bootstrap with
  the current stamp. An entry that races one run unstamped is refused once, then replaced.
- No schema change, no app version bump, no new dependency, no release claim.

## Worker contract

All three workers normalize the captured stamp (trim; blank equals missing) before deciding.

| Worker | STALE | LEGACY / unstamped | PROCEED |
| --- | --- | --- | --- |
| JudgeSyncWorker | `recordStaleGeneration(judge, accountId, stamp)` then success with `STALE_DATA_GENERATION` | `recordStaleGeneration(judge, accountId, null)` then success with `UNSTAMPED_DATA_GENERATION` | normal sync |
| LuoguOpenResultWorker | success with `STALE_DATA_GENERATION`, no Room write | success with `UNSTAMPED_DATA_GENERATION`, no Room write | normal result poll |
| ScheduledBackupWorker | success with `STALE_DATA_GENERATION`, no backup and no backup-status record | same refusal | normal export |

`JudgeSyncDispatcher.recordStaleGeneration` accepts a null/blank generation and records a
`STALE_GENERATION` ledger row with empty `dataGeneration` and the existing
`STALE_DATA_GENERATION` error marker; account-missing and same-generation guards stay.

`ScheduledBackupScheduler.update` stamps the current generation into the periodic request
input under the worker's `DATA_GENERATION` key and keeps its UPDATE replacement policy and
Starting/Blocked refusal. `JudgeSyncWorker.enqueuePeriodic` switches KEEP to UPDATE.
`enqueueManual` stays KEEP and stamps at enqueue as today.

## Acceptance

1. Classifier tests keep RV020/RV021/RV022 projections unchanged; a blank stamp is worker-
   normalized to unstamped, not silently compared.
2. Ready-state worker tests: stale and unstamped inputs return the exact markers with zero
   business I/O beyond the permitted admission recording; the sync ledger contains exactly
   one `STALE_GENERATION` row per refused sync (empty generation for unstamped) and no
   operation row from a coordinator run; the backup worker leaves backup-status preferences
   unwritten; the Luogu worker leaves submission-job rows untouched.
3. Ledger recording test: `recordStaleGeneration(null)` writes the auditable empty-generation
   row and still refuses a same-generation call.
4. Scheduler stamping: the backup periodic request input carries the current generation, and
   admission reads the same key.
5. Fresh full local gates (`testDebugUnitTest --rerun test assembleDebug assembleRelease`),
   connected suite on the dedicated Phase127 test AVD when available, scoped commits, and an
   evidence record that states what remains open for Phase127 (typed import storage errors,
   old-engine export compatibility, full release gate).
