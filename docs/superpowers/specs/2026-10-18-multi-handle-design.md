# Spec — Multi-Handle Per Judge, Design-Gated (Phase 97)

## Goal

docs/LONG_RANGE_PLAN.md:199-203: spec + prototype for a second public handle per judge with
isolated datasets. UI implementation splits into a later phase if risky. DoD: spec accepted;
migration test plan written.

## Ground truth (verified in code, 2026-10-18)

- `judge_accounts` unique index is `(judge, canonical_handle)` — several accounts per judge are
  ALREADY representable. `findActiveByJudge` reads `WHERE judge = ? AND enabled = 1 LIMIT 1`, so
  the "active handle" is really "the only enabled row".
- `sync_states` PK is `judge` — one cursor/freshness row per JUDGE. This is the real blocker for
  true per-handle isolation: cursors, freshness stamps, and the submission cursor would be shared
  or thrashed between handles.
- `sync_operations` already carries `account_id`; per-stage receipts are attributable.
- Work identity is already account-scoped: `JudgeWorkNames.manual(judge, accountId)` /
  `JudgeWorkNames.periodic(judge, accountId)`; workers verify `account.enabled` and
  `account.judge` before acting, so stale work for a removed account is a no-op.
- Data generation (Phase 73) is a GLOBAL stamp in `oj-nexus-data-generation`, captured in worker
  inputs and in `sync_operations.data_generation`. Its ONLY job is to invalidate work after a
  whole-database restore. It is not a per-handle concept and must stay global — per-handle
  staleness is already answered by the account id in work inputs and ledger rows.
- Phase 88 `wipeJudgeData(judge)` wipes every account of a judge — unchanged by multi-handle.

## Decisions

1. **Generation stays global.** Restores replace the whole database, so a per-handle generation
   adds nothing; the Phase 73 invariant (work created under an earlier generation performs no
   remote operation after restoration) is preserved verbatim.
2. **Stage 1 (this phase): secondary handles are DISABLED accounts.** A second handle binds
   through the normal connector (validated, canonical) and is stored with `enabled = false` so
   `findActiveByJudge` keeps resolving to the current handle. Switching = enable the target +
   disable the previous + delete the previous `sync_states` row when the handle changed (the
   incremental cursor belongs to the old handle — same rule as `connect` today). No migration is
   required for stage 1.
3. **Stage 2 (later phase, migration-gated): per-account cursors.** v14 migration changes
   `sync_states` PK to `(judge, account_id)` (backfilling `account_id` from the existing column).
   Only after that can two handles keep independent cursors and freshness stamps.
4. **Dataset isolation is OUT of stage 1 scope.** `problems` unique key is
   `(judge, external_id)` and attempts are user-authored training history, not remote snapshots.
   True dataset separation needs an account dimension on problems/attempts/reviews — a v15-scale
   change deferred until stage 2 proves the cursors work.

## Prototype (this phase)

`core/data/sync/MultiHandlePrototype.kt` — pure decision planner encoding the stage-1 rules:
`planMultiHandleConnect(accounts, judge, requestedCanonical)` returns `REUSE_EXISTING` (same
canonical handle re-binds in place), `ADD_SECONDARY` (new disabled row, fresh cursor on switch),
or `REPLACE_ACTIVE` (at the 2-handle cap: remove other accounts + cursors, reuse the slot), plus
`isWorkStaleForAccount(workAccountId, activeAccountIds)` (work whose account is gone is stale).
Unit-tested; NOT wired into production flows — `connect()` keeps its current single-handle
behavior until the UI phase adopts the planner.

## Migration test plan (stage 2, v14)

1. `MIGRATION_13_14` rebuilds `sync_states` with PK `(judge, account_id)` + unique index; the
   existing row keeps its judge, cursor columns, and `account_id`.
2. Migration test: create v13 db with one populated `sync_states` row (cursor values set), run
   13→14, assert the row survives with identical column values and is joined to its account.
3. Room `fallbackToDestructiveMigration` stays OFF; the chain becomes 1→14.
4. `DatabaseRestoreCoordinator.REQUIRED_TABLES` unchanged (`sync_states` remains one table).
5. Snapshot round trip (Phase 86) re-run: export on v14, import on v14, counts match.
6. Restore of an OLD v13 snapshot on a v14 app: schema mismatch → REJECTED with the existing
   typed reason (schema upgrade of snapshots is intentionally not automatic).
7. `wipeJudgeData` (Phase 88) test re-run on v14: all `sync_states` rows of the judge disappear.
8. Phase 73 stale-generation test re-run: a worker carrying an old generation still records
   STALE_DATA_GENERATION — global generation semantics unchanged.

## DoD check

- Spec written and accepted in-repo (this document). Migration test plan: section above.
- Prototype pure + unit-tested. UI deferred (per plan, split into a later phase).
- No schema change shipped in this phase; no production flow altered.
