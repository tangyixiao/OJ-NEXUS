# OJ NEXUS — Phase 73–76 Design

## Intent

OJ NEXUS is currently at Phase 72 / v0.3.70. The existing app already has multi-OJ public sync,
local training and review, a Luogu OpenApp submission workflow, database backup/import, an OJ
connector center, and a Dashboard command surface. The next development cycle should strengthen
those working paths before adding another broad feature surface.

The phases are ordered by risk and dependency:

1. Phase 73 — SAFE RESTORE: make database backup restoration atomic, recoverable, and observable.
2. Phase 74 — ACTION CONTINUITY: keep date-sensitive screens current and route Dashboard commands
   to the exact active session, review, contest, or pending submission.
3. Phase 75 — TRAINING CALIBRATION: make difficulty fit and weak-area evidence affect candidate
   selection without hiding older high-value problems.
4. Phase 76 — SYNC OPERATIONS LEDGER: retain bounded per-operation sync outcomes and expose precise
   recovery actions.

Each phase is a complete, independently releasable vertical slice. Phase 74 may depend on the
restore generation introduced in Phase 73 for stale-work invalidation. Phase 75 depends only on
the current Room training data. Phase 76 comes last because the connector center already exposes
the current sync snapshot; historical operations are useful after the higher-risk data and action
continuity work is secure.

## Shared boundaries

- Native Kotlin, Jetpack Compose, Room, DataStore, WorkManager, Coroutines/Flow, Retrofit/OkHttp,
  and kotlinx.serialization remain the stack.
- The single `app` module and manual `AppContainer` remain until a separate measured need justifies
  modularization or Hilt.
- New UI copy is uppercase, operational, localized in English and Simplified Chinese, and routed
  through resources.
- The app remains local-first. Existing local data stays readable when network work fails.
- No OJ passwords, main-site cookies, automatic submissions, fabricated remote data, cloud
  account, social surface, AI advice, or embedded website shell is introduced.
- Existing Android Keystore-backed OpenApp credentials remain outside database exports.
- Every phase handles loading, empty, error, offline, and success states relevant to its surface.
- Each release must pass unit tests, Debug/Release builds, lint, connected tests, signed APK
  verification, install smoke, secret review, and final Git diff inspection.

## Phase 73 — Safe Restore

### User outcome

Importing a valid OJ NEXUS backup cannot silently destroy the current database. The app reports
whether a restore was applied, rejected, or rolled back, and preserves enough local evidence to
recover from an interrupted file replacement.

### Design

The import picker continues to stage a selected file under the app-private files directory. The
staged database is validated more deeply: SQLite integrity, current schema identity, and the full
set of required Room tables must agree with the current exported schema. A valid staging file is
never treated as the active database while Room is open.

At the next process start, restoration runs before `OjNexusDatabase.build`. The current database
and its WAL/SHM companions are first moved into a restore rollback set. The staged database is
copied to a same-directory candidate, synced, and atomically renamed into the target path. The
candidate is reopened and validated before the rollback set is removed. Any failure restores the
original files and leaves a typed local result for Settings.

A small restore journal records the state transitions `STAGED`, `SWAPPING`, `APPLIED`,
`ROLLED_BACK`, and `REJECTED`. Startup recovery uses the journal and the actual files rather than
assuming the previous process completed. The journal contains no problem data or credentials.

Database restoration also changes the local data generation. Pending WorkManager jobs created
against the replaced database must not act on reused account or request IDs. Sync/result workers
receive the generation in their input and return a stale-input result if it differs; startup then
reconciles valid work from restored Room state.

### Verification

- Production WAL-mode export is a readable, internally consistent snapshot.
- Invalid SQLite, wrong schema, missing required tables, and failed integrity checks are rejected.
- Replacement failures before and after target rename preserve or restore the original data.
- A simulated interrupted `SWAPPING` state converges to either the old valid database or the new
  valid database on the next startup.
- Work created under an earlier data generation performs no remote operation after restoration.
- Settings exposes a stable localized restore outcome without raw exception text.

## Phase 74 — Action Continuity

### User outcome

Leaving the app open across midnight refreshes date-sensitive data automatically. Dashboard
commands open the exact item that needs attention: resume the active session, open the due review,
open the next contest, or inspect a pending/failed submission.

### Design

A reusable local-day flow emits the current epoch day immediately and again at the next local
midnight, including after timezone or wall-clock changes observed when the app resumes. Dashboard,
Training, Review Run, and day-window analytics use that flow instead of capturing a day once in a
ViewModel constructor. Room queries are switched with `flatMapLatest`, so the old day's stream is
cancelled before the new one is observed.

Dashboard state adds the active training session and the most actionable local submission job.
The command projection uses a deterministic precedence:

1. `NOW`: active running/paused session; otherwise today's first incomplete task; otherwise an
   honest empty training command.
2. `NEXT`: overdue/due review; otherwise pending or failed submission; otherwise next contest;
   otherwise no action.
3. `SIGNAL`: sync error/partial state; otherwise connected OJ state; otherwise local-ready state.

Targets carry stable identifiers rather than screen categories. Navigation opens `session/{id}`,
`review/{problemId}`, the relevant submission request, or the selected contest. If the target is
deleted between projection and click, the destination shows its existing not-found/empty state
and offers a route back.

Fixed-height Dashboard cells become minimum-height cells so 200% font scale and narrow screens do
not clip the label or action value. Touch targets and visible text remain sufficient without
depending on color.

### Verification

- Fake-clock tests cross midnight and a timezone change without recreating the ViewModel.
- Dashboard precedence is covered for competing active-session, review, submission, contest, and
  sync signals.
- Route tests prove identifiers reach the intended destination and one-shot navigation context is
  consumed once.
- Compose tests cover 360 dp width, 200% font scale, reduced motion, empty states, and click
  semantics.
- A process recreation test resumes the same active session and does not duplicate navigation.

## Phase 75 — Training Calibration

### User outcome

The training plan favors problems that match a user-controlled difficulty target and address weak
knowledge areas. Every recommendation still explains its score using local evidence.

### Design

Training preferences add an optional target difficulty per judge plus a default target band. A
missing target remains valid and simply removes `DIFFICULTY_FIT` from the score. The UI offers a
small calibration control near Focus Sprint creation; it never guesses that an absent OJ rating
is a target.

Candidate retrieval stops taking the 20 most recently updated rows before scoring. Room provides
a bounded but policy-aware pool containing all due reviews up to a safety cap, recent unsolved
problems, failure-heavy problems, and problems linked to low-mastery knowledge areas. The pure
planner then ranks the merged, deduplicated pool.

`coverageValue` is replaced by explicit weak-area evidence. Candidate rows expose their linked
knowledge areas; the feature layer joins those areas to `KnowledgeAreaState.score` and derives a
bounded weakness contribution. Reason codes distinguish `LOW_MASTERY`, `REVIEW_DUE`,
`FAILURE_HISTORY`, `DIFFICULTY_FIT`, and `UNSOLVED`. Tie-breaking is stable and documented.

The plan snapshot shown before starting a session is the exact ID sequence passed to the existing
transaction. Changes arriving after confirmation affect the next plan, not the current session.

### Verification

- Planner tests cover absent targets, judge-specific targets, difficulty bands, weak-area scores,
  multiple linked areas, and stable ties.
- DAO tests prove old high-priority problems are not excluded by newer low-priority rows and all
  due reviews enter the pool within the safety cap.
- ViewModel tests prove preferences and mastery changes recompute the preview.
- Compose tests cover editing/clearing targets, explanations, large fonts, and no-candidate state.
- A large synthetic library benchmark records candidate query and projection time with a fixed
  data set and an explicit regression threshold.

## Phase 76 — Sync Operations Ledger

### User outcome

The connector center shows recent sync attempts per judge, what each module changed, where a run
stopped, and the narrowest safe recovery action.

### Design

The existing `sync_states` row remains the fast current snapshot and cursor owner. A new bounded
`sync_operations` history records one row per explicit run, while `sync_operation_modules` records
module outcomes for profile, rating, submissions, contests, and problemset. Each module entry
stores attempted/imported/updated counts, completion time, and a stable failure category. Raw
server messages and credentials are excluded.

Coordinators open an operation, append module results only after the corresponding Room write
succeeds, then close the operation as success, partial, error, cancelled, or stale generation.
History retention is bounded by count per judge and pruned transactionally after completion.

Connector rows open a local history sheet. A failed module offers `RETRY FAILED STAGE` only where
the adapter contract supports a safe stage restart; otherwise it offers the existing full manual
sync. Retry inputs include judge, account ID, operation ID, stage, and data generation, and pass
through the same dispatcher identity checks as current sync.

### Verification

- Migration 12→13 preserves existing accounts, cursors, freshness timestamps, and current state.
- Coordinator tests cover success, partial completion, cancellation, duplicate enqueue, stale
  generation, and persistence failure.
- Counts are recorded only after committed writes and repeated imports remain idempotent.
- Retention tests prove pruning affects only old completed operations for the same judge.
- Compose tests cover empty history, mixed outcomes, precise retry availability, large fonts, and
  state conveyed with text.

## Delivery sequence

Phase 73 is the immediate release target. Phase 74 follows after restore and stale-work behavior
are stable. Phase 75 improves daily training value without widening remote access. Phase 76 is a
schema-changing observability slice and should begin only after the first three releases have
completed their install and data-preservation smoke tests.
