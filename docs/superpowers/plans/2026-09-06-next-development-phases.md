# OJ NEXUS Phase 73–76 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver four sequential releases that secure backup restoration, preserve action continuity across day changes, improve explainable training selection, and add bounded sync-operation history.

**Architecture:** Keep the current single-module, local-first architecture. Add small pure policy types beside the existing repositories and ViewModels, persist only data that must survive process death, and route every remote action through the current judge adapter/dispatcher boundary.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Room, DataStore, WorkManager, Coroutines/Flow, Retrofit/OkHttp, kotlinx.serialization, JUnit, Robolectric, AndroidX Compose UI tests.

**Spec:** `docs/superpowers/specs/2026-09-06-next-development-phases-design.md`

## Global Constraints

- Start from Phase 72 / `versionName=0.3.70`, `versionCode=70`; verify the current branch and worktree before each phase.
- Keep the standalone `org.jetbrains.kotlin.android` plugin unapplied.
- UI remains English/Chinese localized, uppercase and telemetry-style, with design-system tokens only.
- Preserve all local data, Room migration paths, adapter isolation, and offline readability.
- Never persist or log OJ passwords, cookies, tokens, OpenApp secrets, source code, or custom input in sync/backup metadata.
- No automatic OJ submission, fabricated API output, WebView shell, social feature, or AI recommendation surface.
- Each phase is one independently reviewable release and ends with the repository Definition of Done.

---

### Task 1: Phase 73 — Define restore protocol and failure model

**Files:**
- Create: `app/src/main/java/com/ojnexus/core/data/restore/RestoreProtocol.kt`
- Create: `app/src/test/java/com/ojnexus/core/data/restore/RestoreProtocolTest.kt`
- Modify: `app/src/main/java/com/ojnexus/core/data/repository/BackupRepository.kt`

**Interfaces:**
- Produces: `enum class RestoreStage { STAGED, SWAPPING, APPLIED, ROLLED_BACK, REJECTED }`
- Produces: `sealed interface RestoreOutcome`
- Produces: `data class RestoreJournal(val stage: RestoreStage, val generation: String, val detail: RestoreFailure?)`
- Consumes: `OjNexusDatabase.CURRENT_SCHEMA_VERSION` and `OjNexusDatabase.DATABASE_NAME`.

- [ ] **Step 1: Add pure protocol tests**

Cover legal stage transitions, startup recovery decisions for every combination of target,
candidate, rollback, and journal state, and stable failure categories such as invalid SQLite,
schema mismatch, integrity failure, file replacement failure, and rollback failure.

- [ ] **Step 2: Run the focused tests and confirm the new types are absent**

```powershell
.\tools\gradlew-local.bat testDebugUnitTest --tests "com.ojnexus.core.data.restore.RestoreProtocolTest"
```

Expected: compilation or assertion failure because the restore protocol is not implemented.

- [ ] **Step 3: Implement the pure restore state machine**

Keep file I/O out of the policy. Its central decision boundary must be equivalent to:

```kotlin
fun decideRestoreRecovery(
    journal: RestoreJournal?,
    files: RestoreFileState,
): RestoreRecoveryAction
```

Return explicit actions for apply candidate, validate target, restore rollback, reject staging,
or clear completed artifacts.

- [ ] **Step 4: Run the focused tests**

Run the command from Step 2. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the protocol**

```bash
git add app/src/main/java/com/ojnexus/core/data/restore/RestoreProtocol.kt app/src/test/java/com/ojnexus/core/data/restore/RestoreProtocolTest.kt
git commit -m "feat: define recoverable database restore protocol"
```

### Task 2: Phase 73 — Implement atomic restore and full validation

**Files:**
- Create: `app/src/main/java/com/ojnexus/core/data/restore/DatabaseRestoreCoordinator.kt`
- Create: `app/src/test/java/com/ojnexus/core/data/restore/DatabaseRestoreCoordinatorTest.kt`
- Modify: `app/src/main/java/com/ojnexus/core/data/repository/BackupRepository.kt`
- Modify: `app/src/test/java/com/ojnexus/core/data/repository/BackupRepositoryTest.kt`
- Modify: `app/src/main/java/com/ojnexus/OjNexusApplication.kt`

**Interfaces:**
- Consumes: restore policy from Task 1.
- Produces: `fun prepareImport(source: Uri): RestoreOutcome`
- Produces: `fun restoreBeforeRoomOpen(): RestoreOutcome`
- Produces: `fun currentDataGeneration(): String`

- [ ] **Step 1: Add failing filesystem and WAL tests**

Use isolated explicit test directories. Cover production WAL mode, SQLite `quick_check`, required
schema objects from the Room schema, failure before target rename, failure after rename, rollback,
and a simulated process stop in `SWAPPING`. Assert row identity, not only row counts.

- [ ] **Step 2: Run the focused repository/coordinator tests**

```powershell
.\tools\gradlew-local.bat testDebugUnitTest --tests "com.ojnexus.core.data.restore.*" --tests "com.ojnexus.core.data.repository.BackupRepositoryTest"
```

Expected: failures at the new WAL, validation, and rollback assertions.

- [ ] **Step 3: Implement staging and same-directory replacement**

Checkpoint the live Room database before export and create a consistent snapshot. During startup,
move the existing target companions into an explicit rollback set, copy the staged file into the
database directory, sync it, replace the target atomically, reopen it read-only for validation,
and remove rollback files only after validation succeeds.

- [ ] **Step 4: Wire startup before Room construction**

Replace the ignored Boolean call in `AppContainer.init` with the coordinator outcome. Do not open
Room until recovery converges. Persist only the typed outcome and data generation required by the
Settings UI and worker guards.

- [ ] **Step 5: Re-run focused tests**

Run Step 2. Expected: `BUILD SUCCESSFUL` with every failure injection preserving a valid database.

- [ ] **Step 6: Commit atomic restore**

```bash
git add app/src/main/java/com/ojnexus/core/data app/src/test/java/com/ojnexus/core/data app/src/main/java/com/ojnexus/OjNexusApplication.kt
git commit -m "fix: make database restore recoverable"
```

### Task 3: Phase 73 — Invalidate stale work and expose restore outcome

**Files:**
- Modify: `app/src/main/java/com/ojnexus/judge/sync/JudgeSyncWorker.kt`
- Modify: `app/src/main/java/com/ojnexus/judge/luogu/open/LuoguOpenResultWorker.kt`
- Modify: `app/src/main/java/com/ojnexus/judge/sync/JudgeSyncBootstrap.kt`
- Modify: `app/src/main/java/com/ojnexus/judge/luogu/open/LuoguResultWorkBootstrap.kt`
- Modify: `app/src/main/java/com/ojnexus/feature/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/ojnexus/feature/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Test: `app/src/test/java/com/ojnexus/judge/sync/JudgeSyncBootstrapTest.kt`
- Test: `app/src/test/java/com/ojnexus/judge/luogu/open/LuoguOpenResultWorkerTest.kt`
- Test: `app/src/test/java/com/ojnexus/judge/luogu/open/LuoguResultWorkBootstrapTest.kt`
- Test: `app/src/test/java/com/ojnexus/feature/settings/SettingsViewModelSyncAllTest.kt`

**Interfaces:**
- Consumes: `currentDataGeneration()` from Task 2.
- Produces: worker input key `DATA_GENERATION` and a typed stale-generation result.

- [ ] **Step 1: Add failing stale-generation and Settings tests**

Assert that old sync/result work exits before adapter or OpenApp calls, bootstrap recreates work
from restored Room rows, and Settings renders `RESTORE APPLIED`, `RESTORE ROLLED BACK`, or
`RESTORE REJECTED` without raw error messages.

- [ ] **Step 2: Run the focused tests**

```powershell
.\tools\gradlew-local.bat testDebugUnitTest --tests "com.ojnexus.judge.sync.*" --tests "com.ojnexus.judge.luogu.open.*BootstrapTest" --tests "com.ojnexus.feature.settings.*"
```

Expected: new generation and outcome assertions fail.

- [ ] **Step 3: Add generation to scheduling and worker validation**

Schedulers snapshot the current generation. Workers compare it before resolving account/request
IDs and before network access. Startup reconciliation remains the only path that creates fresh
work after restore.

- [ ] **Step 4: Render and clear typed restore outcomes**

Expose one dismissible Settings status row. Keep credentials and database contents out of saved
instance state, strings, and logs.

- [ ] **Step 5: Run Phase 73 validation**

```powershell
.\tools\gradlew-local.bat test
.\tools\gradlew-local.bat assembleDebug
.\tools\gradlew-local.bat assembleRelease
.\tools\gradlew-local.bat lintDebug
```

Expected: every command ends with `BUILD SUCCESSFUL`.

- [ ] **Step 6: Update release identity and commit**

Set `versionName=0.3.71`, `versionCode=71`; update `README.md`, `docs/ROADMAP.md`,
`docs/DATA_SAFETY.md`, and add `docs/releases/v0.3.71.md` after verification.

```bash
git add app README.md docs
git commit -m "release: prepare safe restore v0.3.71"
```

### Task 4: Phase 74 — Add a lifecycle-aware local-day source

**Files:**
- Create: `app/src/main/java/com/ojnexus/core/time/LocalDaySource.kt`
- Create: `app/src/test/java/com/ojnexus/core/time/LocalDaySourceTest.kt`
- Modify: `app/src/main/java/com/ojnexus/feature/dashboard/DashboardViewModel.kt`
- Modify: `app/src/main/java/com/ojnexus/feature/training/TrainingViewModel.kt`
- Modify: `app/src/main/java/com/ojnexus/feature/training/ReviewRunViewModel.kt`
- Modify: `app/src/main/java/com/ojnexus/core/data/repository/AnalyticsRepository.kt`
- Modify: ViewModel factories in the affected screen files.

**Interfaces:**
- Produces: `interface LocalDaySource { val day: Flow<Long>; fun refresh() }`
- Consumes: `Clock` and the application lifecycle/resume signal.

- [ ] **Step 1: Add fake-clock rollover tests**

Cover initial emission, next-midnight emission, app resume after midnight, timezone change, and
cancellation. Add ViewModel tests proving old Room queries are replaced with the new day's query.

- [ ] **Step 2: Run the focused tests and observe failures**

```powershell
.\tools\gradlew-local.bat testDebugUnitTest --tests "com.ojnexus.core.time.LocalDaySourceTest" --tests "com.ojnexus.feature.training.*ViewModelTest" --tests "com.ojnexus.feature.dashboard.*"
```

- [ ] **Step 3: Implement the day flow and replace captured dates**

Use `flatMapLatest` for date-keyed Room streams. Analytics day windows and review bucketing must
derive from the emitted day. Do not add periodic database writes.

- [ ] **Step 4: Re-run focused tests and commit**

```bash
git add app/src/main/java/com/ojnexus/core/time app/src/test/java/com/ojnexus/core/time app/src/main/java/com/ojnexus/feature app/src/main/java/com/ojnexus/core/data/repository/AnalyticsRepository.kt
git commit -m "fix: refresh date-sensitive state across midnight"
```

### Task 5: Phase 74 — Route Dashboard to exact actions

**Files:**
- Modify: `app/src/main/java/com/ojnexus/feature/dashboard/DashboardViewModel.kt`
- Modify: `app/src/main/java/com/ojnexus/feature/dashboard/DashboardCommandDeck.kt`
- Modify: `app/src/main/java/com/ojnexus/feature/dashboard/DashboardScreen.kt`
- Modify: `app/src/main/java/com/ojnexus/app/NexusApp.kt`
- Modify: `app/src/main/java/com/ojnexus/core/database/dao/SubmissionJobDao.kt`
- Test: `app/src/test/java/com/ojnexus/feature/dashboard/DashboardCommandSurfaceTest.kt`
- Test: `app/src/test/java/com/ojnexus/app/DashboardNavigationTest.kt`
- Test: `app/src/androidTest/java/com/ojnexus/feature/dashboard/DashboardCommandSurfaceComposeTest.kt`

**Interfaces:**
- Produces: `sealed interface DashboardAction` with identifier-bearing session, task, review,
  submission, contest, settings, and no-action variants.
- Consumes: active session flow and one actionable submission job flow from existing Room data.

- [ ] **Step 1: Add precedence and route tests**

Test all competing states from the spec and assert the exact session ID, problem ID, request ID,
or contest identity carried by the selected action.

- [ ] **Step 2: Run focused tests and confirm category-only routing fails them**

```powershell
.\tools\gradlew-local.bat testDebugUnitTest --tests "com.ojnexus.feature.dashboard.*" --tests "com.ojnexus.app.DashboardNavigationTest"
```

- [ ] **Step 3: Extend local Dashboard inputs and projection**

Add active session and actionable submission flows to the existing combined snapshot. Keep the
projection pure; it must never query a DAO or network client.

- [ ] **Step 4: Wire identifier-bearing navigation**

Navigate to the existing session/review/contest routes and add a request-focused Submission Center
route using one-shot navigation context. Deleted targets use existing empty/not-found behavior.

- [ ] **Step 5: Replace fixed cell height with minimum accessible sizing**

Add Compose coverage at 360 dp width, 200% font scale, and reduced motion. Assert visible labels,
click semantics, and no clipped action text.

- [ ] **Step 6: Run Phase 74 validation and release**

Run unit, Debug/Release, lint, connected tests, signed install smoke, and `git diff --check`. Set
`versionName=0.3.72`, `versionCode=72`, update current status/release docs, and commit:

```bash
git add app README.md docs
git commit -m "release: prepare action continuity v0.3.72"
```

### Task 6: Phase 75 — Persist explicit training targets

**Files:**
- Modify: `app/src/main/java/com/ojnexus/core/data/preferences/UserPreferencesRepository.kt`
- Create: `app/src/main/java/com/ojnexus/core/model/TrainingTarget.kt`
- Create: `app/src/test/java/com/ojnexus/core/data/preferences/TrainingTargetPreferencesTest.kt`
- Modify: `app/src/main/java/com/ojnexus/feature/training/TrainingViewModel.kt`
- Modify: `app/src/main/java/com/ojnexus/feature/training/TrainingScreen.kt`
- Modify: English and Chinese string resources.

**Interfaces:**
- Produces: `data class TrainingTarget(val judge: JudgeId?, val center: Int?, val tolerance: Int)`
- Produces: preference flow and setters for default and per-judge targets.

- [ ] **Step 1: Test round-trip, clearing, and invalid values**

Require nullable targets, a bounded positive tolerance, and judge-independent defaults. Do not
derive target values from missing ratings.

- [ ] **Step 2: Run focused tests, implement DataStore mapping, and re-run**

```powershell
.\tools\gradlew-local.bat testDebugUnitTest --tests "com.ojnexus.core.data.preferences.TrainingTargetPreferencesTest"
```

- [ ] **Step 3: Add localized calibration controls**

Expose edit and clear actions in the existing Focus Sprint flow. Keep the preview valid when no
target exists and add large-font Compose coverage.

- [ ] **Step 4: Commit target preferences**

```bash
git add app/src/main/java/com/ojnexus/core app/src/main/java/com/ojnexus/feature/training app/src/test app/src/main/res
git commit -m "feat: add explicit training difficulty targets"
```

### Task 7: Phase 75 — Build a fair candidate pool and mastery score

**Files:**
- Modify: `app/src/main/java/com/ojnexus/core/database/dao/ProblemDao.kt`
- Modify: `app/src/main/java/com/ojnexus/core/data/repository/TrainingRepository.kt`
- Modify: `app/src/main/java/com/ojnexus/core/domain/TrainingPlanner.kt`
- Modify: `app/src/main/java/com/ojnexus/feature/training/TrainingViewModel.kt`
- Modify: `app/src/main/java/com/ojnexus/feature/training/FocusSprint.kt`
- Modify: related DAO, planner, ViewModel, and Focus Sprint tests.

**Interfaces:**
- Produces: a bounded `TrainingCandidateEvidence` with due state, difficulty, failures, and linked
  knowledge areas.
- Consumes: `TrainingTarget` and `KnowledgeAreaState`.

- [ ] **Step 1: Add failing fairness and scoring tests**

Insert more than the current limit with an old due/high-failure item outside the recent window.
Assert its presence. Cover low mastery, multiple areas, missing evidence, stable ties, and exact
reason codes.

- [ ] **Step 2: Run focused DAO and planner tests**

```powershell
.\tools\gradlew-local.bat testDebugUnitTest --tests "com.ojnexus.core.database.OjNexusDatabaseTest" --tests "com.ojnexus.core.domain.TrainingPlannerTest" --tests "com.ojnexus.feature.training.FocusSprintTest"
```

- [ ] **Step 3: Implement bounded source buckets and merge policy**

Query due, recent-unsolved, failure-heavy, and weak-area-linked buckets with explicit caps. Merge
and deduplicate by local problem ID before pure scoring. Define the total cap in one named policy
constant and keep query ordering deterministic.

- [ ] **Step 4: Connect target difficulty and mastery evidence**

Remove the hard-coded null target. Replace relation-count coverage with a bounded weakness score
from the linked knowledge areas. Render reason codes already present in the plan preview.

- [ ] **Step 5: Add a fixed-data performance regression test**

Use a deterministic large local data set; measure only after warm-up and choose a threshold from
the measured baseline with enough CI headroom. Record the device/JVM and data size in
`docs/PERFORMANCE.md`.

- [ ] **Step 6: Run Phase 75 validation and release**

Run the full release checks. Set `versionName=0.3.73`, `versionCode=73`, update docs, and commit:

```bash
git add app README.md docs
git commit -m "release: prepare training calibration v0.3.73"
```

### Task 8: Phase 76 — Persist bounded sync operation history

**Files:**
- Create: `app/src/main/java/com/ojnexus/core/database/entity/SyncOperationEntity.kt`
- Create: `app/src/main/java/com/ojnexus/core/database/entity/SyncOperationModuleEntity.kt`
- Create: `app/src/main/java/com/ojnexus/core/database/dao/SyncOperationDao.kt`
- Modify: `app/src/main/java/com/ojnexus/core/database/OjNexusDatabase.kt`
- Create: `app/schemas/com.ojnexus.core.database.OjNexusDatabase/13.json`
- Modify: `app/src/test/java/com/ojnexus/core/database/MigrationTest.kt`
- Create: `app/src/test/java/com/ojnexus/core/database/SyncOperationDaoTest.kt`

**Interfaces:**
- Produces: operation status and module status enums with stable stored names.
- Produces: DAO methods to open, append committed module outcome, close, observe recent by judge,
  and prune completed history.
- Preserves: `SyncStateEntity` as current state, freshness, and cursor owner.

- [ ] **Step 1: Add migration and DAO tests first**

Seed a schema-12 database with accounts, cursors, and sync freshness. Migrate to 13 and assert all
old values survive. Test foreign keys, ordering, retention, and per-judge pruning boundaries.

- [ ] **Step 2: Run the focused tests and verify the migration is missing**

```powershell
.\tools\gradlew-local.bat testDebugUnitTest --tests "com.ojnexus.core.database.MigrationTest" --tests "com.ojnexus.core.database.SyncOperationDaoTest"
```

- [ ] **Step 3: Implement schema 13 and migration 12→13**

Use foreign keys only where deletion behavior is explicit. Index judge/start time and operation
foreign keys. Export schema 13 and verify Room identity with the existing migration test harness.

- [ ] **Step 4: Implement transactional retention**

Prune only completed operations older than the newest fixed count for the same judge. Never prune
an active operation or another judge's history.

- [ ] **Step 5: Re-run focused tests and commit**

```bash
git add app/src/main/java/com/ojnexus/core/database app/src/test/java/com/ojnexus/core/database app/schemas
git commit -m "feat: persist bounded sync operation history"
```

### Task 9: Phase 76 — Record module outcomes and add precise retry

**Files:**
- Modify: `app/src/main/java/com/ojnexus/judge/codeforces/CodeforcesSyncCoordinator.kt`
- Modify: `app/src/main/java/com/ojnexus/judge/atcoder/AtCoderSyncCoordinator.kt`
- Modify: `app/src/main/java/com/ojnexus/judge/luogu/LuoguSyncCoordinator.kt`
- Modify: `app/src/main/java/com/ojnexus/judge/JudgeSyncDispatcher.kt`
- Modify: `app/src/main/java/com/ojnexus/judge/sync/JudgeSyncWorker.kt`
- Modify: `app/src/main/java/com/ojnexus/core/data/sync/SyncModel.kt`
- Modify: `app/src/test/java/com/ojnexus/judge/codeforces/CodeforcesSyncTest.kt`
- Modify: `app/src/test/java/com/ojnexus/judge/atcoder/AtCoderSyncRepositoryTest.kt`
- Modify: `app/src/test/java/com/ojnexus/judge/luogu/LuoguSyncCoordinatorTest.kt`
- Modify: `app/src/test/java/com/ojnexus/judge/JudgeSyncRoutingTest.kt`

**Interfaces:**
- Consumes: operation DAO from Task 8 and data generation from Phase 73.
- Produces: `SyncRetryRequest(judge, accountId, operationId, stage, dataGeneration)`.

- [ ] **Step 1: Add failing operation lifecycle tests**

Cover success, partial, typed error, cancellation, committed counts, database-write failure,
duplicate enqueue, unsupported stage retry, and stale generation. Verify network fixtures remain
deterministic and are never presented as live results.

- [ ] **Step 2: Run judge tests and observe missing ledger writes**

```powershell
.\tools\gradlew-local.bat testDebugUnitTest --tests "com.ojnexus.judge.*"
```

- [ ] **Step 3: Record outcomes after successful Room writes**

Open one operation per explicit run. Append a module outcome only after its data transaction
commits; then close the operation. Keep current `sync_states` updates and cursors intact.

- [ ] **Step 4: Route safe stage retries**

Validate judge/account/operation/stage/generation in the dispatcher. Fall back to full explicit
sync when an adapter cannot restart the selected stage safely.

- [ ] **Step 5: Run all judge tests and commit**

```bash
git add app/src/main/java/com/ojnexus/judge app/src/main/java/com/ojnexus/core/data/sync app/src/test/java/com/ojnexus/judge
git commit -m "feat: record sync modules and retry failed stages"
```

### Task 10: Phase 76 — Render ledger, strengthen CI, and publish

**Files:**
- Modify: `app/src/main/java/com/ojnexus/feature/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/ojnexus/feature/settings/SettingsScreen.kt`
- Create: `app/src/main/java/com/ojnexus/feature/settings/SyncOperationHistory.kt`
- Create: `app/src/test/java/com/ojnexus/feature/settings/SyncOperationHistoryTest.kt`
- Create: `app/src/androidTest/java/com/ojnexus/feature/settings/SyncOperationHistoryComposeTest.kt`
- Modify: `.github/workflows/android.yml`
- Modify: English/Chinese strings and release documentation.

**Interfaces:**
- Consumes: recent operation Flow and `SyncRetryRequest` from Tasks 8–9.
- Produces: bounded connector history UI with capability-gated retry actions.

- [ ] **Step 1: Add projection and Compose tests**

Cover no history, mixed status, module counts, unsupported retry, stale generation, 360 dp width,
200% font scale, reduced motion, and text-based status semantics.

- [ ] **Step 2: Run focused tests before implementation**

```powershell
.\tools\gradlew-local.bat testDebugUnitTest --tests "com.ojnexus.feature.settings.SyncOperationHistoryTest"
```

- [ ] **Step 3: Implement the local history sheet**

Render recent operations under the selected connector. Keep the connector center's current
snapshot visible and make retry actions explicit and duplicate-safe.

- [ ] **Step 4: Strengthen the CI gate**

Add `lintDebug` and `assembleRelease` to the existing Linux job. Add a separate emulator job for
the committed connected suite with Gradle/AVD caching; upload test reports and screenshots only as
artifacts. Keep secrets absent and do not call real OJ endpoints.

- [ ] **Step 5: Run final local validation**

```powershell
.\tools\gradlew-local.bat test
.\tools\gradlew-local.bat assembleDebug
.\tools\gradlew-local.bat assembleRelease
.\tools\gradlew-local.bat lintDebug
.\gradlew.bat connectedDebugAndroidTest
git diff --check
git status --short
```

Expected: all Gradle commands report `BUILD SUCCESSFUL`, connected tests have zero failures and
zero skips, and the final status contains only intentional Phase 76 changes.

- [ ] **Step 6: Verify and publish the release**

Set `versionName=0.3.74`, `versionCode=74`. Install the exact signed Release APK on the available
emulator/device, confirm package identity and no fatal exception, compute SHA-256, update
`README.md`, `docs/ROADMAP.md`, `docs/PERFORMANCE.md`, and `docs/releases/v0.3.74.md`, then inspect
the diff for secrets before push/tag/Release.

```bash
git add .github app README.md docs
git commit -m "release: prepare sync operations ledger v0.3.74"
```

## Plan self-review record

- Spec coverage: all Phase 73–76 requirements map to Tasks 1–10.
- Dependency order: restore generation precedes worker guards and later retry inputs; schema 13
  precedes coordinator ledger writes; data projections precede Compose surfaces.
- Scope: each phase is separately testable and releasable; existing backup, current sync receipt,
  credential storage, and navigation routes are extended rather than rebuilt.
- Type consistency: `RestoreOutcome`, data generation, `DashboardAction`, `TrainingTarget`, sync
  operation entities, and `SyncRetryRequest` are introduced before their consumers.
