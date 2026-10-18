# Plan — Windows Problem Library View (Phase 98)

1. Schema v6 in `SchemaMigrator.cs` (problems table, additive) + update exact-table test.
2. Domain: `ProblemEntry` record + `ProblemStatus` (SOLVED/ATTEMPTED) + URL builders
   (`ProblemUrls.For(judge, externalId)`-style pure helpers).
3. `ISyncStore.GetProblemsAsync(judge?, status?, limit)`; SqliteSyncStore implementation +
   InMemorySyncStore; derivation inside submission-payload persistence transactions.
4. CLI: `ProblemsListCommand` record + parser + Program dispatch + ConsoleRenderer human/JSON.
5. WPF: DesktopPage.Problems + ProblemRow + VM fields/commands + MainWindow.xaml page +
   screenshot mapping.
6. Tests (migrator, store, parser, program, viewmodel) — all offline.
7. Gates `dotnet test` + `dotnet build -c Release`; commit feat:.

## Result

Shipped (Phase 98, 2026-10-18):

- Schema v6 additive `problems` table (judge, external_id PK pair; title, url, solved
  NOT NULL DEFAULT 0, first_seen_at, last_seen_at). Every migration arm (fresh/0–5) runs
  `ApplyVersionSixMigration`; current-version validation checks all seven columns.
- Rows are derived inside the SAME transaction that persists
  `CodeforcesSubmissionsPayload` / `AtCoderSubmissionsPayload` (Luogu contributes no rows —
  counts-only payload). Solved is STICKY (`MAX(problems.solved, excluded.solved)`), titles
  keep their first value, external ids: CF `"{contestId}{index}"`, AtCoder
  `"{contestId}+{problemId}"`; accepted verdict = `OK` | `AC` | startsWith `Accepted`.
- `ProblemUrls.For(judge, externalId)` rebuilds public links:
  `https://codeforces.com/contest/{c}/problem/{index}` (index %20-escaped) and
  `https://atcoder.jp/contests/{c}/tasks/{p}`; returns null for malformed ids.
- CLI: `problems list [--judge] [--status solved|attempted] [--limit] [--json]`, default
  limit 50, exit Success even when empty; human renderer prints
  `{n}. {JUDGE} {externalId} [{SOLVED|ATTEMPTED}] {title}`.
- WPF: new PROBLEMS page (nav rail + screenshot mapping `problems.png`) with judge +
  status filter combos, per-row OPEN button (`Process.Start` UseShellExecute, only when a
  URL exists), empty state, bounded to 100 rows.
- Tests: migrator exact-table list now expects 10 tables (adds `problems`); store tests
  cover CF sticky-solve flip WA→OK, AtCoder `+` identity, judge/status/limit filters;
  parser tests cover defaults, full options, missing `list` subcommand, invalid status,
  non-positive limit, unknown flag; program tests cover JSON ledger, judge+status filter,
  empty ledger, invalid status error; viewmodel tests cover projection + both filters +
  unknown-filter error; `DomainContractTests.TestSyncStore` implements the new member.
- Gates: `dotnet test` — Core 75 + CLI 49 + Desktop 21 = 145 passing; `dotnet build -c
  Release` succeeded with 0 warnings/0 errors. No new `HttpClient` call sites.
