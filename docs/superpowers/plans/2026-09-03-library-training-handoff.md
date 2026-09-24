# Problem Library to Training Handoff Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a one-shot `BUILD FROM VIEW` action that opens the existing training form with the currently visible local problem IDs preselected.

**Architecture:** Extract the local problem-ID handoff and its blue-rail action into `ProblemLibraryTraining.kt`. `NexusApp` owns only an in-memory pending ID list while navigating from Problems to Training; `TrainingScreen` consumes it once and passes it to the existing `NewSessionDialog`. The existing `TrainingViewModel` transaction remains the only creator of sessions.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Navigation Compose, StateFlow, Room-backed existing repositories, JUnit, Compose AndroidTest.

**Spec:** `docs/superpowers/specs/2026-09-03-library-training-handoff-design.md`

## Global Constraints

- Keep the native Kotlin + Compose + Material 3 stack; do not add a new module or third-party UI library.
- Keep UI strings in both `res/values/strings.xml` and `res/values-zh-rCN/strings.xml`.
- Put new colors, spacing, radii, and rail dimensions in `core/designsystem`; use the single NEXUS BLUE accent.
- Do not add network requests, Room/DataStore fields, migrations, credentials, background work, compiler behavior, or automatic submission.
- Preserve the existing editable confirmation flow: no session is created until the existing `START` action is confirmed.
- Respect `NexusTheme.reduceMotion`, meaningful 120–300ms motion, touch targets, and non-color-only state communication.
- Finish with `test`, `assembleDebug`, `lintDebug`, and `connectedDebugAndroidTest` successful; verify the final APK version and runtime behavior.

### Task 1: Add the handoff model boundary and blue-rail action

**Files:**
- Create: `app/src/main/java/com/ojnexus/feature/problems/ProblemLibraryTraining.kt`
- Create: `app/src/test/java/com/ojnexus/feature/problems/ProblemLibraryTrainingTest.kt`
- Create: `app/src/androidTest/java/com/ojnexus/feature/problems/ProblemLibraryTrainingComposeTest.kt`
- Modify: `app/src/main/java/com/ojnexus/core/designsystem/NexusDimens.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`

**Interfaces:**
- Produces `internal fun buildTrainingProblemIds(problems: List<Problem>): List<Long>`; it returns `problems.map { it.id }.distinct()` and keeps first-seen order.
- Produces `@Composable internal fun LibraryTrainingActionRail(problemCount: Int, onClick: () -> Unit)`; it renders nothing for `problemCount <= 0`, otherwise exposes `BUILD FROM VIEW`, the count hint, `Role.Button`, a complete click label, a NEXUS BLUE rail, and reduce-motion-safe `animateContentSize`.

- [ ] **Step 1: Write failing unit tests** for empty input, duplicate IDs, and stable first-seen order in `ProblemLibraryTrainingTest.kt`.
- [ ] **Step 2: Run the focused unit tests** with `.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.problems.ProblemLibraryTrainingTest --no-daemon --console=plain`; expect unresolved symbols until implementation exists.
- [ ] **Step 3: Implement the pure ID extractor and rail** using `NexusTheme`, `NexusSpacing`, `NexusRadius`, `NexusSize`, `NexusMotion`, and resource-backed strings only.
- [ ] **Step 4: Add a Compose AndroidTest** that renders the rail in `NexusTheme`, asserts the title/count/click semantics, clicks the merged action node, and verifies the callback.
- [ ] **Step 5: Run the focused unit test and AndroidTest**; expect both to pass before moving to navigation.
- [ ] **Step 6: Commit** with `feat: add library training handoff action`.

### Task 2: Connect the local Problems view to top-level Training navigation

**Files:**
- Modify: `app/src/main/java/com/ojnexus/feature/problems/ProblemsScreen.kt`
- Modify: `app/src/main/java/com/ojnexus/app/NexusApp.kt`
- Modify: `app/src/test/java/com/ojnexus/app/DashboardNavigationTest.kt` or a new `app/src/test/java/com/ojnexus/app/TrainingHandoffNavigationTest.kt`

**Interfaces:**
- Extend `ProblemsScreen` with `onBuildTraining: (List<Long>) -> Unit = {}`.
- Pass `onBuildTraining` into `LibraryContent`; after `LibraryPulse`, render `LibraryTrainingActionRail` only when `uiState.problems` is non-empty and call `onBuildTraining(buildTrainingProblemIds(uiState.problems))`.
- Add `var pendingTrainingProblemIds by remember { mutableStateOf<List<Long>?>(null) }` in `NexusApp`.
- Wire the Problems callback to set the pending list and call `navController.navigateToTopLevel(NexusDestination.TRAINING.route)`.
- Pass `pendingTrainingProblemIds.orEmpty()` and a clear callback into `TrainingScreen` in Task 3; do not encode IDs into a navigation route.

- [ ] **Step 1: Add a failing source-level/wiring regression test** asserting Problems uses the visible list and invokes the training callback, while the remote branch does not render the rail.
- [ ] **Step 2: Run the focused test** and observe the expected failure for the missing callback/wiring.
- [ ] **Step 3: Add the callback and library-only rail wiring** without changing remote catalog behavior.
- [ ] **Step 4: Add the in-memory pending ID state and top-level navigation callback** in `NexusApp`.
- [ ] **Step 5: Run the focused tests plus existing route tests** and verify they pass.
- [ ] **Step 6: Commit** with `feat: route library view into training`.

### Task 3: Consume the handoff in the existing editable training form

**Files:**
- Modify: `app/src/main/java/com/ojnexus/feature/training/TrainingScreen.kt`
- Create: `app/src/test/java/com/ojnexus/feature/training/TrainingLaunchPrefillTest.kt`
- Modify: `app/src/main/java/com/ojnexus/app/NexusApp.kt`

**Interfaces:**
- Extend `TrainingScreen` with `initialProblemIds: List<Long> = emptyList()` and `onInitialProblemIdsConsumed: () -> Unit = {}`.
- `TrainingContent` consumes non-empty `initialProblemIds` in a `LaunchedEffect`, opens `NewSessionDialog`, stores the IDs in screen-local saveable state, and calls the clear callback exactly once.
- `NewSessionDialog` receives the library IDs when `focusSprintMode` is false; focus sprint IDs retain precedence when it is true.
- The library handoff defaults to `TrainingType.PRACTICE` and `stringResource(R.string.training_library_view_tag)`; dismissing the dialog clears the local handoff IDs.

- [ ] **Step 1: Write failing tests** for initial IDs becoming the library dialog selection and for the focus-sprint precedence helper/boundary.
- [ ] **Step 2: Run the focused tests** and confirm the new boundary is not yet implemented.
- [ ] **Step 3: Add the one-shot consumption state and dialog parameters** while leaving `TrainingViewModel.startSession` untouched.
- [ ] **Step 4: Wire `NexusApp` to pass and clear `pendingTrainingProblemIds`**.
- [ ] **Step 5: Run unit tests and `connectedDebugAndroidTest`**; verify the existing Compose palette test and the new rail test remain green.
- [ ] **Step 6: Commit** with `feat: prefill training form from library view`.

### Task 4: Version, documentation, and full verification

**Files:**
- Modify: `app/build.gradle.kts` (`versionCode=66`, `versionName="0.3.66"`)
- Modify: `README.md`
- Modify: `docs/ROADMAP.md`
- Create: `docs/releases/v0.3.66.md`

**Interfaces:**
- Package identity becomes `versionName=0.3.66`, `versionCode=66`.
- Roadmap and README identify Phase 68 and accurately state the local-only one-shot behavior.
- Release notes record the final APK hash, build/test commands, emulator package version, clean-launch PID, and screenshots of the rail and prefilled Training dialog.

- [ ] **Step 1: Update version identity and release documentation** with no stale Phase 67 claims at the top of README/ROADMAP.
- [ ] **Step 2: Run `.\tools\gradlew-local.bat test assembleDebug lintDebug --no-daemon --console=plain`**; require `BUILD SUCCESSFUL`.
- [ ] **Step 3: Run `.\tools\gradlew-local.bat :app:connectedDebugAndroidTest --no-daemon --console=plain`** on Pixel 9 API 37; require all AndroidTests to pass.
- [ ] **Step 4: Install the exact final APK**, verify `versionCode=66/versionName=0.3.66`, clear logcat, launch, and verify no `FATAL EXCEPTION`.
- [ ] **Step 5: Manually filter the local library to `CODEFORCES 1029E`, tap `BUILD FROM VIEW`, assert Training opens with the problem preselected, cancel and confirm no session was created, then repeat and press `START` to verify the existing transaction path.
- [ ] **Step 6: Capture final screenshots, compute a stable SHA-256 three times, update `docs/releases/v0.3.66.md`, run `git diff --check`, and commit** with `release: prepare library training handoff v0.3.66`.
- [ ] **Step 7: Check `git status --short --branch`, inspect the final log/diff, and obtain a fresh read-only review before claiming the phase complete.
