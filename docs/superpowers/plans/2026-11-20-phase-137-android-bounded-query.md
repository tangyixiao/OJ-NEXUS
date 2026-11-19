# Phase 137 — Android Bounded Problem Query — Implementation Plan

Status: ACTIVE. Binds the design
(`specs/2026-11-20-phase-137-android-bounded-query-design.md`) to ordered,
verifiable tasks. Single integration branch
`codex/phase-137-android-bounded-query`; per-wave PRs to `main`; closeout
PR ends the arc.

Baseline main: `8175fdd`.

## Task waves

### Wave 1

- **T1 — schema v17 + bounded DAO** (`OjNexusDatabase.kt` migration chain,
  `ProblemEntity.kt` `title_lower` column + index, `ProblemDao.kt`):
  `PagingSource<Int, ProblemWithTagsPojo> pagingSource(filter…)` with the
  contract-parity WHERE/ORDER BY, `observeMatchCount/observeTotalCount`
  and the `SUM(CASE …)` summary Flows. Gradle: add room-paging +
  paging-runtime + paging-compose to the version catalog and app module.
  Local gate: `gradlew test` (migration + DAO tests).
- **T2 — repository + ViewModel rewrite + UI**
  (`ProblemRepository.kt`, `ProblemsViewModel.kt`, `ProblemsScreen.kt`,
  `ProblemLibrarySummary.kt`): Pager + collectAsLazyPagingItems, SQL-backed
  stats, SHOW MORE removal, filter→SQL translation identical to
  `applyFilterSort` semantics. Contract-vector parity harness
  (`ProblemQueryVectorTest`): all 25 golden vectors through the Room path.
  Local gate: `gradlew test` (vector parity + adapted suites).

### Wave 2

- **T3 — DB-backed perf + insertion/deletion tests**
  (`ProblemQueryPerfTest` 10k/50k Robolectric in-memory Room; budgeted first
  page; no full-list collection assertion; insert/delete-during-filter
  uniqueness/anchor test). Local gate: `gradlew test`.
- **T4 — full gate + delivery**: `gradlew test assembleDebug` locally; CI
  `android.yml` (build + emulator) on the delivery PR; independent review
  record; verification record; plan ledger; roadmap tick; closeout PR.

## File ownership (exclusive)

- T1: `core/database/**`, `gradle/**`, `app/build.gradle.kts`.
- T2: `core/data/repository/**`, `feature/problems/**`.
- T3: `app/src/test/**` (new files only).
- T4: `docs/verification/phase-137-*`, `docs/LONG_RANGE_PLAN_121_220.md`.

## Acceptance (G-A, measured)

- All 25 phase-136 contract vectors reproduced through the Room SQL path.
- 10k and 50k synthetic DB tests green with budgets; no full-list UI
  subscription on the library path (asserted by fakes).
- Insert/delete during an active filter: no duplicate keys, no position jump.
- Statistics derived from SQL counts (never the visible page).
- `gradlew test assembleDebug` + CI android.yml green on the delivery head.
