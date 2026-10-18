# Sync op-log ledger (RFC 0001 §3) — Plan

Date: 2026-10-18
Phase: 118 (reserved slot filled; the RFC survived the Phase 117 verdict)
Spec: docs/superpowers/specs/2026-10-18-sync-oplog-ledger.md

## Steps executed

1. Schema v15 (OJ_NEXUS_SCHEMA_VERSION 14→15): three additive tables —
   `sync_log_operations` (opId PK, deviceId, lamport, createdAtEpochMs, schemaVersion, status,
   encryptedPayloadPath), `sync_log_ops` (auto id, opId, tableName, rowKey, kind, payloadJson),
   `sync_conflict_log` (auto id, tableName, rowKey, winnerOpId, winnerDeviceId, winnerLamport,
   resolvedAtEpochMs, reason). MIGRATION_14_15 registered in build(); all 11 addMigrations test
   chains extended.
2. Three DAOs: SyncLogOperationDao (upsert/findById/recent/findByStatus/observeRecent/
   updateStatus/maxLamport/deleteById), SyncLogOpDao (insertAll/findByOpId/deleteByOpId),
   SyncConflictLogDao (insert/recent/count/clear).
3. SyncLogRepository (core/data/sync): appendLocalBatch (lamport = max+1, transactional batch +
   row ops), resolveRemoteBatch (SyncMergeKernel merge; every loser appended to the conflict
   log with the kernel reason string), markSealed/markUploaded lifecycle with unknown-batch
   rejection.
4. Tests: SyncLogRepositoryTest 4 tests (lamport monotonicity + persistence, conflict-log rows
   carry winner + reason, PENDING→SEALED→UPLOADED transitions with payload path, empty batch
   rejected); RoomIndexAuditTest updated to v15 + renamed; MigrationTest chains extended.

## Result

- `com.ojnexus.core.database.*` + `com.ojnexus.core.sync.*` + SyncLogRepositoryTest all green;
  full gates test assembleDebug assembleRelease BUILD OK.
- Credential boundary held: the ledger has no reference to judge_accounts; the repository API
  accepts only explicit row ops. The conflict log makes every deterministic resolution
  inspectable (RFC §3 requirement).

### Lessons

- Room @Index entries must use the Kotlin property names (deviceId, not device_id) — the
  compiler emits the "referenced in the index does not exist" KSP error otherwise; the
  migration DDL must then use the SAME camelCase column names Room generates.
- `androidx.room.withTransaction` is an extension on RoomDatabase — needs an explicit import.
- SyncOpStatus lives in the entity file's package (com.ojnexus.core.database.entity), not
  core.sync — import list trimmed accordingly.
