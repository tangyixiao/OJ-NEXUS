# Problem Library 2.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver Phase 56 / v0.3.52 as a local library pulse with honest counts, a visible filter reset, and a more scannable accessible problem list.

**Architecture:** Add one pure summary function over the complete and filtered `Problem` lists. Compute it in the existing `ProblemsViewModel` combine block, keep filter and sort state local to the existing ViewModel, and render the summary/reset controls in `ProblemsScreen` without changing repository, Room, network, or navigation interfaces.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Coroutines/Flow, JUnit, existing NEXUS design tokens and localized resources.

**Spec:** `docs/superpowers/specs/2026-09-02-problem-library-2-design.md`

## Global Constraints

- Preserve the native Kotlin/Compose/Material 3 stack and single `app` module.
- Keep the existing local repository Flow as the only source of library truth; add no Room migration, network field, credential, or route.
- Use `NexusTheme`, `NexusSpacing`, `NexusRadius`, `NexusSize`, semantic tones, and named screen dimensions; no raw colors or arbitrary inline dimensions in new UI.
- Add every new UI string to both English and Simplified Chinese resources.
- Preserve search, status/judge/favorite/tag filters, sort cycling, remote catalog, add, delete, favorite, detail, loading, error, and empty behavior.
- Follow red-green-refactor: each new production behavior gets a focused test that fails for the intended missing behavior first.

---

### Task 1: Add the pure library summary contract

**Files:**
- Create: `app/src/test/java/com/ojnexus/feature/problems/ProblemLibrarySummaryTest.kt`
- Create later: `app/src/main/java/com/ojnexus/feature/problems/ProblemLibrarySummary.kt`

**Interfaces:**
- Consumes: `List<Problem>` for the complete library and `List<Problem>` for the filtered visible list.
- Produces: `ProblemLibrarySummary` and `summarizeProblemLibrary(problems, visibleProblems)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.ojnexus.feature.problems

import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.Problem
import com.ojnexus.core.model.ProblemKey
import org.junit.Assert.assertEquals
import org.junit.Test

class ProblemLibrarySummaryTest {
    private fun problem(
        id: Long,
        solved: Boolean = false,
        attemptCount: Int = 0,
        inReview: Boolean = false,
        favorite: Boolean = false,
    ) = Problem(
        id = id,
        key = ProblemKey(JudgeId.CODEFORCES, "P$id"),
        title = "Problem $id",
        difficulty = 1200,
        createdAt = id,
        updatedAt = id,
        firstSolvedAt = null,
        lastAttemptAt = null,
        attemptCount = attemptCount,
        solved = solved,
        favorite = favorite,
        sourceUrl = null,
        tags = emptyList(),
        inReview = inReview,
    )

    @Test
    fun `summary counts complete library and current visible rows`() {
        val all = listOf(
            problem(1L, solved = true, favorite = true),
            problem(2L, attemptCount = 2),
            problem(3L, solved = true, inReview = true),
        )

        assertEquals(
            ProblemLibrarySummary(total = 3, visible = 2, solved = 2, review = 1, favorites = 1),
            summarizeProblemLibrary(all, all.take(2)),
        )
    }

    @Test
    fun `summary uses derived review status and accepts empty lists`() {
        val reviewedSolved = problem(7L, solved = true, inReview = true)

        assertEquals(
            ProblemLibrarySummary(total = 1, visible = 0, solved = 1, review = 1, favorites = 0),
            summarizeProblemLibrary(listOf(reviewedSolved), emptyList()),
        )
        assertEquals(
            ProblemLibrarySummary(total = 0, visible = 0, solved = 0, review = 0, favorites = 0),
            summarizeProblemLibrary(emptyList(), emptyList()),
        )
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended missing-symbol failure**

Run: ` .\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.problems.ProblemLibrarySummaryTest --no-daemon --console=plain`

Expected: test compilation fails only because `ProblemLibrarySummary` and `summarizeProblemLibrary` do not yet exist.

- [ ] **Step 3: Implement the minimal summary function**

Create `ProblemLibrarySummary.kt`:

```kotlin
package com.ojnexus.feature.problems

import com.ojnexus.core.model.Problem

data class ProblemLibrarySummary(
    val total: Int,
    val visible: Int,
    val solved: Int,
    val review: Int,
    val favorites: Int,
)

fun summarizeProblemLibrary(
    problems: List<Problem>,
    visibleProblems: List<Problem>,
): ProblemLibrarySummary = ProblemLibrarySummary(
    total = problems.size,
    visible = visibleProblems.size,
    solved = problems.count { it.solved },
    review = problems.count { it.status == com.ojnexus.core.model.ProblemStatus.REVIEW },
    favorites = problems.count { it.favorite },
)
```

- [ ] **Step 4: Run the focused test and verify green**

Run the same focused Gradle command. Expected: `BUILD SUCCESSFUL` with two passing tests.

- [ ] **Step 5: Commit the pure summary module**

```bash
git add app/src/main/java/com/ojnexus/feature/problems/ProblemLibrarySummary.kt app/src/test/java/com/ojnexus/feature/problems/ProblemLibrarySummaryTest.kt
git commit -m "feat: add problem library summary"
```

### Task 2: Expose summary and make reset restore the default view

**Files:**
- Modify: `app/src/main/java/com/ojnexus/feature/problems/ProblemsViewModel.kt`
- Test: `app/src/test/java/com/ojnexus/feature/problems/ProblemLibrarySummaryTest.kt`

**Interfaces:**
- Consumes: `summarizeProblemLibrary(problems, visibleProblems)` and existing `ProblemFilter`/`ProblemSort`.
- Produces: `ProblemsUiState.summary` and `clearFilter()` that resets both `ProblemFilter()` and `ProblemSort.UPDATED`.

- [ ] **Step 1: Add a failing pure reset-state test**

Append to `ProblemLibrarySummaryTest.kt`:

```kotlin
@Test
fun `default view predicate is false for active filter or non-default sort`() {
    assertEquals(false, isProblemLibraryDefaultView(ProblemFilter(query = "tree"), ProblemSort.UPDATED))
    assertEquals(false, isProblemLibraryDefaultView(ProblemFilter(), ProblemSort.TITLE))
    assertEquals(true, isProblemLibraryDefaultView(ProblemFilter(), ProblemSort.UPDATED))
}
```

- [ ] **Step 2: Run the focused test and verify the missing predicate failure**

Run the focused `ProblemLibrarySummaryTest` command. Expected: compilation fails only because `isProblemLibraryDefaultView` is missing.

- [ ] **Step 3: Implement state exposure and reset behavior**

Add to `ProblemLibrarySummary.kt`:

```kotlin
fun isProblemLibraryDefaultView(filter: ProblemFilter, sort: ProblemSort): Boolean =
    filter.isDefault && sort == ProblemSort.UPDATED
```

Change `ProblemsUiState` to include `val summary: ProblemLibrarySummary`, compute `val visible = problems.applyFilterSort(f, s)` in the existing combine block, and pass `summary = summarizeProblemLibrary(problems, visible)`. Change `clearFilter()` to:

```kotlin
fun clearFilter() {
    filter.update { ProblemFilter() }
    sort.update { ProblemSort.UPDATED }
}
```

- [ ] **Step 4: Run focused tests and compile**

Run:

```text
.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.problems.ProblemLibrarySummaryTest --tests com.ojnexus.feature.problems.ProblemFilterTest --no-daemon --console=plain
.\tools\gradlew-local.bat :app:compileDebugKotlin --no-daemon --console=plain
```

Expected: both commands report `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit ViewModel state changes**

```bash
git add app/src/main/java/com/ojnexus/feature/problems/ProblemsViewModel.kt app/src/main/java/com/ojnexus/feature/problems/ProblemLibrarySummary.kt app/src/test/java/com/ojnexus/feature/problems/ProblemLibrarySummaryTest.kt
git commit -m "feat: expose problem library pulse state"
```

### Task 3: Render and localize the library pulse and reset action

**Files:**
- Modify: `app/src/main/java/com/ojnexus/feature/problems/ProblemsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`

**Interfaces:**
- Consumes: `ProblemsUiState.summary`, `isProblemLibraryDefaultView`, and `viewModel.clearFilter()`.
- Produces: localized `LIBRARY PULSE` metrics, visible result count, and accessible reset action.

- [ ] **Step 1: Add the localized resource contract**

Add matching keys with these exact values:

```xml
<!-- app/src/main/res/values/strings.xml -->
<string name="problems_section_pulse">LIBRARY PULSE</string>
<string name="problems_pulse_total">TOTAL</string>
<string name="problems_pulse_visible">VISIBLE</string>
<string name="problems_pulse_solved">SOLVED</string>
<string name="problems_pulse_review">REVIEW</string>
<string name="problems_clear_filters">CLEAR FILTERS</string>
<string name="problems_clear_filters_cd">Clear problem filters and restore default sorting</string>
<string name="problems_favorite_on_cd">Remove problem from favorites</string>
<string name="problems_favorite_off_cd">Add problem to favorites</string>
<string name="problems_delete_cd">Delete problem</string>

<!-- app/src/main/res/values-zh-rCN/strings.xml -->
<string name="problems_section_pulse">题库脉冲</string>
<string name="problems_pulse_total">总数</string>
<string name="problems_pulse_visible">当前显示</string>
<string name="problems_pulse_solved">已解决</string>
<string name="problems_pulse_review">复习中</string>
<string name="problems_clear_filters">清除筛选</string>
<string name="problems_clear_filters_cd">清除题库筛选并恢复默认排序</string>
<string name="problems_favorite_on_cd">取消收藏题目</string>
<string name="problems_favorite_off_cd">收藏题目</string>
<string name="problems_delete_cd">删除题目</string>
```

- [ ] **Step 2: Add the pulse composable and wire it into LibraryContent**

Insert `LibraryPulse(summary, showClear, onClear)` directly after `SearchField` and before existing filter chips. Animate the four integer values with `animateIntAsState`; use `NexusMetric` for `TOTAL`, `VISIBLE`, `SOLVED`, and `REVIEW`, and use `NexusMotion.DURATION_NORMAL` or `snap()` according to `NexusTheme.reduceMotion`.

Use the reset action only when `!isProblemLibraryDefaultView(uiState.filter, uiState.sort)`. Its modifier must include `clickable(role = Role.Button, onClickLabel = stringResource(R.string.problems_clear_filters_cd))` and `semantics { contentDescription = stringResource(R.string.problems_clear_filters_cd) }`, and its label must come from `R.string.problems_clear_filters`.

- [ ] **Step 3: Make the top and results counts use the filtered visible value**

Keep the full library total inside `TOTAL`; change the top-bar count and the library result readout to use `uiState.summary.visible` so an active filter is reflected honestly. Keep the existing `problems_empty_title` and `problems_no_match` branches unchanged.

- [ ] **Step 4: Run resource and focused summary tests**

Run:

```text
.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.core.resources.LocalizationResourceTest --tests com.ojnexus.feature.problems.ProblemLibrarySummaryTest --no-daemon --console=plain
.\tools\gradlew-local.bat :app:compileDebugKotlin --no-daemon --console=plain
```

Expected: both commands report `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the pulse UI and resources**

```bash
git add app/src/main/java/com/ojnexus/feature/problems/ProblemsScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml
git commit -m "feat: add problem library pulse UI"
```

### Task 4: Polish rows and localize the new UI

**Files:**
- Modify: `app/src/main/java/com/ojnexus/feature/problems/ProblemsScreen.kt`

**Interfaces:**
- Consumes: semantic status tones and existing `ProblemStatus.labelRes()`.
- Produces: localized pulse/reset copy and explicit favorite/delete content descriptions.

- [ ] **Step 1: Add status rail and target semantics**

Define a named `ProblemStatusRailWidth` dimension and place a `Box` with `width(ProblemStatusRailWidth)` and `height(ProblemRowHeight)` before the row content. Resolve its color through `problem.status.tone().foregroundColor(colors)`. Add localized `onClickLabel` and `semantics` descriptions to the favorite and delete targets; keep visible status text in `NexusTag`.

- [ ] **Step 2: Run localization and focused regression tests**

Run:

```text
.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.core.resources.LocalizationResourceTest --tests com.ojnexus.feature.problems.ProblemLibrarySummaryTest --tests com.ojnexus.feature.problems.ProblemFilterTest --no-daemon --console=plain
.\tools\gradlew-local.bat :app:compileDebugKotlin --no-daemon --console=plain
```

Expected: both commands report `BUILD SUCCESSFUL` with no missing-resource errors.

- [ ] **Step 3: Commit row polish**

```bash
git add app/src/main/java/com/ojnexus/feature/problems/ProblemsScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml
git commit -m "feat: polish problem library rows"
```

### Task 5: Version, documentation, and full verification

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `README.md`
- Modify: `docs/ROADMAP.md`
- Create: `docs/releases/v0.3.52.md`

- [ ] **Step 1: Advance package identity**

Change only the current identity to `versionCode = 52` and `versionName = "0.3.52"`; preserve all historical release text.

- [ ] **Step 2: Document Phase 56**

Add bilingual entries describing `LIBRARY PULSE`, filtered visible counts, `CLEAR FILTERS`, row accessibility/status rail polish, reduced-motion behavior, and the unchanged local-only boundary. Add a v0.3.52 release note with the same verified scope.

- [ ] **Step 3: Run the complete verification gate serially**

Run each command separately:

```text
.\tools\gradlew-local.bat test --no-daemon --console=plain
.\tools\gradlew-local.bat assembleDebug --no-daemon --console=plain
.\tools\gradlew-local.bat lintDebug --no-daemon --console=plain
```

Expected: all commands exit 0 and report `BUILD SUCCESSFUL`.

- [ ] **Step 4: Inspect the real APK**

If `D:\Android\platform-tools\adb.exe devices` reports an online emulator, install the generated APK with `adb install -r`, launch `com.ojnexus/.MainActivity`, open the Library scope, verify the pulse, apply a filter, clear it, inspect the row layout, and open one problem detail. Capture a screenshot and check for `FATAL EXCEPTION|Process: com.ojnexus` in logcat.

- [ ] **Step 5: Audit and commit release metadata**

Run `git diff --check`, `git status --short --branch`, `git log --oneline -10`, `rg -n "versionCode|versionName" app/build.gradle.kts`, and `Get-FileHash app/build/outputs/apk/debug/app-debug.apk -Algorithm SHA256`. Confirm the worktree is clean and then commit:

```bash
git add app/build.gradle.kts README.md docs/ROADMAP.md docs/releases/v0.3.52.md
git commit -m "release: prepare problem library v0.3.52"
```
