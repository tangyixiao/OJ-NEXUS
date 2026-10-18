# Plan: Scheduled local backup (Phase 87)

Implements docs/superpowers/specs/2026-10-18-scheduled-backup-design.md.

1. `UserPreferencesRepository`: add `backupCadence: BackupCadence`, `backupTreeUri: String`,
   `backupKeepCount: Int`, `backupLastStatus: String` to `UserPreferences`; keys
   `backup_cadence`/`backup_tree_uri`/`backup_keep_count`/`backup_last_status`; flows + setters
   `setBackupCadence(cadence, treeUri)` (persists tree permission), `setBackupTreeUri(uri)`,
   `setBackupKeepCount(n)` (clamped 1..20), `recordBackupStatus(success)`.
2. `core/data/backup/BackupCadence.kt`: enum OFF/DAILY/WEEKLY with `fromStored(value: String?)`
   (never throws, unknown → OFF) and `stored()`; object `ScheduledBackupScheduler` with
   `update(context, cadence)` + `UNIQUE_NAME`.
3. `core/data/backup/ScheduledBackupWorker.kt`: CoroutineWorker; `autoBackupName(clock)` returns
   `oj-nexus-auto-<yyyyMMdd-HHmmss>.zip`; `pruneSelection(names, keep)` pure; worker resolves tree,
   creates document, `exportSnapshot`, prunes, `recordBackupStatus`; returns success/failure
   (retry once on IOException? No — deterministic failure, no retry for SAF misconfig).
4. `OjNexusApplication`: expose `backupRepository` already exists; add
   `ScheduledBackupScheduler.update(this, cadence)` on create, reading the pref synchronously via
   runBlocking first() (acceptable on cold start, mirrors restorePending).
5. `SettingsViewModel`: expose `backupCadence`, `backupTreeUri`, `backupKeepCount`,
   `backupLastStatus` from prefs; actions `chooseBackupFolder(uri)`, `clearBackupFolder()`,
   `setBackupCadence(cadence)`, `setBackupKeepCount(n)`, `dismissBackupStatus()`; cadence setter
   also calls the scheduler.
6. `SettingsScreen`: data section gains an AUTO BACKUP block — cadence chip row (OFF/DAILY/WEEKLY),
   folder row (chosen folder label or PICK FOLDER via `ActivityResultContracts.OpenDocumentTree`),
   keep-count stepper (− N +), status banner (dismissible). Strings EN + zh-rCN.
7. Tests: `BackupCadenceTest` (parse/unknown), `ScheduledBackupWorkerTest` (Robolectric:
   autoBackupName format, pruneSelection keep-N, OFF cancels work — via WorkManager test init if
   available, else scheduler logic separated; keep to pure + name tests plus a Robolectric
   recordBackupStatus test), settings layout source test extended if a banner id is added.
8. Gates: `test` + `assembleDebug` green; bump version; ROADMAP + LONG_RANGE_PLAN; commit.
