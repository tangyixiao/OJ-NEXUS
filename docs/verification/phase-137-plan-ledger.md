# Phase 137 — Plan Ledger

Working record of the phase-137 arc (Android bounded problem query). Rows
flip only on observed, verifiable events; PENDING rows name the event that
will flip them.

Branch: `codex/phase-137-android-bounded-query` (delivery PR #84); closeout
on `codex/phase-137-closeout`. Baseline main: `69c8c73`.

| # | Task | Evidence | Status |
|---|------|----------|--------|
| 1 | SDD + implementation plan merged | PR #84 commit `c01009b` (design + plan from the recon report: full-subscription chain `observeLibrary` → combine → in-memory filter → 60-row window pinned with file:line anchors) | merged (PR #84) |
| 2 | T1 schema v17 + bounded DAO | commits `93423ed`/`79eca21`: `title_lower` + index, backfill migration v16→v17, contract WHERE/ORDER BY builder, RawQuery PagingSource + count/summary Flows, room-paging deps, exported `17.json`; local `compileDebugKotlin` + `compileDebugUnitTestKotlin` green after the CI-caught template/duplicate-block fixes | merged (PR #84) |
| 3 | T2 repository + VM rewrite + UI + parity harness | commits `dd8cf2b`/`3e21c41`: `libraryPager` (Room PagingSource), `ProblemsViewModel` SQL stats state, `ProblemsScreen` `collectAsLazyPagingItems` (SHOW MORE removed), `ProblemFilter.toLibraryQuery`, `ProblemQueryVectorTest` 28/28 local green; full suite 895/0 local | merged (PR #84) |
| 4 | T3 perf + insertion/deletion tests | `ProblemQueryPerfTest`: 10k/50k bounded first pages, SQL stats, filter-time insert/delete uniqueness — local green | merged (PR #84) |
| 5 | T4 delivery gate: CI android.yml (build + emulator + release) on the delivery head | PENDING — flips at PR #84 merge | pending |
| 6 | T4 delivery gate: CI windows.yml regression | PENDING — flips at PR #84 merge | pending |
| 7 | T6 independent review + dispositions | [phase-137-task6-review.md](phase-137-task6-review.md): PASS-WITH-OBSERVATIONS, 2×P2 + 7×P3; F1/F2/F3/F8/F9 fixed, F4–F7 recorded observations; disposition PR #87 CI all green (Android `44778262820`, Windows `44778262786`) and merged as `661b400` | merged (PR #87) |
| 8 | T6 closeout: verification record, this ledger, roadmap tick, closeout PR to `main` | closeout PR #88 all four CI checks success and merged as `94431e5` (verification record VERIFIED, roadmap ticked, handoff landed) | merged (PR #88) |

## Deviations

- The design's "replay-current-DDL" upgrade-path simulation in
  `RoomIndexAuditTest` was replaced with Room's exported v13 schema file:
  replaying the current DDL breaks the moment any migration adds a column
  (v17 adds `title_lower` via a non-idempotent `ALTER TABLE ADD COLUMN`).
  The schema-file build is equally faithful for the legacy-upgrade path and
  robust to future column additions.
- The vector corpus grew from the plan's 25 to 28 (phase-136's independent
  review added 026–028); the harness runs all 28.
- Agent-team waves were skipped: the controller implemented all tasks
  directly after this session's subagents repeatedly stalled (phase-134
  pattern); per-task commits preserve the same review granularity.
