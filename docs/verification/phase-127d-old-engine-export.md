# Phase 127D — Old-engine (SQLite < 3.27) export compatibility

Status: VERIFIED for the Phase127D boundary: a real pre-3.27 engine (API 27 emulator,
SQLite 3.19.4) demonstrated the export defect RED and the fallback GREEN end-to-end, both
dedicated AVDs run the full connected suite green at the final head, and the full unit gate
passes. The full Phase127 remains ACTIVE only for its release gate; no release, no version
change.

## Source and scope

- [Design](../superpowers/specs/2026-10-30-phase-127d-old-engine-export-design.md) (strategy
  revised during execution: SQLite forbids a checkpoint inside a transaction on its own
  connection, so the original BEGIN-EXCLUSIVE-then-checkpoint draft was replaced — see the
  spec's revision note).
- [Plan](../superpowers/plans/2026-10-30-phase-127d-old-engine-export.md)
- Base: the Phase127C tip `3490cac` on the `codex/phase-127-recovery-safety` line.
- Commits: `803b6cc` (spec), `b97c4b7` (plan), `9088ee1` (connected test),
  `de8914e` (implementation), `b226160` (review fixes).

## Real old-engine evidence (the core requirement)

The API 27 system image (`system-images;android-27;default;x86_64`) was downloaded onto
this host and a dedicated AVD `OJNexus_Phase127_OldEngine` created (pixel_7 profile, serial
`emulator-5584`). Its engine is genuinely pre-3.27:

| Engine | AVD | Export path exercised |
| --- | --- | --- |
| SQLite 3.19.4 (real old engine) | `OJNexus_Phase127_OldEngine`, API 27 | checkpoint fallback |
| SQLite 3.50.6 | `OJNexus_Phase127_Temp`, API 37.1 | VACUUM INTO (unchanged) |

- **RED on the real old engine, before the fix**: the connected export test failed with
  exactly `export failed on engine 3.19.4` — the prior fail-closed `VACUUM INTO` code
  cannot export on API 26–28 devices. (Also observed earlier: the D8 dexer rejects spaces
  in androidTest method names, so the connected test uses a camelCase name.)
- **GREEN on the real old engine, after the fix**: the same test passes on API 27 — the
  checkpoint fallback captures WAL-resident rows (the test disables autocheckpoint, asserts
  the `-wal` file is non-empty with the newest row inside, exports, and reads both rows back
  from the archive) — and on API 37.1 the unchanged VACUUM path passes the same body.

## Implementation summary

- Version gate on `SELECT sqlite_version()` parsed by `SqliteVersion` (always three parts,
  component-wise compare; garbage degrades to the fallback — the safe direction).
- Fallback under the process-wide `mainFileRewriteLock`, which also serialises the
  maintenance VACUUM: `wal_autocheckpoint` read, set to 0, restored in `finally`; on WAL
  databases `wal_checkpoint(TRUNCATE)` without a transaction requires `busy == 0` and a
  zero-length `-wal`; a materialised read transaction then guards the main-file copy
  (WAL read snapshot; rollback-mode SHARED lock blocks committing writers); copy + `fd.sync`.
- Both paths validate the staging file (schema version, `quick_check`, `integrity_check`,
  required tables) before it is zipped; failure deletes the staging file and fails the
  export fail-closed.
- The independent review found a P1 (the maintenance VACUUM's TRUNCATE checkpoint could
  rewrite the main file mid-copy) plus P2s (non-WAL databases degraded to an unlocked copy;
  spec/code mismatch on the abandoned BEGIN-EXCLUSIVE design; hardcoded autocheckpoint
  restore value; a missing WAL-residency precondition in the connected test; exception-unsafe
  close). All are addressed by `b226160` (shared mutex, journal-mode-aware checks, read-
  transaction guard, read-restore of `wal_autocheckpoint`, spec revision note, connected-test
  precondition and cleanup hardening). The double `integrity_check` per export (validation +
  `countTables`) was accepted as a P2 performance note, not a correctness issue.

## Full local gate (final head `b226160`)

| Check | Actual result |
| --- | --- |
| `tools\gradlew-local.bat testDebugUnitTest --rerun test assembleDebug assembleRelease` | Exit0, BUILD SUCCESSFUL in 2m07s |
| Debug unit XML | 848 tests / 185 suites; zero failures/errors/skips |
| Warnings | Only the two pre-existing JVM warnings (Conscrypt native access, DataStore/protobuf Unsafe) |

## Dedicated emulator connected gates (final head `b226160`)

| Engine | Suite result |
| --- | --- |
| API 37.1, SQLite 3.50.6 (`emulator-5582`) | 12 suites / 36 tests, zero failures/errors/skips, BUILD SUCCESSFUL 1m30s |
| API 27, SQLite 3.19.4 (`emulator-5584`) | 12 suites / 36 tests, zero failures/errors/skips, BUILD SUCCESSFUL 38s |

Both runs include the 35 existing cases plus the new engine-export test on their own engine
path. Two earlier full-suite attempts on this host aborted with emulator process deaths
(recorded as failed infrastructure, same Gradle-daemon memory-pressure pattern as 127B/127C;
resolved by stopping idle daemons and re-running clean). Emulators were shut down after each
gate; no physical device or user data was touched.

## Honest limits

- The WAL-capture proof on the device is the connected test's precondition (non-empty WAL)
  plus row capture; checkpoint mechanics under Robolectric run on the host's modern engine.
- Low-RAM devices running Room's rollback-journal AUTOMATIC fallback on pre-3.27 engines are
  covered by the read-transaction guard, but no such device was available for direct
  verification.
- The maintenance VACUUM and the fallback export are mutually excluded only within one
  process — the app owns its database exclusively, so cross-process writers are out of scope.
- Phase127 remains ACTIVE for its release gate; no application release is included.
