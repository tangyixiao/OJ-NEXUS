# Phase 122-b: WAL-consistent snapshot export verification

## Change

`BackupRepository` creates the standalone archive database directly with `VACUUM INTO`, SQLite's
transaction-consistent snapshot operation. The old fallback that copied only the main database
file is removed, as is the manifest-count fallback to live Room data. An unavailable command,
invalid snapshot, schema mismatch, or failed integrity check aborts export. ZIP manifest counts are
read from the exact standalone database that is exported. Temporary snapshot files are removed in
`finally` or on snapshot creation failure.

## Regression and evidence

- The file-backed Room regression disabled `wal_autocheckpoint`, committed two problem rows, and
  confirmed the `-wal` sidecar was non-empty before export. It verified the ZIP has exactly
  `manifest.json` and `database.db`, extracted and opened the database without sidecars, read both
  rows including the newest WAL-backed row, verified the manifest count, and checked preview count.
- Before the production fix, this regression was RED: the exported database/manifest contained only
  the main-file row (manifest count 1 instead of 2).
- An unwritable-destination failure regression confirms export returns false, preserves a
  pre-existing sentinel byte-for-byte, leaves the live Room row available, and leaks no
  `ojn-*.db` snapshot temporary file.
- After the fix, focused repository tests passed:

  ```powershell
  .\gradlew.bat :app:testDebugUnitTest --tests com.ojnexus.core.data.repository.BackupSnapshotWalTest --tests com.ojnexus.core.data.repository.BackupSnapshotRepositoryTest --no-daemon
  ```

  Captured log: `%TEMP%\phase122b-wal-focused-final.log`.
- Final full gates passed with `BUILD SUCCESSFUL`:

  ```powershell
  .\gradlew.bat test assembleDebug assembleRelease --no-daemon
  ```

  The run executed 782 tests across 172 XML suites with 0 failures, errors, or skipped tests.
  Log: `%TEMP%\phase122b-wal-full-final.log`; XML: `app/build/test-results/testDebugUnitTest/`.
  Artifacts: `app/build/outputs/apk/debug/app-debug.apk` and
  `app/build/outputs/apk/release/app-release-unsigned.apk`.

The fixture uses synthetic problem rows and does not touch a user database. No ADB or emulator was
used; device/runtime validation remains for the validator-owned emulator. No checkpoint/busy-reader
branch remains: `VACUUM INTO` itself creates a transaction-consistent snapshot, so checkpointing was
removed as an unnecessary prerequisite.

`VACUUM INTO` is available starting with SQLite 3.27.0. Older bundled SQLite builds fail closed;
this repository does not claim a successful old-SQLite fallback. The exact Android device SQLite
version support boundary still needs device validation. See the [SQLite 3.27.0 release notes](https://sqlite.org/releaselog/3_27_0.html).
