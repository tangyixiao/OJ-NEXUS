# Workspace Execution Cockpit Implementation Plan

> For agentic workers: use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Turn the existing local Workspace into a clearer execution cockpit with sample context, an O2 control, local telemetry, and explicit result state feedback.

**Architecture:** Keep NexusRoutes as the route-context boundary, WorkspaceViewModel as the owner of editable execution state, and Compose as a renderer of WorkspaceState. Sample input/output is optional navigation context from the already-loaded Luogu detail screen; no new remote fetch, database column, or Open Platform field is introduced.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Navigation Compose, StateFlow, JUnit, existing Luogu Open Platform gateway and NEXUS design-system components.

**Spec:** docs/superpowers/specs/2026-09-02-workspace-execution-cockpit-design.md

## Global Constraints

- Native Kotlin + Compose Material 3 only; no Flutter, React Native, Electron, or WebView shell.
- Keep the existing single app module and UI → ViewModel → repository/gateway boundaries.
- Keep dark-first NEXUS BLUE telemetry styling; use named design tokens and no feature-local arbitrary colors, spacing, or shapes.
- Every new UI string must exist in both values/strings.xml and values-zh-rCN/strings.xml.
- Do not add passwords, cookies, sessions, tokens, database migrations, remote sample fetching from Workspace, background POSTs, or automatic retries.
- Keep all stored drafts and existing request/result semantics intact.
- Preserve existing entry points that do not provide sample context.

---

### Task 1: Thread optional sample context through navigation

**Files:**
- Modify: app/src/main/java/com/ojnexus/app/NexusApp.kt
- Modify: app/src/main/java/com/ojnexus/feature/problems/LuoguProblemDetailScreen.kt
- Test: app/src/test/java/com/ojnexus/app/NexusRoutesTest.kt

**Interfaces:**
- Produce NexusRoutes.workspace(pid: String, title: String? = null, sampleInput: String? = null, sampleOutput: String? = null): String.
- Produce optional nullable Navigation arguments named title, sampleInput, and sampleOutput.
- Change the detail-to-workspace callback to (String, String, String?, String?) -> Unit.

- [ ] Step 1: Write failing route tests.

~~~kotlin
@Test
fun workspace_route_encodes_sample_context() {
    val route = NexusRoutes.workspace("P1001", "A+B", "1 2\n", "3\n")

    assertTrue(route.startsWith("workspace/P1001?title="))
    assertTrue("sampleInput=" in route)
    assertTrue("sampleOutput=" in route)
    assertTrue("1 2" !in route)
}

@Test
fun workspace_route_omits_blank_sample_context() {
    assertEquals(
        "workspace/P1001?title=A%2BB",
        NexusRoutes.workspace("P1001", "A+B", " ", ""),
    )
}
~~~

- [ ] Step 2: Run the focused route tests and confirm the new tests fail.

~~~powershell
.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.app.NexusRoutesTest --no-daemon --console=plain
~~~

Expected: compile or assertion failure because the route builder has no sample parameters.

- [ ] Step 3: Implement the optional route query builder. Keep the existing PID encoder. Build query pairs in the order title, sampleInput, sampleOutput; include a pair only when its trimmed value is non-blank; encode each value with the existing UTF-8 URL encoder. Update the route declaration with nullable arguments and defaultValue = null.

- [ ] Step 4: Change LuoguProblemDetailScreen to invoke its callback with pid, detail.title, detail.samples.getOrNull(0), and detail.samples.getOrNull(1). Update NexusApp to pass those values into NexusRoutes.workspace. Keep every other Workspace entry point on the default arguments.

- [ ] Step 5: Run the focused route tests and commit.

~~~powershell
git add app/src/main/java/com/ojnexus/app/NexusApp.kt app/src/main/java/com/ojnexus/feature/problems/LuoguProblemDetailScreen.kt app/src/test/java/com/ojnexus/app/NexusRoutesTest.kt
git commit -m "feat: carry problem samples into workspace"
~~~

### Task 2: Add sample actions and O2 state to the ViewModel

**Files:**
- Modify: app/src/main/java/com/ojnexus/feature/workspace/WorkspaceViewModel.kt
- Modify: app/src/test/java/com/ojnexus/feature/workspace/WorkspaceViewModelTest.kt

**Interfaces:**
- Extend WorkspaceState with sampleInput: String? = null and sampleOutput: String? = null.
- Extend WorkspaceViewModel construction with optional sampleInput and sampleOutput after title.
- Produce loadSampleInput() and clearInput() actions.
- Keep setO2(Boolean) and ensure both request paths read the current state snapshot.

- [ ] Step 1: Add tests for initial sample context, loading/clearing input, and O2 forwarding on both request types.

~~~kotlin
@Test
fun sample_input_can_be_loaded_and_cleared_without_changing_code() = runBlocking {
    val viewModel = WorkspaceViewModel(
        pid = "P1001",
        title = "A+B",
        sampleInput = "1 2\n",
        sampleOutput = "3\n",
        gateway = FakeGateway(),
        credentialStore = FakeStore(),
        testScope = CoroutineScope(coroutineContext),
    )

    viewModel.setCode("int main() {}")
    viewModel.loadSampleInput()
    assertEquals("1 2\n", viewModel.state.value.input)
    assertEquals("int main() {}", viewModel.state.value.code)
    viewModel.clearInput()
    assertEquals("", viewModel.state.value.input)
}

@Test
fun run_forwards_the_o2_flag() = runBlocking {
    val gateway = FakeGateway()
    val viewModel = WorkspaceViewModel("P1001", "A+B", gateway, FakeStore(), CoroutineScope(coroutineContext))
    viewModel.setCode("print(1)")
    viewModel.setO2(true)
    viewModel.submit()
    assertEquals(true, gateway.lastRunRequest?.o2)
}

@Test
fun submit_forwards_the_o2_flag() = runBlocking {
    val gateway = FakeGateway()
    val viewModel = WorkspaceViewModel("P1001", "A+B", gateway, FakeStore(), CoroutineScope(coroutineContext))
    viewModel.setCode("int main() {}")
    viewModel.setO2(true)
    viewModel.setMode(WorkspaceMode.SUBMIT)
    viewModel.submit()
    assertEquals(true, gateway.lastProblemRequest?.o2)
}
~~~

- [ ] Step 2: Run the focused ViewModel tests and confirm the new tests fail before implementation.

~~~powershell
.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.workspace.WorkspaceViewModelTest --no-daemon --console=plain
~~~

- [ ] Step 3: Initialize sample fields from constructor arguments. Implement loadSampleInput as a no-op for blank samples and otherwise delegate to setInput(sample), so draft scheduling and error clearing stay centralized. Implement clearInput with setInput(""). Do not persist sample output and do not change gateway DTOs.

- [ ] Step 4: Run focused tests, expect BUILD SUCCESSFUL, and commit.

~~~powershell
git add app/src/main/java/com/ojnexus/feature/workspace/WorkspaceViewModel.kt app/src/test/java/com/ojnexus/feature/workspace/WorkspaceViewModelTest.kt
git commit -m "feat: add workspace sample and optimization state"
~~~

### Task 3: Add pure Workspace telemetry

**Files:**
- Create: app/src/main/java/com/ojnexus/feature/workspace/WorkspaceTelemetry.kt
- Create: app/src/test/java/com/ojnexus/feature/workspace/WorkspaceTelemetryTest.kt

**Interfaces:**
- Produce WorkspaceTelemetry(mode: WorkspaceMode, language: String, codeLines: Int, draftState: WorkspaceDraftState).
- Produce workspaceTelemetry(state: WorkspaceState): WorkspaceTelemetry.
- Produce sourceLineCount(code: String): Int where blank code is 0 and trailing line breaks do not create phantom lines.

- [ ] Step 1: Write focused pure-model tests.

~~~kotlin
@Test
fun source_line_count_handles_blank_and_multiline_code() {
    assertEquals(0, sourceLineCount(""))
    assertEquals(1, sourceLineCount("int main() {}\n"))
    assertEquals(2, sourceLineCount("int main() {\n  return 0;\n}\n"))
}

@Test
fun telemetry_mirrors_editable_workspace_state() {
    val state = WorkspaceState(
        pid = "P1001",
        title = "A+B",
        code = "a\nb",
        language = "cxx/17/gcc",
        mode = WorkspaceMode.SUBMIT,
        draftState = WorkspaceDraftState.SAVED,
    )

    assertEquals(
        WorkspaceTelemetry(WorkspaceMode.SUBMIT, "cxx/17/gcc", 2, WorkspaceDraftState.SAVED),
        workspaceTelemetry(state),
    )
}
~~~

- [ ] Step 2: Run the focused test and confirm failure.

~~~powershell
.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.workspace.WorkspaceTelemetryTest --no-daemon --console=plain
~~~

- [ ] Step 3: Normalize CRLF to LF, remove trailing LF characters, return 0 for blank content, and count remaining newline-separated lines. Map mode, language, codeLines, and draftState directly from WorkspaceState.

- [ ] Step 4: Run the focused test, expect BUILD SUCCESSFUL, and commit.

~~~powershell
git add app/src/main/java/com/ojnexus/feature/workspace/WorkspaceTelemetry.kt app/src/test/java/com/ojnexus/feature/workspace/WorkspaceTelemetryTest.kt
git commit -m "feat: add workspace telemetry model"
~~~

### Task 4: Build the Workspace cockpit UI

**Files:**
- Modify: app/src/main/java/com/ojnexus/feature/workspace/WorkspaceScreen.kt
- Modify: app/src/main/java/com/ojnexus/app/NexusApp.kt only for final argument wiring if Task 1 leaves a call site

**Interfaces:**
- WorkspaceScreen accepts sampleInput and sampleOutput optional arguments and passes them to the ViewModel factory.
- Render a four-metric WORKSPACE PULSE section.
- Render an O2 switch wired to viewModel.setO2.
- Render sample load/clear actions and a read-only expected-output block only when optional sample data exists.
- Render text-bearing IDLE/PENDING/READY tags around the existing result content.

- [ ] Step 1: Add matching English and Simplified Chinese resources for workspace_pulse, workspace_pulse_mode, workspace_pulse_language, workspace_pulse_lines, workspace_pulse_draft, workspace_o2, workspace_o2_cd, workspace_load_sample, workspace_load_sample_cd, workspace_clear_input, workspace_clear_input_cd, workspace_expected_output, workspace_result_idle, workspace_result_ready, and workspace_result_pending_cd.

- [ ] Step 2: Use NexusSection, NexusMetric, NexusDivider, NexusTag, NexusSpacing, NexusRadius, NexusSize, NexusMotion, and NexusTheme. Use Material 3 Switch with Role.Switch and a content description for O2. Keep the code editor editable and show sample output in a separate read-only code-block component.

- [ ] Step 3: Use animateIntAsState for line count and animateContentSize for pulse/result sections; use snap when NexusTheme.reduceMotion is true and a 120–300ms NEXUS motion token otherwise. Keep result labels textual even when tone changes.

- [ ] Step 4: Compile and lint the UI.

~~~powershell
.\tools\gradlew-local.bat :app:compileDebugKotlin :app:lintDebug --no-daemon --console=plain
~~~

Expected: BUILD SUCCESSFUL with no new lint errors.

- [ ] Step 5: Commit the UI.

~~~powershell
git add app/src/main/java/com/ojnexus/feature/workspace/WorkspaceScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml
git commit -m "feat: add workspace execution cockpit UI"
~~~

### Task 5: Version and release documentation

**Files:**
- Modify: app/build.gradle.kts
- Modify: README.md
- Modify: docs/ROADMAP.md
- Create: docs/releases/v0.3.56.md

**Interfaces:**
- Set versionCode = 56 and versionName = "0.3.56".
- Document Phase 60 as Workspace Execution Cockpit / 工作区执行驾驶舱.

- [ ] Step 1: Describe sample context, O2 control, local pulse, result rail, and the no-new-network/no-migration scope. Keep earlier phase notes untouched. The release file must contain actual final verification evidence, with no pending, TODO, or placeholder wording after verification.

- [ ] Step 2: Run docs checks and commit.

~~~powershell
git diff --check
rg -n "TODO|TBD|pending|placeholder" docs/releases/v0.3.56.md
git add app/build.gradle.kts README.md docs/ROADMAP.md docs/releases/v0.3.56.md
git commit -m "release: prepare workspace cockpit v0.3.56"
~~~

Expected: diff check is clean and the release scan returns no matches.

### Task 6: Full verification and runtime evidence

**Files:**
- Modify: docs/releases/v0.3.56.md with final evidence only
- Create: app/build/reports/ojnexus-workspace-v056.png as a generated runtime artifact

- [ ] Step 1: Run the complete build gates.

~~~powershell
.\tools\gradlew-local.bat test assembleDebug lintDebug --no-daemon --console=plain
~~~

Expected: all tasks complete with BUILD SUCCESSFUL.

- [ ] Step 2: Install app/build/outputs/apk/debug/app-debug.apk on emulator-5554 with D:\Android\platform-tools\adb.exe. Verify versionCode=56 and versionName=0.3.56 with dumpsys package. Record SHA-256 with Get-FileHash.

- [ ] Step 3: Clear logcat, open a locally available Workspace, and verify through UIAutomator and screenshot: WORKSPACE PULSE, O2, sample controls when the detail route supplies a sample pair, expected output, and the text result state. If installed local data has no sample pair, verify sample controls are omitted and record that honest empty behavior instead of injecting data.

- [ ] Step 4: Search post-interaction logcat for FATAL EXCEPTION and Process: com.ojnexus; record NO_APP_FATAL_EXCEPTION when absent. Update v0.3.56.md with actual command output summary, screenshot path, package identity, hash, and log result.

- [ ] Step 5: Run git diff --check, git status --short, and git log -8 --oneline. The worktree must be clean and release evidence must match the installed APK.

