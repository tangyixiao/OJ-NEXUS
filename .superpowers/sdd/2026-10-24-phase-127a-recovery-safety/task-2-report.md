# Phase 127A Task 2 report

## Implementation

- `OjNexusApplication` now starts in immutable `NexusStartupState.Starting`, resolves recovery exactly once through `DatabaseRestoreCoordinator.resolveBeforeRoomOpen()`, and publishes either `Ready(container)` or `Blocked(failure, recovery)`. Container creation, global context setup, crash preference access, and all three bootstrap reconciliations only run after `Ready` is published.
- `AppContainer` requires `RestoreReadiness.Ready`, retains its resolved outcome, and returns the immutable process generation without starting another recovery pass.
- `MainActivity` gates preference collection and `NexusApp` behind `Ready`; blocked and starting states use resource-backed screens. `onResume` refreshes the local day only for a ready snapshot.
- Judge sync, Luogu result, and scheduled backup workers resolve startup before business I/O. Starting retries; Blocked fails with `result=RECOVERY_BLOCKED`; Ready uses the captured container snapshot.
- Judge, Luogu, and scheduled-backup enqueue entry points return before generation lookup or WorkManager access unless startup is Ready. Luogu WorkManager lookup is lazy.
- Added Robolectric coverage for real legacy blocked recovery, clean install, Starting, all three real Workers, all public enqueue overloads, and no missing OJ database on blocked startup. Added an Android Compose component test for exact blocked copy at 1.4 font scale.

## RED/GREEN evidence

A meaningful behavioral RED was captured by temporarily replacing the Judge worker's Blocked gate with the previous unguarded `application.container` access. Command:

```text
.\gradlew.bat :app:testDebugUnitTest --tests com.ojnexus.RecoveryWorkerGateTest
```

Result: `RecoveryWorkerGateTest > blockedWorkersReturnRecoveryBlockedBeforeBusinessIo FAILED`, `IllegalStateException` at the actual `doWork()` assertion. Restored the readiness gate and reran the same case as part of the final focused command; it passed. No missing-symbol compile failure is claimed as RED.

## Verification

Environment set process-locally: `JAVA_HOME=D:/Android Studio/jbr`, `ANDROID_HOME=D:/Android`.

Final command:

```text
.\gradlew.bat :app:testDebugUnitTest --tests com.ojnexus.RecoveryStartupTest --tests com.ojnexus.RecoveryCleanInstallStartupTest --tests com.ojnexus.RecoveryWorkerGateTest --tests com.ojnexus.RecoveryStartingWorkerGateTest --tests com.ojnexus.judge.luogu.open.LuoguOpenResultWorkerTest --tests com.ojnexus.core.data.restore.DatabaseRestoreRecoveryFaultTest --tests com.ojnexus.core.data.backup.BackupCadenceTest :app:assembleDebug
```

Result: `BUILD SUCCESSFUL`. XML totals: **41 tests, 0 failures, 0 errors, 0 skipped** across 7 suites (startup 2, clean install 1, blocked workers/enqueues 1, Starting workers 1, existing Luogu worker 7, restore recovery 23, backup cadence 6). `assembleDebug` completed successfully. `git diff --check` passed; only Windows LF-to-CRLF notices were emitted. No device/connected test was run per integration instructions; root owns dedicated AVD and connected-suite validation.

## Self-review and limits

- `StartingProbeApplication` deliberately leaves state at the production default to cover worker retry without a global bypass flag. `StartupProbeApplication` writes a real legacy `ROLLED_BACK` / `ROLLBACK_FAILURE` journal before Application startup; the real coordinator blocks while the actual OJ database file is absent. Graph factory, bootstrap, crash preference hook, and global-context hook are poison/counting seams.
- Clean-install test uses the real coordinator and `AppContainer`, opens Room, and confirms the normal database is created.
- The Compose scaling test is source-added but not run in this Windows unit-test invocation; root is running connected suite. Activity launch/resume on the dedicated AVD is also root-owned.
- Strict stale/missing worker generation policy remains Phase 127B as scoped.
- No schema, dependency, build configuration, coordinator/protocol/Task 1 files, version, push, or release changes.

## Changed files

- `app/src/main/java/com/ojnexus/OjNexusApplication.kt`
- `app/src/main/java/com/ojnexus/MainActivity.kt`
- `app/src/main/java/com/ojnexus/app/RecoveryStartup.kt`
- `app/src/main/java/com/ojnexus/app/RecoveryBlockedScreen.kt`
- `app/src/main/java/com/ojnexus/judge/sync/JudgeSyncWorker.kt`
- `app/src/main/java/com/ojnexus/judge/luogu/open/LuoguOpenResultWorker.kt`
- `app/src/main/java/com/ojnexus/core/data/backup/ScheduledBackupWorker.kt`
- `app/src/main/java/com/ojnexus/core/data/backup/BackupCadence.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/ojnexus/RecoveryStartupTest.kt`
- `app/src/test/java/com/ojnexus/RecoveryWorkerGateTest.kt`
- `app/src/androidTest/java/com/ojnexus/app/RecoveryBlockedScreenComposeTest.kt`
