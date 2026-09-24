# Luogu OpenApp background result convergence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Converge already-created Luogu OpenApp asynchronous results in the background without background submission or credential expansion.

**Architecture:** A small result-work policy separates retry classification from Android WorkManager. A Keystore-backed gateway remains behind the existing `LuoguSubmissionRepository`; the repository schedules a unique result worker only after local metadata is persisted. App startup reconciles a bounded set of pending local jobs into the same queue.

**Tech Stack:** Kotlin, Coroutines, WorkManager `CoroutineWorker`, Room, JUnit, existing Retrofit/OkHttp OpenApp client.

**Spec:** `docs/superpowers/specs/2026-09-01-luogu-background-result-design.md`

## Global Constraints

- Only official OpenApp `GET /judge/result/{requestId}` is used in the background; never submit or retry a POST.
- Never add passwords, main-site cookies, sessions, CSRF state, source code, or standard input to worker input or database lifecycle metadata.
- Keep the existing Keystore-backed `OpenAppCredentialStore`; missing or permanent credentials are not retried indefinitely.
- Preserve local-first behavior, existing foreground polling, historical docs, and the Room schema unless a migration is required by the implementation.
- Every UI string remains in resources; no UI behavior is color-only; existing design tokens remain the source of layout styling.
- Every production behavior is preceded by a failing test and every task ends with focused verification.

---

### Task 1: Define result-worker decisions and pending-job query

**Files:**
- Create: `app/src/main/java/com/ojnexus/judge/luogu/open/LuoguResultWorkPolicy.kt`
- Modify: `app/src/main/java/com/ojnexus/core/database/dao/SubmissionJobDao.kt`
- Test: `app/src/test/java/com/ojnexus/judge/luogu/open/LuoguResultWorkPolicyTest.kt`

**Interfaces:**
- Produces `LuoguResultWorkDecision` with `Success`, `Retry`, and `Failure` outcomes.
- Produces `fun LuoguOpenApiError.isRetryableResultError(): Boolean` for network and HTTP 408/425/429/5xx failures.
- Produces `SubmissionJobDao.findPendingForBackground(limit: Int): List<SubmissionJobEntity>` ordered by `updated_at ASC, id ASC`.

- [ ] **Step 1: Write the failing policy and DAO-facing tests**

Test these exact behaviors:

```kotlin
assertEquals(LuoguResultWorkDecision.Success, decide(LuoguOpenResult.Ready(evaluation), 0))
assertEquals(LuoguResultWorkDecision.Retry, decide(LuoguOpenResult.Pending, 0))
assertEquals(LuoguResultWorkDecision.Retry, decide(LuoguOpenApiError.Network(IOException()), 0))
assertEquals(LuoguResultWorkDecision.Failure, decide(LuoguOpenApiError.Unauthorized, 0))
assertEquals(LuoguResultWorkDecision.Success, decide(LuoguOpenResult.Pending, MAX_RESULT_ATTEMPTS))
```

Use an in-memory Room database test for the DAO query: insert two `PENDING` rows and one `READY`
row, request a limit of two, and assert only the oldest two pending request IDs are returned.

- [ ] **Step 2: Run the focused tests and verify the expected RED failure**

Run:

```text
.\tools\gradlew-local.bat testDebugUnitTest --tests com.ojnexus.judge.luogu.open.LuoguResultWorkPolicyTest --no-daemon --console=plain
```

Expected: compilation fails because the decision policy and pending DAO query do not exist.

- [ ] **Step 3: Implement the minimal policy and DAO query**

Use `MAX_RESULT_ATTEMPTS = 20`. Treat `Pending` and `InProgress` as `Retry` below the budget and
`Success` at or after the budget. Treat `Network` and `Http` status 408, 425, 429, or 500–599 as
retryable; classify all other `LuoguOpenApiError` values as `Failure`.

Add this Room query:

```kotlin
@Query("SELECT * FROM submission_jobs WHERE judge = 'luogu' AND status = 'PENDING' ORDER BY updated_at ASC, id ASC LIMIT :limit")
suspend fun findPendingForBackground(limit: Int): List<SubmissionJobEntity>
```

- [ ] **Step 4: Run the focused tests and verify GREEN**

Run the same command from Step 2. Expected: all policy and DAO assertions pass.

- [ ] **Step 5: Commit the task**

```text
git add app/src/main/java/com/ojnexus/judge/luogu/open/LuoguResultWorkPolicy.kt app/src/main/java/com/ojnexus/core/database/dao/SubmissionJobDao.kt app/src/test/java/com/ojnexus/judge/luogu/open/LuoguResultWorkPolicyTest.kt
git commit -m "test: define Luogu result work policy / 定义洛谷结果任务策略"
```

### Task 2: Build the unique WorkManager request and worker

**Files:**
- Create: `app/src/main/java/com/ojnexus/judge/luogu/open/LuoguOpenResultWorker.kt`
- Create: `app/src/test/java/com/ojnexus/judge/luogu/open/LuoguOpenResultWorkerTest.kt`
- Modify: `app/src/main/java/com/ojnexus/judge/luogu/open/LuoguResultWorkPolicy.kt`

**Interfaces:**
- Produces `LuoguResultWorkScheduler` with `fun enqueue(requestId: String)`, a no-op for blank IDs.
- Produces `WorkManagerLuoguResultScheduler(context: Context)` using `ExistingWorkPolicy.KEEP`.
- Produces `LuoguOpenResultWorker(context: Context, params: WorkerParameters)` that reads only `request_id`.

- [ ] **Step 1: Write failing tests for request identity and worker outcomes**

Assert that the request factory produces a connected-network one-time request with a 10-second
initial delay, exponential 30-second backoff, only `request_id` in `inputData`, and a stable unique
name derived from the trimmed request ID. Assert policy mapping for ready, pending, transient,
permanent, and exhausted cases through a fake result source.

- [ ] **Step 2: Run the focused worker tests and verify RED**

```text
.\tools\gradlew-local.bat testDebugUnitTest --tests com.ojnexus.judge.luogu.open.LuoguOpenResultWorkerTest --no-daemon --console=plain
```

Expected: compilation fails because the scheduler, request factory, and worker do not exist.

- [ ] **Step 3: Implement the request factory, scheduler, and worker**

Use the existing application container to obtain `luoguSubmissionRepository`, call
`refreshResult(requestId)`, and map the result through `LuoguResultWorkPolicy`. Return
`Result.retry()` only for retry decisions while `runAttemptCount < MAX_RESULT_ATTEMPTS`; return
`Result.success()` for terminal or exhausted decisions and `Result.failure()` for permanent errors.
Use `Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()` and unique work
names of `luogu-result:<trimmedRequestId>`.

- [ ] **Step 4: Run focused worker tests and verify GREEN**

Run the same command from Step 2 and require all assertions to pass.

- [ ] **Step 5: Commit the task**

```text
git add app/src/main/java/com/ojnexus/judge/luogu/open/LuoguOpenResultWorker.kt app/src/main/java/com/ojnexus/judge/luogu/open/LuoguResultWorkPolicy.kt app/src/test/java/com/ojnexus/judge/luogu/open/LuoguOpenResultWorkerTest.kt
git commit -m "feat: add Luogu result worker / 增加洛谷结果后台任务"
```

### Task 3: Wire scheduling after persistence and preserve pending status on transient errors

**Files:**
- Modify: `app/src/main/java/com/ojnexus/judge/luogu/open/LuoguSubmissionRepository.kt`
- Modify: `app/src/main/java/com/ojnexus/OjNexusApplication.kt`
- Modify: `app/src/test/java/com/ojnexus/judge/luogu/open/LuoguSubmissionRepositoryTest.kt`

**Interfaces:**
- `LuoguSubmissionRepository` accepts an optional `LuoguResultWorkScheduler` after existing constructor parameters.
- A successful `submitProblem` or supported `run` persists first, then calls `scheduler.enqueue(requestId)`.
- Transient fetch errors leave a persisted job `PENDING` while recording `lastErrorType`; permanent errors remain `FAILED`.

- [ ] **Step 1: Write failing repository tests**

Add a recording scheduler and assert a successful submit produces a database row before exactly one
enqueue call. Add a transient network-result test that asserts status remains `PENDING`, and a
permanent unauthorized-result test that asserts status becomes `FAILED`.

- [ ] **Step 2: Run the focused repository tests and verify RED**

```text
.\tools\gradlew-local.bat testDebugUnitTest --tests com.ojnexus.judge.luogu.open.LuoguSubmissionRepositoryTest --no-daemon --console=plain
```

Expected: tests fail because no scheduler hook exists and transient errors currently mark every
OpenApp error as `FAILED`.

- [ ] **Step 3: Implement minimal wiring and error status classification**

Inject the scheduler, call it only after `persist` returns, and use
`isRetryableResultError()` when writing the error status. In `AppContainer`, construct the
production scheduler with the application context and pass it to `LuoguSubmissionRepository`.

- [ ] **Step 4: Run focused repository, existing OpenApp, and workspace tests**

```text
.\tools\gradlew-local.bat testDebugUnitTest --tests com.ojnexus.judge.luogu.open.LuoguSubmissionRepositoryTest --tests com.ojnexus.feature.workspace.WorkspaceViewModelTest --tests com.ojnexus.feature.submissions.SubmissionCenterViewModelTest --no-daemon --console=plain
```

Expected: all tests pass and existing foreground behavior remains unchanged.

- [ ] **Step 5: Commit the task**

```text
git add app/src/main/java/com/ojnexus/OjNexusApplication.kt app/src/main/java/com/ojnexus/judge/luogu/open/LuoguSubmissionRepository.kt app/src/test/java/com/ojnexus/judge/luogu/open/LuoguSubmissionRepositoryTest.kt
git commit -m "feat: schedule persisted Luogu results / 调度已保存的洛谷结果"
```

### Task 4: Reconcile pending jobs on application startup

**Files:**
- Create: `app/src/main/java/com/ojnexus/judge/luogu/open/LuoguResultWorkBootstrap.kt`
- Modify: `app/src/main/java/com/ojnexus/OjNexusApplication.kt`
- Test: `app/src/test/java/com/ojnexus/judge/luogu/open/LuoguResultWorkBootstrapTest.kt`

**Interfaces:**
- `LuoguResultWorkBootstrap.reconcilePending()` reads at most `MAX_PENDING_BOOTSTRAP = 50` rows and calls `scheduler.enqueue(requestId)` for each request ID.
- It runs in an application-lifetime `SupervisorJob + Dispatchers.IO`; startup reconciliation failures are isolated and do not crash the app.

- [ ] **Step 1: Write the failing bootstrap test**

Use a fake repository/DAO returning 51 pending jobs and a recording scheduler. Assert exactly 50
IDs are enqueued in the DAO order and a scheduler exception for one ID does not prevent the other
IDs from being attempted.

- [ ] **Step 2: Run the focused bootstrap test and verify RED**

```text
.\tools\gradlew-local.bat testDebugUnitTest --tests com.ojnexus.judge.luogu.open.LuoguResultWorkBootstrapTest --no-daemon --console=plain
```

Expected: compilation fails because bootstrap does not exist.

- [ ] **Step 3: Implement bounded startup reconciliation and application wiring**

Create the bootstrap with `SubmissionJobDao` and `LuoguResultWorkScheduler` dependencies, catch
per-ID exceptions inside the loop, and launch it from `OjNexusApplication.onCreate` after the
container is created. Do not block `onCreate` and do not log credentials or response payloads.

- [ ] **Step 4: Run bootstrap and application smoke tests**

Run the same focused command and then install/launch the debug APK on the connected emulator,
checking the resumed activity and logcat for `FATAL EXCEPTION`.

- [ ] **Step 5: Commit the task**

```text
git add app/src/main/java/com/ojnexus/OjNexusApplication.kt app/src/main/java/com/ojnexus/judge/luogu/open/LuoguResultWorkBootstrap.kt app/src/test/java/com/ojnexus/judge/luogu/open/LuoguResultWorkBootstrapTest.kt
git commit -m "feat: reconcile pending Luogu results / 收敛待处理洛谷结果"
```

### Task 5: Documentation, full verification, and GitHub Release

**Files:**
- Modify: `README.md`
- Modify: `docs/ROADMAP.md`
- Modify: `docs/DATABASE.md` only if schema facts changed
- Create: `docs/releases/v0.3.24.md`

- [ ] **Step 1: Add bilingual Phase 28 documentation without deleting earlier phases**

Document the exact GET-only background result behavior, bounded retry policy, startup reconciliation,
local error states, and explicit non-goals in English + Chinese. Keep prior README, roadmap,
database, and release text intact.

- [ ] **Step 2: Run formatting and full verification**

```text
git diff --check
.\tools\gradlew-local.bat clean test assembleDebug lintDebug --no-daemon --rerun-tasks --console=plain
```

Expected: `BUILD SUCCESSFUL`, zero test failures, and no diff-check errors.

- [ ] **Step 3: Install and smoke-test the APK**

```text
$adb='D:\Android\platform-tools\adb.exe'
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
& $adb shell monkey -p com.ojnexus 1
& $adb shell dumpsys activity activities | Select-String 'mResumedActivity|mFocusedApp'
& $adb logcat -d -t 500 | Select-String 'FATAL EXCEPTION|AndroidRuntime'
```

Expected: `Success`, `com.ojnexus/.MainActivity`, and no fatal application exception.

- [ ] **Step 4: Commit, push, and create the formal Release**

Use a bilingual commit message, push `codex/phase-5-arena`, create tag `v0.3.24` targeting the
verified HEAD, upload `app-debug.apk`, and keep the Release published (not draft/prerelease).

- [ ] **Step 5: Verify remote identity and artifact integrity**

Compare local HEAD with `git ls-remote`, compare the tag target with HEAD, and compare GitHub's
asset digest with `Get-FileHash app/build/outputs/apk/debug/app-debug.apk -Algorithm SHA256`.
Only report the Release after every comparison matches and `git status --short` is empty.
