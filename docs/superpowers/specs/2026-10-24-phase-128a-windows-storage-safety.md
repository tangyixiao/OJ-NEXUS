# Phase 128A — Windows storage startup and lifetime safety

Date: 2026-10-24. Status: approved development scope; acceptance pending.

## Problem and scope

The native Windows CLI and WPF currently create/migrate SQLite in a factory constructor. At integration baseline `20e3fa9`, an actual CLI `status --json` against an already-existing zero-byte `ojnexus.db` returned exit 0 / `ready` and expanded the file to 126976 bytes. An unreadable or missing established database must not become an apparently empty new workspace.

128A delivers one shared Core startup authority, explicit lifetime ownership, storage failures in CLI/WPF, and a real blocked WPF shell with retry and exit. It is a prerequisite slice of phase 128, not completion of Windows backup/restore. Phase 128 remains open for coherent backups, candidate validation, journaled installation/recovery, generation fencing, staged legacy migration and fault acceptance (128B–D). No placeholder backup/restore buttons or claims are included in A.

No Android, Linux, Apple runtime change; no framework shell, schema version bump, dependency upgrade, private OJ authentication, or main/PR merge. Feature push and scoped PR are authorized. Release publication requires a separately verified useful artifact and accurate limitations; this slice alone does not justify a full backup/restore release.

## Startup authority

Only `WindowsStorage.OpenAsync` authorizes a production `WindowsStorageSession`. Factory construction has no startup side effects in the final branch. CLI and WPF must call this authority before constructing their normal store/sync/ViewModel graph. Normal connections use ReadWrite (never create), ForeignKeys=true, Pooling=false, DefaultTimeout=2 seconds. Read-only probes and maintenance also explicitly disable pooling.

`OpenAsync(string? rootOverride, IClock? clock, CancellationToken)` returns `StorageOpenResult` containing exactly a ready session or a typed failure. Cancellation is an OperationCanceledException matching the supplied token, not a corruption result. A local lock contention attempt returns immediately; retry is an explicit caller action. Do not add sleeps, background retry loops or a provider-wide ClearAllPools workaround.

Potentially blocking filesystem/SQLite startup work runs off the WPF dispatcher. Check cancellation between safe stages and await actual maintenance completion/handle closure before returning cancellation; cancelling a Task wrapper must not abandon an executing migration or release ownership behind it.

Derived paths, immutable after one `Path.GetFullPath` resolution:

| Purpose | Name |
| --- | --- |
| Active database | `ojnexus.db` |
| Permanent lifecycle lock | `ojnexus.lifecycle.lock` |
| Initialized marker | `ojnexus.initialized` (exact UTF-8, no BOM, `v1\n`) |
| Marker staging file | `ojnexus.initialized.tmp` |
| First creation stage | `ojnexus.init.db` and its SQLite companions |
| Reserved restore evidence | Any entry beginning `ojnexus.restore.` |

The supported location is an ordinary directory on a local fixed/removable drive. Reject UNC, device/extended namespace, SQLite URI, mapped network roots, existing reparse points in ancestors or controlled artifact paths, and DB/lock files with more than one hard link. Use Windows handle information for actual link count. Reject control characters and ambiguous trailing-dot/space path components. Validate before acquisition and again under ownership. Relative/case aliases of an ordinary directory resolve to the same physical lock rather than a path-hashed mutex. A directory occupying a controlled file path is a typed failure. This is a supported-location policy, not a claim of protection from hostile same-privilege namespace retargeting. Old binaries and external SQLite tools do not participate in this application protocol.

## Shared runtime and exclusive maintenance

The permanent lock is a privately held FileStream, OpenOrCreate, ReadWrite access. Shared runtime uses FileShare.ReadWrite; exclusive initialization/migration uses FileShare.None. Neither permits delete sharing. Never truncate, delete, rename or write a PID into this file. Its existence/age does not indicate ownership; Windows sharing violations do. Native sharing/lock errors 32/33 map to STORAGE_BUSY, not arbitrary IOExceptions.

1. Acquire shared ownership; inventory controlled evidence and inspect the existing database read-only. A healthy current database with a valid initialized marker can become Ready while keeping this same shared lease.
2. If fresh creation, recognized legacy migration or marker enrollment is necessary, close probe resources, release shared, acquire exclusive, then repeat the complete inventory/probe. Pre-lock observations never authorize writes.
3. Perform only authorized maintenance with the exclusive lease. Close/flush all maintenance resources. Release exclusive, acquire a new shared lease and recheck inventory/schema/marker before returning Ready. The transition is not atomic; another owner may win and return Busy. Never construct runtime connections in this gap.
4. Ordinary WPF and CLI sessions can coexist under shared leases. Exclusive maintenance fails while either session remains alive.

## Fresh, established and damaged states

True first use requires the active database to be absent and all initialized-marker/temp, initialization-stage, SQLite sidecar and reserved restore evidence to be absent. Unrelated files do not imply a damaged store. Check this under exclusive ownership immediately before creating anything.

For first creation, durably write the marker through a CreateNew temporary file, Flush(true), then File.Move without overwrite; create only `ojnexus.init.db` using explicit ReadWriteCreate; initialize schema 7 in the existing transaction; validate the closed stage and Flush(true); promote with File.Move without overwrite. No active file is overwritten. An interruption can leave marker/stage evidence; A blocks on this evidence instead of inventing an empty replacement. Automatic recovery of interrupted initialization is deferred to the phase 128 recovery protocol. This limitation must be visible in verification/release notes.

Existing zero-byte, short-header, unrelated SQLite, invalid metadata, corrupt or unsupported future-schema files are preserved and blocked. Existing files are never implicitly initialized. A missing active database with an initialized marker, main WAL/SHM/journal or controlled lifecycle evidence is a recovery failure, never first use. A valid current legacy workspace without the new marker can enroll a marker only under exclusive ownership after validation. A malformed marker or any marker temp/init-stage/reserved restore entry blocks startup; do not delete unknown evidence.

Exact classification: forbidden marker-temp/init/restore evidence takes precedence as RecoveryBlocked, even when a marker or active DB is present. In the absence of that incomplete evidence, absent active DB with an initialized marker or main SQLite companions is RecoverySourceMissing. This distinguishes interrupted initialization from an otherwise established workspace missing its source. Recognized older schemas always migrate under exclusive ownership regardless of marker presence; both Ready returns require schema 7. Validation failures from the read-only schema boundary (including missing/invalid metadata or malformed recognized layout) are IntegrityFailure; a metadata version above 7 is UnsupportedSchema. Normal main WAL/SHM with an existing DB is probed by SQLite and does not by itself block marker enrollment. Inventory controlled names case-insensitively; if a case-fold match exists but the nominal path is distinct on a case-sensitive directory, fail UnsupportedLocation rather than creating a shadow artifact.

Probe with Mode=ReadOnly, Pooling=false and an explicitly deferred read transaction. Check SQLite header, integrity_check and foreign_key_check, then reuse the existing schema-specific validators. A recognized metadata version 0–6 may migrate using the current single SQLite transaction under exclusive ownership, after validating its existing version; schema stays 7. Version 0 uses the existing version-one base layout (metadata, accounts, sync_operations and sync_modules), including its real legacy rows, as demonstrated by `SqliteSyncStoreTests.VersionZeroSchemaSql`; validate that layout before migration. Do not accept an arbitrary or metadata-only database simply because its metadata says 0. Migration failures keep the original transaction rollback semantics and block startup. A hot rollback journal/read-only recovery requirement is RecoveryBlocked or Busy as appropriate, not proof of corruption. Provider-managed normal WAL companions must not be manually removed.

Current healthy startup does not run the write migrator. No writes to business tables are needed to inspect a current store. Validate and classify before enrolling a marker on a damaged store.

## Explicit session and operation lifetimes

Public `IStorageOperationSource : IAsyncDisposable` exposes:

```csharp
Task RunAsync(Func<ISyncStore, CancellationToken, Task> work, CancellationToken cancellationToken);
Task<T> RunAsync<T>(Func<ISyncStore, CancellationToken, Task<T>> work, CancellationToken cancellationToken);
```

`StorageOperationScope(ISyncStore store)` supports ordinary in-memory dependency injection and shares the same admission/drain contract. It is not a production bypass flag. The guarded session uses an internal scoped-store factory instead.

`WindowsStorageSession` implements that interface and exposes immutable DataDirectory/DatabasePath plus `BeginOperation(CancellationToken)` for explicitly scoped composition/tests. Its public disposable operation context owns a Store and linked CancellationToken. Each context has an explicit, owner-bound permit; do not use AsyncLocal, global current-session lookup or an ambient store. Permit closure is permanent.

Admission and drain share a synchronization gate: Ready -> Draining -> Closed, with no reopening. Admit/count an operation only while Ready. DisposeAsync permanently closes admission, cancels linked work and awaits all admitted operations **and native connection leases**, then releases the shared directory handle last. DisposeAsync is idempotent. Cancellation callbacks run outside the gate. There is no timeout that unlocks behind running work. Cancellation requests, VM cancellation dictionaries, HasActiveSyncs, raw SQLite StateChange events or pool clearing do not prove drain.

Already-admitted operations may finish cleanup writes with CancellationToken.None while Draining. New work through any retained session/context/store reference after its permit closes fails with SESSION_CLOSED. Store entry checks apply even to methods with an early no-SQL return. Register a native connection reservation atomically with permit validation before opening. An internal explicit connection lease owns the raw connection; close it before releasing the reservation. Failed native opens release reservations. Closing an operation with an unfinished SQL task does not release the session lock while that connection remains live. If a native close fails, retain blocked ownership rather than report a drained session.

The final factory and SQL-store constructors are internal and require the session/permit. No public raw SqliteConnection-returning API remains. Commands/readers/transactions do not escape store methods. A closed old session never becomes valid because a new session opens the same path.

SyncService takes adapters/clock/generation at construction and an explicit ISyncStore argument per RunAsync. Its per-account gate and all OpenOperation/AppendModule/CloseOperation writes are inside the caller's admitted operation. Preserve non-cancellable ledger cleanup while holding ownership. Desktop public async actions admit once and call private helpers with the explicit operation store; nested Save/SyncAll/Refresh and the finally Refresh(None) must not try to enter a second operation. App close awaits the full action and its final refresh. In-memory unit tests use StorageOperationScope.

## Stable failures and native projection

Core owns `StorageFailureCode`, `StorageFailure` and `StorageException` with sanitized stage/code; no raw SQL, exception message, private path or credential enters new error output. Stable wire codes:

| Core case | Wire code |
| --- | --- |
| Busy | STORAGE_BUSY |
| IntegrityFailure | INTEGRITY_FAILURE |
| UnsupportedSchema | UNSUPPORTED_SCHEMA |
| RecoveryBlocked | RECOVERY_BLOCKED |
| RecoverySourceMissing | RECOVERY_SOURCE_MISSING |
| PermissionDenied | PERMISSION_DENIED |
| UnsupportedLocation | UNSUPPORTED_LOCATION |
| MigrationFailed | MIGRATION_FAILED |
| IoFailure | STORAGE_IO_FAILURE |
| StorageFull | STORAGE_FULL |
| SessionClosed | SESSION_CLOSED |

Map SQLite BUSY/LOCKED separately from malformed/integrity failures; SQLite FULL and Windows disk-full errors (112/39) map to StorageFull. Do not label every exception Busy or Corrupt. Do not relabel SQLite persistence failures as Network: add SyncError.Storage (additive), catch the specific storage/provider failures and let final persistence failures reach the typed top boundary. Existing network classification and ledger transactions remain.

If AppendModule fails, retain its typed storage failure, attempt the final non-cancellable CloseOperation with SyncError.Storage while the action remains admitted, then throw the retained typed failure to the caller even if CloseOperation succeeds. If both append and close fail, the typed close failure wins because completion of the ledger is itself unresolved. Do not return a normal error SyncReport that loses the wire code or maps to the generic partial exit. A failure of OpenOperation/CloseOperation also reaches the typed storage boundary. In WPF runtime actions the existing ERROR/LastError projection must display the wire code after cleanup/final refresh; the startup blocked shell is required only for startup authorization failures. No automatic runtime reopen is introduced.

CLI parses first, so usage errors still work without opening storage. For all normal storage commands, await startup, execute in a counted operation and await disposal. On a storage startup/runtime failure, exit reserved code 6. Existing exit numbers stay unchanged. With --json, stdout has one object `{"status":"blocked","error":"WIRE_CODE"}` and no normal successful payload; stderr does not leak internals. Human mode emits a short uppercase storage message/code. Existing successful JSON shapes remain compatible.

WPF constructor/InitializeComponent creates a storage-free shell. On Loaded it enters OPENING asynchronously, then READY only after authority returns a session. BLOCKED shows `LOCAL STORAGE UNAVAILABLE`, `LOCAL FILES PRESERVED`, the same wire code, and working `RETRY OPEN` / `EXIT` controls using existing Nexus resources. Normal page content/actions stay unavailable before Ready. No restore placeholder is exposed. Retry is serialized and uses a fresh startup attempt. Closing cancels any pending startup, awaits it and any late returned session, then awaits full VM/session drain before actual close. Never .Wait/.Result the dispatcher; Closed is too late to begin drain. Event handlers cannot dereference a null VM or allow unhandled async-void failures during startup/close.

## Acceptance and evidence

Use isolated GUID temporary roots; never the user's default workspace. Retain existing 167 Windows tests and require new behavioral tests. Record real RED/GREEN chronology without claiming a compile error or fixture simulation was a real native failure.

Core tests: existing zero/truncated/unrelated/future/corrupt preservation; missing established DB with every evidence type; marker content/temp/stage; true first-run and existing-current enrollment; legacy row-preserving migration/rollback; two normal sessions and exclusive contention; old session/store/context rejection; operation-plus-connection drain including delayed non-cancellable cleanup; no public raw connection bypass; supported aliases and rejected reparse/hard links/UNC; typed errors. Test-only fault seams may be internal and tightly named; no public unsafe production mode.

Use a small test-only native console helper under windows/tests for actual separate-process barriers. Prove shared coexistence, exclusive rejection both directions, no lock rename/delete while held, first-run race outcomes, process-kill handle release and stale lock-file reuse. Process-kill evidence is not power-loss durability. Use stdin/stdout barriers and bounded timeouts, not sleeps as proof. Native permission and real disk-full evidence must be labelled separately from exception-classifier tests; no claim of fault coverage without the actual scenario.

Integration: real Program tests first reproduce the zero-file failure before wiring; actual executable after fix returns exit 6 and unchanged bytes. WPF tests cover opening/blocked/retry/close and late-session disposal; actual WPF/UIAutomation shows blocked corrupt/Busy code, Retry resolution after another holder exits, and Exit. Run existing four-page smoke on a healthy isolated store. Screenshot evidence includes displayed codes and original-byte hashes. No OJ network calls are needed.

Required final gates: dotnet restore, all Windows tests, Release build --warnaserror, existing native CLI smoke, actual native lifecycle/blocked-shell acceptance, and Android `test assembleDebug` BUILD SUCCESSFUL. Include exact test counts, commits, screenshots, limitations and open full-128 work in docs/verification. Independent task reviews and whole-branch review precede the scoped PR. Do not claim DONE until this slice's gates pass; phase 128 stays ACTIVE after 128A.
