# SESSION MOMENTUM Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make an active training session move deterministically from result logging to the next pending problem and an honest completion state.

**Architecture:** Keep session rows, attempt counts, solved state, and progress in the existing Room-backed `SessionViewModel` flows. Add a pure `SessionMomentum` derivation for the ephemeral selection/feedback projection, then let Compose render a small `NOW / NEXT / LEFT` rail and invoke existing repository actions.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Coroutines/Flow, Room, JUnit, AndroidX Compose UI tests.

**Spec:** `docs/superpowers/specs/2026-09-04-next-three-phases-design.md` (Phase 70).

## Global Constraints

- Use only existing Kotlin/Compose/Room architecture; do not add a database migration.
- Keep the app local-first: a quick verdict is not an OJ submission and must not start network work.
- Keep selection and momentum feedback ephemeral; Room remains the source of truth for problem state.
- Route every new UI string and content description through English and Simplified Chinese resources.
- Use `core/designsystem` tokens only; no arbitrary feature-level colors, dimensions, shapes, gradients, glow, emoji, or looping animation.
- Preserve Loading, Success, Empty, Error, Offline, accessibility, reduced-motion, and large-font behavior.
- Finish with unit tests, Compose tests, connected smoke coverage, `assembleDebug`, `assembleRelease`, `lintDebug`, `git diff --check`, and release documentation.

### Task 1: Define the pure momentum projection

**Files:**
- Create: `app/src/main/java/com/ojnexus/feature/training/SessionMomentum.kt`
- Test: `app/src/test/java/com/ojnexus/feature/training/SessionMomentumTest.kt`

**Interfaces:**
- Consumes: `TrainingSession?`, `List<SessionProblem>`, `Long elapsedMs`, and `Long? selectedProblemId`.
- Produces: `SessionMomentumState(now: SessionProblem?, next: SessionProblem?, pendingCount: Int, isComplete: Boolean, selectedProblemId: Long?, remainingTargetMs: Long?)` and `deriveSessionMomentum(...)`.

- [ ] **Step 1: Write failing tests for ordering and completion.**

  Cover an ordered list with solved, attempted, and pending rows; assert that `next` is the first
  unsolved row in session order, `pendingCount` counts unsolved rows, `isComplete` is true only
  when the list is non-empty and every row is solved, a removed selection is normalized to null,
  and a target duration returns a non-negative remaining time. Also cover a null/empty session and
  an empty problem list.

- [ ] **Step 2: Run the focused test and verify it fails for the missing projection.**

  Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.ojnexus.feature.training.SessionMomentumTest --no-daemon --console=plain` with `JAVA_HOME` set to `D:\Android Studio\jbr`.

  Expected: compilation failure because `SessionMomentumState` and `deriveSessionMomentum` do not exist.

- [ ] **Step 3: Implement the minimal pure projection.**

  Normalize selection against the supplied list, use the first `!solved` row as `next`, count
  `!solved`, mark completion only for a non-empty all-solved list, and derive remaining target
  time from `targetDurationMin` and `elapsedMs` with a zero floor. Do not inspect network, attempt
  timestamps, or global library data.

- [ ] **Step 4: Run the focused test and verify it passes.**

  Run the same Gradle test command; expected result is PASS with all projection cases green.

- [ ] **Step 5: Commit the isolated projection.**

  Run:

  ```text
  git add app/src/main/java/com/ojnexus/feature/training/SessionMomentum.kt app/src/test/java/com/ojnexus/feature/training/SessionMomentumTest.kt
  git commit -m "feat: derive session momentum state"
  ```

### Task 2: Advance ephemeral selection after a successful result

**Files:**
- Modify: `app/src/main/java/com/ojnexus/feature/training/SessionViewModel.kt`
- Test: `app/src/test/java/com/ojnexus/feature/training/SessionViewModelTest.kt` (create if no focused ViewModel test exists)

**Interfaces:**
- Consumes: existing `ProblemRepository.addAttempt(problemId, verdict)` and the pure momentum projection.
- Produces: `lastLoggedProblemId: StateFlow<Long?>`, `actionInFlight: StateFlow<Boolean>`, and a success-only result event or state consumed by `SessionScreen`.

- [ ] **Step 1: Inspect the existing ViewModel test fixtures and write failing action-state tests.**

  Assert that a successful `logAttempt` clears `SessionActionError`, emits the logged problem ID,
  and returns `actionInFlight` to false; a failure keeps the selected ID and exposes the mapped
  `SessionActionError`; a second call while the first is running is ignored.

- [ ] **Step 2: Run the focused ViewModel tests and verify the new assertions fail.**

  Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.ojnexus.feature.training.SessionViewModelTest --no-daemon --console=plain`.

- [ ] **Step 3: Add guarded action state without changing repository contracts.**

  Set `actionInFlight` before calling `addAttempt`, use `try/finally` to reset it, emit the logged
  problem ID only for `DataResult.Success`, and retain the existing error mapping. Keep all work
  in `viewModelScope`; do not add WorkManager or network calls.

- [ ] **Step 4: Run the focused ViewModel tests and the existing session tests.**

  Run the focused command and `.\gradlew.bat :app:testDebugUnitTest --tests com.ojnexus.feature.training.SessionCommandDeckTest --no-daemon --console=plain`; expected result is PASS.

- [ ] **Step 5: Commit the action-state change.**

  Run:

  ```text
  git add app/src/main/java/com/ojnexus/feature/training/SessionViewModel.kt app/src/test/java/com/ojnexus/feature/training/SessionViewModelTest.kt
  git commit -m "feat: advance session actions safely"
  ```

### Task 3: Render the NOW / NEXT / LEFT rail

**Files:**
- Modify: `app/src/main/java/com/ojnexus/feature/training/SessionScreen.kt`
- Modify: `app/src/main/java/com/ojnexus/feature/training/SessionCommandDeck.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/java/com/ojnexus/core/designsystem/NexusDimens.kt` only if a shared rail height token is needed
- Test: `app/src/androidTest/java/com/ojnexus/feature/training/SessionMomentumComposeTest.kt`

**Interfaces:**
- Consumes: `SessionMomentumState`, `SessionViewModel.lastLoggedProblemId`, existing elapsed/session flows, and existing `onOpenProblem`.
- Produces: an accessible `SessionMomentumRail` with `NOW`, `NEXT`, `LEFT`, and `OPEN NEXT` semantics.

- [ ] **Step 1: Write failing Compose tests.**

  Render the rail under `NexusTheme(reduceMotion = true)` and assert visible `NOW`, `NEXT`, and
  `LEFT` labels, pending count, the next problem identity, an accessible `OPEN NEXT` action, the
  all-solved completion label, and a failure state that leaves the current selection visible.

- [ ] **Step 2: Run the isolated instrumentation test and verify it fails.**

  Run: `.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.ojnexus.feature.training.SessionMomentumComposeTest" --no-daemon --console=plain`.

  Expected: compilation or assertion failure because the rail is not yet present.

- [ ] **Step 3: Implement the rail using existing design tokens.**

  Add the rail beside the existing progress board/quick-result area. After a successful result,
  normalize selection to the derived next row in a `LaunchedEffect` keyed by the Room-backed
  problems and logged ID. If no pending row remains, expose a completion label and the existing
  finish/debrief controls. Disable only the rail action while an attempt is in flight; do not
  hide the queue or change `OPEN` behavior.

- [ ] **Step 4: Add localized strings and content descriptions.**

  Add English and Simplified Chinese resources for labels, next-action text, completion text,
  pending count, and the `OPEN NEXT` description. Keep verdict and problem identity data-driven.

- [ ] **Step 5: Run the isolated Compose test and the existing command-deck test.**

  Run the runner-filtered connected command above and the Task 1 unit command; expected result is PASS with reduced-motion
  content and accessibility semantics intact.

- [ ] **Step 6: Commit the UI slice.**

  Run:

  ```text
  git add app/src/main/java/com/ojnexus/feature/training/SessionScreen.kt app/src/main/java/com/ojnexus/feature/training/SessionCommandDeck.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml app/src/main/java/com/ojnexus/core/designsystem/NexusDimens.kt app/src/androidTest/java/com/ojnexus/feature/training/SessionMomentumComposeTest.kt
  git commit -m "feat: add session momentum rail"
  ```

### Task 4: Run the phase gates and publish the release candidate

**Files:**
- Modify: `app/build.gradle.kts` (`versionCode=68`, `versionName="0.3.68"`)
- Modify: `README.md` and `docs/ROADMAP.md` current-status sections
- Create: `docs/releases/v0.3.68.md`
- Create: `docs/releases/SHA256SUMS-v0.3.68.txt`

**Interfaces:**
- Consumes: the completed Phase 70 UI and repository behavior.
- Produces: a verified, signed `OJ-NEXUS-v0.3.68.apk`, checksum, tag, and GitHub Release.

- [ ] **Step 1: Run the full verification gates.**

  Run `test`, `assembleDebug`, `assembleRelease`, `lintDebug`, and the full connected suite with
  `JAVA_HOME=D:\Android Studio\jbr`. Install the exact signed Release APK on the configured
  emulator, verify package identity, cold launch, and absence of `FATAL EXCEPTION`.

- [ ] **Step 2: Record only actual evidence.**

  Record the final APK SHA-256, test counts, device/API, package identity, install result, and
  runtime result in `docs/releases/v0.3.68.md`; write the same hash and APK filename to the
  checksum file.

- [ ] **Step 3: Review the release diff and secret boundary.**

  Run `git diff --check`, inspect `git status --short`, scan the staged diff for credentials,
  and confirm no `local.properties`, keystore, cookie, password, or unrelated user change is
  staged.

- [ ] **Step 4: Commit, push, tag, and publish.**

  Use `release: prepare session momentum v0.3.68`, push the current branch and `v0.3.68` tag
  without force, then create the GitHub Release with the signed APK and checksum asset. Verify
  the public assets return HTTP 200 and the uploaded APK digest equals the local hash.

## Later plans

Phase 71 OJ CONNECTOR CENTER and Phase 72 DASHBOARD COMMAND SURFACE remain in the approved
design scope. They receive separate implementation plans after Phase 70 is verified so their
repository and UI interfaces are based on the actual completed momentum state rather than guesses.
