# Contest Command Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (\`- [ ]\`) syntax for tracking.

**Goal:** Turn the existing contest list into a local command surface with contest pulse metrics, phase filters, and an accessible shortcut to the next upcoming contest.

**Architecture:** Keep contest acquisition, Room entities, repositories, countdown calculation, and navigation unchanged. Add a pure presentation model that summarizes and filters the existing \`ContestCenterUiState\`, then let \`ContestCenterScreen\` render the pulse and filtered groups from that model.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, StateFlow, JUnit, existing OJ NEXUS design-system tokens and \`NexusSection\`/\`NexusMetric\`/\`NexusTag\` primitives.

**Spec:** \`docs/superpowers/specs/2026-09-02-contest-command-center-design.md\`

## Global Constraints

- Native Android Kotlin + Jetpack Compose Material 3; do not add Flutter, React Native, Electron, or WebView.
- Keep the existing single \`app\` module, judge adapter boundary, local-first data flow, repository behavior, and contest-focus navigation route unchanged.
- UI remains dark-first with one NEXUS BLUE accent, restrained 4–12dp radii, hairline separators, named design-system dimensions, and meaningful 120–300ms motion.
- Feature code must not use raw \`Color(0xFF...)\`, arbitrary inline \`.dp\`/\`.sp\` literals, or direct \`RoundedCornerShape(...)\`; use existing design-system tokens or named screen constants.
- Every new UI string must be added to both \`app/src/main/res/values/strings.xml\` and \`app/src/main/res/values-zh-rCN/strings.xml\`.
- Do not add fake contests, credentials, passwords, cookies, sessions, network fields, database migrations, background submission, or automatic retry behavior.
- Every task ends with a focused test/build check and a logical commit; final verification must include \`test\`, \`assembleDebug\`, \`lintDebug\`, APK installation, and emulator inspection.
- Release identity for this phase is \`versionCode=53\` and \`versionName="0.3.53"\`.

---

### Task 1: Contest Summary and Phase Filter Model

**Files:**
- Create: \`app/src/test/java/com/ojnexus/feature/contests/ContestCommandCenterTest.kt\`
- Create: \`app/src/main/java/com/ojnexus/feature/contests/ContestCommandCenter.kt\`
- Reference: \`app/src/main/java/com/ojnexus/feature/contests/ContestCenterViewModel.kt\`

**Interfaces:**
- Consumes the existing \`ContestCenterUiState\` and \`ContestRow\` types from \`ContestCenterViewModel.kt\`.
- Produces \`ContestPhaseFilter\`, \`ContestCenterSummary\`, \`summarizeContestCenter(state: ContestCenterUiState)\`, and \`filterContestCenter(state: ContestCenterUiState, filter: ContestPhaseFilter)\`.

- [ ] **Step 1: Write failing unit tests**

Add a local \`row\` helper that constructs every required \`ContestRow\` field:

~~~kotlin
private fun row(
    id: String,
    judge: JudgeId,
    start: Long,
    phase: ContestTimeState,
    countdown: Long = 0,
) = ContestRow(
    contestId = id,
    name = "Contest $id",
    judge = judge,
    startTimeSeconds = start,
    durationSeconds = 60,
    phase = phase,
    countdownSeconds = countdown,
    rawPhase = phase.name,
)
~~~

Test counts and earliest selection, deterministic same-time tie-breaking, empty upcoming behavior, and every phase filter:

~~~kotlin
@Test
fun summaryCountsGroupsAndSelectsEarliestUpcoming() {
    val live = row("live", JudgeId.CODEFORCES, 300, ContestTimeState.LIVE)
    val first = row("first", JudgeId.ATCODER, 100, ContestTimeState.UPCOMING, countdown = 50)
    val later = row("later", JudgeId.LUOGU, 200, ContestTimeState.UPCOMING, countdown = 150)
    val recent = row("recent", JudgeId.CODEFORCES, 50, ContestTimeState.ENDED)

    val summary = summarizeContestCenter(
        ContestCenterUiState(
            upcoming = listOf(later, first),
            live = listOf(live),
            recent = listOf(recent),
        ),
    )

    assertEquals(1, summary.live)
    assertEquals(2, summary.upcoming)
    assertEquals(1, summary.recent)
    assertEquals(4, summary.total)
    assertEquals(first, summary.nextUpcoming)
}

@Test
fun summaryBreaksUpcomingTiesByJudgeThenContestId() {
    val codeforcesLaterId = row("z", JudgeId.CODEFORCES, 100, ContestTimeState.UPCOMING)
    val atcoder = row("a", JudgeId.ATCODER, 100, ContestTimeState.UPCOMING)
    val codeforcesEarlierId = row("a", JudgeId.CODEFORCES, 100, ContestTimeState.UPCOMING)

    val summary = summarizeContestCenter(
        ContestCenterUiState(
            upcoming = listOf(codeforcesLaterId, atcoder, codeforcesEarlierId),
            live = emptyList(),
            recent = emptyList(),
        ),
    )

    assertEquals(codeforcesEarlierId, summary.nextUpcoming)
}

@Test
fun summaryHasNoNextContestWhenUpcomingIsEmpty() {
    val summary = summarizeContestCenter(
        ContestCenterUiState(upcoming = emptyList(), live = emptyList(), recent = emptyList()),
    )

    assertEquals(0, summary.total)
    assertNull(summary.nextUpcoming)
}

@Test
fun phaseFiltersKeepOnlyTheSelectedGroup() {
    val live = row("live", JudgeId.CODEFORCES, 300, ContestTimeState.LIVE)
    val upcoming = row("upcoming", JudgeId.ATCODER, 100, ContestTimeState.UPCOMING)
    val recent = row("recent", JudgeId.LUOGU, 50, ContestTimeState.ENDED)
    val source = ContestCenterUiState(
        upcoming = listOf(upcoming),
        live = listOf(live),
        recent = listOf(recent),
    )

    val all = filterContestCenter(source, ContestPhaseFilter.ALL)
    val liveOnly = filterContestCenter(source, ContestPhaseFilter.LIVE)
    val upcomingOnly = filterContestCenter(source, ContestPhaseFilter.UPCOMING)
    val recentOnly = filterContestCenter(source, ContestPhaseFilter.RECENT)

    assertEquals(source, all)
    assertEquals(listOf(live), liveOnly.live)
    assertTrue(liveOnly.upcoming.isEmpty())
    assertTrue(liveOnly.recent.isEmpty())
    assertEquals(listOf(upcoming), upcomingOnly.upcoming)
    assertTrue(upcomingOnly.live.isEmpty())
    assertTrue(upcomingOnly.recent.isEmpty())
    assertEquals(listOf(recent), recentOnly.recent)
    assertTrue(recentOnly.live.isEmpty())
    assertTrue(recentOnly.upcoming.isEmpty())
    assertEquals(listOf(live), source.live)
    assertEquals(listOf(upcoming), source.upcoming)
    assertEquals(listOf(recent), source.recent)
    assertNotSame(source, liveOnly)
}
~~~

Use the existing \`JudgeId\` and \`ContestTimeState\` imports from the contests package.

- [ ] **Step 2: Run the focused test to verify it fails**

Run:

~~~powershell
.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.contests.ContestCommandCenterTest --no-daemon --console=plain
~~~

Expected result: compilation fails because \`ContestPhaseFilter\`, \`ContestCenterSummary\`, \`summarizeContestCenter\`, and \`filterContestCenter\` do not exist yet.

- [ ] **Step 3: Write the minimal pure implementation**

Create the following API and behavior:

~~~kotlin
enum class ContestPhaseFilter { ALL, LIVE, UPCOMING, RECENT }

data class ContestCenterSummary(
    val live: Int,
    val upcoming: Int,
    val recent: Int,
    val total: Int,
    val nextUpcoming: ContestRow?,
)

fun summarizeContestCenter(state: ContestCenterUiState): ContestCenterSummary {
    val next = state.upcoming.minWithOrNull(
        compareBy<ContestRow> { it.startTimeSeconds ?: Long.MAX_VALUE }
            .thenBy { it.judge.ordinal }
            .thenBy { it.contestId },
    )
    return ContestCenterSummary(
        live = state.live.size,
        upcoming = state.upcoming.size,
        recent = state.recent.size,
        total = state.live.size + state.upcoming.size + state.recent.size,
        nextUpcoming = next,
    )
}

fun filterContestCenter(
    state: ContestCenterUiState,
    filter: ContestPhaseFilter,
): ContestCenterUiState = when (filter) {
    ContestPhaseFilter.ALL -> state.copy()
    ContestPhaseFilter.LIVE -> ContestCenterUiState(live = state.live, upcoming = emptyList(), recent = emptyList())
    ContestPhaseFilter.UPCOMING -> ContestCenterUiState(live = emptyList(), upcoming = state.upcoming, recent = emptyList())
    ContestPhaseFilter.RECENT -> ContestCenterUiState(live = emptyList(), upcoming = emptyList(), recent = state.recent)
}
~~~

- [ ] **Step 4: Run the focused test to verify it passes**

Run the same focused Gradle command. Expected result: \`BUILD SUCCESSFUL\`, with all contest command-center tests passing.

- [ ] **Step 5: Commit the model**

~~~powershell
git add app/src/main/java/com/ojnexus/feature/contests/ContestCommandCenter.kt app/src/test/java/com/ojnexus/feature/contests/ContestCommandCenterTest.kt
git diff --cached --check
git commit -m "feat: add contest command model"
~~~

### Task 2: Contest Pulse and Local Phase Controls

**Files:**
- Modify: \`app/src/main/java/com/ojnexus/feature/contests/ContestCenterScreen.kt\`
- Reference: existing \`NexusSection\`, \`NexusMetric\`, \`NexusTag\`, \`NexusMotion\`, and the current \`ContestRowView\`.

**Interfaces:**
- Consumes \`ContestCenterSummary\`, \`ContestPhaseFilter\`, \`summarizeContestCenter\`, and \`filterContestCenter\` from Task 1.
- Preserves \`onOpenFocus(judgeId: String, contestId: String)\` and the existing judge filter callback.

- [ ] **Step 1: Add local derived state**

Inside \`ContestCenterScreen\`, retain the ViewModel-owned judge filter and add:

~~~kotlin
var phaseFilter by rememberSaveable { mutableStateOf(ContestPhaseFilter.ALL) }
val summary = summarizeContestCenter(rows)
val visibleRows = filterContestCenter(rows, phaseFilter)
~~~

Use \`visibleRows\` only for group rendering and empty-state decisions; use \`summary\` for the pulse so counts remain global to the current judge selection.

- [ ] **Step 2: Add the pulse section**

Render a \`NexusSection\` titled with \`R.string.contest_section_pulse\` immediately below judge controls. It contains four compact \`NexusMetric\` readouts for \`summary.live\`, \`summary.upcoming\`, \`summary.recent\`, and the formatted countdown from \`summary.nextUpcoming?.countdownSeconds\`. When no upcoming row exists, the NEXT value is the localized \`contest_pulse_no_upcoming\` label rather than a fabricated number.

Use \`animateIntAsState\` for the three counts only when reduce-motion is disabled; use raw values when it is enabled. Preserve the existing named design-system dimensions and avoid adding arbitrary feature-level color, shape, or spacing literals.

- [ ] **Step 3: Add the next-contest action**

Below the metrics, add a bordered button labeled \`OPEN NEXT\`. When \`summary.nextUpcoming == null\`, render the disabled \`NO UPCOMING\` state. When enabled, invoke exactly:

~~~kotlin
summary.nextUpcoming?.let { onOpenFocus(it.judge.id, it.contestId) }
~~~

Attach the localized content description \`contest_pulse_open_next_cd\` or \`contest_pulse_no_upcoming_cd\` according to state, while keeping the visible label state-bearing.

- [ ] **Step 4: Add phase controls and single-tree transition**

Below the pulse, add accessible controls for \`ALL\`, \`LIVE\`, \`UPCOMING\`, and \`RECENT\`. Each control uses visible localized text, selected styling from existing NEXUS accent primitives, \`Role.Button\`, and localized click-label/content-description semantics. Clicking a control only updates \`phaseFilter\`.

Wrap the phase-group column in \`animateContentSize\` using the existing \`NexusMotion\` duration when reduce-motion is disabled and \`snap()\` when it is enabled. Render the existing \`ContestGroup\` functions from \`visibleRows\`. If the global \`rows\` is empty, retain \`contest_empty\`; if only \`visibleRows\` is empty because of a phase filter, show \`contest_phase_empty\` and leave controls available.

- [ ] **Step 5: Run focused tests and compile**

Run serially:

~~~powershell
.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.contests.ContestCommandCenterTest --no-daemon --console=plain
.\tools\gradlew-local.bat :app:compileDebugKotlin --no-daemon --console=plain
~~~

Expected result: \`BUILD SUCCESSFUL\`; no contest-center compiler diagnostics.

- [ ] **Step 6: Commit the UI behavior**

~~~powershell
git add app/src/main/java/com/ojnexus/feature/contests/ContestCenterScreen.kt
git diff --cached --check
git commit -m "feat: add contest command center controls"
~~~

### Task 3: Localized Contest Command Copy

**Files:**
- Modify: \`app/src/main/res/values/strings.xml\`
- Modify: \`app/src/main/res/values-zh-rCN/strings.xml\`

**Interfaces:**
- Supplies the exact string resources used by Task 2; no feature code contains new user-facing literals.

- [ ] **Step 1: Add English resources**

Add these entries in the contest resource section:

~~~xml
<string name="contest_section_pulse">CONTEST PULSE</string>
<string name="contest_pulse_live">LIVE</string>
<string name="contest_pulse_upcoming">UPCOMING</string>
<string name="contest_pulse_recent">RECENT</string>
<string name="contest_pulse_next">NEXT</string>
<string name="contest_pulse_open_next">OPEN NEXT</string>
<string name="contest_pulse_no_upcoming">NO UPCOMING</string>
<string name="contest_pulse_open_next_cd">Open the next upcoming contest</string>
<string name="contest_pulse_no_upcoming_cd">No upcoming contest is available</string>
<string name="contest_filter_all">ALL</string>
<string name="contest_filter_live">LIVE</string>
<string name="contest_filter_upcoming">UPCOMING</string>
<string name="contest_filter_recent">RECENT</string>
<string name="contest_filter_all_cd">Show all contests</string>
<string name="contest_filter_live_cd">Show live contests</string>
<string name="contest_filter_upcoming_cd">Show upcoming contests</string>
<string name="contest_filter_recent_cd">Show recent contests</string>
<string name="contest_phase_empty">NO CONTESTS IN THIS VIEW</string>
~~~

- [ ] **Step 2: Add Simplified Chinese resources**

Add the same resource names with these translations:

~~~xml
<string name="contest_section_pulse">竞赛脉冲</string>
<string name="contest_pulse_live">进行中</string>
<string name="contest_pulse_upcoming">即将开始</string>
<string name="contest_pulse_recent">最近</string>
<string name="contest_pulse_next">下一场</string>
<string name="contest_pulse_open_next">打开下一场</string>
<string name="contest_pulse_no_upcoming">暂无即将开始</string>
<string name="contest_pulse_open_next_cd">打开下一场即将开始的竞赛</string>
<string name="contest_pulse_no_upcoming_cd">当前没有即将开始的竞赛</string>
<string name="contest_filter_all">全部</string>
<string name="contest_filter_live">进行中</string>
<string name="contest_filter_upcoming">即将开始</string>
<string name="contest_filter_recent">最近</string>
<string name="contest_filter_all_cd">显示全部竞赛</string>
<string name="contest_filter_live_cd">显示进行中的竞赛</string>
<string name="contest_filter_upcoming_cd">显示即将开始的竞赛</string>
<string name="contest_filter_recent_cd">显示最近的竞赛</string>
<string name="contest_phase_empty">当前视图没有竞赛</string>
~~~

- [ ] **Step 3: Run resource checks**

~~~powershell
.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.contests.ContestCommandCenterTest --no-daemon --console=plain
.\tools\gradlew-local.bat :app:lintDebug --no-daemon --console=plain
~~~

Expected result: \`BUILD SUCCESSFUL\`, including resource compilation and lint.

- [ ] **Step 4: Commit localization**

~~~powershell
git add app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml
git diff --cached --check
git commit -m "feat: localize contest command center"
~~~

### Task 4: Release Notes and Version Identity

**Files:**
- Modify: \`app/build.gradle.kts\`
- Modify: \`README.md\`
- Modify: \`docs/ROADMAP.md\`
- Create: \`docs/releases/v0.3.53.md\`

**Interfaces:**
- Publishes the phase identity \`versionCode=53\`, \`versionName="0.3.53"\` and documents the local-only contest command-center scope.

- [ ] **Step 1: Bump the app version**

Change only the existing version fields in \`app/build.gradle.kts\` to:

~~~kotlin
versionCode = 53
versionName = "0.3.53"
~~~

- [ ] **Step 2: Document the release**

Add a bilingual Phase 57 entry near the top of \`README.md\` and \`docs/ROADMAP.md\` covering \`CONTEST PULSE\`, live/upcoming/recent metrics, local phase filters, \`OPEN NEXT\`, and the unchanged focus route. Create \`docs/releases/v0.3.53.md\` with \`Highlights\`, \`Verification\`, and \`Scope\` sections. The Verification section will record the exact successful commands, APK SHA-256, emulator package version, screenshot paths, and crash-log result collected in Task 5.

- [ ] **Step 3: Validate documentation and version diff**

~~~powershell
rg -n "0\.3\.53|versionCode|Contest Command Center|竞赛脉冲|OPEN NEXT" README.md docs/ROADMAP.md docs/releases/v0.3.53.md app/build.gradle.kts
git diff --check
~~~

Expected result: all four files contain the intended release identity/copy, and \`git diff --check\` prints no errors.

- [ ] **Step 4: Commit release metadata**

~~~powershell
git add app/build.gradle.kts README.md docs/ROADMAP.md docs/releases/v0.3.53.md
git diff --cached --check
git commit -m "release: prepare contest command center v0.3.53"
~~~

### Task 5: Full Verification and Emulator Review

**Files:**
- Inspect: \`app/build/outputs/apk/debug/app-debug.apk\`
- Create/inspect: \`app/build/reports/ojnexus-contest-v053.png\`
- Create/inspect: \`app/build/reports/ojnexus-contest-v053-filtered.png\`
- Modify: \`docs/releases/v0.3.53.md\`

**Interfaces:**
- Verifies all production and test changes from Tasks 1–4 as one user-visible release candidate.

- [ ] **Step 1: Run the full unit test suite**

~~~powershell
.\tools\gradlew-local.bat test --no-daemon --console=plain
~~~

Expected result: \`BUILD SUCCESSFUL\` and no failed tests.

- [ ] **Step 2: Build and lint the release candidate**

Run serially:

~~~powershell
.\tools\gradlew-local.bat assembleDebug --no-daemon --console=plain
.\tools\gradlew-local.bat lintDebug --no-daemon --console=plain
~~~

Expected result: both commands end with \`BUILD SUCCESSFUL\`.

- [ ] **Step 3: Verify APK provenance**

Use the existing Android SDK tools to inspect the generated APK and confirm package version \`53\` / \`0.3.53\`, then record its SHA-256:

~~~powershell
Get-FileHash app/build/outputs/apk/debug/app-debug.apk -Algorithm SHA256
~~~

- [ ] **Step 4: Install and inspect the emulator flow**

Install \`app/build/outputs/apk/debug/app-debug.apk\` on the available \`Pixel_9\` emulator. Open Contests and verify:

1. The pulse is visible below judge filters with LIVE, UPCOMING, RECENT, and NEXT values.
2. Enabled \`OPEN NEXT\` opens the existing contest focus screen for the earliest upcoming row.
3. Selecting LIVE, UPCOMING, and RECENT changes only visible groups and shows \`NO CONTESTS IN THIS VIEW\` whenever the selected group is empty.
4. Selecting ALL restores all groups; pulse counts remain tied to the current judge selection.
5. The next countdown changes after at least one second without row overlap or layout crash.
6. Capture the initial and a filtered screen as \`app/build/reports/ojnexus-contest-v053.png\` and \`app/build/reports/ojnexus-contest-v053-filtered.png\` with the existing emulator screenshot workflow.

- [ ] **Step 5: Check crash output and worktree**

Search the captured logcat for \`FATAL EXCEPTION\` and \`Process: com.ojnexus\`; both must be absent. Then run:

~~~powershell
git diff --check
git status --short --branch
git log --oneline -8
~~~

Expected result: no whitespace errors, no unintended generated files staged, and only intentional Phase 57 commits on top of the prior clean release.

- [ ] **Step 6: Update release verification and commit evidence**

Write the actual successful test/build/lint results, APK SHA-256, emulator package version, screenshot paths, and crash-log result into \`docs/releases/v0.3.53.md\`. Commit only the release-note evidence:

~~~powershell
git add docs/releases/v0.3.53.md
git diff --cached --check
git commit -m "docs: record contest command center verification"
~~~
