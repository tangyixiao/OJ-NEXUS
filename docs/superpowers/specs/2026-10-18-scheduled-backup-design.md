# Design: Scheduled local backup (Phase 87)

Date: 2026-10-18 · Phase 87 of docs/LONG_RANGE_PLAN.md

## Goal

Opt-in WorkManager backup of the full local snapshot (Phase 86 archive) to a user-picked SAF
folder, daily or weekly, keeping the last N archives (default 5). No cloud upload ever. Failures
surface as a dismissible banner in the settings data section.

## Decisions

1. **Cadence preference.** `BackupCadence { OFF, DAILY, WEEKLY }` stored as a string
   DataStore key `backup_cadence` (`OFF` default). The SAF tree URI is stored as
   `backup_tree_uri` (string, empty default). Retention count `backup_keep_count` (int, default 5,
   clamped 1..20). All live in `UserPreferencesRepository` next to the existing keys; the
   `UserPreferences` data class grows `backupCadence`, `backupTreeUri`, `backupKeepCount`.
   Persisting a tree URI also takes `takePersistableUriPermission` (read+write) so the worker can
   use it across reboots.
2. **Worker.** `core/data/backup/ScheduledBackupWorker.kt` — `CoroutineWorker` in `core/data/backup`
   (it is data-layer work, not judge sync). It reads the prefs, resolves the tree URI, creates a
   dated document `oj-nexus-auto-<yyyyMMdd-HHmmss>.zip` via `DocumentsContract.createDocument`
   (no `androidx.documentfile` dependency — the catalog has none and DocumentsContract is
   sufficient), delegates to `BackupRepository.exportSnapshot`, then prunes: children of the tree
   whose display name matches `oj-nexus-auto-*.zip` are sorted by name (the timestamp sorts
   lexically) and deleted beyond the newest `keepCount`. The worker never touches credentials.
3. **Scheduling.** `BackupBackupScheduler`-style object `ScheduledBackupScheduler` with
   `update(context, cadence)`: OFF → `cancelUniqueWork`; DAILY → `enqueueUniquePeriodicWork`
   (`ExistingPeriodicWorkPolicy.UPDATE`, 1 day); WEEKLY → 7 days. Constraints:
   `setRequiresBatteryNotLow(true)` + `setRequiresStorageNotLow(true)`, **no network constraint**
   (the job is purely local). Unique work name constant `oj-nexus-scheduled-backup`. Called from
   `SettingsViewModel` whenever the cadence preference changes, and from
   `OjNexusApplication.onCreate` (so an installed schedule survives reboots — WorkManager
   persists work, this re-enqueue with UPDATE policy is idempotent and also re-prunes stale
   schedules if the tree URI was revoked the cadence stays; the worker itself fails gracefully).
4. **Failure surface.** The worker writes the outcome into the same DataStore:
   `backup_last_status` = `success:<epochMs>` / `failed:<epochMs>` (string). The settings data
   section shows a dismissible banner (same pattern as the restore banner): last success time or
   the last failure time. Dismiss clears the flag locally (session-only dismiss, the stored value
   stays for history).
5. **Retention.** `pruneSnapshots(resolver, treeUri, keep)` is a top-level pure-ish function taking
   the content resolver so the unit-testable listing/sorting logic can be tested with Robolectric's
   ShadowDocumentsContract — in practice the pure part (name filter + keep-N selection from a list
   of display names) is extracted to `pruneSelection(names: List<String>, keep): List<String>` and
   unit-tested without any Android dep.
6. **No new dependencies.** DocumentsContract + existing WorkManager; no documentfile, no cloud.

## Out of scope

- Any network upload, any credential flow, any Room migration.
- Exact-time-of-day scheduling (WorkManager periodic intervals only — daily/weekly).
