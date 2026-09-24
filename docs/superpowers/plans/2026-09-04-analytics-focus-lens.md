# Analytics Focus Lens Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (\`- [ ]\`) syntax for tracking.

**Goal:** Add a local Analytics Pulse and selectable 14D, 30D, and 90D activity lenses for the solve-trend and training-time charts.

**Architecture:** Keep AnalyticsViewModel and AnalyticsRepository unchanged because the ViewModel already receives a zero-filled 365-day activity snapshot. Add a pure feature model that slices that snapshot and summarizes it, then let AnalyticsScreen derive the selected slice and pass it only to the activity charts while all-time reports remain intact.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, JUnit, existing ActivityPolicy, NexusSection/NexusMetric/NexusTag components, NexusMotion tokens.

**Spec:** \`docs/superpowers/specs/2026-09-04-analytics-focus-lens-design.md\`

## Global Constraints

- Native Android Kotlin + Jetpack Compose Material 3; do not add Flutter, React Native, Electron, or WebView.
- Keep AnalyticsViewModel, AnalyticsRepository, Room schema, network sync, rating history, all-time totals, and navigation unchanged.
- UI remains dark-first with one NEXUS BLUE accent, restrained radii, hairline separators, named dimensions, and meaningful 120–300ms motion.
- Feature code must not use raw Color(0xFF...), arbitrary inline .dp/.sp literals, or direct RoundedCornerShape(...); use existing design-system tokens.
- Every new UI string must be added to both app/src/main/res/values/strings.xml and app/src/main/res/values-zh-rCN/strings.xml.
- Use the existing ActivityPolicy.isActive definition for active-day counts.
- Do not create fake activity, ratings, training time, credentials, passwords, cookies, sessions, migrations, network fields, or submission behavior.
- Release identity for this phase is versionCode=55 and versionName="0.3.55".

---

### Task 1: Pure Activity Window Model

**Files:**
- Create: \`app/src/test/java/com/ojnexus/feature/analytics/AnalyticsFocusLensTest.kt\`
- Create: \`app/src/main/java/com/ojnexus/feature/analytics/AnalyticsFocusLens.kt\`
- Reference: \`app/src/main/java/com/ojnexus/core/domain/StreakCalculator.kt\`
- Reference: \`app/src/main/java/com/ojnexus/core/domain/DayActivity.kt\`

**Interfaces:**
- Consumes \`List<DayActivity>\`.
- Produces \`AnalyticsWindow\`, \`AnalyticsWindowSummary\`, \`analyticsWindowDays(days, window)\`, and \`summarizeAnalyticsWindow(days)\`.

- [ ] **Step 1: Write failing tests**

Use this fixture:

~~~kotlin
private fun day(
    index: Long,
    solved: Int = 0,
    attempts: Int = 0,
    reviews: Int = 0,
    trainingMs: Long = 0,
) = DayActivity(
    dayIndex = index,
    solved = solved,
    attempts = attempts,
    reviewsCompleted = reviews,
    trainingMs = trainingMs,
)
~~~

Add tests for suffix lengths, shorter/empty sources, ordering, and the summary active-day policy:

~~~kotlin
@Test
fun windowsReturnOrderedSuffixAndCopyShortSources() {
    val source = (1L..100L).map { day(it) }

    val last14 = analyticsWindowDays(source, AnalyticsWindow.DAYS_14)
    val last30 = analyticsWindowDays(source, AnalyticsWindow.DAYS_30)
    val last90 = analyticsWindowDays(source, AnalyticsWindow.DAYS_90)
    val shortSource = listOf(day(1), day(2))

    assertEquals(14, last14.size)
    assertEquals(87L, last14.first().dayIndex)
    assertEquals(100L, last14.last().dayIndex)
    assertEquals(30, last30.size)
    assertEquals(71L, last30.first().dayIndex)
    assertEquals(90, last90.size)
    assertEquals(11L, last90.first().dayIndex)
    assertEquals(shortSource, analyticsWindowDays(shortSource, AnalyticsWindow.DAYS_90))
    assertNotSame(shortSource, analyticsWindowDays(shortSource, AnalyticsWindow.DAYS_90))
    assertTrue(analyticsWindowDays(emptyList(), AnalyticsWindow.DAYS_14).isEmpty())
}

@Test
fun summarySumsMetricsAndUsesActivityPolicyForActiveDays() {
    val summary = summarizeAnalyticsWindow(
        listOf(
            day(1, solved = 2, attempts = 3, reviews = 0, trainingMs = 120_000),
            day(2, solved = 0, attempts = 2, reviews = 0, trainingMs = 1_800_000),
            day(3, solved = 0, attempts = 0, reviews = 1, trainingMs = 0),
        ),
    )

    assertEquals(
        AnalyticsWindowSummary(
            solved = 2,
            attempts = 5,
            activeDays = 3,
            trainingMs = 1_920_000,
        ),
        summary,
    )
}

@Test
fun emptySummaryHasZeroValues() {
    assertEquals(
        AnalyticsWindowSummary(solved = 0, attempts = 0, activeDays = 0, trainingMs = 0),
        summarizeAnalyticsWindow(emptyList()),
    )
}
~~~

- [ ] **Step 2: Run the focused test and verify the red failure**

~~~powershell
.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.analytics.AnalyticsFocusLensTest --no-daemon --console=plain
~~~

Expected result: compilation fails because the four new model symbols are absent.

- [ ] **Step 3: Implement the minimal pure model**

Create:

~~~kotlin
package com.ojnexus.feature.analytics

import com.ojnexus.core.domain.ActivityPolicy
import com.ojnexus.core.domain.DayActivity

enum class AnalyticsWindow(val days: Int) {
    DAYS_14(14),
    DAYS_30(30),
    DAYS_90(90),
}

data class AnalyticsWindowSummary(
    val solved: Int,
    val attempts: Int,
    val activeDays: Int,
    val trainingMs: Long,
)

fun analyticsWindowDays(
    days: List<DayActivity>,
    window: AnalyticsWindow,
): List<DayActivity> {
    val count = window.days
    return if (days.size <= count) days.toList() else days.takeLast(count)
}

fun summarizeAnalyticsWindow(days: List<DayActivity>): AnalyticsWindowSummary =
    AnalyticsWindowSummary(
        solved = days.sumOf { it.solved },
        attempts = days.sumOf { it.attempts },
        activeDays = days.count(ActivityPolicy::isActive),
        trainingMs = days.sumOf { it.trainingMs },
    )
~~~

- [ ] **Step 4: Run the focused test and verify green**

Run the same focused command. Expected result: BUILD SUCCESSFUL with every window and summary test passing.

- [ ] **Step 5: Commit the model**

~~~powershell
git add app/src/main/java/com/ojnexus/feature/analytics/AnalyticsFocusLens.kt app/src/test/java/com/ojnexus/feature/analytics/AnalyticsFocusLensTest.kt
git diff --cached --check
git commit -m "feat: add analytics focus lens model"
~~~

### Task 2: Analytics Pulse and Window Controls

**Files:**
- Modify: \`app/src/main/java/com/ojnexus/feature/analytics/AnalyticsScreen.kt\`
- Reference: Task 1 APIs and existing \`TrendSection\`/\`TrainingTimeSection\`.

**Interfaces:**
- Consumes \`AnalyticsWindow\`, \`analyticsWindowDays\`, and \`summarizeAnalyticsWindow\`.
- Changes \`TrendSection\` and \`TrainingTimeSection\` inputs from \`AnalyticsUiState\` to \`List<DayActivity>\`; all other section contracts stay unchanged.

- [ ] **Step 1: Add selected-window state and derived data**

Inside the non-empty branch of \`AnalyticsContent\`, add:

~~~kotlin
var selectedWindow by rememberSaveable { mutableStateOf(AnalyticsWindow.DAYS_14) }
val activityDays = analyticsWindowDays(state.heatmapDays, selectedWindow)
val activitySummary = summarizeAnalyticsWindow(activityDays)
~~~

Import \`animateContentSize\`, \`animateIntAsState\`, \`snap\`, \`tween\`, \`rememberSaveable\`, \`setValue\`, \`NexusMotion\`, and \`NexusMetric\` while retaining all existing chart imports.

- [ ] **Step 2: Render the pulse before the heatmap**

Insert \`AnalyticsPulse(activitySummary, selectedWindow) { selectedWindow = it }\` after the scrollable column starts and before \`HeatmapSection(state)\`. The pulse contains four weighted \`NexusMetric\` values:

~~~kotlin
NexusMetric(
    label = stringResource(R.string.analytics_pulse_solved),
    value = formatCount(animatedSolved),
    modifier = Modifier.weight(1f),
)
NexusMetric(
    label = stringResource(R.string.analytics_pulse_attempts),
    value = formatCount(animatedAttempts),
    modifier = Modifier.weight(1f),
)
NexusMetric(
    label = stringResource(R.string.analytics_pulse_active_days),
    value = formatCount(animatedActiveDays),
    modifier = Modifier.weight(1f),
)
NexusMetric(
    label = stringResource(R.string.analytics_pulse_training),
    value = formatDuration(summary.trainingMs / 60_000),
    modifier = Modifier.weight(1.3f),
)
~~~

Animate the first three values with \`animateIntAsState\` using \`NexusMotion.DURATION_NORMAL\`; use \`snap()\` when \`NexusTheme.reduceMotion\` is true. The training value uses the existing duration formatter and updates without a fabricated intermediate value.

- [ ] **Step 3: Add accessible 14D/30D/90D controls**

Below the pulse metrics, add three weighted \`NexusTag\` controls mapped to \`AnalyticsWindow.DAYS_14\`, \`DAYS_30\`, and \`DAYS_90\`. Each uses its localized label resource, selected accent styling, \`Role.Button\`, localized \`onClickLabel\`, and a content description. Selecting a control updates only \`selectedWindow\`.

- [ ] **Step 4: Feed the selected slice into activity charts**

Change the screen calls to:

~~~kotlin
TrendSection(activityDays)
~~~

and:

~~~kotlin
TrainingTimeSection(activityDays)
~~~

Update those functions to use \`days\` for sums, max values, bar loops, and date labels. Keep HeatmapSection on \`state.heatmapDays\`, TotalsSection on \`state.totals\`, rating sections, verdicts, performance, judge breakdown, difficulty, and all empty-state logic unchanged.

- [ ] **Step 5: Add reduced-motion-safe chart transitions**

Apply \`animateContentSize\` to the chart section modifiers using \`NexusMotion.DURATION_NORMAL\` with \`snap()\` under reduce motion. Use the existing \`NexusRadius\`, \`NexusSpacing\`, and named chart dimensions; do not introduce raw feature-level colors or dimensions.

- [ ] **Step 6: Run focused tests and compile**

~~~powershell
.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.analytics.AnalyticsFocusLensTest --no-daemon --console=plain
.\tools\gradlew-local.bat :app:compileDebugKotlin --no-daemon --console=plain
~~~

Expected result: both commands end with BUILD SUCCESSFUL.

- [ ] **Step 7: Commit the screen behavior**

~~~powershell
git add app/src/main/java/com/ojnexus/feature/analytics/AnalyticsScreen.kt
git diff --cached --check
git commit -m "feat: add analytics focus controls"
~~~

### Task 3: Localized Analytics Copy

**Files:**
- Modify: \`app/src/main/res/values/strings.xml\`
- Modify: \`app/src/main/res/values-zh-rCN/strings.xml\`

**Interfaces:**
- Supplies every label and accessibility description introduced by Task 2.

- [ ] **Step 1: Add English resources**

~~~xml
<string name="analytics_section_pulse">ANALYTICS PULSE</string>
<string name="analytics_pulse_solved">SOLVED</string>
<string name="analytics_pulse_attempts">ATTEMPTS</string>
<string name="analytics_pulse_active_days">ACTIVE DAYS</string>
<string name="analytics_pulse_training">TRAINING</string>
<string name="analytics_window_14d">14D</string>
<string name="analytics_window_30d">30D</string>
<string name="analytics_window_90d">90D</string>
<string name="analytics_window_14d_cd">Show the last 14 days</string>
<string name="analytics_window_30d_cd">Show the last 30 days</string>
<string name="analytics_window_90d_cd">Show the last 90 days</string>
~~~

- [ ] **Step 2: Add Simplified Chinese resources**

~~~xml
<string name="analytics_section_pulse">分析脉冲</string>
<string name="analytics_pulse_solved">已解决</string>
<string name="analytics_pulse_attempts">尝试次数</string>
<string name="analytics_pulse_active_days">活跃天数</string>
<string name="analytics_pulse_training">训练时长</string>
<string name="analytics_window_14d">14天</string>
<string name="analytics_window_30d">30天</string>
<string name="analytics_window_90d">90天</string>
<string name="analytics_window_14d_cd">显示最近 14 天</string>
<string name="analytics_window_30d_cd">显示最近 30 天</string>
<string name="analytics_window_90d_cd">显示最近 90 天</string>
~~~

- [ ] **Step 3: Run resource and lint checks**

~~~powershell
.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.analytics.AnalyticsFocusLensTest --no-daemon --console=plain
.\tools\gradlew-local.bat :app:lintDebug --no-daemon --console=plain
~~~

Expected result: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit localization**

~~~powershell
git add app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml
git diff --cached --check
git commit -m "feat: localize analytics focus lens"
~~~

### Task 4: Version and Release Documentation

**Files:**
- Modify: \`app/build.gradle.kts\`
- Modify: \`README.md\`
- Modify: \`docs/ROADMAP.md\`
- Create: \`docs/releases/v0.3.55.md\`

**Interfaces:**
- Publishes versionCode=55/versionName=0.3.55 and documents the local analytics lens boundary.

- [ ] **Step 1: Update version identity**

~~~kotlin
versionCode = 55
versionName = "0.3.55"
~~~

- [ ] **Step 2: Add release documentation**

Add a bilingual Phase 59 entry at the top of README.md and docs/ROADMAP.md describing ANALYTICS
PULSE, 14D/30D/90D controls, selected trend/training charts, preserved 365-day heatmap and
all-time sections, and reduce-motion behavior. Create docs/releases/v0.3.55.md with Highlights,
Verification, and Scope sections. Verification is populated from Task 5's actual outputs.

- [ ] **Step 3: Validate metadata**

~~~powershell
rg -n "0\.3\.55|versionCode|Analytics Focus Lens|ANALYTICS PULSE|分析脉冲" README.md docs/ROADMAP.md docs/releases/v0.3.55.md app/build.gradle.kts
git diff --check
~~~

Expected result: all release identity and feature terms are present with no diff-check errors.

- [ ] **Step 4: Commit release metadata**

~~~powershell
git add app/build.gradle.kts README.md docs/ROADMAP.md docs/releases/v0.3.55.md
git diff --cached --check
git commit -m "release: prepare analytics focus lens v0.3.55"
~~~

### Task 5: Full Verification and Emulator Review

**Files:**
- Inspect: \`app/build/outputs/apk/debug/app-debug.apk\`
- Create/inspect: \`app/build/reports/ojnexus-analytics-v055.png\`
- Create/inspect: \`app/build/reports/ojnexus-analytics-v055-30d.png\`
- Modify: \`docs/releases/v0.3.55.md\`

**Interfaces:**
- Verifies the complete release candidate from Tasks 1–4 without changing external state.

- [ ] **Step 1: Run the complete test suite**

~~~powershell
.\tools\gradlew-local.bat test --no-daemon --console=plain
~~~

Expected result: BUILD SUCCESSFUL with no failed tests.

- [ ] **Step 2: Build and lint serially**

~~~powershell
.\tools\gradlew-local.bat assembleDebug --no-daemon --console=plain
.\tools\gradlew-local.bat lintDebug --no-daemon --console=plain
~~~

Expected result: each command ends with BUILD SUCCESSFUL.

- [ ] **Step 3: Install and inspect Analytics**

Install app/build/outputs/apk/debug/app-debug.apk on the available Pixel_9 emulator. Open Analytics from
the existing bottom navigation and verify:

1. ANALYTICS PULSE appears before the heatmap with SOLVED, ATTEMPTS, ACTIVE DAYS, and TRAINING.
2. 14D is selected initially; tapping 30D and 90D updates the pulse and the solve/training chart
   data without changing the 365-day heatmap or all-time sections.
3. Count and chart transitions remain readable and do not overlap; the controls expose visible labels.
4. If the local analytics database is empty, verify the existing global empty screen instead and do
   not fabricate activity.

Capture the initial and 30D views at the two report paths.

- [ ] **Step 4: Verify package, hash, and crash output**

~~~powershell
$adb = "D:\Android\platform-tools\adb.exe"
& $adb shell pm dump com.ojnexus | Select-String -Pattern "versionCode|versionName"
Get-FileHash app/build/outputs/apk/debug/app-debug.apk -Algorithm SHA256
& $adb logcat -d -v brief | Select-String -Pattern "FATAL EXCEPTION|Process: com\.ojnexus"
~~~

Expected result: installed package reports 55/0.3.55, a SHA-256 is recorded, and the crash search
returns no app fatal exception.

- [ ] **Step 5: Record verification and commit**

Write the actual successful commands, APK hash, package identity, screenshot paths, selected-window
observations, and crash result into docs/releases/v0.3.55.md, then:

~~~powershell
git add docs/releases/v0.3.55.md
git diff --cached --check
git commit -m "docs: record analytics focus lens verification"
git status --short --branch
~~~

Expected result: the final worktree is clean with only intentional Phase 59 commits ahead of the
previous release.
