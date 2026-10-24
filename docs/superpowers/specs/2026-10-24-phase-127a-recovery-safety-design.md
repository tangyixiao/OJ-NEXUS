# Phase 127A — Preserve recovery sources and gate startup

Status: implementation design under the authorized Phase 121–220 roadmap. This is one
delivery boundary within Phase 127, not completion of the whole phase.

## Baseline and purpose

Branch `codex/phase-127-recovery-safety` starts at integration commit `b096045`, combining
Phase 126 `036b333` with accepted Android Phase 122 composition `19f0bbc`. Neither is merged
into main. The reusable integration ref is `codex/phase-127-android-base`; a fix PR targets
that ref and states dependencies on PRs #14/#15/#18/#19. Do not modify those PR branches.
Fresh integration baseline: 800 tests / 176 suites, zero failures/errors/skips; Gradle
`testDebugUnitTest --rerun test assembleDebug` BUILD SUCCESSFUL in 2m08s.

Fix actual coordinator failures: APPLIED restart currently forgets generation repair;
post-APPLIED metadata errors can incorrectly trigger rollback; both initial preservation and
rollback consume main/WAL sources in separate moves; failed rollback becomes terminal cleanup;
Application constructs Room despite rejected recovery. Preserve all surviving data and expose
an explicit blocked startup instead of silently creating an empty store.

## Constraints and delivery boundary

- Native Kotlin/Room/Compose, existing dependencies and database schema/version remain unchanged.
- Protocol AND Application/Activity/three Worker startup gates ship together. Intermediate core
  commits are not a safe application release without the wiring task.
- Recovery filesystem operations occur only before the application Room graph opens. Copying
  closed recovery files is not a fallback for exporting an open SQLite database.
- Keep legacy journal encoding and stage names readable. Add stage values with defined ownership;
  do not reinterpret an old SWAPPING marker as proof of a new complete-copy boundary.
- Keep `contracts/recovery/v1` normative outcomes and nine legacy classifier projections intact.
  New native tests bind RV015/RV017 and additional checkpoints to real file/SQLite behavior.
- No credentials, raw error/path display, reset button, new driver, application version bump,
  power-loss claim or change to the accepted WAL-complete snapshot exporter.
- The full Phase 127 still requires typed ENOSPC/import errors, strict generation policy for all
  background work (including stale-ledger writes and scheduled backup), and safe minSdk 26 export
  compatibility with real old-engine acceptance. Record those as open, not removed scope.

## Startup contracts

Add `RecoveryDisposition { RECONCILE_FORWARD_ON_NEXT_START, INSPECTION_REQUIRED }` and
`RestoreOutcome.Blocked(failure: RestoreFailure, recovery: RecoveryDisposition)`.
Add `RestoreReadiness.Ready(outcome: RestoreOutcome, generation: String)` and
`RestoreReadiness.Blocked(failure: RestoreFailure, recovery: RecoveryDisposition)`.

`DatabaseRestoreCoordinator.resolveBeforeRoomOpen(): RestoreReadiness` performs exactly one
recovery pass followed by generation establishment. Preserve the existing
`restoreBeforeRoomOpen(): RestoreOutcome` entry for compatibility/tests. Blocked never mints
generation. APPLIED uses the exact journal generation; completed rollback requires the existing
OLD generation, never the attempted import generation. A clean install with no database and no
recovery evidence may establish its first generation and construct Room normally. Normal clean
existing stores keep Room's migration path; this slice does not prohibit legitimate old schemas.

Unreadable journal, orphan rollback/candidate/metadata-temp evidence, and ambiguous split-source
legacy states block and preserve. An orphan pending file without a journal does not authorize
automatic replacement. A rejected invalid import with a verified untouched OLD target can
continue safely; do not equate every Rejected with Blocked.

Application publishes one immutable `NexusStartupState`: Starting, Ready(container), or
Blocked(failure, recovery), with safe cross-thread visibility. `NexusStartupStateOwner` exposes
that state. Default is Starting. A ready-container accessor is a lookup, never a graph factory.
AppContainer receives resolved readiness; remove its independent recovery pass, and expose the
resolved immutable process generation while live replacement remains unsupported.

Application bootstrap AND crash-preference access occur only after Ready. MainActivity gates
preferences, NexusApp composition and onResume. Blocked uses a DB-free default NexusTheme screen
with resource-backed uppercase copy: `LOCAL RECOVERY BLOCKED`, `LOCAL FILES PRESERVED`, and
`AUTOMATIC RECOVERY STOPPED. CLOSE THE APP AND KEEP THE LOCAL FILES FOR INSPECTION.` No raw paths
or exception text. Starting shows a resource-backed initialization state without container access.
The four recovery keys must also exist in the current Chinese resource catalog to preserve its
key/placeholder parity contract; this slice retains the specified uppercase English copy there.

All three Workers resolve the startup owner before preferences, generation lookup, account reads,
ledger writes, export, network or result-status persistence. Starting returns retry with no business
I/O. Blocked returns failure with output `result=RECOVERY_BLOCKED`, never retry or stale-ledger
recording. Ready uses the immutable state snapshot's container. WorkManager's own scheduler DB is
outside the application Room guarantee; previously persisted worker business paths remain gated.
Public Judge/Luogu/backup enqueue entry points also refuse Starting/Blocked before resolving
generation or WorkManager. Luogu WorkManager resolution becomes lazy inside admitted enqueue;
explicit-generation overloads do not bypass readiness. Existing Unit signatures may remain no-op
on refusal; strict stamp and queue replacement semantics remain Phase127B.

## Filesystem ownership and ordering

Add one internal `RestoreFileOperations` boundary with `copyAndSync(File, File)`,
`writeAndSync(File, ByteArray)`, `atomicReplace(File, File)` and `deleteChecked(File)`.
Default uses real FileOutputStream fd.sync and atomic Files.move; missing delete is idempotent,
failed deletion is observable. Tests decorate real operations and fail a selected operation/path.
Keep actual SQLite validation; do not add a fake recovery interpreter.

Retain current rollback paths. Backup-copy temp paths append `.tmp` to those paths. Rollback
installation temps append `.restore-rollback-install` to normal main/WAL/SHM paths. Inventory and
cleanup include these, journal/generation temporary metadata, candidate and pending paths.

New stages:

| Durable stage | Ownership and startup action |
| --- | --- |
| SAVING_ROLLBACK | Live OLD files have not been mutated. Copy/sync complete main and present WAL/SHM into rollback-owned files. On interruption, validate the untouched OLD target and existing OLD generation, abort the attempted import and clean owned partial copies; if that cannot be established or cleanup fails, preserve and block. Never apply a partial new candidate. |
| INSTALLING | Written only after all OLD sources are copied/synced, or uninterrupted code knows there was no old target. Only now mutate live files and install candidate. Restart does not infer NEW identity from target validity; use complete rollback sources. If no rollback is available after interrupted INSTALLING, block instead of inventing a clean install. |
| ROLLING_BACK | Persist BEFORE any rollback destination mutation. Require main source, copy/sync source set to install temps, replace destinations under normal SQLite main/-wal/-shm names, validate, persist ROLLED_BACK, then reclaim originals. On interrupted/failed rollback, preserve every source and block inspection; do not blindly retry. |

An intent-write failure performs zero destructive destination operations. If ROLLING_BACK was
never persisted, a future start may retry writing that intent from INSTALLING; it has not retried
a partially executed rollback. Once durable ROLLING_BACK exists, restart performs no recovery
mutation. Do not pretend an enum can durably remember an I/O failure while every metadata write
fails. Catch-path diagnostics are optional best effort and cannot replace a Blocked result with
another exception; retaining the earlier intent is sufficient.

APPLIED is forward-convergence intent, not complete logical commit. A shared finalizer validates
NEW target, writes and reads back the exact journal generation, then performs checked cleanup
with journal LAST. Once APPLIED writing is attempted, an uncertain marker-write result blocks;
no rollback-capable catch surrounds APPLIED metadata or cleanup. Generation-write failure and
cleanup failure block with forward reconciliation on next start. Invalid APPLIED target blocks
inspection because the journal does not retain authoritative old generation.

Terminal ROLLED_BACK validates OLD target and existing OLD generation before cleanup. Legacy
ROLLED_BACK/ROLLBACK_FAILURE blocks immediately. Legacy SWAPPING requires richer inventory before
its existing classifier route: live main without rollback main plus rollback sidecars, or missing
live main with rollback main plus live sidecars, are split-source ambiguity and must not be deleted.
Unambiguous legacy target/rollback validation may still converge; classifier-only tests remain
narrower than this file-aware preflight. Preserve UNKNOWN legacy states rather than claiming
automatic recovery. Absence of recovery evidence alone permits the normal clean-install path.

Tradeoff: source-preserving copies need additional temporary space. Pre-INSTALLING failure must
not damage OLD live data. No general directory-entry power-loss durability is claimed.

## Acceptance

Use real coordinator and real isolated SQLite files with distinct OLD/NEW row identities.
Fault results are checked independently from returned enums. Materialized durable checkpoint
fixtures represent interrupted startup; ordinary caught I/O exceptions represent I/O failures,
not process death.

1. RV015 APPLIED/NEW target/OLD generation repairs exact NEW generation and survives second start.
   Failure replacing generation retains source/intent and never restores OLD under NEW generation;
   cleanup failure after generation success also never rolls back.
2. Source-copy/sync failures before INSTALLING preserve OLD live bytes. Safe SAVING_ROLLBACK abort
   keeps OLD usable only after validation and known generation; uncertain states block.
3. RV017 failure at each rollback main/WAL/SHM installation step leaves original recovery bytes;
   two reconstructed starts stay blocked without new file mutations. Initial intent-write failure
   has zero destructive calls. Legacy failed/split rollback and damaged/orphan journal block.
4. Real WAL-dependent OLD row is absent from a main-only control and survives successful rollback.
   Split legacy main/WAL fixture preserves both unique sources byte-for-byte. Do not call random
   bytes a valid WAL or allow fixture closure to checkpoint away the evidence.
5. Actual Application startup and all actual Worker doWork entry points cover Starting/Blocked;
   assert zero graph/bootstrap/preferences/ledger/export/network calls. Fresh-install control
   builds the normal graph. No test-only production bypass flags.
6. Device/emulator validation checks actual blocked Activity launch/resume without creating the
   missing OJ database; UI component tests verify resource copy and scaling. Dedicated test AVD
   only, no user device or normal app data. Unit/device evidence is recorded separately.
7. Fresh full unit tests, assembleDebug/assembleRelease and connected suite; independent task and
   whole-branch reviews; push scoped PR on integration base and attach it. No release or full
   Phase127 VERIFIED claim until the remaining phase gates have independently passed.
