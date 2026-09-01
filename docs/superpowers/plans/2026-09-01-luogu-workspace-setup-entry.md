# Luogu Workspace First-use Setup Entry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give users a direct, bilingual path from an unconfigured Luogu workspace to the existing Settings screen.

**Architecture:** Keep navigation in `NexusApp`; pass an `onOpenSettings` callback into `WorkspaceScreen`. Render the existing credential warning plus a localized, accessible action only when the credential is missing.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Android string resources, JUnit, Gradle.

**Spec:** `docs/superpowers/specs/2026-09-01-luogu-workspace-setup-entry-design.md`

## Global Constraints

- Keep the native Kotlin/Compose architecture and existing NEXUS design tokens.
- Every user-facing string belongs in both default and `values-zh-rCN` resources.
- Do not add passwords, cookies, sessions, CSRF, cloud services, network calls, or automatic submission behavior.
- Preserve the existing warning and all previous release documentation.
- End with `clean test assembleDebug lintDebug`, emulator verification, and a GitHub Release.

### Task 1: Add the navigation contract and localized resources

**Files:**
- Modify: `app/src/main/java/com/ojnexus/feature/workspace/WorkspaceScreen.kt`
- Modify: `app/src/main/java/com/ojnexus/app/NexusApp.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`

**Interfaces:**
- `WorkspaceScreen(pid: String, onBack: () -> Unit, onOpenSettings: () -> Unit)`.
- The settings callback is invoked only by the new missing-credential action.

- [ ] **Step 1: Write the failing contract check**

  Add the callback parameter and its call site in the test fixture or compile-facing source before the implementation is complete; the focused compile must fail until the action is rendered and resources exist.

- [ ] **Step 2: Run the focused compile**

  Run `.\tools\gradlew-local.bat :app:compileDebugKotlin --no-daemon --console=plain`.
  Expected: FAIL until the new resource IDs and callback wiring are complete.

- [ ] **Step 3: Implement the minimal UI contract**

  Add `onOpenSettings` to `WorkspaceScreen`, pass `{ navController.navigate(NexusRoutes.SETTINGS) }` from the workspace route, and render a `WorkspaceAction` with `Role.Button` and a localized content description beneath `workspace_credential_required` when `state.credentialConfigured` is false.

- [ ] **Step 4: Add resource translations**

  Add `workspace_open_settings` and `workspace_open_settings_cd` to both string files with English and Simplified Chinese values.

- [ ] **Step 5: Run the focused compile**

  Run `.\tools\gradlew-local.bat :app:compileDebugKotlin --no-daemon --console=plain`.
  Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

  Run `git add app/src/main/java/com/ojnexus/feature/workspace/WorkspaceScreen.kt app/src/main/java/com/ojnexus/app/NexusApp.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml` and `git commit -m "feat: add workspace settings entry / 增加工作区设置入口"`.

### Task 2: Verify the complete app and publish the phase

**Files:**
- Modify: `README.md`
- Modify: `ROADMAP.md`
- Create: `docs/releases/v0.3.28.md`

**Interfaces:**
- Release tag `v0.3.28` points to the documented phase commit.
- APK asset is `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 1: Run the full verification suite**

  Run `git diff --check` and `.\tools\gradlew-local.bat clean test assembleDebug lintDebug --no-daemon --rerun-tasks --console=plain`.
  Expected: BUILD SUCCESSFUL with no test failures.

- [ ] **Step 2: Verify on the running emulator**

  Install the APK with `D:\Android\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk`, launch `com.ojnexus`, inspect the workspace route through the existing UI, and check logcat for no `FATAL EXCEPTION`. Do not stop or shut down the emulator.

- [ ] **Step 3: Update bilingual project documentation**

  Record Phase 32 in `README.md`, append the stage to `ROADMAP.md`, and create a bilingual release note while preserving all prior notes.

- [ ] **Step 4: Commit, push, tag, and release**

  Commit the documentation with `docs: publish v0.3.28 notes / 发布 v0.3.28 说明`, push `codex/phase-5-arena` and tag `v0.3.28`, then run `gh release create v0.3.28 app\build\outputs\apk\debug\app-debug.apk --verify-tag --title "OJ NEXUS v0.3.28 — Luogu workspace setup / 洛谷工作区配置入口" --notes-file docs\releases\v0.3.28.md`.

- [ ] **Step 5: Verify the published release**

  Compare local HEAD, remote branch SHA, tag commit, release metadata, and the uploaded APK SHA256 before reporting completion.

