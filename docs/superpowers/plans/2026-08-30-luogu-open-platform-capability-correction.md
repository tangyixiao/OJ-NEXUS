# Luogu Open Platform Capability Correction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Luogu Open Platform integration use only documented endpoints and make the workspace hide unsupported custom-input execution.

**Architecture:** Keep `LuoguOpenGateway` as the domain boundary, add a capability property with the official client returning false for custom-input execution, and keep test fakes opt-in by default. Correct Retrofit paths at the network boundary; make the Workspace ViewModel and Compose screen derive their mode and controls from the capability.

**Tech Stack:** Kotlin, Retrofit, kotlinx.serialization, Coroutines/StateFlow, Jetpack Compose, JUnit, MockWebServer.

**Spec:** `docs/superpowers/specs/2026-08-30-luogu-open-platform-capability-correction-design.md`

## Global Constraints

- Use only the official documented Luogu Open Platform endpoints: `POST /judge/problem` and `GET /judge/result/{id}`.
- Do not add main-site passwords, cookies, sessions, CSRF state, cloud storage, or background requests.
- Do not persist source code or custom input in Room.
- Use existing NEXUS design tokens and resource-backed UI strings.
- Every implementation task ends with focused tests; the full phase ends with `clean test assembleDebug lintDebug`.

---

### Task 1: Correct the Open Platform transport boundary

**Files:**
- Modify: `app/src/main/java/com/ojnexus/judge/luogu/open/LuoguOpenPlatform.kt`
- Modify: `app/src/main/java/com/ojnexus/judge/luogu/LuoguUrls.kt` only if the base URL needs a documented suffix; otherwise leave unchanged.
- Test: `app/src/test/java/com/ojnexus/judge/luogu/open/LuoguOpenPlatformTest.kt`

**Interfaces:**
- Produces `LuoguOpenGateway.supportsCustomInputRun: Boolean`, defaulting to `true` for generic test/future providers.
- `LuoguOpenPlatformClient` overrides the capability as `false`.
- `LuoguOpenPlatformClient.run(...)` throws a typed `LuoguOpenApiError.UnsupportedOperation` before reading credentials or making a network call.

- [ ] **Step 1: Add failing transport assertions**

Change the MockWebServer expectations from `/problem` and `/result/{id}` to `/judge/problem` and `/judge/result/{id}`. Add a test that calls `client.run(...)` and asserts `UnsupportedOperation` with zero server requests.

- [ ] **Step 2: Run the focused test and verify the expected failure**

Run: `tools\\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.judge.luogu.open.LuoguOpenPlatformClientTest --no-daemon --rerun-tasks --console=plain`

Expected: failure because the Retrofit annotations still use the old paths and the typed error/capability do not exist.

- [ ] **Step 3: Implement the documented paths and typed unsupported operation**

Use these Retrofit annotations:

```kotlin
@POST("judge/problem")
suspend fun submitProblem(...): Response<LuoguAsyncResponseDto>

@GET("judge/result/{id}")
suspend fun result(...): Response<LuoguJudgeCallbackDto>
```

Remove the Retrofit `/run` method. Add `data object UnsupportedOperation : LuoguOpenApiError(...)`, add the gateway capability property, and make the official client reject `run` without touching the credential store or API.

- [ ] **Step 4: Run the focused test and verify it passes**

Run the command from Step 2. Expected: `BUILD SUCCESSFUL`, all focused Open Platform tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ojnexus/judge/luogu/open/LuoguOpenPlatform.kt app/src/test/java/com/ojnexus/judge/luogu/open/LuoguOpenPlatformTest.kt
git commit -m "fix: align Luogu OpenAPI endpoint paths"
```

### Task 2: Propagate the capability through the workspace

**Files:**
- Modify: `app/src/main/java/com/ojnexus/judge/luogu/open/LuoguSubmissionRepository.kt`
- Modify: `app/src/main/java/com/ojnexus/feature/workspace/WorkspaceViewModel.kt`
- Modify: `app/src/main/java/com/ojnexus/feature/workspace/WorkspaceScreen.kt`
- Modify: `app/src/main/res/values/strings.xml` only for a new unsupported-capability explanation if needed.
- Modify: `app/src/main/res/values-zh-rCN/strings.xml` with the matching translation.
- Test: `app/src/test/java/com/ojnexus/feature/workspace/WorkspaceViewModelTest.kt`

**Interfaces:**
- `LuoguSubmissionRepository.supportsCustomInputRun` delegates to its gateway.
- `WorkspaceState` contains `customRunAvailable: Boolean`; the official client initializes it false and starts in `WorkspaceMode.SUBMIT`.
- Test gateway fakes explicitly override `supportsCustomInputRun = true` when testing the generic RUN branch.

- [ ] **Step 1: Add failing workspace capability tests**

Add tests that construct a gateway with `supportsCustomInputRun = false` and assert the initial state has `customRunAvailable == false` and `mode == WorkspaceMode.SUBMIT`; retain a test gateway with the property true and assert the existing RUN behavior remains available.

- [ ] **Step 2: Run the focused workspace tests and verify the expected failure**

Run: `tools\\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.workspace.WorkspaceViewModelTest --no-daemon --rerun-tasks --console=plain`

Expected: compile or assertion failures until the capability reaches the state model and constructor.

- [ ] **Step 3: Implement capability-aware state and controls**

Initialize the state with `mode = if (gateway.supportsCustomInputRun) RUN else SUBMIT`. During history restore, choose RUN only when both the persisted job is a run and the gateway supports it. In the screen, render the RUN mode action and standard-input field only when `customRunAvailable` is true. Map `UnsupportedOperation` to a resource-backed workspace error as a defensive fallback.

- [ ] **Step 4: Run focused workspace and Open Platform tests**

Run: `tools\\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.workspace.WorkspaceViewModelTest --tests com.ojnexus.judge.luogu.open.LuoguOpenPlatformClientTest --no-daemon --rerun-tasks --console=plain`

Expected: `BUILD SUCCESSFUL`, with both test classes passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ojnexus/judge/luogu/open/LuoguSubmissionRepository.kt app/src/main/java/com/ojnexus/feature/workspace/WorkspaceViewModel.kt app/src/main/java/com/ojnexus/feature/workspace/WorkspaceScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml app/src/test/java/com/ojnexus/feature/workspace/WorkspaceViewModelTest.kt
git commit -m "feat: hide unsupported Luogu custom runner"
```

### Task 3: Update capability documentation and verify the phase

**Files:**
- Modify: `docs/LUOGU_OPEN_PLATFORM.md`
- Modify: `docs/ROADMAP.md`
- Modify: `README.md`

**Interfaces:**
- Documentation must state that official Luogu Open Platform judging includes remote compilation/evaluation, while custom-input execution is not an official endpoint in the current specification.

- [ ] **Step 1: Replace inaccurate `/run` and custom-input claims**

Document `POST /judge/problem`, `GET /judge/result/{id}`, 204 pending results, and the capability-aware workspace. Remove claims that `/run` is an official remote interface.

- [ ] **Step 2: Check documentation for stale endpoint claims**

Run: `rg -n '(/run|POST `/run`|自定义输入运行|custom-input run)' README.md docs app/src/main`

Expected: no claim that `/run` is a supported official Luogu endpoint; any remaining mention must be explicitly marked as unsupported/future capability.

- [ ] **Step 3: Run full verification**

Run: `tools\\gradlew-local.bat clean test assembleDebug lintDebug --no-daemon --rerun-tasks --console=plain`

Expected: `BUILD SUCCESSFUL`; parse `app/build/test-results/testDebugUnitTest/*.xml` and report total tests, failures, and errors.

- [ ] **Step 4: Audit the final diff**

Run: `git diff --check`, `git status --short`, and a focused secret-pattern scan. Confirm no source code, input, credentials, cookies, or cloud fields were added to persistent entities.

- [ ] **Step 5: Commit**

```bash
git add README.md docs/LUOGU_OPEN_PLATFORM.md docs/ROADMAP.md
git commit -m "docs: clarify Luogu runner capability"
```

## Execution record

- Task 1 completed in `fded633`: official endpoint paths and pre-network unsupported-run guard.
- Task 2 completed in `d952639`: capability-aware repository, workspace state, UI controls, and tests.
- Task 3 completed in `25f63cf`: README, roadmap, editor spec, and Open Platform documentation corrected.
- Final verification: `clean test assembleDebug lintDebug` passed with 243 tests, 0 failures, and 0 errors.
