# Phase 137 — Android Bounded Problem Query — Design

Status: ACTIVE. Synthesized by the controller from the phase-137 recon
(read-only report over the Android query layer, 2026-11-20, file:line
anchors on file) and the roadmap entry.

Baseline main: `8175fdd` (phases 131-136 delivered; PR #83 merge).

## Inputs this design synthesizes

- **Roadmap** (`docs/LONG_RANGE_PLAN_121_220.md`, PHASE 137): dependencies
  132 (delivered) + 136 (delivered); A-FEATURE problems, ProblemDao/
  RemoteProblemDao, LibraryWindow. Deliverable: replace "filter the full
  collection in memory, then window it" with Room query paging/window while
  keeping the full match count, filters and return position. Acceptance G-A:
  no full-collection UI-subscription degradation at 10k/50k synthetic rows;
  no duplicate rows or position jumps on insert/delete during filtering;
  statistics are never computed over only the visible ~60 rows.
- **Recon findings (all verified)**:
  - The full-list chain exists end to end: `ProblemDao.observeLibrary()`
    (ProblemDao.kt:47, the only unbounded Flow over `problems`) →
    `ProblemRepository.observeLibrary()` (ProblemRepository.kt:54-55) →
    `combine(...)` in ProblemsViewModel (ProblemsViewModel.kt:70-91) →
    `collectAsStateWithLifecycle` (ProblemsScreen.kt:142). Filtering/sorting
    is pure in-memory `applyFilterSort` (ProblemFilter.kt:36-65); the manual
    window is `visibleCount` 60/60 (LibraryWindow.kt:8-15, windowList = take).
  - Bounded-SQL precedent exists in-house: RemoteProblemDao.search
    (RemoteProblemDao.kt:58-82, LIMIT/OFFSET, doc: "nothing here ever loads
    the full table into memory") and the five training-candidate Flows with
    `LIMIT :limit` (ProblemDao.kt:49-90).
  - Schema is version 16 with a full migration chain (OjNexusDatabase.kt:95,
    MIGRATION_1_2…15_16). ProblemEntity indices: unique (judge, external_id),
    judge, solved, updated_at — no title/difficulty/favorite index, no
    lowered-title column. No FTS.
  - androidx.paging is NOT on the classpath (no room-paging/paging-runtime/
    paging-compose in gradle or the version catalog); Room 2.8.4.
  - Stats: `summarizeProblemLibrary(problems, visibleProblems)` computes
    total over the full list and visible over the filtered list
    (ProblemLibrarySummary.kt:14-23) — currently correct only because the
    full list is in memory.
  - Tests: 10k in-memory perf test exists (LibraryWindowTest.kt:52-68,
    <250ms); no 50k DB-backed test, no insert/delete-during-filter test.
  - Extra full-list consumers outside the library: SessionViewModel.kt:121
    and TrainingViewModel.kt:173 also expose `observeLibrary()` as a full
    StateFlow (recorded; out of this phase's write scope).
  - UI rows already key on `it.id` (ProblemsScreen.kt:369).

## Decisions

1. **androidx.paging 3 (Room PagingSource), not hand-rolled LIMIT/OFFSET.**
   Room 2.8.4 + `room-paging` + `paging-compose` (official AndroidX). The
   acceptance "no duplicate rows or position jumps on insert/delete during
   filtering" is Paging3's anchored-invalidation guarantee on keyed LazyColumn
   rows; hand-rolled OFFSET windows recompute on every invalidation and jump.
2. **The filter becomes SQL with contract parity.** The phase-136 problem
   contract (`contracts/problems/v1/`, 25 vectors) is the executable
   semantics; the SQL translation must reproduce every vector:
   - identity/search: `search` trimmed + Unicode simple lowercase in Kotlin,
     then `title_lower LIKE '%'||:q||'%' OR external_id LIKE '%'||:q||'%'`
     — `external_id` is ASCII-only by the frozen charset so SQL `LIKE` is
     exact there; titles match against the new `title_lower` column;
   - status: derived (inReview → REVIEW else solved → SOLVED else
     attemptCount > 0 → ATTEMPTED else UNSOLVED) as SQL CASE identical to
     the reference;
   - judge/favorite/tag: exact equality (tag via the existing tag join);
   - difficulty bounds: inclusive; `(difficulty IS NULL)` excluded when
     either bound is set;
   - sorts: UPDATED `updated_at DESC`; DIFFICULTY `(difficulty IS NULL) ASC,
     difficulty ASC`; TITLE `title_lower ASC`; ATTEMPTS `attempt_count DESC`;
     every sort ends with the stable code-point tie-break
     `judge ASC, external_id ASC` — SQLite BINARY collation on UTF-8 IS
     code-point order (same property the Swift/C#/Python ports rely on).
3. **Schema v16 → v17**: add `problems.title_lower TEXT NOT NULL DEFAULT ''`
   + index on it; migration backfills `lower(title)` **in Kotlin inside the
   migration callback** (Unicode simple lowercase via Kotlin `lowercase()`,
   not SQL `lower()` which is ASCII-only), and every write path
   (insert/update/applyRemoteMetadata) maintains it. `external_id` needs no
   derived column (frozen ASCII charset).
4. **Counts and statistics move to SQL.** `observeMatchCount(filter)` Flow
   (`SELECT COUNT(*)` with the same WHERE) drives `summary.visible`;
   unfiltered `observeTotalCount()` drives `summary.total`; solved/review/
   favorite aggregates become `SUM(CASE …)` Flows over the same WHERE. The
   stats are therefore never computed over only the visible page
   (acceptance clause) and stay correct while pages stream.
5. **ViewModel rewrite**: the full-list `combine` is replaced by
   `Pager(PagingConfig(pageSize=60, prefetchDistance=30, enablePlaceholders=true))
   .flow { problemDao.pagingSource(filter) }` + the count/summary Flows.
   `LibraryWindow`/`visibleCount`/`loadMoreLibrary` and the SHOW MORE footer
   are removed (Paging3 loads pages as the user scrolls; placeholders keep
   the scroll anchor). `SessionViewModel`/`TrainingViewModel` full-list
   subscribers are RECORDED as out of scope (their collections are bounded
   by the training queries, not the library surface).
6. **UI**: `collectAsLazyPagingItems` with `key = { it.id }` (already the
   row key), placeholder-aware item counting, empty/no-match states driven
   by the count Flows (not `items.itemCount`), filter chips unchanged.
7. **Tests**:
   - **Contract-vector parity (T1)**: seed each of the 25
     `contracts/problems/v1/golden` datasets into an in-memory Room DB and
     assert the SQL filter+sort output equals the vector `expect` list —
     the platform adoption the contract README prescribes.
   - **10k/50k DB-backed perf (T2)**: Robolectric in-memory Room seeded with
     10k and 50k synthetic rows; assert the first page query completes under
     budget and NO full-list flow is collected (fakes assert
     `observeLibrary()` is never called by the library path).
   - **Insert/delete-during-filter (T2)**: with a filter active, insert a
     matching row and delete a visible row; assert the paged stream updates
     without duplicate keys and without a position jump (keys unique, no
     re-emission of an out-of-window jump).
   - Existing suites stay green: ProblemFilterTest (pure semantics),
     LibraryWindowTest (the in-memory helper remains for other surfaces),
     ProblemLibrarySummaryTest adapts to the SQL-backed summary.
8. **Out of scope**: SessionViewModel/TrainingViewModel full-list consumers,
   remote catalog paging (already bounded), FTS (substring LIKE at these
   scales is the contract semantics), iOS/Windows/Linux equivalents
   (138-140), search-index tuning.

## Verification shape (G-A)

- Contract-vector parity 25/25 through the Room path (measured in CI + local
  `gradlew test`).
- 10k and 50k DB-backed tests green with budgets; insert/delete-during-filter
  green.
- `gradlew test assembleDebug` gate green on the delivery head; CI
  `android.yml` green (build + emulator).
- Independent review + verification record with measured values; roadmap
  tick; closeout PR.
