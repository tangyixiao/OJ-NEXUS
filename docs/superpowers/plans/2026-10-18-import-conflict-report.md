# PLAN — Phase 89: Import Conflict Report

Date: 2026-10-18
Spec: docs/superpowers/specs/2026-10-18-import-conflict-report.md

## Steps

1. `SnapshotArchive.kt`: add `SnapshotConflictRow(table, incoming, current)` and
   `SnapshotConflictReport(schemaVersion, dataGeneration, createdAtEpochMs, rows)`.
2. `BackupRepository.kt`: add `inspectSnapshot(resolver, source)` — stream the zip,
   read ONLY `manifest.json` (reject unknown/duplicate entries), decode the manifest
   with the strict codec, and pair `manifest.tableCounts` with live `SELECT COUNT(*)`
   via the existing live-db counting path (shared helper with `countTables`).
   Return null when the source is not a valid snapshot (legacy path unaffected).
3. `SettingsViewModel.kt`: `pendingImport` StateFlow<SnapshotConflictReport?>;
   `inspectImport(resolver, source)` sets it (null → straight `importBackup`);
   `confirmImport()` clears state and imports; `cancelImport()` clears state only.
4. `SettingsScreen.kt`: import launcher → `inspectImport`; when `pendingImport`
   shows an `AlertDialog` with per-table rows `TABLE  INCOMING → CURRENT` and
   CONFIRM IMPORT / CANCEL actions.
5. Strings (EN + zh-rCN): settings_import_conflict_title, _body, _table_header,
   _confirm, _cancel.
6. Tests (`BackupSnapshotRepositoryTest.kt` extension):
   - preview returns the manifest counts paired with live counts and stages nothing
     (no pending-restore file, no journal);
   - preview counts differ from manifest counts after the database grows;
   - preview returns null for a legacy raw `.db`.
7. Gates: `tools/gradlew-local.bat test assembleDebug` → BUILD SUCCESSFUL.
8. Bookkeeping: versionCode 87 / versionName 0.3.87, ROADMAP entry (bilingual,
   closing no-network/no-migration/no-credential sentence), mark Phase 89 ✅ in
   docs/LONG_RANGE_PLAN.md, commit `feat:`.

## Risks

- Robolectric `SQLiteDatabase.openDatabase` on copied files can fail → reuse the
  live-db fallback path for current counts (same quirk as Phase 86).
- The preview dialog must be cancelable and must not trigger any file writes on
  dismiss (cancel path touches no files by construction).
