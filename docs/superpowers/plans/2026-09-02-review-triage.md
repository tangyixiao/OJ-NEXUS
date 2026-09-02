# Review Triage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver Phase 55 / v0.3.50 as a local-only review triage surface with honest queue counts, local filters, and a real `START NEXT` review action.

**Architecture:** Reuse the existing `ReviewBuckets` produced by `TrainingViewModel`. Add a pure `TrainingReviewTriage` module for summary, filtering, and next-problem selection; keep filter selection in Compose state; and leave review-session navigation owned by the existing `onOpenReview(problemId)` callback.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Coroutines/Flow, JUnit, existing NEXUS design tokens and resources.

**Spec:** `docs/superpowers/specs/2026-09-02-review-triage-design.md`

## Global Constraints

- Preserve the native Kotlin/Compose/Material 3 stack and single `app` module.
- Use existing `ReviewBuckets`, repositories, and `review/{problemId}` navigation; add no API, Room migration, credential, background-work, compiler, or submission behavior.
- Use `NexusTheme`, `NexusSpacing`, `NexusRadius`, `NexusSize`, and named dimensions; no raw colors or arbitrary inline dimensions.
- Add every new UI string to English and Simplified Chinese resources.
- Preserve Session, recommendations, knowledge, Today tasks, history, all existing empty/error states, and unrelated user files.
- Follow red-green-refactor: a production behavior is added only after its focused test has failed for the intended missing behavior.

## File map

- Create `app/src/main/java/com/ojnexus/feature/training/TrainingReviewTriage.kt` for `ReviewQueueFilter`, `ReviewQueueSummary`, and pure helper functions.
- Create `app/src/test/java/com/ojnexus/feature/training/TrainingReviewTriageTest.kt` for summary, filtering, tie-breaking, and immutability tests.
- Modify `app/src/main/java/com/ojnexus/feature/training/TrainingScreen.kt` for pulse UI, filters, reduced-motion transition, and clock-consistent due highlighting.
- Modify both localized `strings.xml` files for pulse, filters, start action, and accessibility copy.
- Modify `app/build.gradle.kts`, `README.md`, `docs/ROADMAP.md`, and create `docs/releases/v0.3.50.md` for the phase identity.

### Task 1: Prove summary, next-item, and filter behavior

**Files:**
- Create: `app/src/test/java/com/ojnexus/feature/training/TrainingReviewTriageTest.kt`
- Create later: `app/src/main/java/com/ojnexus/feature/training/TrainingReviewTriage.kt`

**Interfaces:**
- Produces `ReviewQueueFilter`, `ReviewQueueSummary`, `reviewQueueSummary(ReviewBuckets)`, and `filterReviewBuckets(ReviewBuckets, ReviewQueueFilter)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.ojnexus.feature.training

import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.ReviewQueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test

class TrainingReviewTriageTest {
    private fun review(id: Long, day: Long, dueAt: Long = day): ReviewQueueItem =
        ReviewQueueItem(id, "P$id", JudgeId.CODEFORCES, 800, 0, dueAt, day, null)

    @Test
    fun `summary counts every bucket and selects earliest due problem`() {
        val buckets = ReviewBuckets(
            overdue = listOf(review(2L, 9L, 30L), review(1L, 8L, 40L)),
            dueToday = listOf(review(3L, 10L, 20L)),
            upcoming = listOf(review(4L, 11L)),
        )

        assertEquals(ReviewQueueSummary(2, 1, 1, 4, 1L), reviewQueueSummary(buckets))
    }

    @Test
    fun `summary breaks same-day ties by due time then problem id`() {
        val buckets = ReviewBuckets(
            dueToday = listOf(review(9L, 10L, 30L), review(7L, 10L, 20L), review(8L, 10L, 20L)),
        )

        assertEquals(7L, reviewQueueSummary(buckets).nextDueProblemId)
    }

    @Test
    fun `filters keep only the requested review buckets without mutating source`() {
        val buckets = ReviewBuckets(
            overdue = listOf(review(1L, 8L)),
            dueToday = listOf(review(2L, 10L)),
            upcoming = listOf(review(3L, 11L)),
        )

        val dueNow = filterReviewBuckets(buckets, ReviewQueueFilter.DUE_NOW)
        val upcoming = filterReviewBuckets(buckets, ReviewQueueFilter.UPCOMING)

        assertEquals(listOf(1L), dueNow.overdue.map { it.problemId })
        assertEquals(listOf(2L), dueNow.dueToday.map { it.problemId })
        assertEquals(emptyList<Long>(), dueNow.upcoming.map { it.problemId })
        assertEquals(emptyList<Long>(), upcoming.overdue.map { it.problemId })
        assertEquals(listOf(3L), upcoming.upcoming.map { it.problemId })
        assertNotSame(buckets, dueNow)
        assertNull(reviewQueueSummary(ReviewBuckets()).nextDueProblemId)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended missing-symbol failure**

Run: `.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.training.TrainingReviewTriageTest --no-daemon --console=plain`

Expected: FAIL during test compilation because the four triage contracts do not exist. The test must otherwise compile against the existing `ReviewBuckets` and `ReviewQueueItem` constructors.

- [ ] **Step 3: Implement the minimal pure module**

Create `TrainingReviewTriage.kt` with:

```kotlin
enum class ReviewQueueFilter { ALL, DUE_NOW, UPCOMING }

data class ReviewQueueSummary(
    val overdue: Int,
    val dueToday: Int,
    val upcoming: Int,
    val total: Int,
    val nextDueProblemId: Long?,
)

fun reviewQueueSummary(buckets: ReviewBuckets): ReviewQueueSummary
fun filterReviewBuckets(buckets: ReviewBuckets, filter: ReviewQueueFilter): ReviewBuckets
```

Compute `nextDueProblemId` from `buckets.overdue + buckets.dueToday` with `compareBy<ReviewQueueItem> { it.dueDayIndex }.thenBy { it.dueAt }.thenBy { it.problemId }`; return null when that list is empty. `ALL` returns copies of all three lists, `DUE_NOW` copies only overdue/dueToday, and `UPCOMING` copies only upcoming.

- [ ] **Step 4: Run the focused test and verify green**

Run the same focused Gradle command. Expected: `BUILD SUCCESSFUL`, four test cases completed, zero failures.

- [ ] **Step 5: Commit the pure triage module**

```bash
git add app/src/main/java/com/ojnexus/feature/training/TrainingReviewTriage.kt app/src/test/java/com/ojnexus/feature/training/TrainingReviewTriageTest.kt
git commit -m "feat: add review triage model"
```

### Task 2: Add the Training pulse and local filters

**Files:**
- Modify: `app/src/main/java/com/ojnexus/feature/training/TrainingScreen.kt`

**Interfaces:**
- Consumes `ReviewQueueSummary`, `ReviewQueueFilter`, `reviewQueueSummary`, and `filterReviewBuckets`.
- Keeps `onOpenReview(problemId: Long)` as the only review navigation callback.

- [ ] **Step 1: Add a focused compile-failing UI contract**

Change `TrainingContent` to hold `var reviewFilter by rememberSaveable { mutableStateOf(ReviewQueueFilter.ALL) }` and reference `ReviewQueueFilter` in the queue body. Run `:app:compileDebugKotlin` before adding the pulse composables; expected failure is the missing `ReviewQueueFilter` UI references only if Task 1 has not been completed, otherwise proceed to the implementation.

- [ ] **Step 2: Add the pulse above Review Queue**

After `SessionSection` and before the existing queue, derive `val reviewSummary = reviewQueueSummary(uiState.reviews)` and render a `TRAINING PULSE` `NexusSection`. Show animated counts for `overdue`, `dueToday`, and `upcoming`, plus a bordered `START NEXT` action. The action calls `onOpenReview(reviewSummary.nextDueProblemId)` only for a non-null ID; with null it is disabled and displays the localized no-due label.

- [ ] **Step 3: Add filter controls and filtered queue rendering**

Render three bordered controls for `ALL`, `DUE NOW`, and `UPCOMING`, with selected text and explicit semantics. Compute `val visibleReviews = filterReviewBuckets(uiState.reviews, reviewFilter)`. Wrap only the group rendering in `AnimatedContent(targetState = reviewFilter, ...)`; use a 200ms fade/size transition when motion is enabled and `EnterTransition.None`/`ExitTransition.None` when `NexusTheme.reduceMotion` is true. The queue count in the section trailing slot comes from `visibleReviews.overdue.size + visibleReviews.dueToday.size + visibleReviews.upcoming.size`.

- [ ] **Step 4: Make due highlighting use the ViewModel calendar snapshot**

Pass `uiState.todayEpochDay` through `QueueGroup` to `QueueRow` and replace `LocalDate.now().toEpochDay()` with that parameter. Preserve all row navigation and existing stage/difficulty text.

- [ ] **Step 5: Compile and run focused triage tests**

Run:

```text
.\tools\gradlew-local.bat :app:compileDebugKotlin --no-daemon --console=plain
.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.training.TrainingReviewTriageTest --no-daemon --console=plain
```

Expected: both commands exit 0 and report `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit the Training UI**

```bash
git add app/src/main/java/com/ojnexus/feature/training/TrainingScreen.kt
git commit -m "feat: add review triage controls"
```

### Task 3: Localize the triage surface

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`

- [ ] **Step 1: Add matching resource keys**

Add English and Chinese values for `training_section_pulse`, `training_pulse_overdue`, `training_pulse_today`, `training_pulse_upcoming`, `training_pulse_start_next`, `training_pulse_nothing_due`, `training_filter_all`, `training_filter_due_now`, `training_filter_upcoming`, and four accessibility descriptions. Use positional format arguments only where required.

- [ ] **Step 2: Run resource and focused tests**

Run `.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.core.resources.LocalizationResourceTest --no-daemon --console=plain`. Expected: `BUILD SUCCESSFUL` with zero failures and no Android resource substitution warnings.

- [ ] **Step 3: Commit localization**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml
git commit -m "feat: localize review triage"
```

### Task 4: Advance version identity and release documentation

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `README.md`
- Modify: `docs/ROADMAP.md`
- Create: `docs/releases/v0.3.50.md`

- [ ] **Step 1: Update package identity**

Change only `versionCode = 49` to `50` and `versionName = "0.3.49"` to `"0.3.50"`.

- [ ] **Step 2: Document Phase 55**

Add bilingual top entries describing the pulse, three local filters, real `START NEXT` review navigation, reduced-motion behavior, and the unchanged local-only/security boundary. Preserve all earlier phases, releases, and warnings.

- [ ] **Step 3: Run metadata checks and commit**

Run `rg -n "Phase 55|第 55|0\.3\.50|TODO|TBD" README.md docs/ROADMAP.md docs/releases/v0.3.50.md app/build.gradle.kts` and `git diff --check`. Expected: new Phase 55/version entries are present, no TODO/TBD placeholder is introduced, and whitespace validation is clean.

```bash
git add app/build.gradle.kts README.md docs/ROADMAP.md docs/releases/v0.3.50.md
git commit -m "release: prepare review triage v0.3.50"
```

### Task 5: Complete verification and runtime inspection

**Files:**
- Verify: all Phase 55 files and generated APK

- [ ] **Step 1: Run the full verification gate serially**

Run each command separately to avoid Kotlin cache contention:

```text
.\tools\gradlew-local.bat test --no-daemon --console=plain
.\tools\gradlew-local.bat assembleDebug --no-daemon --console=plain
.\tools\gradlew-local.bat lintDebug --no-daemon --console=plain
```

Expected: every command exits 0 and reports `BUILD SUCCESSFUL`.

- [ ] **Step 2: Install and inspect the real APK when an AVD is available**

Use `D:\Android\platform-tools\adb.exe devices` to confirm a device, install `app\build\outputs\apk\debug\app-debug.apk` with `adb install -r` without clearing data, launch `com.ojnexus/.MainActivity`, wait for the resumed Activity, and capture a screenshot. Confirm the pulse, filter controls, disabled/no-due state or real next action, and no `AndroidRuntime` crash.

- [ ] **Step 3: Audit final Git state**

Run `git diff --check`, `git status --short --branch`, `git log --oneline -10`, `rg -n "versionCode|versionName" app/build.gradle.kts`, and `Get-FileHash app/build/outputs/apk/debug/app-debug.apk -Algorithm SHA256`. Confirm a clean worktree, v0.3.50 identity, and no unrelated file changes before reporting the phase.
