# Spec — Phase 91: Codeforces problemset incremental sync

Status: draft
Date: 2026-10-18
Plan: docs/superpowers/plans/2026-10-18-cf-problemset-delta.md
Long-range anchor: docs/LONG_RANGE_PLAN.md PHASE 91

## Goal

Delta sync of the Codeforces problemset: avoid the full ~10k-row `problemset.problems`
refetch when nothing changed, without any new endpoint, schema change, or scraping.

## Scope

- `judge/codeforces` only: `CodeforcesSyncRepository.syncProblemset`, `SyncPolicy`,
  a new pure planner `ProblemsetDeltaPlanner`, and the adapter boundary.
- No schema change: freshness stamp `problemset_synced_at` already exists; the content
  fingerprint is a pure computation, persisted nowhere (ledger rows already carry
  attempted/imported counts that make the skip visible).

## Design

1. **ProblemsetDeltaPlanner** (pure, unit-tested):
   - `decide(force, lastSyncedAt, now, freshnessMs, knownProblemCount, fetchedProblemCount)` →
     `SKIP_FRESH` | `FULL_REFRESH`.
   - Full refresh path computes `fingerprint(problems, statistics)`: canonical string
     `contestId:index:rating:solvedCount` rows joined and hashed (SHA-256, hex).
   - `sameFingerprint(a, b)` → the caller skips the Room write entirely (idempotent no-op
     with a real skip count) instead of re-upserting 10k rows.
2. **syncProblemset flow**: freshness skip (unchanged) → fetch → merge → fingerprint vs
   nothing persisted means always write on first sync — the skip only applies within a
   single process run pair? NO: fingerprint is compared against `remote_problems`
   max(updated_at) batch? Simplify: **compare fetched row count + max solvedCount sum
   against the live table** (`countByJudge` + new `sumSolvedByJudge`). Equal → skip write.
   Planner stays pure; repository performs the comparison.
3. Bounded work: chunked upsert stays `chunked(2000)`; no full-table loads (SQL counts only).
4. Public API only via `CodeforcesAdapter.fetchProblemset()`; 503 → existing
   `ServerError` retry with exponential backoff (unchanged); no scraping.

## Ledger

`runStage` already appends a module receipt per stage via the coordinator; PROBLEMS
outcomes carry `itemsProcessed = 0` on skip and the row count on write.

## DoD

- Unit tests with recorded-style fixtures (offline fake adapter, deterministic clock):
  fresh skip, force refresh, unchanged-content write skip (counts preserved), changed
  content writes, fingerprint stability (same input → same hash; different solvedCount →
  different hash).
- Full `test assembleDebug` gate, version bump, ROADMAP + plan bookkeeping, commit.
