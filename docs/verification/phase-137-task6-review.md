# Phase 137 — Task 6 Independent Review Record

Verdict: **PASS-WITH-OBSERVATIONS**. The independent fresh-context reviewer
(read-only agent) audited the merged phase-137 implementation (PR #84, head
`888b1dd`, merge `f86e35b`) against the frozen phase-136 contract
(`contracts/problems/v1/check.py` + README + 28 golden vectors), the design
(`specs/2026-11-20-phase-137-android-bounded-query-design.md`), and the
retained in-memory reference. All seven review questions were answered with
file:line evidence; the 28-vector Room-path parity and every `title_lower`
write path were confirmed.

Authorship caveat (disclosed): findings come from the independent review
agent; dispositions were applied by the controller and re-verified by the
local suite + CI on the disposition head.

## Findings and dispositions

| # | Severity | Area | Finding | Disposition |
|---|----------|------|---------|-------------|
| F1 | P2 | `LibraryQueryBuilder.bindArgs` | Bind-arg computation used Kotlin `trim()` (Unicode-wide) while the clause builder uses the frozen ASCII `contractTrim` — for a query like `"\u3000tree"` (ideographic space, realistic from CJK IMEs; Luogu is a first-class judge) the two desynced: wrong matches for the first case and a clause-live/args-empty bind-count error for whitespace-only queries (surfacing as a broken screen via the state `catch`, not a crash). | **FIXED**: `bindArgs` now uses `contractTrim`; two pinned tests added (`ideographicSpaceQueryIsNotTrimmed`, `nonAsciiWhitespaceOnlyQueryKeepsClauseAndArgsMatched` — the latter asserts clause-stays-live, no bind error, zero matches). |
| F2 | P2 | `MigrationTest` v16→v17 backfill | The `title_lower` backfill was never asserted: a no-op backfill would still pass Room schema validation (`DEFAULT ''` is valid) while breaking search/TITLE-sort for every migrated user. | **FIXED**: the 15→16→17 test now asserts `titleLower == "yet another array query"` for the migrated row. |
| F3 | P3 | `ProblemFilter` comment | The retained `applyFilterSort` was labeled a "parity oracle" but keeps legacy in-memory behavior (Unicode-wide `trim()`, UTF-16 title ordering) — the comment overstated it; the vector test compares Room output to golden `expect` directly, so nothing lied. | **FIXED**: comment corrected — it is the legacy-semantics regression reference pinned by `ProblemFilterTest`, not the parity oracle. |
| F4 | P3 | Design/implementation drift | The design specifies `enablePlaceholders=true` and count-flow-driven no-match; the implementation uses `enablePlaceholders=false` and `itemCount == 0 && refresh NotLoading`. Both defensible. | **ACCEPTED as a recorded deviation** in the verification record (placeholders-disabled avoids placeholder-row key churn; the itemCount-based empty state avoids a second query stream). |
| F5 | P3 | Paging `LoadState.Error` | A failed page load renders a blank list with no retry; state stays `Ready` because paging errors travel via loadState. | **ACCEPTED as a recorded observation** — no error UI for paging refresh errors; the SQL-backed stats flows still surface failures through `Loadable.Failed`. |
| F6 | P3 | No search debounce | Every keystroke rebuilds the Pager + count query; the remote catalog already debounces 250 ms. | **ACCEPTED as a recorded observation** — a UX/perf refinement, not a correctness gap; listed for phase 142's editor-loop work. |
| F7 | P3 | Training-rail snapshot key | The `remember(pagingItems.itemCount)` snapshot can hold stale ids on same-count invalidations. | **ACCEPTED as a recorded observation** — ids only (`ProblemLibraryTraining` consumes ids), harmless today. |
| F8 | P3 | Dead resources / unused accessor | `problems_show_more` remained in both string files after SHOW MORE removal; `ProblemRepository.findLibraryPage` had no production caller. | **FIXED**: both string entries removed; the unused accessor removed. |
| F9 | P3 | Test wording vs strength | The insert/delete test drives fresh PagingSource loads rather than a live invalidation collector; the budgets prove boundedness, not latency; the index audit does not yet cover the new `title_lower` index plan. | **FIXED (comment)**: the KDoc now states budgets prove boundedness. The live-collector upgrade and index-plan audit are **ACCEPTED observations** for the phase-142 editor-loop arc. |

## Checklist

| Check | Result | Evidence |
|---|---|---|
| Q1 Contract WHERE semantics | PASS (F1 fixed) | Trim/lowercase/LIKE-escape/derived-status/difficulty/tag/judge/tie-break verified against `check.py` and hand-checked vectors 007/010/011/023/026/027/028 |
| Q2 Migration + write paths | PASS (F2 fixed) | `titleLower` set at every entity construction and all three `applyRemoteMetadata` sites; sync repositories covered; exported `17.json` matches |
| Q3 Paging correctness | PASS | PK-keyed itemKey + unique (judge, external_id) → deterministic pages; canonical flatMapLatest/cachedIn wiring |
| Q4 Stats semantics | PASS | unfiltered totals + filtered visible per the documented summary semantics |
| Q5 UiState consumers | PASS (F8 fixed) | no `uiState.problems` remains; empty/no-match/training-rail re-wired |
| Q6 Test honesty | PASS (F2, F9 addressed) | vectors assert golden `expect` with a hard 28 count; perf asserts real counts and page bounds |
| Q7 Hygiene | PASS | no TODO/println/emoji/debug leftovers; minimal gradle additions |

Scope note: `observeLibrary()` still feeds the session/training ViewModels —
explicitly out of scope per the design doc and unchanged by this diff.
