# Local Data Safety

OJ NEXUS keeps study history, notes, reviews, cached judge data, and derived analytics in a
local Room database. Settings provides two document-picker actions:

- `EXPORT DATABASE BACKUP` checkpoints the database and writes a self-contained SQLite copy.
- `IMPORT DATABASE BACKUP` copies a selected file into a private pending-restore location only
  after checking SQLite format, the current schema version, quick/integrity checks, and every
  required Room table.

The pending file is applied before Room opens on the next app start. The target database and its
WAL/SHM companions are retained in a same-directory rollback set until the replacement validates;
an interrupted swap is recovered from the journal. Until the app is restarted, the current session
continues using its existing database. Invalid files are rejected and do not replace the active
database. A successful restore changes a local data-generation marker, so stale background work
cannot act on reused account or request IDs. No passwords, cookies, OpenApp secrets, source code,
custom input, or private OJ sessions are included in backup metadata.
