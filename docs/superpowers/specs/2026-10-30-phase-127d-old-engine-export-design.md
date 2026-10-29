# Phase 127D — Old-engine (SQLite < 3.27) export compatibility

Status: implementation design under the authorized Phase 121–220 roadmap. One delivery
boundary within the still-ACTIVE Phase 127; it closes the old-engine export compatibility
requirement (roadmap continuation item from Phase 122) and leaves the full phase release
gate open.

## Baseline and purpose

Branch line `codex/phase-127-recovery-safety` at the Phase127C tip (`3490cac`, PR #25).
`BackupRepository.createSnapshotFile` exports through `VACUUM INTO` on the live Room
connection and fails closed with a raw `IllegalStateException` on engines that predate
SQLite 3.27 — which is every device running API 26–28 (SQLite 3.18–3.22), i.e. a large part
of minSdk 26. The roadmap requires a determined compatibility strategy with real old-engine
verification, and forbids restoring the fallback of copying a live main file without its
WAL contents.

## Strategy

Version-gated export with a lock-protected checkpoint fallback. Revision note: the original
draft placed `BEGIN EXCLUSIVE` around the checkpoint on the same dedicated connection;
SQLite forbids a checkpoint inside a transaction on its own connection (it fails with
SQLITE_LOCKED — reproduced on the host engine), so the implemented mechanism is:

1. The exporter asks the engine for its version (`SELECT sqlite_version()`), parsed into a
   comparable triple.
2. Engine ≥ 3.27.0 → the existing `VACUUM INTO` path, byte-for-byte unchanged.
3. Engine < 3.27.0 → a dedicated framework connection, under the process-wide
   `mainFileRewriteLock` that also serialises the maintenance VACUUM (the only other
   production checkpoint):
   - `PRAGMA wal_autocheckpoint` is read and set to 0 (restored in `finally`), so Room's
     writer cannot checkpoint during the copy;
   - on WAL databases: `PRAGMA wal_checkpoint(TRUNCATE)` without a transaction, requiring
     `busy == 0` and a zero-length `-wal` afterwards (proof that every committed frame is in
     the main file);
   - a read transaction is begun and materialised (`SELECT COUNT(*) FROM sqlite_master`),
     pinning a WAL read snapshot — and its rollback-mode SHARED lock blocks committing
     writers on non-WAL (low-RAM) databases — then the main file is copied with `fd.sync()`
     and the transaction committed;
   - the copy therefore reflects the checkpointed state; a stale main file that skips WAL
     rows is impossible by construction, and a torn copy fails the validation below.
4. Both paths validate the staging file before it is zipped: schema version, `quick_check`,
   `integrity_check` and the required-table set (the same contract the restore coordinator
   applies to candidates). A failed validation deletes the staging file and fails the
   export; no broken or partial snapshot is ever published.
5. Any fallback failure (open, lock, checkpoint, copy, validation) fails the export with
   the existing fail-closed behavior. No raw engine messages cross to the user; the export
   result stays the existing Boolean surface.

## Contracts and invariants

- The VACUUM INTO path is untouched; devices with modern engines keep identical behavior.
- The fallback must never copy main without a preceding successful TRUNCATE checkpoint. If
  the checkpoint cannot be proven to have run (non-`ok` result), the export fails.
- `BEGIN EXCLUSIVE` on the dedicated connection must be released in a `finally`; a crashed
  export never leaves the process lock held (SQLite releases on connection close anyway).
- The snapshot file contract (a self-contained SQLite database at the staging path) is
  identical on both paths; the manifest, table counts and archive format are unchanged.
- No schema change, no app version bump, no new dependency, no release claim.
- The engine-version seam is injectable for tests; production default queries the engine.
  No global test flags.

## Real old-engine verification

Download `system-images;android-27;google_apis;x86_64` (SQLite 3.19 — a genuinely
pre-3.27 engine), create a dedicated AVD, and run the connected suite on it: the new
connected export test self-selects the fallback path on API 27 (old engine) and the
VACUUM path on API 37.1 (modern engine), so one test body proves both branches on their
real engines. If the image download is impossible on this host, the strategy and unit
evidence still land and the device verification is recorded as blocked with the exact
remaining command — never claimed.

## Acceptance

1. Unit (real SQLite files): with the version seam forced below 3.27, exporting a database
   whose newest row exists only in the WAL produces a snapshot containing that row (the
   stale-main prohibition holds), and the snapshot passes schema/integrity/required-table
   validation; forced-modern engines keep using VACUUM INTO.
2. The two existing snapshot repository suites keep passing on the default (modern)
   engine.
3. Connected on the dedicated API 37.1 AVD: suite green including the new export test on
   the modern path. Connected on a dedicated API 27 AVD (real old engine): the same export
   test exercises the fallback end-to-end and the suite passes; totals recorded per engine.
4. Fresh full local gates (`testDebugUnitTest --rerun test assembleDebug assembleRelease`),
   independent scoped review, scoped commits, stacked PR, evidence and handoff updates
   without claiming the whole phase.
