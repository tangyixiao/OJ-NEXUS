# Dashboard Command Deck Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver Phase 54 / v0.3.49 as a local-first Dashboard command deck with derived summary readouts, four working shortcuts, restrained motion, and complete localization.

**Architecture:** Keep repositories and Room schemas unchanged. Add a small pure Dashboard summary module for derivation and countdown buckets, feed it from the existing ViewModel flows plus a cancellable minute clock tick, and let `NexusApp` own all navigation. Recompose only the Dashboard presentation while retaining every existing detailed section and empty/error behavior.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Navigation Compose, Coroutines/Flow, JUnit, existing NEXUS design tokens.

**Spec:** `docs/superpowers/specs/2026-09-02-dashboard-command-deck-design.md`

## Global Constraints

- Preserve the native Android Kotlin/Compose/Material 3 stack and the single `app` module.
- Preserve dark-first NEXUS BLUE telemetry styling; feature code uses `NexusTheme`, `NexusSpacing`, `NexusRadius`, and `NexusSize` instead of raw visual literals.
- Keep data local-first and judge-agnostic; use existing Room/Flow repositories only.
- Add no API endpoint, database migration, credential, password, cookie, session, CSRF state, cloud service, cross-device sync, compiler, custom runner, background submission, or automatic POST retry.
- Put every new UI string in both `app/src/main/res/values/strings.xml` and `app/src/main/res/values-zh-rCN/strings.xml`.
- Preserve the current Luogu setup action, detailed Dashboard sections, and all unrelated worktree materials.
- Production behavior is written after a failing test; run the focused test after each red/green cycle.

## File map

- Create `app/src/main/java/com/ojnexus/feature/dashboard/DashboardCommandDeck.kt` for the pure summary model, derivation, and countdown bucket formatter.
- Modify `app/src/main/java/com/ojnexus/feature/dashboard/DashboardViewModel.kt` to expose the summary and a cancellable minute-aligned clock tick.
- Modify `app/src/main/java/com/ojnexus/feature/dashboard/DashboardScreen.kt` to add the summary readout, command deck, callbacks, and reduced-motion transitions.
- Modify `app/src/main/java/com/ojnexus/app/NexusApp.kt` to wire Dashboard callbacks to existing top-level routes and define the tested action-to-route mapping.
- Modify both localized `strings.xml` files with Dashboard command/deck and countdown labels.
- Create `app/src/test/java/com/ojnexus/feature/dashboard/DashboardCommandDeckTest.kt` for pure derivation and countdown boundaries.
- Create `app/src/test/java/com/ojnexus/app/DashboardNavigationTest.kt` for shortcut route mapping.
- Modify `app/build.gradle.kts` to advance the package identity to versionCode 49 / versionName 0.3.49.
- Create `docs/releases/v0.3.49.md` and modify `README.md` and `docs/ROADMAP.md` with the bilingual Phase 54 release note.

### Task 1: Prove Dashboard summary derivation and countdown boundaries

**Files:**
- Create: `app/src/test/java/com/ojnexus/feature/dashboard/DashboardCommandDeckTest.kt`
- Create later: `app/src/main/java/com/ojnexus/feature/dashboard/DashboardCommandDeck.kt`

**Interfaces:**
- Produces `DashboardSummary`, `DashboardCountdown`, `deriveDashboardSummary(...)`, and `dashboardCountdown(...)` for the ViewModel and screen.

- [ ] **Step 1: Write the failing derivation tests**

```kotlin
package com.ojnexus.feature.dashboard

import com.ojnexus.core.database.entity.ContestEntity
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.ReviewQueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardCommandDeckTest {
    @Test
    fun `summary counts only due reviews and enabled judges`() {
        val reviews = listOf(
            ReviewQueueItem(1L, "A", JudgeId.CODEFORCES, 800, 0, 1_000L, 10L, null),
            ReviewQueueItem(2L, "B", JudgeId.CODEFORCES, 900, 1, 1_000L, 11L, null),
            ReviewQueueItem(3L, "C", JudgeId.CODEFORCES, 1_000, 0, 1_000L, 12L, null),
        )

        val summary = deriveDashboardSummary(
            reviews = reviews,
            todayEpochDay = 11L,
            enabledJudgeCount = 2,
            solvedThisWeek = 3,
            contests = emptyList(),
            nowSeconds = 1_000L,
        )

        assertEquals(DashboardSummary(2, 2, 3, null), summary)
    }

    @Test
    fun `summary selects earliest future contest and clamps remaining time`() {
        val contests = listOf(
            ContestEntity("CODEFORCES", "late", "Late", "CONTEST", "BEFORE", false, 3_600L, 1_500L, null, null, null, 1_000L),
            ContestEntity("CODEFORCES", "early", "Early", "CONTEST", "BEFORE", false, 3_600L, 1_200L, null, null, null, 1_000L),
            ContestEntity("CODEFORCES", "past", "Past", "CONTEST", "FINISHED", false, 3_600L, 900L, null, null, null, 1_000L),
        )

        val summary = deriveDashboardSummary(
            reviews = emptyList(),
            todayEpochDay = 11L,
            enabledJudgeCount = 0,
            solvedThisWeek = 0,
            contests = contests,
            nowSeconds = 1_000L,
        )

        assertEquals(200L, summary.nextContestRemainingSeconds)
    }

    @Test
    fun `countdown returns null for no contest and zero for an expired snapshot`() {
        assertNull(dashboardCountdown(null))
        assertEquals(DashboardCountdown(days = 0L, hours = 0L, minutes = 0L), dashboardCountdown(0L))
        assertEquals(DashboardCountdown(days = 2L, hours = 3L, minutes = 4L), dashboardCountdown(2L * 86_400L + 3L * 3_600L + 4L * 60L + 59L))
        assertEquals(DashboardCountdown(days = 0L, hours = 0L, minutes = 1L), dashboardCountdown(-1L))
    }
}
```

- [ ] **Step 2: Run the focused test and verify the expected missing-symbol failure**

Run: `.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.dashboard.DashboardCommandDeckTest --no-daemon --console=plain`

Expected: FAIL because `DashboardSummary`, `ContestEntity` constructor usage, `deriveDashboardSummary`, and `dashboardCountdown` have not been added yet. If the failure is a test-compilation mistake rather than missing production symbols, correct the test before proceeding.

- [ ] **Step 3: Write the minimal pure production module**

Create `DashboardCommandDeck.kt` with these exact contracts:

```kotlin
data class DashboardSummary(
    val dueReviews: Int,
    val connectedJudges: Int,
    val solvedThisWeek: Int,
    val nextContestRemainingSeconds: Long?,
)

data class DashboardCountdown(val days: Long, val hours: Long, val minutes: Long)

fun deriveDashboardSummary(
    reviews: List<ReviewQueueItem>,
    todayEpochDay: Long,
    enabledJudgeCount: Int,
    solvedThisWeek: Int,
    contests: List<ContestEntity>,
    nowSeconds: Long,
): DashboardSummary

fun dashboardCountdown(remainingSeconds: Long?): DashboardCountdown?
```

Implement due count with `count { it.dueDayIndex <= todayEpochDay }`, clamp `enabledJudgeCount` and `solvedThisWeek` to zero, select the minimum contest start strictly greater than `nowSeconds`, and clamp remaining seconds to zero before splitting into days/hours/minutes. Do not format localized text in this pure module.

- [ ] **Step 4: Run the focused test and verify it passes**

Run the same focused Gradle command. Expected: `BUILD SUCCESSFUL` and all three `DashboardCommandDeckTest` methods pass.

- [ ] **Step 5: Commit the tested pure model**

```bash
git add app/src/main/java/com/ojnexus/feature/dashboard/DashboardCommandDeck.kt app/src/test/java/com/ojnexus/feature/dashboard/DashboardCommandDeckTest.kt
git commit -m "feat: add dashboard command summary"
```

### Task 2: Feed the summary from live local state

**Files:**
- Modify: `app/src/main/java/com/ojnexus/feature/dashboard/DashboardViewModel.kt`

**Interfaces:**
- Consumes `deriveDashboardSummary(...)` and `dashboardCountdown(...)` from Task 1.
- Produces `DashboardUiState.summary: DashboardSummary` and keeps `nowSeconds` current while subscribed.

- [ ] **Step 1: Add the failing ViewModel-level shape test**

Extend `DashboardCommandDeckTest` with a direct construction assertion that `DashboardUiState` requires and retains `summary`:

```kotlin
@Test
fun `ui state carries the command summary`() {
    val summary = DashboardSummary(1, 2, 3, 4L)
    val state = DashboardUiState(
        todayTasks = emptyList(),
        week = WeekSummary(3, 4, 5L),
        currentStreak = 1,
        longestStreak = 2,
        nextReview = null,
        recent = emptyList(),
        loadWeek = emptyList(),
        summary = summary,
        nowSeconds = 100L,
    )

    assertEquals(summary, state.summary)
}
```

- [ ] **Step 2: Run the focused test and verify the expected constructor failure**

Run the Task 1 focused command. Expected: FAIL because `DashboardUiState` does not yet expose `summary`.

- [ ] **Step 3: Implement the minimal ViewModel integration**

Add `summary` to `DashboardUiState` and add a private `clockTicks` flow that emits `clock.instant().epochSecond` immediately, then once per minute while `currentCoroutineContext().isActive`; use `delay(60_000L)`. Add the tick as the fourth input to the outer `combine`, use the tick value for contest filtering and `nowSeconds`, and pass the same due-review predicate, enabled connection count, solved-week total, and contest list to `deriveDashboardSummary`. Keep `todayEpochDay` behavior and all existing `Loadable`/error handling unchanged.

- [ ] **Step 4: Run the focused test and compile the feature**

Run: `.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.dashboard.DashboardCommandDeckTest --no-daemon --console=plain`

Expected: all summary tests pass and the Dashboard production sources compile.

- [ ] **Step 5: Commit the local-state integration**

```bash
git add app/src/main/java/com/ojnexus/feature/dashboard/DashboardViewModel.kt app/src/test/java/com/ojnexus/feature/dashboard/DashboardCommandDeckTest.kt
git commit -m "feat: expose live dashboard summary"
```

### Task 3: Add localized strings and tested shortcut routing

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/java/com/ojnexus/app/NexusApp.kt`
- Create: `app/src/test/java/com/ojnexus/app/DashboardNavigationTest.kt`

**Interfaces:**
- Produces `DashboardCommand` and `dashboardCommandRoute(DashboardCommand): String` in `com.ojnexus.app`.

- [ ] **Step 1: Write the failing route mapping test**

```kotlin
package com.ojnexus.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardNavigationTest {
    @Test
    fun `command deck uses existing destinations`() {
        assertEquals(NexusDestination.TRAINING.route, dashboardCommandRoute(DashboardCommand.TRAINING))
        assertEquals(NexusDestination.TRAINING.route, dashboardCommandRoute(DashboardCommand.REVIEW))
        assertEquals(NexusDestination.PROBLEMS.route, dashboardCommandRoute(DashboardCommand.PROBLEMS))
        assertEquals(NexusRoutes.SUBMISSIONS, dashboardCommandRoute(DashboardCommand.SUBMISSIONS))
    }
}
```

- [ ] **Step 2: Run the route test and verify the expected missing-symbol failure**

Run: `.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.app.DashboardNavigationTest --no-daemon --console=plain`

Expected: FAIL because `DashboardCommand`, `dashboardCommandRoute`, and the new Dashboard callback wiring do not exist.

- [ ] **Step 3: Add the minimal route mapping and resource strings**

Add the enum and `when` mapping in `NexusApp.kt`; in the Dashboard destination call `navigateToTopLevel(dashboardCommandRoute(command))`. Add English and Chinese resources for the command section, four action labels and content descriptions, four summary labels, and pending/days/hours/minutes countdown forms. Use resource formatting rather than concatenating localized words in Kotlin.

- [ ] **Step 4: Run the route test and resource validation**

Run the focused route command, then run `.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.core.resources.LocalizationResourceTest --no-daemon --console=plain`. Expected: both commands finish successfully with zero failures.

- [ ] **Step 5: Commit navigation and localization**

```bash
git add app/src/main/java/com/ojnexus/app/NexusApp.kt app/src/test/java/com/ojnexus/app/DashboardNavigationTest.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml
git commit -m "feat: add dashboard command routes"
```

### Task 4: Build the command-deck UI and motion treatment

**Files:**
- Modify: `app/src/main/java/com/ojnexus/feature/dashboard/DashboardScreen.kt`

**Interfaces:**
- Consumes `DashboardUiState.summary`, `dashboardCountdown`, and the four callbacks.
- Produces the visible readout and command deck while retaining all existing detailed sections.

- [ ] **Step 1: Add the new callback parameters with a compile-failing call site**

Add `onOpenTraining`, `onOpenReview`, `onOpenProblems`, and `onOpenSubmissions` to `DashboardScreen` and `DashboardContent`, then run `:app:compileDebugKotlin` before updating `NexusApp`. Expected: compile failure at the Dashboard call site, proving the new interface is active.

- [ ] **Step 2: Implement the readout and command deck using existing tokens**

Place a compact two-row readout and a two-column command grid immediately after the top spacing. Use named file-level dimensions for any new cell height/spacing. Each command cell must use `background(colors.surface)`, `border(NexusSize.dividerThickness, colors.borderStrong, NexusRadius.sm)`, `clickable(role = Role.Button, ...)`, and a `semantics` content description. Map Review to the Training callback, as the current app has no generic review route.

Render `summary.dueReviews`, `summary.solvedThisWeek`, and `summary.connectedJudges` with `formatCount`. Render the countdown by converting `summary.nextContestRemainingSeconds` with `dashboardCountdown` and selecting localized pending/day/hour/minute resources. Keep the old System Status through Training Load sections below the new deck.

- [ ] **Step 3: Add reduced-motion-aware transitions**

Use `AnimatedContent` or `animateIntAsState` for summary values and an `animateDpAsState`-style height value for training bars, with `NexusMotion.DURATION_NORMAL`. Choose immediate values when `NexusTheme.reduceMotion` is true. Do not add looping effects, shimmer, glow, gradients, or new dependencies.

- [ ] **Step 4: Wire the shell callbacks and compile**

Pass four callbacks from `NexusApp` to `DashboardScreen`; each callback navigates through the tested `DashboardCommand` mapping. Run `.\tools\gradlew-local.bat :app:compileDebugKotlin --no-daemon --console=plain`. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the UI**

```bash
git add app/src/main/java/com/ojnexus/feature/dashboard/DashboardScreen.kt app/src/main/java/com/ojnexus/app/NexusApp.kt
git commit -m "feat: add dashboard command deck UI"
```

### Task 5: Advance the phase identity and documentation

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `README.md`
- Modify: `docs/ROADMAP.md`
- Create: `docs/releases/v0.3.49.md`

- [ ] **Step 1: Update package identity**

Change only `versionCode = 48` to `49` and `versionName = "0.3.48"` to `"0.3.49"`; preserve all other Gradle configuration.

- [ ] **Step 2: Add bilingual Phase 54 documentation**

Prepend a Phase 54 status block to the README and a matching top Roadmap entry. Create `docs/releases/v0.3.49.md` describing the command readout, four existing-route shortcuts, local-only summary derivation, reduced-motion support, and the explicit non-goals. Preserve every prior phase paragraph and release note.

- [ ] **Step 3: Self-check documentation and identity**

Run `rg -n "Phase 54|第 54|0\.3\.49|TODO|TBD" README.md docs/ROADMAP.md docs/releases/v0.3.49.md app/build.gradle.kts`. Expected: the new phase/version entries are present and no TODO/TBD placeholder is introduced.

- [ ] **Step 4: Commit the release identity**

```bash
git add app/build.gradle.kts README.md docs/ROADMAP.md docs/releases/v0.3.49.md
git commit -m "release: prepare dashboard command deck v0.3.49"
```

### Task 6: Run the complete verification gate

**Files:**
- Verify: all modified files and Git history

- [ ] **Step 1: Check whitespace and repository scope**

Run `git diff --check`, `git status --short`, and `git diff HEAD~4 --stat`. Confirm only the Dashboard feature, app shell, localized resources, version metadata, and Phase 54 docs changed; preserve unrelated user files.

- [ ] **Step 2: Run focused tests**

Run:

```text
.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.dashboard.DashboardCommandDeckTest --no-daemon --console=plain
.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.app.DashboardNavigationTest --no-daemon --console=plain
```

Expected: both commands report `BUILD SUCCESSFUL` and zero failed tests.

- [ ] **Step 3: Run the full test/build/lint gate**

Run the complete required commands:

```text
.\tools\gradlew-local.bat test --no-daemon --console=plain
.\tools\gradlew-local.bat assembleDebug --no-daemon --console=plain
.\tools\gradlew-local.bat lintDebug --no-daemon --console=plain
```

Expected: all three commands exit 0 and report `BUILD SUCCESSFUL`.

- [ ] **Step 4: Audit final state before claiming delivery**

Run `git status --short --branch`, `git log --oneline -8`, and `rg -n "versionCode|versionName" app/build.gradle.kts`. Confirm the working tree is clean, the branch contains the Dashboard commits, and the identity is exactly 49 / 0.3.49. Report any failed command verbatim instead of claiming completion.
