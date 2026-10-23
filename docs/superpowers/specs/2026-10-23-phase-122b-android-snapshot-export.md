# Phase 122-b — Android snapshot export wiring

## Scope

Fix the Settings export action so the ZIP archive promised by `CreateDocument("application/zip")`
contains the repository's existing snapshot format. Keep legacy raw SQLite import supported and
use the existing snapshot inspection/cancel path. Do not change the archive schema, UI strings,
app version, unrelated note work, or device configuration.

## Acceptance

- A regression test drives `SettingsViewModel.exportBackup` with a real Room database and a real
  file URI, then checks a ZIP with exactly `manifest.json` and `database.db`, the current Room
  schema, seeded rows, and no credential-like material.
- The same file goes through `inspectImport`; it creates a conflict preview and cancelling that
  preview leaves no restore staging files and does not alter the live Room data.
- Existing legacy raw `.db` import remains supported.
- Run the requested red/green test cycle, then `test`, `assembleDebug`, and `assembleRelease`.
  Retain fresh test XML in `%TEMP%` and write verification evidence with limits.

## Plan

1. Add a ViewModel-level Robolectric regression using the existing real Room/repository fixture
   pattern; run it before production edits and confirm it fails because the exported file is raw
   SQLite.
2. Change only the export repository call from `exportTo` to `exportSnapshot`.
3. Rerun the regression and full unit/build gates, retain fresh XML/log evidence, and document
   that physical-device confirmation remains for the validator-owned emulator/device lane.
