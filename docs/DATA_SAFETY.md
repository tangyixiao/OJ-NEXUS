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

The restore coordinator validates SQLite format, the current schema version,
quick/integrity checks and required table names. This does not compare the complete Room column,
index and identity schema. The pending file is applied before Room opens, with the target and
WAL/SHM companions retained as a same-directory rollback set while replacement validates.
Journal recovery, atomic moves and file synchronization are implementation entry points;
they do not establish exhaustive crash recovery or power-loss durability.

The [shared recovery contract](../contracts/recovery/v1/README.md) specifies valid old/new data,
durable generation transitions and preservation of recovery artifacts when no valid active data
can be established. It forbids silently replacing unrecoverable data with an empty database.
Phase 126 executes only the documented Android routing/classification subset, not physical faults.

The unmerged Phase127A branch adds source-preserving copies, explicit interruption stages and
blocked readiness. APPLIED reconciles the exact journal generation before cleanup; failed rollback
preserves sources and refuses automatic retries. Application constructs its graph only after Ready;
Activity, all three Workers and public scheduling entry points consume that readiness. Starting work
retries; blocked work fails before business I/O. The [Phase127A evidence](verification/phase-127a-recovery-safety.md)
records real SQLite/WAL fault tests, 829 passing unit tests, 35 connected tests and actual blocked
Activity launches with unchanged evidence hashes. These changes are not in published `v1.0.3`.

The same branch now also enforces strict stale/missing-generation work admission (RV020–RV022,
[Phase127B evidence](verification/phase-127b-worker-generation.md)): all three Workers proceed only
on an exactly matching captured generation; sync refusals are recorded in the operations ledger
(unstamped refusals with an empty generation), scheduled backups carry the resolved generation stamp
and refuse before any preference read, and the periodic queues are replaced with freshly stamped
entries on every ready start. The Phase126 classifier and its projections are unchanged; these
changes are also not in a published release.

Staging-temp creation can still fail outside the
import error handler (RV004); typed storage-full import handling and functional minSdk26 backup
compatibility also remain open. Startup readiness is not a claim that all old work is write-isolated.
The [Phase126 evidence](verification/phase-126-recovery-vectors.md) retains the narrower pure-decision
boundary. Full Room schema validation, physical-device behavior and directory-entry power-loss
durability are not established by the Phase127A slice.
