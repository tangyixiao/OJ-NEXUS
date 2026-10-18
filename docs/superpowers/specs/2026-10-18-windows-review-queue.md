# Windows Review Queue (Phase 99)

## Context

- SPEC docs/LONG_RANGE_PLAN.md:217-219: due-review list + PASS/HARD/FAIL recording on Windows
  writing to the shared ledger; DoD CLI `review due` / `review mark` parity with the Android
  scheduler semantics.
- The Windows client currently has no review surface: schema v6 tables are
  schema_metadata/accounts/sync_operations/sync_modules/codeforces_profiles/codeforces_ratings/codeforces_submissions/atcoder_submissions/luogu_payloads/problems.
- Android ground truth for scheduling: `ReviewScheduler.kt` — intervals [1,3,7,21,45,90] days,
  PASS advances stage capped at index 5 with the full interval, HARD stays at the stage with half
  the interval (min 1 day), FAIL drops the stage floored at 0 and re-tests the next day, SKIP
  repeats the stage with the full interval.

## Constraints

- Additive schema change only (v6 → v7): new tables, no alteration of existing tables.
- No credentials, no cookies, no new network call sites; everything is local ledger state.
- Reuse the shared SQLite ledger: reviews must be readable by the CLI (`review due` /
  `review mark`) in this phase; WPF is not required by the DoD.
- Timezone contract: due_day_index uses the machine-local calendar day (same as Android's local
  epoch day); due_at is UTC (ISO-8601 "O" round-trip format like other ledger timestamps).

## Design

- Schema v7 additive:
  - `reviews` — PK (judge, external_id); stage INTEGER NOT NULL, due_at TEXT NOT NULL,
    due_day_index TEXT NOT NULL (yyyy-MM-dd local), last_result TEXT NULL,
    last_reviewed_at TEXT NULL, created_at TEXT NOT NULL.
  - `review_log` — id INTEGER PK AUTOINCREMENT, (judge, external_id), result TEXT NOT NULL,
    stage INTEGER NOT NULL, completed_at TEXT NOT NULL, day_index TEXT NOT NULL.
- Pure scheduler mirror `ReviewIntervalLadder` in
  `windows/src/OjNexus.Windows.Core/Domain/ReviewModels.cs`:
  - intervals [1,3,7,21,45,90], max stage index 5;
  - PASS → stage+1 (capped at 5) + full interval; HARD → same stage + max(interval/2, 1);
    FAIL → stage-1 (floor 0) + 1 day; SKIP → same stage + full interval;
  - `ScheduleNext(result, stage, now)` returns (newStage, dueAt, dueDayIndexLocal).
- Store surface (ISyncStore + both implementations):
  - `GetDueReviewsAsync(judge?, limit, ct)` — due_day_index <= today(local), ORDER BY
    due_day_index ASC, judge ASC, external_id ASC, LEFT JOIN problems for title/url;
  - `MarkReviewAsync(judge, externalId, result, now, ct)` — transactional upsert of the review
    row (stage transition per ladder) + append to review_log; result parse "pass|hard|fail|skip".
- CLI:
  - `review due [--judge <judge>] [--limit <positive>] [--json]` (default limit 50; empty →
    "REVIEWS DUE: EMPTY", success exit);
  - `review mark --judge --problem --result pass|hard|fail|skip [--json]` — allowed to create a
    review row for any (judge, externalId) the user names; title/url joined from `problems` when
    the row exists.
- Renderer overloads: RenderHuman/RenderJson over `IReadOnlyList<ReviewEntry>` and a mark
  confirmation line / JSON object.

## Test Plan

- Migrator: v6→v7 chain step test + exact-tables list updated to 11 tables.
- Store: due list ordering + limit + judge filter; mark creates row at stage 0 due tomorrow;
  PASS/HARD/FAIL/SKIP stage transitions (sticky ladder parity with Android); log rows appended
  each mark; mark on unknown problem still works (no FK).
- CLI parser: `review due` defaults, options validation (unknown judge/status/limit/flag),
  `review mark` requires --judge/--problem/--result, invalid result rejected.
- Program tests: `review due` empty + populated rendering (human + JSON); `review mark` writes
  ledger and renders confirmation (human + JSON).
- Gates: `dotnet test` on the Windows solution (CLI + Core + Desktop) + `dotnet build -c Release`.

## Result

(filled at completion)
