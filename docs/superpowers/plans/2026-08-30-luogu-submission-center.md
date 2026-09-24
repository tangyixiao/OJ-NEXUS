# Luogu Local Submission Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a local Room-backed Luogu submission center for inspecting and manually refreshing Open Platform jobs.

**Architecture:** Extend the existing Luogu submission repository with a small feature-facing interface that exposes a recent-job Flow and explicit result refresh. Add a ViewModel and Compose screen using the existing `Loadable` and NEXUS design system, then wire Profile, command palette, and navigation to the route.

**Tech Stack:** Kotlin, Coroutines/Flow, Room, Jetpack Compose, Material 3, Navigation Compose, JUnit.

**Spec:** `docs/superpowers/specs/2026-08-30-luogu-submission-center-design.md`

## Global Constraints

- No main-site passwords, cookies, sessions, or CSRF state.
- No source code, standard input, credentials, or cloud data in the submission-center database/query.
- Result refresh is a foreground user action; no WorkManager polling or automatic POST retry.
- All UI strings use `res/values/strings.xml` and `res/values-zh-rCN/strings.xml`.
- Feature UI uses `core/designsystem` tokens and handles Loading / Success / Empty / Error.
- Every implementation task ends with a focused test and a logical commit.

---

### Task 1: Submission-center repository contract

**Files:**
- Modify: `app/src/main/java/com/ojnexus/judge/luogu/open/LuoguSubmissionRepository.kt`
- Modify: `app/src/main/java/com/ojnexus/core/database/dao/SubmissionJobDao.kt`
- Test: `app/src/test/java/com/ojnexus/judge/luogu/open/LuoguSubmissionRepositoryTest.kt`

**Interfaces:**
- Produces `LuoguSubmissionCenter.observeRecentJobs(limit: Int): Flow<List<SubmissionJobEntity>>`.
- Produces `LuoguSubmissionCenter.refreshResult(requestId: String): LuoguOpenResult`.
- Keeps `SubmissionJobDao.observeRecent(limit)` as the Room source of truth.

- [ ] **Step 1: Add a failing recent-ordering test** using two persisted jobs with different `updatedAt` values and assert `latestForProblem`/recent observation returns newest first.
- [ ] **Step 2: Run the focused repository test and confirm the missing interface/query behavior fails.**
- [ ] **Step 3: Implement the feature-facing interface and delegate it to the existing DAO/gateway without adding new persistence fields.**
- [ ] **Step 4: Run the repository test and confirm it passes.**
- [ ] **Step 5: Commit with `feat: expose local Luogu submission history`.**

### Task 2: Submission-center ViewModel

**Files:**
- Create: `app/src/main/java/com/ojnexus/feature/submissions/SubmissionCenterViewModel.kt`
- Create: `app/src/test/java/com/ojnexus/feature/submissions/SubmissionCenterViewModelTest.kt`

**Interfaces:**
- Consumes `LuoguSubmissionCenter`.
- Produces `StateFlow<Loadable<SubmissionCenterUiState>>`.
- `SubmissionCenterUiState` contains `jobs: List<SubmissionJobEntity>`, `busyRequestIds: Set<String>`, and `actionError: SubmissionCenterActionError?`.
- `checkResult(requestId: String)` ignores duplicate calls while that request ID is busy and delegates exactly one explicit refresh.

- [ ] **Step 1: Write failing tests** for ready rows, empty rows, duplicate-query suppression, and action-error recovery using a fake `LuoguSubmissionCenter` and a real `MutableStateFlow`.
- [ ] **Step 2: Run only `SubmissionCenterViewModelTest` and verify expected failures.**
- [ ] **Step 3: Implement the ViewModel with `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)` and per-request busy tracking.**
- [ ] **Step 4: Run the focused ViewModel test until all cases pass.**
- [ ] **Step 5: Commit with `feat: add local submission center state`.**

### Task 3: Compose screen and resources

**Files:**
- Create: `app/src/main/java/com/ojnexus/feature/submissions/SubmissionCenterScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Test: `app/src/test/java/com/ojnexus/core/resources/LocalizationResourceTest.kt` (only if a key check needs extension)

**Interfaces:**
- Screen parameters: `onBack: () -> Unit`, `onOpenWorkspace: (String) -> Unit`.
- Consumes `SubmissionCenterUiState`; never accesses Room or network directly.

- [ ] **Step 1: Add resource keys for title, empty/loading/error, row metadata, statuses, retry, and workspace action in both locales.**
- [ ] **Step 2: Implement the screen with existing `NexusTopBar`, `NexusSection`, `NexusTag`, spacing, and color tokens.**
- [ ] **Step 3: Render PENDING/FAILED query actions and READY score/status metadata; render no source code/input.**
- [ ] **Step 4: Run resource validation and compile the feature test source set.**
- [ ] **Step 5: Commit with `feat: add local submission center screen`.**

### Task 4: Navigation and discoverability

**Files:**
- Modify: `app/src/main/java/com/ojnexus/app/NexusApp.kt`
- Modify: `app/src/main/java/com/ojnexus/app/CommandPalette.kt`
- Modify: `app/src/main/java/com/ojnexus/feature/profile/ProfileScreen.kt`
- Modify: `app/src/main/java/com/ojnexus/OjNexusApplication.kt` only if dependency wiring is required
- Test: `app/src/test/java/com/ojnexus/app/CommandPaletteTest.kt` if command filtering/dispatch data requires coverage

**Interfaces:**
- Route: `NexusRoutes.SUBMISSIONS = "submissions"`.
- Profile callback: `ProfileScreen(onOpenSubmissions: () -> Unit = {})`.
- Command ID: `submissions`.

- [ ] **Step 1: Add a failing command-list test or route-level compile reference for the new command.**
- [ ] **Step 2: Wire Profile and command palette to `NexusRoutes.SUBMISSIONS`; wire the screen to `container.luoguSubmissionRepository`.**
- [ ] **Step 3: Pass workspace navigation using encoded `pid` only.**
- [ ] **Step 4: Run focused navigation/command tests.**
- [ ] **Step 5: Commit with `feat: wire local submission center navigation`.**

### Task 5: Documentation and release gate

**Files:**
- Modify: `docs/LUOGU_OPEN_PLATFORM.md`
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Document the new local list and manual refresh behavior and restate the no-cloud/no-main-site-auth boundary.**
- [ ] **Step 2: Run `git diff --check` and inspect changed files for secrets or source/input persistence.**
- [ ] **Step 3: Run `tools\\gradlew-local.bat clean test assembleDebug lintDebug --no-daemon --rerun-tasks --console=plain`.**
- [ ] **Step 4: Confirm test XML has zero failures/errors and the worktree is clean after commit.**
- [ ] **Step 5: Commit with `docs: document local submission center`.**
