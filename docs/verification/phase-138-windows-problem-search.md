# Phase 138 — Windows Native Problem Search and Virtualization

Status: **VERIFIED PENDING DELIVERY GATES AND REVIEW**. The controller's local
gates are measured below; the delivery CI gates and the independent review flip
at merge. Authoritative inputs:
[Design](../superpowers/specs/2026-11-21-phase-138-windows-problem-search-design.md),
[Plan](../superpowers/plans/2026-11-21-phase-138-windows-problem-search.md),
[Plan ledger](phase-138-plan-ledger.md), handoff
(`docs/PHASE_138_HANDOFF.md`).

Baseline main: `9bfdf21` (phases 131-137 delivered; PR #89 merge).
Integration branch: `codex/phase-138-windows-problem-search`.

## Scope and inputs

On the existing Windows problem ledger: search, tag/status filtering, stable
pagination, a detail entry and WPF native collection virtualization, with
filters that reproduce the frozen phase-136 contract semantics. Out of scope:
difficulty/tag ingest (no payload source — recorded deviation), favorite
toggle UI, portable archive changes, Android/Linux/Apple code.

Inputs / links:

- Roadmap PHASE 138 (`docs/LONG_RANGE_PLAN_121_220.md`): dependencies 133
  (delivered) + 136 (delivered); acceptance G-W — 筛选与 136 向量一致；
  大量记录不创建全部可视元素；重启和返回保留有效筛选；空结果有清除条件入口。
- Phase-138 recon (read-only subagent report, 2026-11-21, file:line anchors on
  the Windows problem surfaces).
- Frozen contract: `contracts/problems/v1` (28 golden vectors, hand-derived
  expectations, checker self-test).

## Honest history

- **Four real defects were caught and fixed during CI-gated development** (each
  a landed fix commit, none deferred):
  1. `pragma_table_info([problems])` — the bracket identifier form parses but
     the pragma table-valued function rejects it; every fresh migration failed
     with a classified IoFailure until the guard query used a quoted string
     literal (caught by the full Core suite going red, fixed same session).
  2. The migrator ladder lacked a `version == 8` branch after the version
     bump — legacy v8 databases fell through the ladder and were never
     migrated (caught by `StorageMigrationTests` v8 rows).
  3. The LIKE search pattern initially bound the escaped literal WITHOUT `%`
     wildcards — substring semantics lost (caught by parity vectors 010/012).
  4. `Microsoft.Data.Sqlite` converts enum parameters to their underlying
     integer: contract seeds bound the raw `JudgeId` struct, storing `0`
     instead of the wire string and silently breaking the reviews/tag EXISTS
     matches (caught by parity vectors 018/019/014).
- **Known transient regression, diagnosed and fixed in the same session:** the
  longer v9 migration widened the concurrent-first-run window so a loser
  process could probe a database file whose initializer transaction had not
  committed — `InvalidOperationException("Missing schema metadata.")`
  classified as STORAGE_IO_FAILURE, breaking the "concurrent first-run
  processes return only READY or BUSY" contract. Fixed by classifying a
  metadata-less, user-table-less database as STORAGE_BUSY
  (SchemaMigrator.MissingSchemaMetadata); concurrent-first-run and
  exclusive-maintenance tests green after the fix.

## Gates

| # | Gate | Requirement | Result |
|---|---|---|---|
| 1 | Contract-vector parity (filters match the 136 vectors) | 28/28 through the real SQL path | **PASS (measured locally)** — `ProblemContractVectorTests`: 28/28 green on `89dea15`; gate surfaced the `%`-wildcard defect above |
| 2 | Large record sets do not create all visual elements | bounded realized containers + source audit | **PASS (measured locally)** — STA probe 10,000 rows on a fixed 480px viewport, realized containers bounded ≪ 200 and the deepest row unrealized (`VirtualizedProblemListTests`); MainWindow.xaml structure pinned by the same class |
| 3 | Filters survive restart and back navigation | persistence + detail-back retention | **PASS (measured locally)** — `ProblemsLedgerViewModelTests.CommittedFiltersPersistAndSurviveViewModelRestart` (save-on-commit → new VM loads persisted filter) and `DetailBackKeepsFiltersAndPageIntact` |
| 4 | Empty results have a clear-filters entry | filtered-empty state + CLEAR FILTERS round trip | **PASS (measured locally)** — `ClearProblemFilters_RestoresUnfilteredPageAndOffersTheEmptyStateExit` + `ui-smoke.ps1` UIA exercise (favorites filter → NO PROBLEMS MATCH THE CURRENT FILTERS → CLEAR FILTERS → NO PROBLEMS RECORDED) |
| 5 | Full G-W suite | build + all tests green | **PASS (measured locally)** — `dotnet build` 0 errors/0 warnings (TWE); `dotnet test`: Core 307/307 (incl. parity 28/28), Cli 99/99, Desktop 49/49 — 455/455 |
| 6 | UI smoke against the real app | page renders + interactions | **PASS (measured locally)** — 5/5 distinct screenshots, FILTERS=3, new controls present, CLEAR-FILTERS=PASS |
| 7 | Delivery CI on the PR head | windows.yml `dotnet test` + build + package green | PENDING — flips at merge |
| 8 | Independent review of the final diff | findings fixed or recorded | PENDING — review agent running |

## Non-claims

- No ingest of difficulty/tags on Windows (no payload source); real synced
  rows carry null difficulty and no tags until a later phase adds ingest.
- No favorite-toggle UI; the favorite filter is exercised by the store API and
  vectors.
- No Android/Linux/Apple behavior changes; no CI workflow edits
  (`.github/workflows/windows.yml` untouched).
- The STA virtualization probe constructs an equivalent list configuration in
  code rather than instantiating MainWindow; the shipped XAML configuration is
  pinned by a source audit in the same test class.
