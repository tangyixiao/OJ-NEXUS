# Review Run Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with review checkpoints.

**Goal:** Build a continuous local review run that lets users clear all reviews due at launch from one focused Android screen.

**Architecture:** Capture a stable ordered list of due review IDs with pure Kotlin helpers. A dedicated `ReviewRunViewModel` owns current-item, completed-item, outcome, and error state while `ReviewRepository` remains the only persistence writer. A no-argument Navigation Compose route hosts the new screen; individual queue/debrief navigation remains unchanged.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Navigation Compose, Room/Flow, lifecycle ViewModel, JUnit, Robolectric.

**Spec:** `docs/superpowers/specs/2026-09-03-review-run-design.md`

## Global Constraints

- Keep the app native Kotlin/Compose/Material 3; do not add Flutter, React Native, WebView, or a new module.
- Preserve local-first behavior: no network, migration, background task, credential, or new review result.
- Use `dueDayIndex <= todayEpochDay` at launch and sort by `dueAt`, then `problemId`; never add newly due items mid-run.
- Keep all user-visible strings in both `values/strings.xml` and `values-zh-rCN/strings.xml`.
- Use existing NEXUS BLUE design tokens, restrained radii, 120–300ms meaningful motion, and reduced-motion fallbacks.
- Do not put raw colors, arbitrary layout literals, gradients, glow, emoji, or marketing copy in feature UI.
- Run focused tests after each task and the full `test assembleDebug lintDebug` gate before completion.

---

### Task 1: Pure due capture and progress model

**Files:**
- Create: `app/src/main/java/com/ojnexus/feature/training/ReviewRun.kt`
- Test: `app/src/test/java/com/ojnexus/feature/training/ReviewRunTest.kt`

**Interfaces:**
- Produces `captureReviewRunQueue(queue: List<ReviewQueueItem>, todayEpochDay: Long): List<ReviewQueueItem>`.
- Produces `reviewRunProgress(total: Int, completed: Int): Float` clamped to `0f..1f`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `capture keeps due items in due-time then id order`() {
    val queue = listOf(
        review(id = 3L, day = 10L, dueAt = 40L),
        review(id = 1L, day = 9L, dueAt = 80L),
        review(id = 2L, day = 9L, dueAt = 40L),
        review(id = 4L, day = 11L, dueAt = 10L),
    )

    assertEquals(listOf(2L, 3L, 1L), captureReviewRunQueue(queue, todayEpochDay = 10L).map { it.problemId })
}

@Test
fun `progress is bounded and zero when no run exists`() {
    assertEquals(0f, reviewRunProgress(0, 0))
    assertEquals(0.5f, reviewRunProgress(4, 2))
    assertEquals(1f, reviewRunProgress(4, 9))
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./tools/gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.training.ReviewRunTest --no-daemon --console=plain`

Expected: compilation fails because `captureReviewRunQueue` and `reviewRunProgress` do not exist.

- [ ] **Step 3: Implement the minimal pure helpers**

```kotlin
fun captureReviewRunQueue(queue: List<ReviewQueueItem>, todayEpochDay: Long): List<ReviewQueueItem> =
    queue.filter { it.dueDayIndex <= todayEpochDay }
        .sortedWith(compareBy<ReviewQueueItem> { it.dueAt }.thenBy { it.problemId })

fun reviewRunProgress(total: Int, completed: Int): Float =
    if (total <= 0) 0f else (completed.toFloat() / total).coerceIn(0f, 1f)
```

- [ ] **Step 4: Run the focused tests to verify they pass**

Run the same Gradle command. Expected: all `ReviewRunTest` tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ojnexus/feature/training/ReviewRun.kt app/src/test/java/com/ojnexus/feature/training/ReviewRunTest.kt
git commit -m "feat: add review run queue model"
```

### Task 2: Review run ViewModel

**Files:**
- Create: `app/src/main/java/com/ojnexus/feature/training/ReviewRunViewModel.kt`
- Test: `app/src/test/java/com/ojnexus/feature/training/ReviewRunViewModelTest.kt`

**Interfaces:**
- `ReviewRunUiState` exposes `captured`, `completedCount`, `active`, `completedItem`, `lastOutcome`, and `error`.
- `ReviewRunViewModel(reviewRepository: ReviewRepository, clock: Clock)` exposes `state: StateFlow<Loadable<ReviewRunUiState>>`, `record(result: ReviewResult)`, and `next()`.

- [ ] **Step 1: Write the failing state tests**

Use the existing in-memory Room setup and fixed UTC clock used by `TrainingRepositorySessionTest`. Insert two due review rows, construct the ViewModel, wait for its ready state, call `record(ReviewResult.PASS)`, and assert the first item is completed while the second remains active. Record the second result and assert `completedCount == 2`, `active == null`, and the last outcome is retained. Add a missing-review case that asserts the current item remains active and `error` is non-null after `record`.

- [ ] **Step 2: Run the ViewModel tests to verify they fail**

Run: `./tools/gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.training.ReviewRunViewModelTest --no-daemon --console=plain`

Expected: compilation fails because `ReviewRunViewModel` and `ReviewRunUiState` do not exist.

- [ ] **Step 3: Implement the minimal ViewModel**

Collect `reviewRepository.observeQueue()` once into a private state flow. On the first emission, call `captureReviewRunQueue`, retain its IDs and set the first ID active. `record` calls `completeReview` for the active ID; on success add the ID to the completed set, retain the item and `ReviewOutcome`, and clear the error; on failure preserve the current ID and store the repository error message. `next` clears the result and selects the first captured ID not in the completed set. Derive `ReviewRunUiState` from the queue and these state flows so the active item is always current.

- [ ] **Step 4: Run the ViewModel tests to verify they pass**

Run the same command. Expected: PASS for advancement, final completion, and mutation failure retention.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ojnexus/feature/training/ReviewRunViewModel.kt app/src/test/java/com/ojnexus/feature/training/ReviewRunViewModelTest.kt
git commit -m "feat: add review run state machine"
```

### Task 3: Compose screen and navigation integration

**Files:**
- Create: `app/src/main/java/com/ojnexus/feature/training/ReviewRunScreen.kt`
- Modify: `app/src/main/java/com/ojnexus/feature/training/TrainingScreen.kt`
- Modify: `app/src/main/java/com/ojnexus/app/NexusApp.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Test: `app/src/test/java/com/ojnexus/feature/training/ReviewRunUiLayoutTest.kt`
- Test: `app/src/test/java/com/ojnexus/app/NexusRoutesTest.kt`

**Interfaces:**
- `ReviewRunScreen(onDone: () -> Unit)` is the no-argument route surface.
- Training's `START NEXT` invokes `onOpenReviewRun: () -> Unit`; individual `onOpenReview` callbacks remain unchanged.

- [ ] **Step 1: Write failing source/layout tests**

Assert that `TrainingScreen.kt` contains `onOpenReviewRun`, `ReviewPulse` invokes it, the app route contains `review-run`, and `ReviewRunScreen.kt` contains `PASS`, `HARD`, `FAIL`, `SKIP` resource usage, `reviewRunProgress`, `animateContentSize`, and a reduced-motion branch.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./tools/gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.training.ReviewRunUiLayoutTest --tests com.ojnexus.app.NexusRoutesTest --no-daemon --console=plain`

Expected: assertions fail because the route, callback, and screen do not exist.

- [ ] **Step 3: Implement the screen and route**

Add the route constant and route test. Change only the Training pulse callback used by `START NEXT` to navigate to the new route. Build the screen with a top progress section (`DONE`, `LEFT`, `TOTAL` and a determinate rail), current-problem section, two-row outcome controls, result/next section, empty state, and localized error text. Use `animateFloatAsState` for the rail and `animateContentSize` for active/result changes; use `snap()` when `NexusTheme.reduceMotion` is true. Give action controls explicit semantics and keep touch targets at least the existing tokenized action height.

- [ ] **Step 4: Run focused tests and compile**

Run the same test command. Expected: PASS and successful Kotlin/resource compilation.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ojnexus/feature/training/ReviewRunScreen.kt app/src/main/java/com/ojnexus/feature/training/TrainingScreen.kt app/src/main/java/com/ojnexus/app/NexusApp.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml app/src/test/java/com/ojnexus/feature/training/ReviewRunUiLayoutTest.kt app/src/test/java/com/ojnexus/app/NexusRoutesTest.kt
git commit -m "feat: add continuous review run screen"
```

### Task 4: Release, runtime verification, and documentation

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `README.md`
- Modify: `docs/ROADMAP.md`
- Create: `docs/releases/v0.3.63.md`

- [ ] **Step 1: Advance current package identity**

Set only the current identity to `versionCode = 63` and `versionName = "0.3.63"`; preserve all historical release text.

- [ ] **Step 2: Run the full gate**

Run: `./tools/gradlew-local.bat test assembleDebug lintDebug --no-daemon --console=plain`

Expected: `BUILD SUCCESSFUL` for all requested tasks.

- [ ] **Step 3: Install and exercise the run**

Install `app/build/outputs/apk/debug/app-debug.apk` on `emulator-5554`, verify Android reports `versionCode=63` and `versionName=0.3.63`, open Training, tap `START NEXT`, record two outcomes from existing due local reviews, verify `DONE/LEFT` changes and final `RUN COMPLETE`, then capture `app/build/reports/ojnexus-review-run-v063.png`. Clear logcat before launch and require `NO_APP_FATAL_EXCEPTION` afterward.

- [ ] **Step 4: Record evidence and inspect the final diff**

Write the exact build result, package identity, APK SHA-256, runtime observations, PID, crash result, and screenshot path to `docs/releases/v0.3.63.md`. Run `git diff --check`, `git status --short --branch`, and inspect `git diff origin/codex/phase-5-arena...HEAD` for secrets or unrelated changes.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts README.md docs/ROADMAP.md docs/releases/v0.3.63.md
git commit -m "release: prepare v0.3.63"
```

