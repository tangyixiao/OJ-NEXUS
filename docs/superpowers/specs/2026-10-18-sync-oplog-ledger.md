# Sync op-log ledger (RFC 0001 §3) — Spec

Date: 2026-10-18
Phase: 118 (reserved slot filled by the RFC/POC evidence; RFC survived the Phase 117 verdict)

## Goal

Give the sync RFC a real persistence floor: the device-local op-log tables, the merge/conflict
write path, and the lifecycle transitions — all additive, all local, no network.

## Scope

1. **Schema v15** (additive): `sync_log_operations` (opId PK, deviceId, lamport,
   createdAtEpochMs, schemaVersion, status, encryptedPayloadPath), `sync_log_ops` (row ops per
   batch), `sync_conflict_log` (one row per displaced op, with the kernel's reason string).
   Indices on (deviceId, lamport), status, (tableName, rowKey), resolvedAtEpochMs.
2. **SyncLogRepository** (core/data/sync): appendLocalBatch (lamport = max+1, transactional
   insert of batch + row ops), resolveRemoteBatch (kernel merge, conflict rows for every
   loser), markSealed/markUploaded lifecycle.
3. Migration 14→15 registered; no destructive DDL; REQUIRED_TABLES unchanged (the sync log is
   device-local and never syncs itself — it is not a data table).

## Hard boundaries (RFC §0 restated for this phase)

- No table references `judge_accounts`; the repository has no API surface naming credentials or
  the Luogu open-platform store.
- The ledger records *intent*; nothing here performs network I/O. Transport remains Phase 119+
  territory, still behind `BuildConfig.SYNC_POC`.

## DoD

- Migration tested (v14→v15 in-place); repository tests: lamport monotonicity, batch rows
  persisted, conflict log carries every loser with the reason string, lifecycle transitions,
  empty batch rejected.
- Ledger tables excluded from snapshot REQUIRED_TABLES (they are operational state, not user
  data) — snapshot round trips unaffected.
