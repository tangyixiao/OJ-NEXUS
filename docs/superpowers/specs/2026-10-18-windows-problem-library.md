# Spec — Windows Problem Library View (Phase 98)

## Goal

docs/LONG_RANGE_PLAN.md:211-215: a WPF `PROBLEMS` view over the shared SQLite ledger with
judge/status filters and open-external-link, plus CLI parity (`problems list --judge`). DoD:
`dotnet test` green; CLI gains `problems list --judge`.

## Probe result (documented first, per phase convention)

The Windows ledger (schema v5, `%LOCALAPPDATA%\OJ-NEXUS\ojnexus.db`) has NO problem table —
problem titles exist only inside `codeforces_submissions.problem_name`. A problem-library
surface therefore needs a schema addition. Sources verified in `windows/src/OjNexus.Windows.Core/`:

- `CodeforcesSubmission` carries `ContestId`, `ProblemIndex`, `ProblemName` — enough to derive a
  canonical problem row (`contestId + index` is the Codeforces problem identity, same rule the
  Android app uses).
- `AtCoderSubmission` carries `ProblemId` and `ContestId` — canonical identity `contestId +
  problemId` suffix.
- `LuoguPayloadSnapshot` carries counts only (no problem rows) — Luogu contributes no rows until
  a Luogu problem sync exists; the table is judge-generic so that is a later additive change.
- Problem rows are DERIVED at sync time, not fetched from a new endpoint: no network contract
  changes, no scraping, public data only.

## Design

1. **Schema v6** — new table `problems`:
   `judge TEXT NOT NULL, external_id TEXT NOT NULL, title TEXT NOT NULL, url TEXT NOT NULL,
   first_seen_at TEXT NOT NULL, last_seen_at TEXT NOT NULL, PRIMARY KEY (judge, external_id)`.
   `url` is the canonical public problem link (Codeforces `contest/<id>/problem/<index>`,
   AtCoder `contests/<id>/tasks/<problemId>`). Migrator steps 1..6; v6 is additive (CREATE TABLE
   IF NOT EXISTS + validation); `Migrate_CreatesOnlyTheRequiredSchemaTables` updates from 9 to
   10 tables.
2. **Derivation** — `SqliteSyncStore` derives problem rows inside the SAME transaction that
   persists submission payloads (`CodeforcesSubmissionsPayload`, `AtCoderSubmissionsPayload`):
   UPSERT by (judge, external_id) with `first_seen_at` preserved on conflict, `last_seen_at`
   refreshed. No behavior change when a submission set contains no new problems.
3. **Store reader** — `ISyncStore.GetProblemsAsync(judge?, status?, limit)`; `status` filter
   reuses the verdict-free problem fields we actually have: `SOLVED` (the problem appears with an
   `AC`/accepted verdict in submissions) vs `ATTEMPTED` (appears only with non-accepted
   verdicts). This is honest local data — the Windows ledger does not track practice status
   beyond submissions. Implementation: one extra column `solved INTEGER NOT NULL DEFAULT 0`
   maintained during the same UPSERT (set to 1 when any accepted submission maps to the problem;
   never reset to 0 — an AC is sticky).
4. **CLI** — `ojnexus problems list [--judge <judge>] [--status <solved|attempted>]
   [--limit <n>] [--json]`; human output uppercase lines `#ID  JUDGE  TITLE  STATUS`; JSON
   stable shape `{ "problems": [ { judge, externalId, title, url, solved, firstSeenAt,
   lastSeenAt } ] }`. Exit codes unchanged (Success for a readable list, InvalidArguments for
   bad `--status`).
5. **WPF** — `PROBLEMS` nav entry + page: judge filter ComboBox (ALL/CODEFORCES/ATCODER/LUOGU
   reusing the existing filter pattern), status filter (ALL/SOLVED/ATTEMPTED), rows bound to
   `ProblemRow` (Judge, Title, Status, LastSeen), row OPEN button launching the canonical URL
   via `Process.Start` with `UseShellExecute = true` (no WebView). Page reuses
   NexusBackgroundBrush/Surface/Border/Text/Muted/Accent vocabulary; empty state `NO PROBLEMS
   RECORDED`.
6. **Boundaries** — no new HttpClient call sites (audit test must stay green); no credentials,
   cookies, or sessions; problem titles are data (no localization); the view is read-only —
   recording attempts/reviews stays Android-side until the review-ledger parity phase.

## Tests

- `SchemaMigratorTests`: v6 additive migration (fresh db → 10 tables; v5 db → v6 keeps rows);
  problem table column validation.
- `SqliteSyncStoreTests`: seeding Codeforces submissions derives problem rows with correct
  external_id/url/solved flags; re-persisting keeps first_seen_at and refreshes last_seen_at;
  AtCoder mapping; status filter; limit; malformed rows skipped.
- `CliParserTests`: `problems list` happy path, unknown option rejected, bad `--status` rejected,
  `--limit` must be positive.
- `ProgramTests`: seeded store → `problems list --judge codeforces --json` exit 0 with stable
  JSON; `--status solved` filters; no problems → empty list (still Success).
- `DesktopViewModelTests`: `RefreshProblemsAsync` projects rows + filters by judge/status; OPEN
  action URL composition exposed as a pure function (URL launch itself not unit-tested —
  Windows shell).

## DoD check

- `dotnet test windows/OjNexus.Windows.sln` green (no network).
- CLI `problems list --judge` shipped. WPF PROBLEMS view shipped with judge/status filter +
  external link.
