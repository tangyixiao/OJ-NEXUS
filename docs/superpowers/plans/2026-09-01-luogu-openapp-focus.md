# Luogu OpenApp Settings Focus Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Luogu workspace setup action land directly on the OpenApp credential section.

**Architecture:** Pass a boolean focus intent from `NexusApp` to `SettingsScreen`; attach a `BringIntoViewRequester` to the OpenApp `NexusSection` and request it once when focused navigation is requested.

**Tech Stack:** Kotlin, Jetpack Compose Foundation, Material 3, Android resources, Gradle.

**Spec:** `docs/superpowers/specs/2026-09-01-luogu-openapp-focus-design.md`

## Global Constraints

- Keep the native Kotlin/Compose architecture and existing NEXUS design tokens.
- Use layout-aware bring-into-view behavior; do not hard-code scroll offsets.
- Keep ordinary Settings navigation unchanged when `focusOpenApp` is false.
- Do not add main-site passwords, Cookie, Session, CSRF login, cloud services, or submission behavior.
- Preserve all earlier documentation and Releases.

### Task 1: Add focus-aware Settings navigation

**Files:**
- Modify: `app/src/main/java/com/ojnexus/app/NexusApp.kt`
- Modify: `app/src/main/java/com/ojnexus/feature/settings/SettingsScreen.kt`

**Interfaces:**
- `SettingsScreen(onBack: () -> Unit, focusOpenApp: Boolean = false)`.
- Workspace navigation passes `focusOpenApp = true`; all other call sites use the default.

- [ ] **Step 1: Write the failing compile contract**

  Add the `focusOpenApp = true` call-site argument before the Settings signature and focus implementation exist, then run `./tools/gradlew-local.bat :app:compileDebugKotlin --no-daemon --console=plain`; compilation must fail because the parameter is not yet accepted.

- [ ] **Step 2: Implement layout-aware focus**

  Import `BringIntoViewRequester`, `bringIntoViewRequester`, and `LaunchedEffect`; remember a requester, call `bringIntoView()` when `focusOpenApp` is true, and pass `Modifier.bringIntoViewRequester(requester)` to the OpenApp `NexusSection`.

- [ ] **Step 3: Run the focused compile**

  Run `./tools/gradlew-local.bat :app:compileDebugKotlin --no-daemon --console=plain`.
  Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

  Run `git add app/src/main/java/com/ojnexus/app/NexusApp.kt app/src/main/java/com/ojnexus/feature/settings/SettingsScreen.kt` and `git commit -m "feat: focus OpenApp setup from workspace / 从工作区定位 OpenApp 配置"`.

### Task 2: Verify and publish v0.3.29

**Files:**
- Modify: `README.md`
- Modify: `docs/ROADMAP.md`
- Create: `docs/releases/v0.3.29.md`

**Interfaces:**
- Release tag `v0.3.29` points to the published documentation commit.
- APK asset is `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 1: Run the full quality gate**

  Run `git diff --check` and `./tools/gradlew-local.bat clean test assembleDebug lintDebug --no-daemon --rerun-tasks --console=plain`; expected result is BUILD SUCCESSFUL.

- [ ] **Step 2: Verify the user path on the running emulator**

  Install the Debug APK, open a local Luogu problem workspace with no OpenApp credential, tap `OPEN SETTINGS`, and dump the UI tree. Confirm `SETTINGS` and `LUOGU OPENAPP` are visible after navigation; inspect logcat for no `FATAL EXCEPTION`. Do not shut down the emulator.

- [ ] **Step 3: Update bilingual documentation**

  Change the current README status to Phase 33, append the Phase 33 entry to `docs/ROADMAP.md`, and create `docs/releases/v0.3.29.md` describing the focus behavior, safety boundary, tests, emulator evidence, and APK SHA-256.

- [ ] **Step 4: Commit, push, tag, and release**

  Commit with `docs: publish v0.3.29 notes / 发布 v0.3.29 说明`, push `codex/phase-5-arena`, create and push annotated tag `v0.3.29`, then run `gh release create v0.3.29 app\\build\\outputs\\apk\\debug\\app-debug.apk --verify-tag --title "OJ NEXUS v0.3.29 — Luogu OpenApp focus / 洛谷 OpenApp 定位" --notes-file docs\\releases\\v0.3.29.md`.

- [ ] **Step 5: Verify publication**

  Compare local HEAD, remote branch SHA, local and remote tag commit, Release metadata, uploaded APK digest, and final clean Git status.

