# Submission Ops Deck Implementation Plan

> For agentic workers: use superpowers:executing-plans to implement this plan task by task. Steps use checkbox syntax for tracking.

**Goal:** Make the local Submission Center faster to scan and operate with manual bulk actions and a collapsible per-request inspector.

**Architecture:** Keep request behavior behind SubmissionCenterViewModel and the existing LuoguSubmissionCenter and LuoguResultWorkScheduler contracts. Add pure request-ID selectors in SubmissionControlTower.kt, delegate bulk commands to existing per-request guards, and keep expansion state in Compose keyed by request ID. No Room, network DTO, or navigation changes are required.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, StateFlow, Coroutines, JUnit, Robolectric.

**Spec:** docs/superpowers/specs/2026-09-02-submission-ops-deck-design.md

## Global Constraints

- Preserve local-first behavior and existing foreground polling/recovery behavior.
- Bulk actions are manual only; do not add background submission, automatic POST retry, or new jobs.
- Use existing Nexus spacing, typography, tones, dividers, and motion tokens.
- Add every new UI string and content description to both locale resource files.
- Do not add passwords, cookies, sessions, CSRF state, cloud accounts, migrations, or third-party UI dependencies.
- Release identity is versionCode 57 and versionName 0.3.57.

---

### Task 1: Add stable bulk request selectors

**Files:**
- Modify: app/src/main/java/com/ojnexus/feature/submissions/SubmissionControlTower.kt
- Test: app/src/test/java/com/ojnexus/feature/submissions/SubmissionControlTowerTest.kt

**Interfaces:**
- Produce pendingSubmissionRequestIds(jobs: List<SubmissionJobEntity>): List<String>.
- Produce failedSubmissionRequestIds(jobs: List<SubmissionJobEntity>): List<String>.
- Both functions return nonblank request IDs, remove duplicates, and preserve source order.

- [ ] Step 1: Write failing selector tests.

Add tests using the existing job helper:

~~~kotlin
@Test
fun pendingRequestIdsRemoveBlanksAndDuplicatesInSourceOrder() {
    val first = job(1, SubmissionJobStatus.PENDING.name).copy(requestId = "req-a")
    val duplicate = job(2, SubmissionJobStatus.PENDING.name).copy(requestId = " req-a ")
    val blank = job(3, SubmissionJobStatus.PENDING.name).copy(requestId = " ")
    val ready = job(4, SubmissionJobStatus.READY.name).copy(requestId = "req-ready")

    assertEquals(listOf("req-a"), pendingSubmissionRequestIds(listOf(first, duplicate, blank, ready)))
}

@Test
fun failedRequestIdsKeepOnlyFailedRowsInSourceOrder() {
    val failedA = job(1, SubmissionJobStatus.FAILED.name).copy(requestId = "req-a")
    val ready = job(2, SubmissionJobStatus.READY.name).copy(requestId = "req-ready")
    val failedB = job(3, SubmissionJobStatus.FAILED.name).copy(requestId = "req-b")

    assertEquals(listOf("req-a", "req-b"), failedSubmissionRequestIds(listOf(failedA, ready, failedB)))
}
~~~

- [ ] Step 2: Run the focused test and verify it fails.

~~~powershell
.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.submissions.SubmissionControlTowerTest --no-daemon --console=plain
~~~

Expected: compilation/test failure because the selector functions do not exist yet.

- [ ] Step 3: Implement the minimal selectors.

~~~kotlin
private fun submissionRequestIds(
    jobs: List<SubmissionJobEntity>,
    status: SubmissionJobStatus,
): List<String> = jobs.asSequence()
    .filter { it.status == status.name }
    .mapNotNull { it.requestId.trim().takeIf(String::isNotEmpty) }
    .distinct()
    .toList()

fun pendingSubmissionRequestIds(jobs: List<SubmissionJobEntity>): List<String> =
    submissionRequestIds(jobs, SubmissionJobStatus.PENDING)

fun failedSubmissionRequestIds(jobs: List<SubmissionJobEntity>): List<String> =
    submissionRequestIds(jobs, SubmissionJobStatus.FAILED)
~~~

- [ ] Step 4: Run the focused test and verify it passes.

Run the command from Step 2. Expected: BUILD SUCCESSFUL and all tests in the class pass.

- [ ] Step 5: Commit.

~~~powershell
git add app/src/main/java/com/ojnexus/feature/submissions/SubmissionControlTower.kt app/src/test/java/com/ojnexus/feature/submissions/SubmissionControlTowerTest.kt
git commit -m "feat: add submission bulk request selectors"
~~~

### Task 2: Add ViewModel bulk commands and availability state

**Files:**
- Modify: app/src/main/java/com/ojnexus/feature/submissions/SubmissionCenterViewModel.kt
- Test: app/src/test/java/com/ojnexus/feature/submissions/SubmissionCenterViewModelTest.kt

**Interfaces:**
- SubmissionCenterUiState gains recoveryAvailable: Boolean.
- SubmissionCenterViewModel.checkPending() snapshots current ready jobs and calls checkResult once per pending request ID.
- SubmissionCenterViewModel.queueFailed() snapshots current ready jobs and calls queueRecovery once per failed request ID.

- [ ] Step 1: Write failing tests.

Verify scheduler availability and recovery ordering with the existing RecordingScheduler:

~~~kotlin
@Test
fun stateExposesRecoveryAvailabilityAndQueueFailedUsesFailedSnapshot() = runBlocking {
    val jobs = MutableStateFlow(
        listOf(
            pendingJob("req-pending"),
            pendingJob("req-failed").copy(status = SubmissionJobStatus.FAILED.name),
            pendingJob("req-failed-2").copy(status = SubmissionJobStatus.FAILED.name),
        ),
    )
    val scheduler = RecordingScheduler()
    val viewModel = SubmissionCenterViewModel(FakeSubmissionCenter(jobs), scheduler = scheduler)
    val collector = collectState(viewModel)
    awaitReady(viewModel)

    assertEquals(true, awaitReady(viewModel).recoveryAvailable)
    viewModel.queueFailed()

    withTimeout(1_000) {
        while (scheduler.requestIds.size < 2) {
            drainMainLooper()
            delay(1)
        }
    }
    assertEquals(listOf("req-failed", "req-failed-2"), scheduler.requestIds)
    collector.cancel()
}
~~~

Also add a test with two pending jobs and delayForResult = {}; assert the fake center receives one refresh call for each request.

- [ ] Step 2: Run the focused ViewModel tests and verify they fail.

~~~powershell
.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.submissions.SubmissionCenterViewModelTest --no-daemon --console=plain
~~~

Expected: compilation failure for the missing state property or methods.

- [ ] Step 3: Implement the state and commands.

Set recoveryAvailable to scheduler != null in the combined state and add:

~~~kotlin
fun checkPending() {
    val ready = state.value as? Loadable.Ready ?: return
    pendingSubmissionRequestIds(ready.value.jobs).forEach(::checkResult)
}

fun queueFailed() {
    val ready = state.value as? Loadable.Ready ?: return
    failedSubmissionRequestIds(ready.value.jobs).forEach(::queueRecovery)
}
~~~

Keep checkResult and queueRecovery unchanged so duplicate guards and action errors stay centralized.

- [ ] Step 4: Run the focused tests and verify they pass.

Expected: BUILD SUCCESSFUL, including the existing duplicate and error tests.

- [ ] Step 5: Commit.

~~~powershell
git add app/src/main/java/com/ojnexus/feature/submissions/SubmissionCenterViewModel.kt app/src/test/java/com/ojnexus/feature/submissions/SubmissionCenterViewModelTest.kt
git commit -m "feat: add submission bulk operations"
~~~

### Task 3: Build the collapsible inspector and pulse actions

**Files:**
- Modify: app/src/main/java/com/ojnexus/feature/submissions/SubmissionCenterScreen.kt
- Modify: app/src/main/res/values/strings.xml
- Modify: app/src/main/res/values-zh-rCN/strings.xml
- Test: app/src/test/java/com/ojnexus/feature/submissions/SubmissionCenterDisplayTest.kt

**Interfaces:**
- SubmissionCenterContent passes onCheckPending, onQueueFailed, and recoveryAvailable into SubmissionPulse.
- SubmissionPulse renders manual bulk actions only when the corresponding count and capability are present.
- SubmissionJobCard keeps header and valid actions visible while moving detailed metadata behind request-ID-keyed expansion state.

- [ ] Step 1: Add a pure display-label test.

~~~kotlin
@Test
fun detailsLabelTracksExpansionState() {
    assertEquals("DETAILS", submissionDetailsLabel(false))
    assertEquals("HIDE DETAILS", submissionDetailsLabel(true))
}
~~~

- [ ] Step 2: Run the display test and verify it fails.

~~~powershell
.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.submissions.SubmissionCenterDisplayTest --no-daemon --console=plain
~~~

Expected: compilation failure because submissionDetailsLabel is absent.

- [ ] Step 3: Implement the UI.

Add submissionDetailsLabel(expanded: Boolean), the new strings, and rememberSaveable(job.requestId) for expansion. Apply animateContentSize with NexusMotion.DURATION_NORMAL and snap when NexusTheme.reduceMotion is true:

~~~kotlin
var detailsExpanded by rememberSaveable(job.requestId) { mutableStateOf(false) }
Column(
    modifier = Modifier.animateContentSize(
        animationSpec = if (NexusTheme.reduceMotion) snap() else tween(
            NexusMotion.DURATION_NORMAL,
            easing = NexusMotion.EasingStandard,
        ),
    ),
) {
    // compact header, language/time, and valid actions stay visible
    if (detailsExpanded) {
        // request ID, PID/title, status, score, compile, output, exit, time, memory, error
    }
}
~~~

Add manual pulse buttons with existing SubmissionActionTag and Role.Button semantics:
- submissions_check_pending and submissions_check_pending_cd
- submissions_queue_failed and submissions_queue_failed_cd
- submissions_details, submissions_hide_details, and submissions_details_cd
- submissions_details_collapsed and submissions_details_expanded

Update submissions_row_cd with expansion state. Preserve existing per-row check, recovery, and workspace actions.

- [ ] Step 4: Run display and resource tests.

~~~powershell
.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.submissions.SubmissionCenterDisplayTest --tests com.ojnexus.core.resources.LocalizationResourceTest --no-daemon --console=plain
~~~

Expected: BUILD SUCCESSFUL.

- [ ] Step 5: Commit.

~~~powershell
git add app/src/main/java/com/ojnexus/feature/submissions/SubmissionCenterScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml app/src/test/java/com/ojnexus/feature/submissions/SubmissionCenterDisplayTest.kt
git commit -m "feat: add submission inspector and bulk actions"
~~~

### Task 4: Publish Phase 61 identity and documentation

**Files:**
- Modify: app/build.gradle.kts
- Modify: README.md
- Modify: docs/ROADMAP.md
- Create: docs/releases/v0.3.57.md

- [ ] Step 1: Update release identity and documentation.

Change only the current Gradle identity, add bilingual Phase 61 status paragraphs, and create release notes with highlights, local-only scope, and verification bullets. Do not put placeholder, pending, TODO, or guessed hash text in the release note.

- [ ] Step 2: Check documentation and diff hygiene.

~~~powershell
rg -n "Phase 61|第 61|0\.3\.57|TODO|TBD|placeholder|pending" README.md docs/ROADMAP.md docs/releases/v0.3.57.md app/build.gradle.kts
git diff --check
~~~

Expected: Phase 61 and 0.3.57 matches are present, the release file has no placeholder wording, and diff check is clean.

- [ ] Step 3: Commit.

~~~powershell
git add app/build.gradle.kts README.md docs/ROADMAP.md docs/releases/v0.3.57.md
git commit -m "release: prepare submission ops deck v0.3.57"
~~~

### Task 5: Full verification and runtime acceptance

**Files:**
- Verify: app/build/outputs/apk/debug/app-debug.apk
- Capture: app/build/reports/ojnexus-submissions-v057.png

- [ ] Step 1: Run all quality gates.

~~~powershell
.\tools\gradlew-local.bat test assembleDebug lintDebug --no-daemon --console=plain
~~~

Expected: BUILD SUCCESSFUL with no test, compile, or lint failure.

- [ ] Step 2: Install and verify package identity.

~~~powershell
$adb = "D:\Android\platform-tools\adb.exe"
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
& $adb shell dumpsys package com.ojnexus | Select-String -Pattern "versionCode=|versionName="
Get-FileHash app\build\outputs\apk\debug\app-debug.apk -Algorithm SHA256
~~~

Expected: Android reports versionCode 57 and versionName 0.3.57; record the actual SHA-256 in release notes.

- [ ] Step 3: Verify runtime interaction.

Open Submission Center from the dashboard or command surface. With local rows present, verify a row initially shows compact metadata, DETAILS changes to HIDE DETAILS and reveals request details, and a visible pending/failed bulk action changes busy/queued state after a real tap. Capture app/build/reports/ojnexus-submissions-v057.png.

- [ ] Step 4: Check fatal logs and final repository state.

~~~powershell
$fatal = & $adb logcat -d -t 500 | Select-String -Pattern "FATAL EXCEPTION|Process: com.ojnexus"
if ($fatal) { $fatal } else { "NO_APP_FATAL_EXCEPTION" }
git diff --check
git status --short --branch
git log --oneline -8
~~~

Expected: NO_APP_FATAL_EXCEPTION, clean diff, and the branch contains the Phase 61 commits.

