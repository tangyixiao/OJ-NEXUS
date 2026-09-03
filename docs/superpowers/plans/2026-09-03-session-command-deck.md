# Session Command Deck Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the active training-session queue into a local command deck where a selected problem can receive a quick unified verdict and immediately refresh the existing session progress.

**Architecture:** Keep selection ephemeral inside `SessionRunningView`, expose a focused `SessionQuickActions` composable for the selected `SessionProblem`, and add `SessionViewModel.logAttempt` as the only UI-to-repository bridge. The existing `ProblemRepository.addAttempt` transaction and Room-backed `sessionProblems` flow remain authoritative; no session schema or network code changes.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Room, Coroutines/StateFlow, JUnit, Compose AndroidTest.

**Spec:** `docs/superpowers/specs/2026-09-03-session-command-deck-design.md`

## Global Constraints

- Keep the native Kotlin + Compose + Material 3 stack and the single `app` module.
- Keep session result logging local-only; do not add automatic OJ submission, network requests, credentials, migrations, or new persisted fields.
- Reuse `ProblemRepository.addAttempt(problemId, verdict)` so attempt and derived problem counters update in its existing transaction.
- Use `NexusTheme`, `NexusSpacing`, `NexusRadius`, `NexusSize`, `NexusMotion`, and `NexusTone`; add no feature-level colors or arbitrary layout literals.
- Put every new visible label and content description in both `res/values/strings.xml` and `res/values-zh-rCN/strings.xml`.
- Preserve visible verdict labels and state text; selection must not be color-only, and all actions must be button-sized and accessible.
- Use 120–300ms meaningful motion and `snap()` when `NexusTheme.reduceMotion` is enabled.
- Finish with `test`, `assembleDebug`, `lintDebug`, and `connectedDebugAndroidTest` successful, then install and manually verify the final APK.

---

### Task 1: Define the selection boundary and quick command rail

**Files:**
- Create: `app/src/main/java/com/ojnexus/feature/training/SessionCommandDeck.kt`
- Create: `app/src/test/java/com/ojnexus/feature/training/SessionCommandDeckTest.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`

**Interfaces:**
- Produces `internal fun normalizeSessionSelection(selectedProblemId: Long?, problems: List<SessionProblem>): Long?`, returning the selected ID only when it still exists in the current queue.
- Produces `@Composable internal fun SessionQuickActions(selectedProblem: SessionProblem, onLogResult: (Verdict) -> Unit)`, rendering the selected identity, `LOG RESULT`, and every `Verdict.entries` action with resource-backed labels and content descriptions.

- [ ] **Step 1: Write failing unit tests** for null selection, an ID present in the queue, and an ID removed from the queue.

```kotlin
@Test
fun removedSelectionIsCleared() {
    assertNull(normalizeSessionSelection(9L, listOf(sessionProblem(2L))))
}
```

- [ ] **Step 2: Run the focused test and observe RED**

Run `./gradlew.bat :app:testDebugUnitTest --tests com.ojnexus.feature.training.SessionCommandDeckTest --no-daemon --console=plain` and expect the missing boundary symbol before implementation.

- [ ] **Step 3: Implement the pure boundary and composable**

Use `problems.any { it.problemId == selectedProblemId }` for normalization. Build the command rail with `NexusSection`, `NexusTag`, `NexusSpacing`, `NexusTone`, `Role.Button`, `stringResource`, and `NexusTheme.reduceMotion`; chunk the eight verdicts into rows so the compact portrait layout does not overflow.

- [ ] **Step 4: Add the English and Chinese resources**

Add `session_quick_log_result`, `session_quick_selected`, and `session_quick_verdict_cd` in both locale files. The description must include the verdict and problem identity, while the visible UI must retain the verdict text.

- [ ] **Step 5: Run the focused unit test** and require `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit the boundary and rail**

```bash
git add app/src/main/java/com/ojnexus/feature/training/SessionCommandDeck.kt app/src/test/java/com/ojnexus/feature/training/SessionCommandDeckTest.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml
git commit -m "feat: add session quick result rail"
```

### Task 2: Wire quick verdicts through the existing local attempt transaction

**Files:**
- Modify: `app/src/main/java/com/ojnexus/feature/training/SessionViewModel.kt`
- Modify: `app/src/main/java/com/ojnexus/feature/training/SessionScreen.kt`
- Modify: `app/src/test/java/com/ojnexus/feature/training/SessionCommandDeckTest.kt`

**Interfaces:**
- Produces `fun SessionViewModel.logAttempt(problemId: Long, verdict: Verdict)`, launching `problemRepository.addAttempt(problemId, verdict)` in `viewModelScope` and mapping failures to the existing `SessionActionError.Generic` state.
- `SessionRunningView` owns `var selectedProblemId by rememberSaveable`; it resolves the current selection with `normalizeSessionSelection` and passes `selectedProblem` plus `viewModel::logAttempt` to `SessionQuickActions`.
- `SessionProblemQueueRow` keeps its existing `OPEN` callback and adds a separate selectable content region that updates the selected ID without writing data.

- [ ] **Step 1: Add a failing wiring test** asserting that `SessionViewModel` calls `problemRepository.addAttempt` from `logAttempt` and that `SessionRunningView` renders `SessionQuickActions` with the normalized selection.

```kotlin
@Test
fun sessionScreenWiresQuickActionsToAttemptLogging() {
    val source = Files.readString(Path.of("src/main/java/com/ojnexus/feature/training/SessionScreen.kt"))
    assertTrue(source.contains("SessionQuickActions"))
    assertTrue(source.contains("viewModel.logAttempt"))
}
```

- [ ] **Step 2: Run the focused wiring test and observe RED** before adding the bridge and screen integration.

- [ ] **Step 3: Add `logAttempt` without changing repository semantics**

```kotlin
fun logAttempt(problemId: Long, verdict: Verdict) {
    viewModelScope.launch {
        when (val result = problemRepository.addAttempt(problemId, verdict)) {
            is DataResult.Success -> actionError.value = null
            is DataResult.Failure -> actionError.value = result.error.toActionError()
        }
    }
}
```

- [ ] **Step 4: Integrate selection, rail, and error placement**

Use `rememberSaveable` for the selected ID, clear it when normalization returns null, keep it after a failed result, and render the existing `actionError` beneath `SessionQuickActions`. The rail must disappear for empty queues and when the surface is no longer active.

- [ ] **Step 5: Run unit tests and compile the debug app**

Run `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain`; require success before instrumentation work.

- [ ] **Step 6: Commit the session wiring**

```bash
git add app/src/main/java/com/ojnexus/feature/training/SessionViewModel.kt app/src/main/java/com/ojnexus/feature/training/SessionScreen.kt app/src/test/java/com/ojnexus/feature/training/SessionCommandDeckTest.kt
git commit -m "feat: wire session quick result logging"
```

### Task 3: Add UI and end-to-end regression coverage

**Files:**
- Create: `app/src/androidTest/java/com/ojnexus/feature/training/SessionCommandDeckComposeTest.kt`
- Modify: `app/src/androidTest/java/com/ojnexus/app/ProblemLibraryTrainingHandoffComposeTest.kt` only if shared setup selectors need extraction

**Interfaces:**
- The isolated Compose test renders a selected `SessionProblem` in `NexusTheme`, verifies identity and all verdict labels, clicks a verdict, and verifies the callback.
- The real Activity test seeds demo data, starts a library-created session, selects the first queue row, logs `WA`, and verifies the queue refreshes from 3 to 4 attempts while the `OPEN` action remains available.

- [ ] **Step 1: Write the isolated Compose test** for the command rail and selected problem semantics, including reduced-motion composition and empty-queue absence.

- [ ] **Step 2: Run the focused Compose test and fix only test or implementation defects**

Run `./gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.ojnexus.feature.training.SessionCommandDeckComposeTest" --no-daemon --console=plain`; require success on `Pixel_9 / API 37`.

- [ ] **Step 3: Extend the real Activity flow**

After the existing library handoff assertions, start the session, wait for `PROBLEM QUEUE`, select `CODEFORCES 1029E`, click the accessible `LOG RESULT` rail, click `WA`, and assert the first row contains `ATTEMPTS 4` plus `OPEN`.

- [ ] **Step 4: Run the full connected test suite** and require all tests to pass.

### Task 4: Version, documentation, review, and release verification

**Files:**
- Modify: `app/build.gradle.kts` (`versionCode=67`, `versionName="0.3.67"`)
- Modify: `README.md`
- Modify: `docs/ROADMAP.md`
- Create: `docs/releases/v0.3.67.md`

**Interfaces:**
- Package identity becomes `versionName=0.3.67`, `versionCode=67`.
- README and ROADMAP identify Phase 69 and state that quick verdicts are local attempt records, not automatic submissions.
- Release notes record the final APK SHA-256, full Gradle gate, connected test count/device, installed package identity, clean-launch PID, manual select → log result → refreshed queue evidence, and final screenshots.

- [ ] **Step 1: Update package identity and phase documentation**

- [ ] **Step 2: Run the full gate**

Run `./gradlew.bat test assembleDebug lintDebug --no-daemon --console=plain` and require `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run all instrumentation tests**

Run `./gradlew.bat :app:connectedDebugAndroidTest --no-daemon --console=plain` on `Pixel_9 / API 37` and record the exact passing count.

- [ ] **Step 4: Install the exact final APK and launch cleanly**

Verify Android reports `versionCode=67` and `versionName=0.3.67`, clear logcat, launch `com.ojnexus/.MainActivity`, wait for the dashboard UI, and confirm no `FATAL EXCEPTION`.

- [ ] **Step 5: Capture a session command-deck screenshot**

Save the local evidence under `app/build/reports/phase69-v067-session.png` and verify the visible selected row, `LOG RESULT`, verdict actions, and refreshed attempt count.

- [ ] **Step 6: Compute the APK hash three times, update release notes, and run `git diff --check`**

- [ ] **Step 7: Obtain a fresh read-only review, fix Critical/Important findings, commit, and verify a clean worktree**

```bash
git commit -m "release: prepare session command deck v0.3.67"
git status --short --branch
```
