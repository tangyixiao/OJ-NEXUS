# Room Index Audit — Plan

Date: 2026-10-18
Phase: 106 (docs/LONG_RANGE_PLAN.md:268-271)
Spec: docs/superpowers/specs/2026-10-18-room-index-audit.md

## Steps

1. Enumerate the ten hottest production queries from DAO call sites (sync idempotency, library
   flow, review-due subqueries, analytics heatmap, Luogu job scan, delta-sync fingerprint read,
   worker dedupe guard, review history, solved distribution).
2. Run `EXPLAIN QUERY PLAN` for each against a seeded 10k-problem / 30k-attempt Robolectric
   database; record plan text per query.
3. Add only the two proven composites: `submission_jobs (judge, status, updated_at)` and
   `remote_problems (judge, external_id)`.
4. Bump `OJ_NEXUS_SCHEMA_VERSION` 13→14; write `MIGRATION_13_14` as additive
   `CREATE INDEX IF NOT EXISTS` DDL; mirror the indices in the entity `@Index` declarations so
   Room schema validation agrees.
5. Register `MIGRATION_13_14` in `OjNexusDatabase.build()` and extend every test migration chain.
6. Test: fresh v14 carries both indices; a hand-built v13 database (DDL captured from a probe
   open) upgrades in place with rows preserved and plans switching to `USING INDEX`.
7. Record before/after numbers honestly in the spec + docs/PERFORMANCE.md (plan shape is the
   device-independent proof; device wall-clock stays PENDING).

## Result

- `OjNexusDatabase.kt`: `OJ_NEXUS_SCHEMA_VERSION` 13→14; `MIGRATION_13_14` added (two
  `CREATE INDEX IF NOT EXISTS` statements) and registered in `build()`.
- `SubmissionJobEntity` / `RemoteProblemEntity` gained the matching `@Index` declarations.
- `RoomIndexAuditTest` (2 tests): fresh-v14 index presence via EXPLAIN QUERY PLAN; v13→v14
  in-place upgrade preserving seeded rows and switching both plans to `USING INDEX`.
- `MigrationTest` chains extended with `MIGRATION_13_14` (11 sites) — otherwise Room threw
  "A migration from 12 to 14 was required but not found" once the version bumped.
- Full gate: `test assembleDebug` BUILD SUCCESSFUL, 742 tests, 0 failures.
- versionCode 98 / versionName 0.3.98; ROADMAP section prepended; spec timing table finalized.

### Lessons

- Room validates EVERY entity table AND every declared index after a migration, not just the
  migrated tables — a hand-built legacy database must capture the full DDL (tables + indices)
  from a probe open, or validation fails with `Migration didn't properly handle: …`.
- Index capture must be filtered by index NAME (excluding only the two the migration creates),
  not by `tbl_name` — the pre-v14 indices on the migrated tables themselves remain part of the
  expected v13 schema.
- NOT NULL columns without Kotlin defaults (`RemoteProblemEntity.difficulty_source`, `.tags`)
  must be supplied explicitly in raw seed INSERTs.
- `SupportSQLiteDatabase.query(sql, args)` takes an object array, not a vararg spread.
