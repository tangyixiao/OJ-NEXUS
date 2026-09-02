# Submission Control Tower Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (\`- [ ]\`) syntax for tracking.

**Goal:** Add a local Submission Pulse, status filters, clear-filter behavior, and restrained visual hierarchy to the existing Submission Center without changing request execution.

**Architecture:** Introduce a pure submissions presentation module that counts known and unknown local job statuses and filters the current in-memory job list in source order. SubmissionCenterScreen derives that model from its existing Loadable state, renders the pulse and controls, then feeds only the selected list into the existing request cards and action callbacks.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, JUnit, existing NexusSection/NexusMetric/NexusTag design-system components, StateFlow/Loadable.

**Spec:** \`docs/superpowers/specs/2026-09-03-submission-control-tower-design.md\`

## Global Constraints

- Native Android Kotlin + Jetpack Compose Material 3; do not add Flutter, React Native, Electron, or WebView.
- Keep the existing single app module, Room schema, repository methods, scheduler behavior, credential behavior, request actions, and workspace navigation route unchanged.
- UI remains dark-first with one NEXUS BLUE accent, restrained radii, hairline separators, named design-system dimensions, and meaningful 120–300ms motion.
- Feature code must not use raw Color(0xFF...), arbitrary inline .dp/.sp literals, or direct RoundedCornerShape(...); use existing design-system tokens.
- Every new UI string must be added to both app/src/main/res/values/strings.xml and app/src/main/res/values-zh-rCN/strings.xml.
- Unknown future status strings count toward total and remain visible in ALL with the existing UNKNOWN label/tone.
- Do not add bulk retry, fake requests, network fields, credentials, passwords, cookies, sessions, migrations, compiler execution, or automatic submission.
- Release identity for this phase is versionCode=54 and versionName="0.3.54".

---

### Task 1: Pure Submission Status Model

**Files:**
- Create: \`app/src/test/java/com/ojnexus/feature/submissions/SubmissionControlTowerTest.kt\`
- Create: \`app/src/main/java/com/ojnexus/feature/submissions/SubmissionControlTower.kt\`
- Reference: \`app/src/main/java/com/ojnexus/feature/submissions/SubmissionCenterViewModel.kt\`
- Reference: \`app/src/main/java/com/ojnexus/core/database/entity/SubmissionJobEntity.kt\`

**Interfaces:**
- Consumes \`SubmissionJobEntity\` and the existing \`SubmissionJobStatus\` enum.
- Produces \`SubmissionStatusFilter\`, \`SubmissionCenterSummary\`, \`summarizeSubmissionCenter(jobs: List<SubmissionJobEntity>)\`, and \`filterSubmissionJobs(jobs: List<SubmissionJobEntity>, filter: SubmissionStatusFilter)\`.

- [ ] **Step 1: Write failing unit tests**

Create a complete test fixture helper:

~~~kotlin
private fun job(id: Long, status: String) = SubmissionJobEntity(
    id = id,
    judge = JudgeId.LUOGU.id,
    requestId = "request-$id",
    kind = SubmissionJobKind.RUN.name,
    pid = null,
    title = null,
    language = "cpp",
    status = status,
    judgeStatus = null,
    score = null,
    createdAt = id,
    updatedAt = id,
    lastErrorType = null,
    compileSuccess = null,
    compileMessage = null,
    output = null,
    exitCode = null,
    executionTimeMs = null,
    memoryKiB = null,
)
~~~

Add these tests:

~~~kotlin
@Test
fun summaryCountsKnownAndUnknownStatuses() {
    val summary = summarizeSubmissionCenter(
        listOf(
            job(1, SubmissionJobStatus.PENDING.name),
            job(2, SubmissionJobStatus.PENDING.name),
            job(3, SubmissionJobStatus.READY.name),
            job(4, SubmissionJobStatus.FAILED.name),
            job(5, "FUTURE_STATUS"),
        ),
    )

    assertEquals(
        SubmissionCenterSummary(total = 5, pending = 2, ready = 1, failed = 1, other = 1),
        summary,
    )
}

@Test
fun summaryAcceptsEmptyJobs() {
    assertEquals(
        SubmissionCenterSummary(total = 0, pending = 0, ready = 0, failed = 0, other = 0),
        summarizeSubmissionCenter(emptyList()),
    )
}

@Test
fun filtersPreserveSourceOrderAndKeepUnknownInAllOnly() {
    val pending = job(1, SubmissionJobStatus.PENDING.name)
    val failed = job(2, SubmissionJobStatus.FAILED.name)
    val unknown = job(3, "FUTURE_STATUS")
    val ready = job(4, SubmissionJobStatus.READY.name)
    val source = listOf(pending, failed, unknown, ready)

    assertEquals(source, filterSubmissionJobs(source, SubmissionStatusFilter.ALL))
    assertEquals(listOf(pending), filterSubmissionJobs(source, SubmissionStatusFilter.PENDING))
    assertEquals(listOf(ready), filterSubmissionJobs(source, SubmissionStatusFilter.READY))
    assertEquals(listOf(failed), filterSubmissionJobs(source, SubmissionStatusFilter.FAILED))
    assertEquals(source, filterSubmissionJobs(source, SubmissionStatusFilter.ALL))
    assertTrue(filterSubmissionJobs(source, SubmissionStatusFilter.PENDING).none { it.status == "FUTURE_STATUS" })
    assertNotSame(source, filterSubmissionJobs(source, SubmissionStatusFilter.ALL))
}
~~~

- [ ] **Step 2: Run the focused test and verify the red failure**

~~~powershell
.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.submissions.SubmissionControlTowerTest --no-daemon --console=plain
~~~

Expected result: compilation fails because the four new model symbols are absent.

- [ ] **Step 3: Implement the minimal pure model**

Create:

~~~kotlin
enum class SubmissionStatusFilter { ALL, PENDING, READY, FAILED }

data class SubmissionCenterSummary(
    val total: Int,
    val pending: Int,
    val ready: Int,
    val failed: Int,
    val other: Int,
)

fun summarizeSubmissionCenter(
    jobs: List<SubmissionJobEntity>,
): SubmissionCenterSummary {
    val pending = jobs.count { it.status == SubmissionJobStatus.PENDING.name }
    val ready = jobs.count { it.status == SubmissionJobStatus.READY.name }
    val failed = jobs.count { it.status == SubmissionJobStatus.FAILED.name }
    return SubmissionCenterSummary(
        total = jobs.size,
        pending = pending,
        ready = ready,
        failed = failed,
        other = jobs.size - pending - ready - failed,
    )
}

fun filterSubmissionJobs(
    jobs: List<SubmissionJobEntity>,
    filter: SubmissionStatusFilter,
): List<SubmissionJobEntity> = when (filter) {
    SubmissionStatusFilter.ALL -> jobs.toList()
    SubmissionStatusFilter.PENDING -> jobs.filter { it.status == SubmissionJobStatus.PENDING.name }
    SubmissionStatusFilter.READY -> jobs.filter { it.status == SubmissionJobStatus.READY.name }
    SubmissionStatusFilter.FAILED -> jobs.filter { it.status == SubmissionJobStatus.FAILED.name }
}
~~~

- [ ] **Step 4: Run the focused test and verify green**

Run the same focused command. Expected result: BUILD SUCCESSFUL with every summary/filter test passing.

- [ ] **Step 5: Commit the pure model**

~~~powershell
git add app/src/main/java/com/ojnexus/feature/submissions/SubmissionControlTower.kt app/src/test/java/com/ojnexus/feature/submissions/SubmissionControlTowerTest.kt
git diff --cached --check
git commit -m "feat: add submission control model"
~~~

### Task 2: Submission Pulse and Local Status Controls

**Files:**
- Modify: \`app/src/main/java/com/ojnexus/feature/submissions/SubmissionCenterScreen.kt\`
- Reference: \`app/src/main/java/com/ojnexus/feature/submissions/SubmissionControlTower.kt\`
- Reference: existing \`NexusSection\`, \`NexusMetric\`, \`NexusTag\`, \`NexusMotion\`, and \`SubmissionActionTag\`.

**Interfaces:**
- Consumes the Task 1 summary/filter API.
- Preserves \`onCheckResult\`, \`onQueueRecovery\`, \`onOpenWorkspace\`, and every existing action condition.

- [ ] **Step 1: Add local derived state**

Inside \`SubmissionCenterContent\`, add:

~~~kotlin
var statusFilter by rememberSaveable { mutableStateOf(SubmissionStatusFilter.ALL) }
val summary = summarizeSubmissionCenter(state.jobs)
val visibleJobs = filterSubmissionJobs(state.jobs, statusFilter)
~~~

Use \`summary\` for global pulse metrics and \`visibleJobs\` only for the request list/empty-state branch.

- [ ] **Step 2: Add the pulse section**

Insert a \`NexusSection\` after the action-error block and before recent requests. Its trailing action is visible only when \`statusFilter != SubmissionStatusFilter.ALL\`, and resets the filter to ALL with button semantics.

Render four weighted \`NexusMetric\` values labelled by resources for TOTAL, PENDING, READY, and FAILED. Animate integer changes with \`animateIntAsState\` and \`NexusMotion.DURATION_NORMAL\`; use \`snap()\` under \`NexusTheme.reduceMotion\`. Format numbers with the existing \`formatCount\`.

- [ ] **Step 3: Add accessible status filters**

Below the pulse, render four weighted \`NexusTag\` controls for ALL, PENDING, READY, and FAILED. Use selected NEXUS accent styling, \`Role.Button\`, localized \`onClickLabel\`, and localized content descriptions. Clicking a control changes only \`statusFilter\`.

- [ ] **Step 4: Render filtered rows with a single-tree transition**

Wrap the recent-request content in a \`Column\` using \`animateContentSize\` with the existing 200ms motion token and \`snap()\` under reduce motion. Keep the global zero-jobs branch using \`submissions_empty\`. When \`state.jobs\` is non-empty but \`visibleJobs\` is empty, show \`submissions_filter_empty\` and keep controls active. Pass \`visibleJobs\` to the existing card loop; retain busy/queued membership checks against each job request ID.

- [ ] **Step 5: Run focused tests and compile**

Run serially:

~~~powershell
.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.submissions.SubmissionControlTowerTest --no-daemon --console=plain
.\tools\gradlew-local.bat :app:compileDebugKotlin --no-daemon --console=plain
~~~

Expected result: BUILD SUCCESSFUL and no new compiler diagnostics.

- [ ] **Step 6: Commit the screen behavior**

~~~powershell
git add app/src/main/java/com/ojnexus/feature/submissions/SubmissionCenterScreen.kt
git diff --cached --check
git commit -m "feat: add submission control tower UI"
~~~

### Task 3: Localized Submission Copy

**Files:**
- Modify: \`app/src/main/res/values/strings.xml\`
- Modify: \`app/src/main/res/values-zh-rCN/strings.xml\`

**Interfaces:**
- Supplies every new UI label and accessibility description used by Task 2.

- [ ] **Step 1: Add English resources**

Add:

~~~xml
<string name="submissions_section_pulse">SUBMISSION PULSE</string>
<string name="submissions_pulse_total">TOTAL</string>
<string name="submissions_pulse_pending">PENDING</string>
<string name="submissions_pulse_ready">READY</string>
<string name="submissions_pulse_failed">FAILED</string>
<string name="submissions_clear_filter">CLEAR FILTER</string>
<string name="submissions_clear_filter_cd">Show all submission requests</string>
<string name="submissions_filter_all">ALL</string>
<string name="submissions_filter_pending">PENDING</string>
<string name="submissions_filter_ready">READY</string>
<string name="submissions_filter_failed">FAILED</string>
<string name="submissions_filter_all_cd">Show all submission requests</string>
<string name="submissions_filter_pending_cd">Show pending submission requests</string>
<string name="submissions_filter_ready_cd">Show ready submission requests</string>
<string name="submissions_filter_failed_cd">Show failed submission requests</string>
<string name="submissions_filter_empty">NO REQUESTS IN THIS VIEW</string>
~~~

- [ ] **Step 2: Add Simplified Chinese resources**

Add:

~~~xml
<string name="submissions_section_pulse">提交脉冲</string>
<string name="submissions_pulse_total">总数</string>
<string name="submissions_pulse_pending">等待中</string>
<string name="submissions_pulse_ready">已就绪</string>
<string name="submissions_pulse_failed">失败</string>
<string name="submissions_clear_filter">清除筛选</string>
<string name="submissions_clear_filter_cd">显示全部提交请求</string>
<string name="submissions_filter_all">全部</string>
<string name="submissions_filter_pending">等待中</string>
<string name="submissions_filter_ready">已就绪</string>
<string name="submissions_filter_failed">失败</string>
<string name="submissions_filter_all_cd">显示全部提交请求</string>
<string name="submissions_filter_pending_cd">显示等待中的提交请求</string>
<string name="submissions_filter_ready_cd">显示已就绪的提交请求</string>
<string name="submissions_filter_failed_cd">显示失败的提交请求</string>
<string name="submissions_filter_empty">当前视图没有请求</string>
~~~

- [ ] **Step 3: Run resource and focused checks**

~~~powershell
.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.submissions.SubmissionControlTowerTest --no-daemon --console=plain
.\tools\gradlew-local.bat :app:lintDebug --no-daemon --console=plain
~~~

Expected result: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit localization**

~~~powershell
git add app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml
git diff --cached --check
git commit -m "feat: localize submission control tower"
~~~

### Task 4: Version and Release Documentation

**Files:**
- Modify: \`app/build.gradle.kts\`
- Modify: \`README.md\`
- Modify: \`docs/ROADMAP.md\`
- Create: \`docs/releases/v0.3.54.md\`

**Interfaces:**
- Publishes versionCode=54/versionName=0.3.54 and records the local-only scope.

- [ ] **Step 1: Update version identity**

Change the current app version fields to:

~~~kotlin
versionCode = 54
versionName = "0.3.54"
~~~

- [ ] **Step 2: Add release documentation**

Add a bilingual Phase 58 entry at the top of README.md and docs/ROADMAP.md describing SUBMISSION PULSE, known-status counts, local filters, CLEAR FILTER, preserved actions, and the unchanged workspace route. Create docs/releases/v0.3.54.md with Highlights, Verification, and Scope sections. Verification must be filled with the actual command/build/emulator evidence after Task 5.

- [ ] **Step 3: Validate metadata**

~~~powershell
rg -n "0\.3\.54|versionCode|Submission Control Tower|SUBMISSION PULSE|提交脉冲" README.md docs/ROADMAP.md docs/releases/v0.3.54.md app/build.gradle.kts
git diff --check
~~~

Expected result: all intended release identity and feature terms are found, with no diff-check errors.

- [ ] **Step 4: Commit release metadata**

~~~powershell
git add app/build.gradle.kts README.md docs/ROADMAP.md docs/releases/v0.3.54.md
git diff --cached --check
git commit -m "release: prepare submission control tower v0.3.54"
~~~

### Task 5: Full Verification and Emulator Review

**Files:**
- Inspect: \`app/build/outputs/apk/debug/app-debug.apk\`
- Create/inspect: \`app/build/reports/ojnexus-submissions-v054.png\`
- Create/inspect: \`app/build/reports/ojnexus-submissions-v054-filtered.png\`
- Modify: \`docs/releases/v0.3.54.md\`

**Interfaces:**
- Verifies the complete local release candidate without changing external submission state.

- [ ] **Step 1: Run the complete test suite**

~~~powershell
.\tools\gradlew-local.bat test --no-daemon --console=plain
~~~

Expected result: BUILD SUCCESSFUL and no failed tests.

- [ ] **Step 2: Build and lint serially**

~~~powershell
.\tools\gradlew-local.bat assembleDebug --no-daemon --console=plain
.\tools\gradlew-local.bat lintDebug --no-daemon --console=plain
~~~

Expected result: each command ends with BUILD SUCCESSFUL.

- [ ] **Step 3: Install and inspect the emulator**

Install app/build/outputs/apk/debug/app-debug.apk on the available Pixel_9 emulator. Open Submission Center through the existing Commands palette and verify:

1. The pulse is visible with TOTAL, PENDING, READY, and FAILED.
2. ALL shows all local request rows and the existing action tags.
3. PENDING, READY, and FAILED change only the visible rows.
4. CLEAR FILTER restores ALL; when a selected filter has no matches, NO REQUESTS IN THIS VIEW appears.
5. Existing OPEN still reaches the workspace route for a problem request.
6. Capture initial and filtered screenshots at the two report paths.

If the installed local database has zero requests, verify the zero-count pulse and existing global empty message; do not fabricate a request or call a live submission endpoint solely to create test data.

- [ ] **Step 4: Verify package, hash, and crash output**

~~~powershell
$adb = "D:\Android\platform-tools\adb.exe"
& $adb shell pm dump com.ojnexus | Select-String -Pattern "versionCode|versionName"
Get-FileHash app/build/outputs/apk/debug/app-debug.apk -Algorithm SHA256
& $adb logcat -d -v brief | Select-String -Pattern "FATAL EXCEPTION|Process: com\.ojnexus"
~~~

Expected result: installed package reports 54/0.3.54, a SHA-256 is recorded, and the crash search returns no app fatal exception.

- [ ] **Step 5: Update release evidence and commit**

Record the actual successful commands, APK hash, emulator package identity, screenshots, and crash result in docs/releases/v0.3.54.md, then:

~~~powershell
git add docs/releases/v0.3.54.md
git diff --cached --check
git commit -m "docs: record submission control tower verification"
git status --short --branch
~~~

Expected result: the final worktree is clean and only intentional Phase 58 commits are ahead of the prior release.
