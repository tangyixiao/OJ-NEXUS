# Workspace problem context Implementation Plan

> **For agentic workers:** Execute this plan task-by-task with tests and verification checkpoints.

**Goal:** Preserve the native Luogu problem title when a user opens its coding workspace.

**Architecture:** Add an optional encoded query argument to the existing workspace route. Pass the
title only from the native Luogu detail screen, retain PID-only compatibility elsewhere, and keep
the title as display-only ViewModel state.

**Tech Stack:** Kotlin, Navigation Compose, Jetpack Compose, JUnit.

**Spec:** `docs/superpowers/specs/2026-09-01-workspace-problem-context-design.md`

## Global Constraints

- Keep `pid` as the required stable identity and omit blank optional titles.
- Use UTF-8 percent-encoding for route values; do not concatenate raw title text into routes.
- Keep submission requests keyed by PID; title is display context only.
- Do not add main-site passwords, Cookie, Session, CSRF state, cloud service, compiler, or custom runner.
- Add failing tests before production behavior and finish with the repository quality gate.

---

### Task 1: Route and ViewModel context

**Files:**
- Modify: `app/src/test/java/com/ojnexus/app/NexusRoutesTest.kt`
- Modify: `app/src/main/java/com/ojnexus/app/NexusApp.kt`
- Modify: `app/src/test/java/com/ojnexus/feature/workspace/WorkspaceViewModelTest.kt`
- Modify: `app/src/main/java/com/ojnexus/feature/workspace/WorkspaceViewModel.kt`

**Interfaces:**
- Produces: `NexusRoutes.workspace(pid: String, title: String? = null): String`.
- Produces: `WorkspaceScreen(..., title: String? = null)` and `WorkspaceState.title` retention.

- [x] **Step 1: Write failing route and state tests**

Add a route assertion for `NexusRoutes.workspace("B/4132", "[信息与未来] 简单")` that contains
encoded values and a second assertion that a blank title produces `workspace/B%2F4132` without a
`title` query. Add a ViewModel test that constructs `WorkspaceViewModel(..., title = "Sample")`
and asserts `state.value.title == "Sample"`.

- [x] **Step 2: Run focused tests and verify RED**

Run:

```text
.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.app.NexusRoutesTest --tests com.ojnexus.feature.workspace.WorkspaceViewModelTest --no-daemon --console=plain
```

Expected: compilation/test failure because the route helper and title parameter are not yet
available.

- [x] **Step 3: Implement the smallest route/state change**

Add the route helper and optional Navigation argument, thread `title` into the workspace screen
and ViewModel, and retain it in the existing state without changing submission payloads.

- [x] **Step 4: Run focused tests and verify GREEN**

Run the same focused command and confirm `BUILD SUCCESSFUL`.

### Task 2: Native detail navigation and user-facing rendering

**Files:**
- Modify: `app/src/main/java/com/ojnexus/feature/problems/LuoguProblemDetailScreen.kt`
- Modify: `app/src/main/java/com/ojnexus/app/NexusApp.kt`
- Modify: `app/src/main/java/com/ojnexus/feature/workspace/WorkspaceScreen.kt`
- Modify: `README.md`
- Modify: `docs/ROADMAP.md`
- Create: `docs/releases/v0.3.43.md`
- Create: `docs/releases/SHA256SUMS-v0.3.43.txt`

**Interfaces:**
- Consumes: the route helper and title-aware workspace state from Task 1.
- Produces: a native detail-to-workspace title-preserving flow and bilingual v0.3.43 Release.

- [x] **Step 1: Pass the live detail title**

Change the Luogu detail workspace callback to provide `(pid, detail.title)` and navigate through
`NexusRoutes.workspace`; keep all PID-only callers on the helper's default `title = null` path.

- [x] **Step 2: Render the title beside the PID**

Display a non-blank `state.title` in the existing workspace identity row, using resource-backed
copy and existing design tokens. Do not add arbitrary colors, dimensions, or new network work.

- [x] **Step 3: Run the full quality gate and install without clearing data**

Run `git diff --check` and `clean test assembleDebug lintDebug assembleRelease`; sign and install
the Release APK over `emulator-5554`, then verify the native Luogu detail-to-workspace path.

- [ ] **Step 4: Publish and audit the GitHub Release**

Commit with bilingual message, push branch and annotated `v0.3.43`, publish APK plus checksum,
then compare remote branch SHA, peeled tag SHA, Release asset digest, manifest hash, installed
package version, emulator state, and clean worktree.
