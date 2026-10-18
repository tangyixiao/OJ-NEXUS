# Snapshot Export 2.0 — Plan

Date: 2026-10-18 · Phase 86

Steps (each gated by compile + tests):

1. `core/data/backup/SnapshotArchive.kt` — `SnapshotManifest`, `SnapshotManifestCodec` (kotlinx
   JSON), `SNAPSHOT_MANIFEST_ENTRY`/`SNAPSHOT_DATABASE_ENTRY` name constants, pure-JVM only.
2. `BackupRepository` — add `exportSnapshot(resolver, destination): Boolean` (snapshot file +
   table counts + manifest + ZipOutputStream) and extend `importFrom` to try the archive path
   first, falling back to the legacy prepareImport. Extract-with-limit for `database.db`.
3. Settings wiring — `CreateDocument("application/zip")` + default name `oj-nexus-snapshot.zip`;
   import MIME list gains `application/zip`; strings updated (`settings_backup_hint` mentions the
   snapshot contents; export/import labels stay).
4. Tests — `SnapshotManifestCodecTest` (pure JVM), `BackupSnapshotRepositoryTest` (Robolectric:
   export shape, no-secret assertions, round trip via restorePending, tamper rejections, legacy
   path still green).
5. Gates, version bump 84/0.3.84, ROADMAP + LONG_RANGE_PLAN entries, commit.
