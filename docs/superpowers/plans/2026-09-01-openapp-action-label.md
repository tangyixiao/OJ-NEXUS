# OpenApp action intent clarity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Luogu workspace primary action say `SUBMIT` when the actual gateway submits code and `RUN` only when a gateway truly supports custom-input execution.

**Architecture:** Add one pure resource-ID mapping beside the workspace UI state, test it independently, and replace the hardcoded primary-action resource lookup in `WorkspaceScreen`. Keep `WorkspaceViewModel`, `LuoguOpenGateway`, persistence, credentials, and network transport unchanged.

**Tech Stack:** Kotlin, Jetpack Compose, existing Android resources, JUnit.

**Spec:** `docs/superpowers/specs/2026-09-01-openapp-action-label-design.md`

## Global Constraints

- Use the existing `workspace_working`, `workspace_mode_submit`, and `workspace_mode_run` resources in both locales.
- Do not add a local compiler, custom-input runner, main-site password, Cookie, Session, CSRF state, cloud account, cross-device sync, or automatic POST retry.
- Preserve `supportsCustomInputRun` capability gating and the existing submit/check-result behavior.
- Do not clear emulator data or shut down the emulator.
- Follow TDD: write and run the failing focused test before implementation.

---

### Task 1: Pure action-label mapping

**Files:**
- Create: `app/src/main/java/com/ojnexus/feature/workspace/WorkspaceActionLabel.kt`
- Create: `app/src/test/java/com/ojnexus/feature/workspace/WorkspaceActionLabelTest.kt`

**Interfaces:** Produce `internal fun workspaceActionLabelRes(state: WorkspaceState): Int`; return `R.string.workspace_working` when `busy`, `R.string.workspace_mode_submit` in submit mode, and `R.string.workspace_mode_run` in run mode.

- [ ] **Step 1: Write the failing test**

Add three tests using `WorkspaceState(pid = "P1001", ...)`: busy expects `R.string.workspace_working`; non-busy submit expects `R.string.workspace_mode_submit`; non-busy run with `customRunAvailable = true` expects `R.string.workspace_mode_run`.

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```powershell
.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.workspace.WorkspaceActionLabelTest --no-daemon --console=plain
```

Expected: compilation fails because `workspaceActionLabelRes` does not exist.

- [ ] **Step 3: Implement the minimal mapping**

Create the function with this exact precedence:

```kotlin
internal fun workspaceActionLabelRes(state: WorkspaceState): Int = when {
    state.busy -> R.string.workspace_working
    state.mode == WorkspaceMode.SUBMIT -> R.string.workspace_mode_submit
    else -> R.string.workspace_mode_run
}
```

- [ ] **Step 4: Run the focused test and verify it passes**

Run the same Gradle command; expected all three tests pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/ojnexus/feature/workspace/WorkspaceActionLabel.kt app/src/test/java/com/ojnexus/feature/workspace/WorkspaceActionLabelTest.kt
git commit -m "test: define workspace action labels / 定义工作区操作标签"
```

### Task 2: Use the mapping in the workspace UI

**Files:**
- Modify: `app/src/main/java/com/ojnexus/feature/workspace/WorkspaceScreen.kt`
- Modify: `docs/superpowers/plans/2026-09-01-openapp-action-label.md`

**Interfaces:** Replace the primary action's `stringResource(if (state.busy) ... else ...)` call with `stringResource(workspaceActionLabelRes(state))`; no other callback or state logic changes.

- [ ] **Step 1: Make the minimal UI substitution**

At the primary `WorkspaceAction`, pass `label = stringResource(workspaceActionLabelRes(state))`. Keep `enabled`, `selected`, and `onClick = viewModel::submit` exactly as they are.

- [ ] **Step 2: Run focused workspace regression tests**

Run:

```powershell
.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.workspace.WorkspaceActionLabelTest --tests com.ojnexus.feature.workspace.WorkspaceViewModelTest --no-daemon --console=plain
```

Expected: all action-label and existing workspace ViewModel tests pass.

- [ ] **Step 3: Commit the UI substitution**

```powershell
git add app/src/main/java/com/ojnexus/feature/workspace/WorkspaceScreen.kt
git commit -m "fix: label Luogu workspace submit action / 明确洛谷工作区提交操作"
```

### Task 3: Full verification and bilingual Release

**Files:**
- Modify: `README.md`
- Modify: `app/build.gradle.kts`
- Modify: `docs/ROADMAP.md`
- Create: `docs/releases/v0.3.38.md`
- Create: `docs/releases/SHA256SUMS-v0.3.38.txt`
- Modify: this plan to record completion

**Interfaces:** Publish `versionName=0.3.38`, `versionCode=38`, `OJ-NEXUS-v0.3.38.apk`, and its SHA-256 manifest.

- [ ] **Step 1: Update bilingual documentation**

Add Phase 42 at the top of README/Roadmap while preserving all prior text. Release notes must state that the real Luogu OpenApp workspace now labels its action `SUBMIT / 提交`, while custom `RUN / 运行` remains capability-gated; do not claim a local compiler or custom runner.

- [ ] **Step 2: Run the complete gate**

Run:

```powershell
git diff --check
.\tools\gradlew-local.bat clean test assembleDebug lintDebug assembleRelease --no-daemon --rerun-tasks --console=plain
```

Expected: `BUILD SUCCESSFUL`, zero test failures, and no new lint errors.

- [ ] **Step 3: Sign, install, and verify runtime intent**

Sign the Release APK with the existing local standard debug keystore without tracking it; install over the existing app using `adb -s emulator-5554 install -r`; open a Luogu workspace; verify package version `0.3.38`, visible primary label `SUBMIT`, no fatal exception, and emulator state `device`. Do not clear data or stop the emulator.

- [ ] **Step 4: Commit, push, tag, and publish**

Use commit message `release: prepare OpenApp action clarity v0.3.38 / 准备 OpenApp 操作明确版本`, push `codex/phase-5-arena`, create annotated tag `v0.3.38`, upload APK and checksum with `gh release create`, then compare local/remote branch and peeled-tag SHAs, Release asset digest, Release status, clean worktree, and emulator state.
