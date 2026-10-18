# Spec — Database size & housekeeping panel (Phase 90)

Source: docs/LONG_RANGE_PLAN.md:154-157.

- Goal: settings shows database size, per-table row counts, and a VACUUM action.
- Constraints: VACUUM runs off the main thread with progress + cancel.
- DoD: no schema change; sizes reported honestly from `PRAGMA`.

## Design

New `DatabaseMaintenanceRepository(database, databaseFile)` in `core/data/repository/`:

- `DatabaseMaintenanceStats(dbFileBytes, walBytes, pageSize, pageCount, freelistCount, rowCounts: Map<String,Int>)` with computed `dbBytes = pageCount * pageSize` (honest, from PRAGMA page_count × page_size).
- `maintenanceStats(): DatabaseMaintenanceStats` — PRAGMA page_size / page_count / freelist_count via `database.openHelper.writableDatabase.query(...)`, row counts via `SELECT COUNT(*) FROM "<table>"` for a curated 22-table list (mirror of the restore REQUIRED_TABLES list), plus raw `databaseFile.length()` and `<db>-wal` length for file-level honesty.
- `suspend fun vacuum()` — `withContext(Dispatchers.IO)`: `wal_checkpoint(TRUNCATE)` then `compileStatement("VACUUM").executeUpdateDelete()`. CancellationException rethrown.

## UI (SettingsScreen data section, MAINTENANCE subsection)

- Size line: db size (formatted), free pages, WAL file bytes.
- ROW COUNTS: table + count rows.
- VACUUM `SettingsAction` — disabled while running.
- Progress: indeterminate, elapsed seconds ticking (SQLite VACUUM has no framework progress callback — elapsed time is the honest signal).
- CANCEL during run: cancels the job. SQLite VACUUM cannot be interrupted mid-statement through the framework API, so when a cancel is requested but the vacuum commits anyway, the UI reports "CANCEL REQUESTED — VACUUM FINISHED ATOMICALLY" (truthful, never fake).

## Tests (Robolectric)

- stats honestly reflect seeded rows (problems count matches) and pageSize > 0, pageCount > 0, dbBytes = pageSize × pageCount.
- vacuum() completes without throwing and stats remain consistent afterwards.
- formatBytes formatting helper (B / KiB / MiB).

Strings: `settings_maintenance_*` EN + zh-rCN. Bookkeeping: versionCode 88 / 0.3.88, ROADMAP prepend, plan mark, commit feat:.
