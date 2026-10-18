# SPEC — Phase 89: Import Conflict Report

Date: 2026-10-18
Phase: 89 of docs/LONG_RANGE_PLAN.md (ARC B — Data integrity & portability)
Status: draft → implemented

## Goal

After import validation, show per-table **incoming vs current** counts BEFORE the
restore replaces the live database. The user confirms or cancels; cancel must leave
the current database untouched.

## Scope

- Phase 73 restore flow UI (Settings → Local Data → Import).
- `BackupRepository` gains a read-only preview; `DatabaseRestoreCoordinator` stays untouched.

## Constraints

- The preview must never stage anything: no pending-restore file, no journal write.
- Legacy raw `.db` sources have no manifest → no preview; they import directly
  (existing behavior preserved).
- The preview reads only the manifest entry — it must not extract the 512 MiB-bounded
  database payload just to show counts.
- Counts come from honest sources: incoming = manifest `table_counts` recorded at
  export time; current = live Room database `SELECT COUNT(*)` per required table.

## Design

1. `SnapshotConflictRow(table, incoming, current)` and
   `SnapshotConflictReport(schemaVersion, dataGeneration, createdAtEpochMs, rows)`
   live in `core/data/backup/SnapshotArchive.kt` next to the manifest types.
2. `BackupRepository.inspectSnapshot(resolver, source): SnapshotConflictReport?`
   streams the zip, reads only `manifest.json` (same strict entry rules as
   `extractSnapshotDatabase`: unknown or duplicate entries → null), and pairs
   manifest counts with live counts for the 22 required tables.
3. `SettingsViewModel`:
   - `inspectImport(resolver, source)` → report non-null → `pendingImport` state;
     report null (legacy/invalid) → direct `importBackup` as before.
   - `confirmImport()` clears the pending state and runs `importBackup`.
   - `cancelImport()` clears the pending state; nothing else happens.
4. `SettingsScreen`: the import launcher now calls `inspectImport`; a preview
   `AlertDialog` lists one row per table (`table  incoming → current`) with
   CONFIRM IMPORT / CANCEL actions, mirroring the Phase 88 typed-confirm dialog.
5. DoD test: repository-level — preview does not create `oj-nexus-pending-restore.db`
   or the journal, and table counts are unchanged; the manifest-vs-live counts differ
   when the database grows after the export.

## Non-goals

- Diffing row content (counts only).
- Changing `DatabaseRestoreCoordinator` or the on-disk restore protocol.
