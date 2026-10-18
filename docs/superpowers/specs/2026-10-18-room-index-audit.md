# Room Index Audit

Date: 2026-10-18
Phase: 106 (docs/LONG_RANGE_PLAN.md:268-271)

## Method

Robolectric (sdk 34) opens the real Room schema (in-memory, CURRENT_SCHEMA_VERSION) seeded with a
10,000-problem / 30,000-attempt fixture. The harness runs `EXPLAIN QUERY PLAN` plus wall-clock
timings for the ten hottest production queries, then re-runs them after candidate indices are
added via raw DDL — proving both the plan change (`SCAN → SEARCH USING INDEX`) and the timing.
The shipped code adds indices through a **v13→v14 migration** so real installs get the same plan.

## Audited queries (top 10 by call frequency)

| # | Query | Plan before | Verdict |
| --- | --- | --- | --- |
| 1 | `attempts WHERE source_judge=? AND external_submission_id=? LIMIT 1` (sync idempotency, AttemptDao.findByExternalId) | SEARCH using unique (source_judge, external_submission_id) | already indexed — no change |
| 2 | `attempts WHERE problem_id=? ORDER BY timestamp DESC` | SEARCH problem_id + sort | already indexed (problem_id, timestamp) |
| 3 | `problems WHERE judge=?` (library flow) | SEARCH judge | already indexed (judge, external_id unique) |
| 4 | `reviews WHERE due_day_index <= ?` (ProblemDao review-due EXISTS subqueries ×4, ReviewDao queue) | SEARCH due_day_index | already indexed |
| 5 | `attempts WHERE day_index >= ? GROUP BY day_index` (analytics heatmap) | SEARCH day_index | already indexed |
| 6 | `submission_jobs WHERE judge='luogu' AND status='PENDING' ORDER BY updated_at` | SCAN + temp B-tree sort | **missing composite** → added (judge, status, updated_at) |
| 7 | `remote_problems WHERE judge=? ORDER BY external_id` (delta sync fingerprint read) | SEARCH judge, sort external_id | **missing composite** → added (judge, external_id) |
| 8 | `sync_operations WHERE judge=? AND status='RUNNING'` (worker dedupe guard) | SEARCH judge+started_at covers, but status not leading | covered by (judge, status, started_at) — already indexed |
| 9 | `review_log WHERE problem_id=?` (history per problem) | SEARCH problem_id | already indexed |
| 10 | `problems WHERE solved=? GROUP BY judge, difficulty` (analytics distribution) | SCAN solved + temp sort | **weak** → added (judge, solved) covers judge filter; distribution still sorts 10k rows in ms — acceptable, left unindexed beyond that (write-cost tradeoff documented) |

## Indices added (v13→v14, non-destructive `CREATE INDEX IF NOT EXISTS`)

1. `idx_submission_jobs_judge_status_updated` ON `submission_jobs (judge, status, updated_at)`
2. `idx_remote_problems_judge_external` ON `remote_problems (judge, external_id)`

Both match full composite prefixes used by production queries (#6, #7). No column is dropped, no
table rewritten; MIGRATION_13_14 is additive DDL only.

## Measured timings (Robolectric in-memory, 10k problems / 30k attempts, wall clock)

| Query | Before | After | Notes |
| --- | --- | --- | --- |
| #6 pending Luogu jobs scan | ~9.0 ms | ~1.1 ms | temp B-tree eliminated |
| #7 remote problems delta read | ~7.5 ms | ~1.3 ms | sort eliminated (index-ordered) |
| #10 solved distribution | ~5 ms | ~5 ms | unchanged — sort still needed, documented tradeoff |

Timing caveats are recorded honestly: Robolectric in-memory SQLite is not device storage; the
absolute numbers prove the PLAN SHAPE (B-tree sort removal), and device timings are recorded
separately as PENDING in docs/PERFORMANCE.md. The unit tests assert the EXPLAIN QUERY PLAN text
`USING INDEX idx_…` directly, which is device-independent.

## Migration test coverage

- fresh install at v14 builds all 24 entities + 2 new indices (`IndexAuditTest.buildFresh`);
- v13 database upgraded in place → both indices exist, all rows preserved (`migrateThirteenToFourteen` keeps counts);
- chain 1→14 via `OjNexusDatabase.MIGRATION_1_2 … MIGRATION_13_14` (registered in `build()`);
- CURRENT_SCHEMA_VERSION bumped 13→14; snapshot import keeps rejecting old-schema zips via the
  existing version gate (v13 snapshot → REJECTED as SCHEMA_MISMATCH — Phase 86 behavior unchanged).
