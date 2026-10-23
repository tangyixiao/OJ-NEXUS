# Phase 122-b — WAL-consistent snapshot export

## Scope

Ensure `BackupRepository` exports every committed Room row, including changes still present only
in SQLite's WAL. The snapshot must be a consistent standalone database before it is added to the
existing ZIP format. Do not change manifest/schema/version/signing or import/restore protocols.

## Acceptance

- A real file-backed Room regression enables WAL, disables automatic checkpointing, commits recent
  rows, proves WAL data is present, exports a snapshot, then extracts and queries the archived DB
  without its WAL sidecar. The archive and preview counts must include the latest data.
- If snapshot creation cannot produce a verified standalone database, export
  fails and cleans up rather than silently copying a potentially stale main DB.
- Keep legacy raw export available only where its source database can be proven consistent.
- Run the test red before production changes, then green, full tests, debug and release builds.
  Keep the validator-owned emulator 5580 untouched.

## Plan

1. Add a file-backed Room/WAL test that asserts latest committed rows in the extracted archive
   database and matching manifest counts; run against the current implementation and record RED.
2. Use `VACUUM INTO` as SQLite's transaction-consistent snapshot operation; remove the stale
   main-file copy fallback and validate the standalone result before manifest counts/export.
3. Rerun focused and full Android gates, retain fresh XML/logs, document synthetic fixture data
   and the remaining device-level validation boundary.
