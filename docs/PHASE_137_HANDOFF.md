# PHASE_137_HANDOFF — Android Bounded Problem Query

Status: **VERIFIED** (delivery PR #84 `f86e35b` + review dispositions PR #87
`661b400`; review PASS-WITH-OBSERVATIONS, F1/F2 P2 fixed). Local gates
measured: vector parity 28/28, perf budgets at 10k/50k, 897/0 suite on the
disposition head. Authoritative record:
`docs/verification/phase-137-android-bounded-query.md`; per-task evidence:
`docs/verification/phase-137-plan-ledger.md`; review:
`docs/verification/phase-137-task6-review.md`.

## 1. What shipped

The problem library no longer subscribes to the full list. On the library
path:

- **SQL-backed paging**: `ProblemRepository.libraryPager(LibraryQuery)` →
  Room RawQuery PagingSource (table-level invalidation) → androidx.paging 3
  → `collectAsLazyPagingItems` in `ProblemsScreen`. The manual SHOW-MORE
  window (`LibraryWindow.kt`, `visibleCount`, `windowList`) is gone.
- **Contract-exact SQL**: `LibraryQueryBuilder` reproduces the phase-136
  vectors' semantics — search over `title_lower` + `lower(external_id)` with
  literal wildcard escaping and the frozen ASCII trim set (` \t\n\r\v\f` —
  NOT Kotlin `trim()`, which would strip NBSP), derived status CASE with the
  REVIEW > SOLVED > ATTEMPTED > UNSOLVED priority, exact tag match, inclusive
  difficulty bounds excluding unknown, and every ORDER BY ending in the
  stable code-point tie-break `judge, external_id, id`.
- **Schema v16→v17**: `problems.title_lower` (Kotlin-side Unicode lowercase —
  SQL `lower()` is ASCII-only) with a backfill migration and index; the
  compiler forces every write point (`insert`, `update`,
  `applyRemoteMetadata`, all fixtures).
- **SQL statistics**: `summary.total` = unfiltered COUNT,
  `summary.visible` = filtered COUNT (RawQuery Flow),
  solved/review/favorites = unfiltered SUMs — never derived from the page.
- **Parity + perf evidence**: `ProblemQueryVectorTest` replays all 28 golden
  vectors through the Room path; `ProblemQueryPerfTest` pins 10k/50k bounded
  first pages, SQL stats, and filter-time insert/delete uniqueness.

## 2. Gotchas for the next agent

- **Kotlin string templates**: `$whereClauseSql(query)` inside a SQL string
  does NOT invoke the function — `${...}` required. Cost one CI iteration.
- **Migration-test chains are hand-listed**: every `Room.databaseBuilder`
  in `MigrationTest` lists its chain explicitly; a new migration must be
  appended to EVERY partial chain (or the DB cannot open at the new current
  version). Final-version asserts live in `MigrationTest` and
  `RoomIndexAuditTest`.
- **Replaying current DDL to simulate a legacy DB breaks when a migration
  adds a column** (plain `ALTER TABLE ADD COLUMN` is not idempotent). The
  audit test now builds from the exported schema JSON — future migrations
  should keep that shape.
- **`String.trim()` is Unicode-wide in Kotlin** — the contract's search trim
  set is ASCII-only; use `contractTrim` in `LibraryQueryBuilder`, not `trim()`.
- **Phases 138–140** (Windows/Linux/Apple problem library) consume the same
  phase-136 vectors; reuse the vector-driven parity-harness pattern here.

## 3. Open work / next phases

- Phase 138 (Windows native problem search + virtualization) depends on 133 ✓
  + 136 ✓ — both delivered; its UI is WPF collection virtualization over the
  same SQL semantics.
- Phase 139 (Linux Qt problem model) depends on 134 ✓ + 136 ✓.
- Phase 140 (Apple SwiftUI library) depends on 135 ✓ + 136 ✓.
