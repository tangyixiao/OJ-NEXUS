# Plan — Database size & housekeeping panel (Phase 90)

1. `core/data/repository/DatabaseMaintenanceRepository.kt`:
   - `DatabaseMaintenanceStats(dbFileBytes, walBytes, pageSize, pageCount, freelistCount, rowCounts: Map<String,Int>)`, `val dbBytes get() = pageSize * pageCount`.
   - `maintenanceStats()`: PRAGMA page_size / page_count / freelist_count on `database.openHelper.writableDatabase.query(...)`, `SELECT COUNT(*)` per MAINTENANCE_TABLES (22 names), file lengths from injected `databaseFile: File`.
   - `suspend vacuum()`: withContext(IO) { wal_checkpoint(TRUNCATE); compileStatement("VACUUM").executeUpdateDelete() }; rethrow CancellationException.
   - companion `MAINTENANCE_TABLES` — same 22 names as restore REQUIRED_TABLES.
2. `formatBytes(bytes): String` helper (B / KiB / MiB / GiB) — pure, unit-testable.
3. AppContainer: `databaseMaintenanceRepository`.
4. SettingsViewModel: `maintenanceStats: MutableStateFlow<DatabaseMaintenanceStats?>`; `refreshMaintenanceStats()` on IO; `startVacuum()` — guard re-entrancy, launch job, `vacuumRunning=true`, elapsed ticker (1s), on completion `vacuumRunning=false` + stats refresh; `requestVacuumCancel()` sets a flag — if the vacuum still commits, expose `vacuumCancelRaced` (UI shows "finished atomically").
5. SettingsScreen data section: MAINTENANCE block — size line, ROW COUNTS list, VACUUM action (disabled while running), progress elapsed ticker text, cancel action, atomic-finish notice.
6. Strings `settings_maintenance_*` EN + zh-rCN.
7. Tests `DatabaseMaintenanceRepositoryTest` (Robolectric): stats honest vs seeded rows; vacuum completes + stats consistent; formatBytes cases.
8. Gates: `test assembleDebug` BUILD SUCCESSFUL; bookkeeping versionCode 88 / 0.3.88, ROADMAP prepend (bilingual), docs/LONG_RANGE_PLAN.md:154 ✅, commit `feat:`.
