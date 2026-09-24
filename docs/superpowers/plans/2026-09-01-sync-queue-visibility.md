# Public Sync Queue Visibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make public judge synchronization visibly queued, active, and explainably failed from the moment the user requests it.

**Architecture:** Reuse the existing Room `sync_states` row as the durable lifecycle source. `JudgeDataRepository` owns the queue-state mutation, `SettingsViewModel` performs it before scheduling WorkManager, and the Settings composable maps persisted phases and typed error markers to localized copy.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Coroutines/Flow, WorkManager, JUnit/Robolectric.

**Spec:** `docs/superpowers/specs/2026-09-01-sync-queue-visibility-design.md`

## Global Constraints

- Keep the app local-first; network only syncs.
- Do not request or store passwords, tokens, cookies, sessions, CSRF state, or keystores.
- Keep Luogu synchronization public-data-only and do not add submission POST retries.
- Route all UI strings through `res/values/strings.xml` and preserve English/Chinese resources.
- Run `./gradlew test` and `./gradlew assembleDebug`; never claim completion without a successful build.

### Task 1: Persist a queued sync request

**Files:**
- Modify: `app/src/main/java/com/ojnexus/core/data/sync/SyncModel.kt`
- Modify: `app/src/main/java/com/ojnexus/core/data/repository/JudgeDataRepository.kt`
- Test: `app/src/test/java/com/ojnexus/core/data/repository/JudgeDataRepositoryTest.kt`

**Interfaces:**
- Produces `SyncPhase.QUEUED` and `suspend fun markSyncQueued(judge: JudgeId, accountId: Long)`.

- [ ] **Step 1: Write the failing test**

Add a Robolectric test that inserts a `SyncStateEntity` with a prior successful timestamp,
calls `markSyncQueued(JudgeId.LUOGU, 7)`, and asserts `state == "QUEUED"`, `accountId == 7`,
both active timestamps are null, and `lastSuccessfulSyncAt` is unchanged.

- [ ] **Step 2: Run the focused test and verify it fails**

Run `.\tools\gradlew-local.bat testDebugUnitTest --tests com.ojnexus.core.data.repository.JudgeDataRepositoryTest --no-daemon --console=plain`.
Expected: compilation failure because `markSyncQueued` does not exist.

- [ ] **Step 3: Implement the minimal repository mutation**

Add `QUEUED` to `SyncPhase`. Add `markSyncQueued` to `JudgeDataRepository`; read the current
row or create `SyncStateEntity(judge = judge.id)`, then upsert a copy with the account ID,
`state = SyncPhase.QUEUED.name`, `startedAt = null`, `finishedAt = null`, and `currentStage = null`.
Do not alter cached data or terminal timestamps.

- [ ] **Step 4: Run the focused test and verify it passes**

Run the same focused Gradle command. Expected: PASS.

- [ ] **Step 5: Commit the data-layer change**

Run `git add app/src/main/java/com/ojnexus/core/data/sync/SyncModel.kt app/src/main/java/com/ojnexus/core/data/repository/JudgeDataRepository.kt app/src/test/java/com/ojnexus/core/data/repository/JudgeDataRepositoryTest.kt && git commit -m "feat: persist queued sync state / 持久化同步排队状态"`.

### Task 2: Queue initial and manual sync requests

**Files:**
- Modify: `app/src/main/java/com/ojnexus/feature/settings/SettingsViewModel.kt`
- Test: `app/src/test/java/com/ojnexus/feature/settings/SettingsSyncCapabilityTest.kt`

**Interfaces:**
- Consumes `JudgeDataRepository.markSyncQueued`.
- Produces `internal fun syncPhaseLabel(syncState: SyncStateEntity?): String?` with queue and active labels for UI tests.

- [ ] **Step 1: Add failing pure helper tests**

Add assertions that a `QUEUED` state maps to `"QUEUED"` and a `SYNCING` state with a stage
maps to `"SYNCING"`; terminal states map to their terminal phase name.

- [ ] **Step 2: Run the focused test and verify it fails**

Run `.\tools\gradlew-local.bat testDebugUnitTest --tests com.ojnexus.feature.settings.SettingsSyncCapabilityTest --no-daemon --console=plain`.
Expected: failure for the new queued helper behavior.

- [ ] **Step 3: Mark the row before enqueuing**

Store `JudgeDataRepository` as a private constructor property. In `connect`, after account
validation and before `enqueueManual`, call `dataRepository.markSyncQueued(judge, account.id)`.
In `syncNow`, launch a coroutine, mark the row queued, then enqueue the same manual request.
Keep duplicate-click protection in WorkManager and do not schedule a second POST or network call.

- [ ] **Step 4: Run the focused tests**

Run the Settings test and the repository test. Expected: PASS.

- [ ] **Step 5: Commit the scheduling change**

Run `git add app/src/main/java/com/ojnexus/feature/settings/SettingsViewModel.kt app/src/test/java/com/ojnexus/feature/settings/SettingsSyncCapabilityTest.kt && git commit -m "feat: expose queued sync requests / 显示同步排队请求"`.

### Task 3: Explain sync phases and failures in Settings

**Files:**
- Modify: `app/src/main/java/com/ojnexus/feature/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Test: `app/src/test/java/com/ojnexus/feature/settings/SettingsSyncCapabilityTest.kt`

**Interfaces:**
- Produces `internal fun syncErrorLabelKey(errorType: String?): String`.

- [ ] **Step 1: Write failing error mapping tests**

Assert `RateLimited` maps to `sync_error_rate_limited`, `Network`/`Timeout` maps to
`sync_error_network`, `UserNotFound` maps to `sync_error_user_not_found`, and unknown/null
maps to `sync_error_api`.

- [ ] **Step 2: Run the focused test and verify it fails**

Run the Settings focused test. Expected: missing helper or wrong mapping failure.

- [ ] **Step 3: Implement localized UI mapping**

Add bilingual resources for `QUEUED`, generic sync error, and an inline `SYNC ERROR` label.
Render queued status in the connection panel. For `PARTIAL`/`ERROR` with an error type,
render the mapped resource string; never render `lastErrorMessage` directly. Preserve the
existing auth-required warning and OpenApp copy.

- [ ] **Step 4: Run tests and compile**

Run `.\tools\gradlew-local.bat testDebugUnitTest assembleDebug --no-daemon --console=plain`.
Expected: PASS and `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the Settings change**

Run `git add app/src/main/java/com/ojnexus/feature/settings/SettingsScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml app/src/test/java/com/ojnexus/feature/settings/SettingsSyncCapabilityTest.kt && git commit -m "feat: explain sync outcomes in settings / 设置页解释同步结果"`.

### Task 4: Documentation, full verification, and release

**Files:**
- Modify: `README.md`
- Modify: `docs/ROADMAP.md`
- Create: `docs/releases/v0.3.30.md`
- Modify: `docs/superpowers/specs/2026-09-01-sync-queue-visibility-design.md`
- Modify: `docs/superpowers/plans/2026-09-01-sync-queue-visibility.md`

- [ ] **Step 1: Update bilingual status and release notes**

Document Phase 34 in English and Chinese, preserve all earlier phase entries, state the
public-only boundary, and include verification commands plus the final APK digest.

- [ ] **Step 2: Run repository verification**

Run `git diff --check` and `.\tools\gradlew-local.bat clean test assembleDebug lintDebug --no-daemon --rerun-tasks --console=plain`.
Expected: `BUILD SUCCESSFUL` with no new errors.

- [ ] **Step 3: Install and inspect the existing emulator**

Run `D:\Android\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk`,
launch the app, and inspect Settings with UIAutomator. Verify the app process and
`emulator-5554` remain online; do not shut down the emulator or computer.

- [ ] **Step 4: Publish the GitHub Release**

Create tag `v0.3.30` on the verified commit and publish the APK with `gh release create`.
Verify the release is public, non-draft, non-prerelease, and the asset SHA256 equals the
local file.

- [ ] **Step 5: Final integrity check**

Verify `git status --short --branch`, local HEAD, remote branch, tag target, and emulator
status. The final state must be clean and pushed.
