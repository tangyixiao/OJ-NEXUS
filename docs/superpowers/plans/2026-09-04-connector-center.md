# Phase 71 — OJ Connector Center

## Goal

Turn the existing Settings judge panels into a clear, real synchronization control surface. The
center must summarize every registered judge, show honest local sync receipts, and let the user
queue an explicit foreground refresh for every connected judge through the existing WorkManager
and `JudgeSyncDispatcher` path.

## Guardrails

- Reuse `JudgeRegistry`, `JudgeAccountRepository`, `JudgeDataRepository`, and `JudgeSyncWorker`.
- No passwords, cookies, main-site sessions, or automatic submissions.
- No new database tables or migrations; sync state remains the existing Room entity.
- No fake remote values: disconnected, queued, syncing, partial, failed, and connected remain
  visibly distinct.
- All new copy goes through both English and Simplified Chinese resources.
- Keep feature layout inside the existing design tokens and preserve the current per-judge panels.

## Implementation tasks

### Task 1 — Add the connector-center projection and tests

Create a pure projection for registered judge connections. It should expose connected count,
supported count, each judge's sync phase, completed receipt count, total receipt count, last
successful timestamp, and whether `SYNC NOW` is available. Test empty, disconnected, queued,
partial, and fully synced rows.

### Task 2 — Add guarded sync-all behavior

Add `syncAll()` to `SettingsViewModel`. It queues only connected judges that advertise
`BACKGROUND_SYNC`, uses the existing manual worker entrypoint, and ignores duplicate calls while
the queue operation is in flight. Test that unsupported/disconnected judges are skipped and every
eligible account is marked `QUEUED` exactly once.

### Task 3 — Render the Connector Center

Add an `OJ CONNECTOR CENTER` section at the top of `SettingsScreen` with an aggregate readout,
per-judge telemetry rows, receipt coverage, and a `SYNC ALL` action. The action remains disabled
when no eligible account exists or while the action is in flight. Add a Compose test for connected
and empty states and verify the action's semantics label.

### Task 4 — Version, verify, and publish

Advance the package identity after implementation, update README/ROADMAP/release notes, run unit
tests, Debug/Release builds, lint, the full connected suite, signed Release install smoke, and
push the branch, tag, and GitHub Release only after all checks pass.

## Verification commands

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.ojnexus.feature.settings.ConnectorCenterTest
.\gradlew.bat :app:testDebugUnitTest --tests com.ojnexus.feature.settings.SettingsViewModelSyncAllTest
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.ojnexus.feature.settings.ConnectorCenterComposeTest"
.\gradlew.bat test assembleDebug assembleRelease lintDebug --no-daemon --console=plain
.\gradlew.bat :app:connectedDebugAndroidTest --no-daemon --console=plain
```
