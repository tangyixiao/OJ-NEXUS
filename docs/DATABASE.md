# OJ NEXUS — Database (Room, v1 implemented)

Status: **implemented** (Phase 1). Schema JSON is exported to `app/schemas/` and committed;
version 1 ships with the Phase 1 code. No destructive migration is configured — future schema
changes require forward migrations with tests.

Deviation from the original plan: there is **no `ActivityEntity` daily-aggregate table**.
Activity is computed by `AnalyticsDao` with SQL `GROUP BY` over precomputed `day_index` columns
(local epoch day, written at record time). At current scale this is simpler, cannot drift, and
already reads only aggregates — revisit if volumes grow.

## Entities (app/schemas/com.ojnexus.core.database.OjNexusDatabase/1.json)

| Table | Purpose | Keys / Notes |
| --- | --- | --- |
| `problems` | unified problem row | UNIQUE(`judge`,`external_id`) = ProblemKey; `difficulty` NULL = unknown; `solved` is sticky (never regresses); `first_solved_at`/`last_attempt_at`/`attempt_count` maintained in the same transaction as attempt inserts |
| `problem_tags` | normalized tag rows | UNIQUE(`name`), stored lowercase-trimmed |
| `problem_tag_cross_ref` | m:n problem↔tag | PK(`problem_id`,`tag_id`), FK CASCADE to both endpoints |
| `attempts` | one row per attempt | FK→problems CASCADE; `day_index` precomputed; `verdict` unified + `raw_verdict` preserved |
| `failure_entries` | root-cause log | FK→problems CASCADE; `attempt_id` FK SET_NULL (survives attempt deletion) |
| `problem_notes` | structured notes (1:1) | PK/FK problem_id CASCADE; upserted on debounce |
| `reviews` | active review schedule (1:1) | PK/FK problem_id CASCADE; stage + `due_at` + `due_day_index` from ReviewScheduler |
| `review_log` | immutable completion history | FK→problems CASCADE; feeds activity (SKIP excluded) |
| `training_tasks` | TODAY list | FK→problems CASCADE; `date_epoch_day` local day key |
| `training_sessions` | session lifecycle | state PLANNED/RUNNING/PAUSED/FINISHED/CANCELLED; timing = persisted snapshots (`started_at`,`paused_at`,`total_paused_ms`,`finished_at`), never ticked |
| `training_session_problems` | session ↔ problem | PK(`session_id`,`problem_id`), FK CASCADE both |

## Conventions & Rules

- All timestamps: UTC epoch millis. All day keys: local epoch day (`LocalDate.toEpochDay()`)
  computed with the user's zone **at write time** — heatmap, streak and queue bucketing never
  re-derive days from timestamps.
- Enum columns store stable Kotlin enum names; mappers degrade unknown names instead of
  throwing (`Verdict.OTHER`, `FailureCategory.OTHER`, …).
- Foreign keys declare explicit `onDelete`; Room enables FK enforcement. Cascades are covered
  by Robolectric DAO tests (`OjNexusDatabaseTest`).
- Unique constraints carry sync idempotency: `(judge, external_id)` will be the dedup key for
  future OJ adapters; the local DB rejects duplicates.
- DAOs return `Flow` for reactive reads and `suspend` for writes; repositories own
  `withTransaction` blocks so multi-table invariants (counters + attempt rows) stay atomic.
