# Local Data Safety

OJ NEXUS keeps study history, notes, reviews, cached judge data, and derived analytics in a
local Room database. Settings provides two document-picker actions:

- `EXPORT DATABASE BACKUP` checkpoints the database and writes a self-contained SQLite copy.
- `IMPORT DATABASE BACKUP` copies a selected file into a private pending-restore location only
  after checking the SQLite format, the current schema version, and the `problems` table.

The pending file is applied before Room opens on the next app start. Until the app is restarted,
the current session continues using its existing database. Invalid files are rejected and do not
replace the pending restore file. No passwords, cookies, or private OJ sessions are included.
