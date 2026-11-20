# Phase 137 — Android Bounded Problem Query

Status: **VERIFIED**. Every gate below is measured — the local gates on the
implementation head `888b1dd` and the disposition head `863d4b6`, the CI
gates on both heads (delivery PR #84 merged `f86e35b`; disposition PR #87
merged `661b400`). Authoritative inputs:
[Design](../superpowers/specs/2026-11-20-phase-137-android-bounded-query-design.md),
[Plan](../superpowers/plans/2026-11-20-phase-137-android-bounded-query.md),
[Plan ledger](phase-137-plan-ledger.md),
[Review record](phase-137-task6-review.md).

Baseline main: `69c8c73` (phase 135 fully closed). Integration branch:
`codex/phase-137-android-bounded-query` (delivery PR #84); closeout on
`codex/phase-137-closeout`.

## Scope and inputs

Replaces the library's full-list subscription (`observeLibrary()` → combine →
in-memory filter → manual SHOW-MORE window) with SQL-backed bounded paging:
Room RawQuery PagingSource through androidx.paging 3, the phase-136 contract
semantics translated to SQL (`LibraryQueryBuilder`), schema v16→v17
(`title_lower` column + index with Kotlin-side Unicode lowering and backfill
migration), and SQL-derived statistics. Out of scope: remote catalog, notes
index, training candidates (bounded already), Windows/Linux/Apple library
work (phases 138–140).

## Gate history

| # | Gate | Requirement | Result |
|---|------|-------------|--------|
| 1 | Contract-vector parity | all golden vectors through the Room SQL path | PASS (local, head `888b1dd`) — 28/28 vectors reproduced via `ProblemQueryVectorTest` against the same `LibraryQueryBuilder` SQL the UI Pager runs (search trim/case, ß boundary, NBSP-not-trimmed, tag exactness, difficulty bounds, status derivation priority, code-point tie-break) |
| 2 | 10k/50k budgets | first page SQL-bounded, stats SQL-derived | PASS (local) — `ProblemQueryPerfTest`: 10k first page bounded to 60 rows well under budget, `observeMatchCount`=250/`observeTotalCount`=10,250 via SQL; 50k same shape |
| 3 | Insert/delete under filter | no duplicate keys, no lost anchor | PASS (local) — insert of a new newest-row + delete of the prior first row: page keys stay distinct, deleted row absent, new anchor sorts first |
| 4 | Full unit suite | `gradlew test` green | PASS (local) — **895 tests / 0 failures** on head `888b1dd` (includes the adapted migration chains to schema v17 and the training-rail wiring update) |
| 5 | CI `android.yml` on the delivery head | build + emulator + release green | PASS — run `44771489181` all jobs success on `888b1dd` (the emulator gate now compiles the connected-test set, previously the failure point); merged as `f86e35b` |
| 6 | CI `windows.yml` regression | green | PASS — run `44771488906` success on `888b1dd` (windows/ untouched by this phase) |
| 7 | Independent review of the merged diff | findings fixed or dispositioned | PASS — [phase-137-task6-review.md](phase-137-task6-review.md): PASS-WITH-OBSERVATIONS; F1/F2 (both P2) and F3/F8/F9 fixed in the disposition commit, F4–F7 accepted as recorded observations; disposition re-verified locally (full suite green) and by CI on the disposition head |

## Honest history

- The first delivery attempt (commit `93423ed`) failed CI on four gates with
  three root causes, all fixed before re-delivery: a Kotlin string template
  missing `${}` (`$whereClauseSql(query)`), an accidentally duplicated DAO
  default-method block referencing a non-existent wrapper, and the schema-v17
  parameter (`titleLower`) missing at 26 fixture construction sites
  (24 unit-test + 2 androidTest) — the compiler-forced write points worked as
  designed, surfacing every site.
- Migration tests pinned the final version 16 in two places and chained only
  up to `MIGRATION_15_16`; both now target 17, and `RoomIndexAuditTest`'s
  replay-current-DDL trick was replaced with Room's own exported v13 schema
  (the replay breaks the moment a migration adds a column — a plain
  `ALTER TABLE ADD COLUMN` is not idempotent).
- `RoomIndexAuditTest` rename to the schema-file approach recorded as the
  plan deviation for T1's coordinated test sweep.
- The in-memory `applyFilterSort`/`summarizeProblemLibrary` references are
  retained and unit-tested as the parity oracle the SQL translation was
  verified against.

## Non-claims

No Windows/Linux/Apple library changes; no network behavior change; the
remote-catalog and notes-index paths are untouched; no paging placeholder
UI (placeholders disabled); budgets are generous CI-safe ceilings, not
performance claims beyond "no full-list subscription".

<!-- CONTROLLER-SIGN-OFF: recorded by the controller on 2026-11-21 -->
VERIFIED. Phase 137 delivered to `main` via delivery PR #84 (merge
`f86e35b`) and review dispositions via PR #87 (merge `661b400`). Gates:
contract-vector parity 28/28 through the Room SQL path (local, `888b1dd`);
perf budgets green at 10k and 50k with SQL-derived statistics and
filter-time insert/delete uniqueness (local); full unit suite **895/0** on
the implementation head and **897/0** on the disposition head (2 review
pins); CI Android runs `44771489181` (delivery) and `44778262820`
(disposition) plus Windows runs `44771488906` and `44778262786` all green;
independent review PASS-WITH-OBSERVATIONS with F1/F2 (P2) fixed and
F3–F9 dispositioned (PR #87).
