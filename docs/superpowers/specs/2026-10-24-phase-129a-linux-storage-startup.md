# Phase 129A — Linux guarded SQLite startup

Date: 2026-10-24. This implements the first prerequisite of approved Phase 129,
not the full recovery phase. SQLite remains the sole settings/cache authority.

## Context and choice

Current `SqliteStore::initialize()` creates metadata before identifying an existing
database, accepts an empty metadata table as first use, and runs initial DDL outside
a transaction. A zero-byte existing file can therefore become an empty workspace.
The fresh Ubuntu-24.04 baseline passed CMake/build and all eight CTest targets;
that result establishes no damaged-store behavior.

Three approaches were considered: (1) guarded startup first, then explicit read
failures and recovery in separate PRs; (2) startup plus all backup/restore and
consumer changes together; (3) add a JSON configuration authority. Choose (1): it
has independently testable preservation behavior and a bounded review. (2) delays
the first useful protection; (3) has no current consumer and would duplicate SQLite.
The user's continuing-development, balanced-platform and subagent instructions
authorize this implementation detail within the approved roadmap.

## Binding behavior

1. `SqliteStore::initialize()` retains `Result<void>` and returns
   `SyncError::Storage` on a rejected or failed startup. `schemaVersion()` is zero
   on failed startup and two only after successful initialization. Repeated
   initialization remains supported, including another live worker connection.
2. Distinguish a truly absent path from every existing object before first-use
   DDL. Reserve absent files atomically with QFile NewOnly or an equivalent
   exclusive-create primitive. A competing creator must never have its database
   initialized, truncated, or deleted by the losing initializer. Returning Storage
   during an incomplete competing initialization is acceptable in this slice.
3. Existing zero-byte, non-SQLite, truncated, metadata-missing, metadata-empty,
   duplicate-metadata, non-integer-version, unsupported-version and structurally
   incomplete databases fail closed. No repair, replacement, deletion, new schema
   objects, migration or stale-operation writes occur before validation succeeds.
   For closed rollback-journal fixtures, original database bytes remain identical
   after rejection and after a second attempted startup.
4. Validate existing files using an independently owned QSQLITE read-only connection
   before opening a write-capable connection. Require successful full consumption
   of `PRAGMA quick_check` with exactly one `ok` row. Require exactly one metadata
   row whose SQLite value type is integer and value is 1 or 2. A query failing or
   returning an error row is failure, not an empty or successful check.
5. Validate required tables, columns, declared TEXT/INTEGER affinity, required
   non-null columns (allow SQLite's primary-key notnull reporting), and primary
   key order needed by runtime conflict targets. Accept the supported v1 fixture
   and its migrated v2 schema: legacy sync_modules need not have the newer foreign
   key. Required objects are real tables, not views. Extra harmless columns/objects
   may remain; an additional required column without a default must not make our
   inserts fail silently. Do not demand physical source-column order: migration
   uses explicitly named source columns. The history performance index may be
   absent without claiming its absence is data corruption.
6. Required shape: metadata(version); accounts(judge PK, handle, enabled,
   updated_at); sync_operations(id PK, judge, handle, data_generation, status,
   started_at, finished_at nullable, error_type; v2 adds owner_pid, owner_boot_id,
   owner_start_ticks); sync_modules(PK operation_id/stage, status, attempted_count,
   imported_count, updated_count, failure_type). v1 profiles has PK judge and
   handle/payload/updated_at; other four payload tables have PK judge/external_id
   and payload/updated_at. v2 profiles has PK scope/judge/handle and
   payload/updated_at; rating_changes/submissions/problems/contests have PK
   scope/judge/handle/external_id and payload/updated_at. Column affinities and
   nullability follow the existing supported DDL, without requiring literal SQL
   formatting or identical constraint names.
7. Revalidate the version and structure on the writable connection before any
   migration/recovery write. Supported v1 migration keeps its payload mappings
   and transaction, attempts rollback on statement/commit failure, then validates
   the resulting v2 shape. Known live RUNNING operations remain RUNNING; only
   definitely stale operations use the existing recovery policy.
8. First creation wraps metadata table, all business tables, index and unique
   version row in one transaction. Statement or commit failure attempts rollback,
   returns Storage and leaves no committed partial schema. A failed newly reserved
   file may remain as a blocked artifact; never automatically recycle an existing
   zero-byte file on a later startup. No user data is deleted as cleanup.
9. SQLite read-only WAL access may create/update shared-memory sidecars. Preserve
   the main database on rejection, record actual sidecar behavior, and do not
   promise byte-identical WAL/SHM files or use immutable mode while writers may
   exist. External file replacement, interrupted restore and loss of an established
   database cannot be detected without the later recovery protocol; no such claim
   is part of 129A.

## Structure and verification

Keep `SqliteStore` as the public authority. A small internal startup-schema helper
may own validation and transactional schema creation, allowing a real SQLite
mid-DDL collision test without production fault switches. Do not introduce a
second public store or runtime testing environment variable.

Use QTemporaryDir and real QSQLITE fixtures. Pin absent success; all rejected
classes above; missing table/column/PK and wrong affinity; valid v1 migration;
healthy v2 reopening; repeated/live operation behavior; middle-of-DDL rollback;
atomic first-use competition; and a valid WAL database with another connection.
Hash closed rejected fixtures before/after both attempts. Test application entry
paths retain storage exit 6 and GUI storage-unavailable state for rejected files;
typed read/JSON-contract changes belong to 129B. Record RED before implementation,
then focused GREEN and all eight CTest targets. Build both CLI and GUI.

Only WSL Ubuntu-24.04 and existing tools are authorized for these host checks.
Use isolated XDG/runtime/data paths and preserve logs/build trees externally before
the WSL session exits. No default XDG data, OJ network, dependency installation,
visible GUI/device/DEB/RPM acceptance or release claim follows from offscreen tests.

## Global constraints

- Native C++20 / Qt 6.4+ / QSQLITE; no new dependency or storage authority.
- Storage failures retain SyncError::Storage and CLI exit code 6.
- English uppercase telemetry UI; no marketing, credentials, cookies or sessions.
- Tests use isolated temporary data and never normal user XDG directories.
- No Android, Windows or Apple runtime changes; no PR/main merge or force push.
- Preserve the pre-existing TEMP/ directory and the separate dirty linux-client worktree.

## Remaining Phase 129

129B: explicit read results and truthful CLI/GUI final storage failures. Later
slices: SQLite-consistent backup, exclusive connection drain, validated restore,
durable recovery marker, interruption convergence, permissions and cancellation
faults. Full Phase 129 remains unchecked until those gates are proven.

References: [Qt exclusive file creation](https://doc.qt.io/qt-6/qiodevicebase.html),
[Qt SQLite connection options](https://doc.qt.io/qt-6/sql-driver.html#qsqlite),
[SQLite quick_check](https://www.sqlite.org/pragma.html#pragma_quick_check).
