# Local Data Safety

OJ NEXUS keeps study history, notes, reviews, cached judge data, and derived analytics in a
local Room database. The reviewed Android Phase 122 composition is `19f0bbc` (PRs #14, #15,
and #18); those changes remain unmerged. The published `v1.0.3` does not contain that composition:
its raw-database export/ZIP inspection mismatch and WAL snapshot gap are recorded in the
[Phase 122 acceptance report](verification/phase-122-android.md).

In the reviewed composition, `EXPORT DATABASE BACKUP` writes a ZIP containing exactly
`manifest.json` and `database.db`. SQLite `VACUUM INTO` creates a standalone snapshot; integrity,
schema and table counts are checked before the destination is opened. The API 37 acceptance
run used SQLite 3.50.6 and compared all 22 tables, including recent notes, reviews and tasks.
Engines older than SQLite 3.27 currently fail closed rather than falling back to a raw copy of
an open database. Supporting those engines safely remains Phase 127 work.

Snapshot inspection and preview cancellation do not stage a restore. Confirmation stages the
candidate for the next start. The legacy raw SQLite import is a direct import action; it does
not acquire snapshot-preview cancellation semantics. Backup metadata excludes passwords,
cookies, OpenApp secrets, source code, custom input and private OJ sessions. Whole-database
backups are distinct from portable workspace exchange; the shared recovery vectors do not
change either backup format.

The existing restore coordinator validates SQLite format, the current schema version,
quick/integrity checks and required table names. This does not compare the complete Room column,
index and identity schema. The pending file is applied before Room opens, with the target and
WAL/SHM companions retained as a same-directory rollback set while replacement validates.
Journal recovery, atomic moves and file synchronization are implementation entry points;
they do not establish exhaustive crash recovery or power-loss durability.

The [shared recovery contract](../contracts/recovery/v1/README.md) specifies valid old/new data,
durable generation transitions and preservation of recovery artifacts when no valid active data
can be established. It forbids silently replacing unrecoverable data with an empty database.
Phase 126 executes only the documented Android routing/classification subset, not physical faults.

Static inspection identified follow-up gaps: an APPLIED journal can precede generation persistence
(RV015); failed rollback can become eligible for terminal cleanup (RV017); staging-temp creation
can fail outside the import error handler (RV004); and worker entry checks do not establish a
generation guard at every write (RV020–RV022). Scheduled backups currently have no generation
guard. Phase 127 must verify these paths with fault injection and startup/write gating. The
[Phase 126 evidence](verification/phase-126-recovery-vectors.md) records the coverage boundary.
