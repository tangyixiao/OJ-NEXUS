# Snapshot Export 2.0 — Design

Date: 2026-10-18 · Phase 86 · v0.3.84 target

## Goal

One file leaves the device: a ZIP archive holding the SQLite database and a JSON manifest of table
counts plus the Phase 73 data-generation stamp. Import validates the manifest, then reuses the
Phase 73/restore validation (schema version, integrity, required tables) before staging.

## Non-goals

No cloud upload, no credential/cookie/secret fields anywhere in the archive, no schema migration,
no change to the restore journal protocol.

## Artifact contract

`oj-nexus-snapshot.zip` (MIME `application/zip`):

- Entry `manifest.json` (first) — `SnapshotManifest` JSON:
  `schemaVersion: Int`, `dataGeneration: String` (non-blank), `createdAtEpochMs: Long`,
  `tableCounts: Map<String,Int>` (one row count per required table), `appVersionName: String?`.
- Entry `database.db` (second) — the `VACUUM INTO` snapshot produced the same way as the legacy
  export (wal_checkpoint(TRUNCATE) first; fallback raw copy on old SQLite builds).
- Exactly two entries. Any extra entry rejects the archive (no stowaways).

## Codec

`SnapshotManifestCodec` (kotlinx.serialization JSON, `ignoreUnknownKeys = true`): `encode` is
deterministic (sorted table counts); `decode` returns `null` on any garbage — never throws.
Unknown/missing fields fail decode; `dataGeneration` blank fails validation at import.

## Provenance rule

The manifest's `dataGeneration` is provenance from the exporting device. Import verifies it is
present but never adopts it as the local generation — the restore protocol still mints its own
generation when the candidate is applied (existing `applyCandidate` behavior).

## Import path

`BackupRepository.importFrom` first opens the source as a snapshot archive:

1. ZipInputStream; entries must be exactly `manifest.json` then `database.db`.
2. Manifest decodes; `schemaVersion == OjNexusDatabase.CURRENT_SCHEMA_VERSION`; `dataGeneration`
   non-blank. Failures return to the legacy path (which rejects non-SQLite input).
3. `database.db` is extracted (bounded read) to a cache temp file and handed to
   `DatabaseRestoreCoordinator.prepareImport(Uri.fromFile(temp))` — the same validation and
   staging as today.
4. Anything that is not a snapshot archive falls through to the legacy raw-database import, so
   previously exported `.db` files keep working.

## Secrets posture

The Room database holds public handles only. The Luogu OpenApp secret lives in
`noBackupFilesDir` (`luogu-openapp-credential.bin`), outside the database and outside this
archive by construction. Tests pin: entry whitelist == {manifest.json, database.db}; raw database
bytes contain no `luogu-openapp` marker; manifest text contains no `secret`/`token`/`cookie`/
`password` substrings.

## Testing

- Pure JVM: codec round trip, garbage decode, unknown keys, missing fields.
- Robolectric: export produces the two-entry zip with correct counts/generation; round trip
  export → staged import → startup restore → row counts match; tampered manifest rejected;
  missing database entry rejected; legacy `.db` import still accepted.
