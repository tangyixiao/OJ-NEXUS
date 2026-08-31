# Workspace Result Continuity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Restore persisted Luogu OpenApp evaluation details in the workspace and show an explicit compile outcome.

**Architecture:** Keep `LuoguSubmissionRepository` as the sole persistence path. Extend the existing startup mapping in
`WorkspaceViewModel`, then render the already available fields in `WorkspaceScreen` through localized resources.

**Tech Stack:** Kotlin, Jetpack Compose, StateFlow, Room entities, JUnit/Robolectric.

**Spec:** `docs/superpowers/specs/2026-08-31-workspace-result-continuity-design.md`

## Global Constraints

- Preserve the local-first foreground-only OpenApp boundary.
- Do not add main-site passwords, cookies, sessions, CSRF state, cloud sync, background submission, or automatic POST retry.
- Do not persist source code, standard input, or credentials.
- Route all UI strings through `res/values/strings.xml` and `res/values-zh-rCN/strings.xml`.
- Use the existing design tokens and do not add arbitrary colors or layout literals.

---

### Task 1: Restore persisted evaluation fields

**Files:**
- Modify: `app/src/test/java/com/ojnexus/feature/workspace/WorkspaceViewModelTest.kt`
- Modify: `app/src/main/java/com/ojnexus/feature/workspace/WorkspaceViewModel.kt`

**Interfaces:**
- Consumes: `SubmissionJobEntity` nullable evaluation fields.
- Produces: restored `WorkspaceState.evaluation` containing the persisted fields.

- [ ] **Step 1: Write the failing test**

Extend the ready-workspace restoration fixture with `compileSuccess = false`, `compileMessage = "compiler output"`,
`output = "program output"`, `exitCode = 2`, `executionTimeMs = 17`, and `memoryKiB = 64`; assert every value on
`viewModel.state.value.evaluation`.

- [ ] **Step 2: Run the focused test to verify it fails**

Run:

```powershell
.\tools\gradlew-local.bat testDebugUnitTest --tests com.ojnexus.feature.workspace.WorkspaceViewModelTest --no-daemon --console=plain
```

Expected: the new assertions fail because the startup mapping currently supplies null for the evaluation details.

- [ ] **Step 3: Write the minimal implementation**

In the `ready` branch of `WorkspaceViewModel` startup restoration, map `job.compileSuccess`, `job.compileMessage`,
`job.output`, `job.exitCode`, `job.executionTimeMs`, and `job.memoryKiB` into `LuoguOpenEvaluation`.

- [ ] **Step 4: Run the focused test to verify it passes**

Run the same focused Gradle command and require `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```powershell
git add app/src/test/java/com/ojnexus/feature/workspace/WorkspaceViewModelTest.kt app/src/main/java/com/ojnexus/feature/workspace/WorkspaceViewModel.kt
git commit -m "fix: restore Luogu workspace evaluation details / 恢复洛谷工作区评测详情"
```

### Task 2: Make compile outcome explicit in the workspace

**Files:**
- Modify: `app/src/main/java/com/ojnexus/feature/workspace/WorkspaceScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/test/java/com/ojnexus/feature/workspace/WorkspaceViewModelTest.kt`

**Interfaces:**
- Consumes: `LuoguOpenEvaluation.compileSuccess`.
- Produces: localized compile status text in `EvaluationContent`.

- [ ] **Step 1: Add the state-level regression assertion**

Keep the restored `compileSuccess = false` fixture from Task 1 and assert it remains false; this protects the UI input
from being silently changed while the label is added.

- [ ] **Step 2: Run the focused test**

Run:

```powershell
.\tools\gradlew-local.bat testDebugUnitTest --tests com.ojnexus.feature.workspace.WorkspaceViewModelTest --no-daemon --console=plain
```

- [ ] **Step 3: Implement the localized rendering**

Add `workspace_compile`, `workspace_compile_success`, and `workspace_compile_failed` to both string files. In
`EvaluationContent`, render a `Text` line when `compileSuccess` is non-null, using `stringResource` and the existing
theme colors. Do not use a color alone to communicate the state.

- [ ] **Step 4: Compile and run the focused test**

Run the focused test command again and require `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/ojnexus/feature/workspace/WorkspaceScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml app/src/test/java/com/ojnexus/feature/workspace/WorkspaceViewModelTest.kt
git commit -m "feat: show Luogu compile outcome / 展示洛谷编译结果"
```

### Task 3: Document, verify, and publish

**Files:**
- Modify: `README.md`
- Modify: `docs/ROADMAP.md`

**Interfaces:**
- Consumes: completed Tasks 1–2 and the current bilingual release convention.
- Produces: Phase 21 bilingual project status and GitHub Release `v0.3.17` with the verified APK.

- [ ] **Step 1: Add bilingual Phase 21 notes**

Append a Phase 21 section to the roadmap and a matching status paragraph to the README. State that this is a local
workspace restoration/UI phase and explicitly retain the non-goals and historical Releases.

- [ ] **Step 2: Run full verification**

```powershell
git diff --check
.\tools\gradlew-local.bat clean test assembleDebug lintDebug --no-daemon --rerun-tasks --console=plain
```

Require all tasks to finish with `BUILD SUCCESSFUL`; install the APK on Pixel_9 and check launch, submission center,
settings, and `FATAL EXCEPTION` absence in the recent log.

- [ ] **Step 3: Commit and push**

```powershell
git add README.md docs/ROADMAP.md app/src/main/java/com/ojnexus/feature/workspace/WorkspaceScreen.kt app/src/main/java/com/ojnexus/feature/workspace/WorkspaceViewModel.kt app/src/main/res app/src/test/java/com/ojnexus/feature/workspace/WorkspaceViewModelTest.kt docs/superpowers/specs/2026-08-31-workspace-result-continuity-design.md docs/superpowers/plans/2026-08-31-workspace-result-continuity-plan.md
git commit -m "feat: ship workspace result continuity / 发布工作区结果连续性"
git push -u origin codex/phase-5-arena
```

- [ ] **Step 4: Create and verify the bilingual GitHub Release**

```powershell
$releaseNotes = @'
## Highlights / 更新

- Workspace restores persisted Luogu OpenApp compile status, output, exit code, time, and memory after reopening. / 工作区重新打开后恢复本地保存的洛谷 OpenApp 编译状态、输出、退出码、耗时和内存。
- Workspace shows an explicit localized compile outcome. / 工作区明确显示本地化编译结果。

## Scope / 范围

Local-first and foreground-only. No main-site password, cookies, sessions, CSRF state, cloud sync, background submission, automatic POST retry, local compiler, or custom-input runner. / 本地优先、仅前台执行，不新增主站密码、Cookie、Session、CSRF、云同步、后台提交、POST 自动重试、本地编译器或自定义输入运行。
'@
Set-Content -LiteralPath .release-notes-v0.3.17.md -Value $releaseNotes -Encoding utf8
gh release create v0.3.17 'app/build/outputs/apk/debug/app-debug.apk#oj-nexus-v0.3.17-debug.apk' --repo tangyixiao/OJ-NEXUS --target codex/phase-5-arena --title 'OJ NEXUS v0.3.17 / OJ NEXUS v0.3.17' --notes-file .release-notes-v0.3.17.md
gh release view v0.3.17 --repo tangyixiao/OJ-NEXUS --json tagName,targetCommitish,isDraft,isPrerelease,assets,url
```

Record the actual APK SHA-256 in the Release notes and final report. Do not delete or rewrite any earlier Release.
