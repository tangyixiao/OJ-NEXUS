# Plan — Windows Review Queue (Phase 99)

1. Schema v7 in `windows/src/OjNexus.Windows.Core/Storage/SchemaMigrator.cs`: bump
   CurrentSchemaVersion to 7, add ApplyVersionSevenMigration (reviews + review_log) +
   ValidateVersionSevenSchema, chain into every migration arm, `version == Current` validates v7.
2. Domain `windows/src/OjNexus.Windows.Core/Domain/ReviewModels.cs`: ReviewResult enum
   (Pass/Hard/Fail/Skip), parser, ReviewEntry record, ReviewIntervalLadder pure scheduler.
3. Contracts: ISyncStore gains GetDueReviewsAsync + MarkReviewAsync; SqliteSyncStore implements
   both (transactional mark); InMemorySyncStore mirrors semantics.
4. CLI: CliParser review due/mark commands; Program dispatch; ConsoleRenderer overloads.
5. Tests: migrator chain + exact-tables (11), store scheduler transitions, parser, program
   round-trips.
6. Gates: dotnet test all projects; dotnet build -c Release.
7. Bookkeeping: plan Result fill, docs/LONG_RANGE_PLAN.md ✅, docs/ROADMAP.md bilingual section,
   commit feat:.

## Result

Completed 2026-10-18. Schema v7 additive: reviews (PK judge+external_id, stage, due_at ISO-8601,
due_day_index yyyy-MM-dd, last_result, last_reviewed_at, created_at) + review_log (append-only,
every mark appends even at the stage cap) + idx_reviews_due_day + idx_review_log_identity; no FK to
problems — marking accepts unknown identities and the due list joins title/url when present.
ReviewIntervalLadder mirrors the Android ReviewScheduler ladder [1,3,7,21,45,90]: PASS stage+1 cap 5
+ full interval, HARD same stage max(full/2,1), FAIL stage-1 floor 0 + next day, SKIP same stage
full interval. CLI parity: review due [--judge][--limit][--json] (default 50) and review mark
--judge --problem --result pass|hard|fail|skip [--json]; unknown result → ARGUMENT ERROR.
Tests: Core 80 (migrator v6→v7 + exact-tables 12, ladder transitions incl cap=5 with 7 log rows,
unknown identity, order/limit honesty — ORDER BY due_day_index ASC, judge ASC, external_id ASC puts
AtCoder before Codeforces), CLI 65 (parser 9 new incl Theory arrays, program mark→due round trip,
empty human output, bad limit). Full gates: Core 80 + CLI 65 + Desktop 21 green, Release build
clean. Test-fix lessons: a FAIL at stage 0 schedules the NEXT day so the row is not due today;
marks stamped with past timestamps intentionally reappear in the due list.

