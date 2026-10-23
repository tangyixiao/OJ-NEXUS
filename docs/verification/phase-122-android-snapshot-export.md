# Phase 122-b — Android snapshot export wiring

## Result

The Settings export action now calls `BackupRepository.exportSnapshot`, matching the ZIP
document picker and filename. The change reuses the existing snapshot archive format; it does
not change the manifest or database schema.

The regression test calls `SettingsViewModel.exportBackup` with a real file-backed Room database
and a file URI. It checks the exported ZIP has exactly `manifest.json` and `database.db`, that the
manifest reports current schema 15 and one seeded problem, and that credential-like markers do
not appear in either entry. It then passes the same output through `inspectImport`, verifies a
preview is pending, cancels it, and confirms there are no restore staging files and the active
Room database still has its seeded row. Existing tests also cover legacy raw `.db` import.

## TDD and build evidence

- RED: with `exportBackup` calling `exportTo`, `SettingsSnapshotVmTest` failed at
  `Settings export must be a ZIP snapshot`. The generated file was raw SQLite, so its first two
  bytes were not the ZIP signature. Log: `%TEMP%\phase122b-red.log`.
- GREEN: after the one-line call-site change to `exportSnapshot`, the same test passed. Log:
  `%TEMP%\phase122b-green.log`.
- Full gates: `gradlew.bat test assembleDebug assembleRelease` — **BUILD SUCCESSFUL**. Log:
  `%TEMP%\phase122b-full-gates.log`.
- Fresh unit-test XML: 171 suites, 780 tests, 0 failures, 0 errors, 0 skipped. Retained at
  `%TEMP%\phase122b-evidence-20261023\testDebugUnitTest`.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Unsigned release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`.

## Limits

No emulator or physical device was operated for this fix because emulator 5580 belongs to the
validator lane. The Settings file-picker interaction and restore-cancel path still need that
device-level recheck. The regression exercises the ViewModel and repositories with Robolectric;
it does not establish visual picker behavior on Android hardware.
