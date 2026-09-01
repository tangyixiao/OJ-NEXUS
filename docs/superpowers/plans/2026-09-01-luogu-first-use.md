# Luogu first-use public sync loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (\`- [ ]\`) syntax for tracking.

**Goal:** Let a user with no Luogu connection open the existing Luogu public binding panel directly from Dashboard and observe the existing sync lifecycle.

**Architecture:** Keep Dashboard read-only and derive a pure \`shouldShowLuoguSetup(Set<JudgeId>)\` predicate from its observed connection list. Add a dedicated \`settings/luogu\` route and extend the existing Settings viewport-relative focus mechanism to target the Luogu panel; reuse the existing connector, repository, WorkManager, and error state without adding data or network layers.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation Compose, StateFlow, existing Room/WorkManager sync stack, JUnit.

**Spec:** \`docs/superpowers/specs/2026-09-01-luogu-first-use-design.md\`

## Global Constraints

- Keep the app native Kotlin/Compose/Material 3 and use existing \`core/designsystem\` tokens.
- Every new visible string must exist in both \`res/values/strings.xml\` and \`res/values-zh-rCN/strings.xml\`.
- Do not add Luogu main-site passwords, cookies, sessions, CSRF state, cloud accounts, cross-device sync, local compiler, custom-input runner, or automatic submission retry.
- Keep the existing \`settings\` route top-aligned and preserve \`settings/openapp\` focus behavior.
- Do not clear the existing emulator data or shut down the emulator.
- Follow TDD: write and run the failing focused test before implementation, then run the focused green test.

---

### Task 1: Dashboard setup predicate

**Files:**
- Create: \`app/src/main/java/com/ojnexus/feature/dashboard/DashboardSetup.kt\`
- Create: \`app/src/test/java/com/ojnexus/feature/dashboard/DashboardSetupTest.kt\`

**Interfaces:** Produce \`internal fun shouldShowLuoguSetup(connectedJudges: Set<JudgeId>): Boolean\`; return true exactly when \`JudgeId.LUOGU\` is absent.

- [ ] **Step 1: Write the failing test**

Add tests for empty connections, Codeforces-only connections, and a Luogu connection:

\`\`\`kotlin
@Test fun \`empty connections need Luogu setup\`() {
    assertTrue(shouldShowLuoguSetup(emptySet()))
}

@Test fun \`Codeforces-only connections still need Luogu setup\`() {
    assertTrue(shouldShowLuoguSetup(setOf(JudgeId.CODEFORCES)))
}

@Test fun \`Luogu connection hides setup\`() {
    assertFalse(shouldShowLuoguSetup(setOf(JudgeId.LUOGU)))
}
\`\`\`

- [ ] **Step 2: Run the focused test and verify it fails**

Run \`.\\tools\\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.dashboard.DashboardSetupTest --no-daemon --console=plain\`; expected compilation failure because the production predicate does not exist.

- [ ] **Step 3: Write the minimal implementation**

Create \`DashboardSetup.kt\` with:

\`\`\`kotlin
package com.ojnexus.feature.dashboard

import com.ojnexus.core.model.JudgeId

internal fun shouldShowLuoguSetup(connectedJudges: Set<JudgeId>): Boolean =
    JudgeId.LUOGU !in connectedJudges
\`\`\`

- [ ] **Step 4: Run the focused test and verify it passes**

Run the same Gradle command; expected all three tests pass.

- [ ] **Step 5: Commit**

\`\`\`powershell
git add app/src/main/java/com/ojnexus/feature/dashboard/DashboardSetup.kt app/src/test/java/com/ojnexus/feature/dashboard/DashboardSetupTest.kt
git commit -m "test: define Luogu first-use setup predicate / 定义洛谷首次使用判断"
\`\`\`

### Task 2: Dedicated Luogu Settings focus route

**Files:**
- Modify: \`app/src/main/java/com/ojnexus/app/NexusRoutes.kt\`
- Modify: \`app/src/main/java/com/ojnexus/app/NexusApp.kt\`
- Modify: \`app/src/main/java/com/ojnexus/feature/settings/SettingsScreen.kt\`
- Create: \`app/src/test/java/com/ojnexus/app/NexusRoutesTest.kt\`

**Interfaces:** Add \`NexusRoutes.SETTINGS_LUOGU = "settings/luogu"\`; make \`SettingsScreen\` accept \`focusLuogu: Boolean = false\`; map the route to \`SettingsScreen(focusLuogu = true)\`.

- [ ] **Step 1: Write the failing route test**

Assert \`NexusRoutes.SETTINGS == "settings"\`, \`NexusRoutes.SETTINGS_OPENAPP == "settings/openapp"\`, and \`NexusRoutes.SETTINGS_LUOGU == "settings/luogu"\`.

- [ ] **Step 2: Run the route test and verify it fails**

Run \`.\\tools\\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.app.NexusRoutesTest --no-daemon --console=plain\`; expected compilation failure because \`SETTINGS_LUOGU\` is absent.

- [ ] **Step 3: Implement route and focus**

Add the route constant and destination. Extend the existing Settings scroll calculation with \`luoguTargetRootY\`; wrap only the Luogu panel in \`onGloballyPositioned\`; select \`openAppTargetRootY\` for \`focusOpenApp\`, \`luoguTargetRootY\` for \`focusLuogu\`, otherwise no target. Keep the existing viewport calculation and maximum-value clamping unchanged.

- [ ] **Step 4: Run focused test and compile**

Run the route test and \`.\\tools\\gradlew-local.bat :app:compileDebugKotlin --no-daemon --console=plain\`; expected both pass.

- [ ] **Step 5: Commit**

\`\`\`powershell
git add app/src/main/java/com/ojnexus/app/NexusRoutes.kt app/src/main/java/com/ojnexus/app/NexusApp.kt app/src/main/java/com/ojnexus/feature/settings/SettingsScreen.kt app/src/test/java/com/ojnexus/app/NexusRoutesTest.kt
git commit -m "feat: add Luogu settings focus route / 添加洛谷设置聚焦路由"
\`\`\`

### Task 3: Dashboard CTA and bilingual resources

**Files:**
- Modify: \`app/src/main/java/com/ojnexus/feature/dashboard/DashboardScreen.kt\`
- Modify: \`app/src/main/java/com/ojnexus/app/NexusApp.kt\`
- Modify: \`app/src/main/res/values/strings.xml\`
- Modify: \`app/src/main/res/values-zh-rCN/strings.xml\`

**Interfaces:** Add \`onOpenLuoguSetup: () -> Unit = {}\` to DashboardScreen. Render the CTA exactly when \`shouldShowLuoguSetup(state.judgeConnections.map { it.judge }.toSet())\` is true. Add resource keys \`dash_connect_luogu\` and \`dash_connect_luogu_cd\` in both locales.

- [ ] **Step 1: Add matching English and Chinese resources**

Use English \`CONNECT LUOGU\` / \`OPEN LUOGU SETUP\` and Simplified Chinese \`连接洛谷\` / \`打开洛谷配置\`; preserve identical keys and zero format arguments.

- [ ] **Step 2: Implement Dashboard action and navigation**

Pass \`onOpenLuoguSetup = { navController.navigate(NexusRoutes.SETTINGS_LUOGU) }\`. Keep the current connection row, then add a compact accessible accent action below it when Luogu is absent. Use only existing spacing, radius, size, color, Role.Button, and content-description tokens.

- [ ] **Step 3: Run focused tests and resource parity**

Run \`.\\tools\\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.dashboard.DashboardSetupTest --tests com.ojnexus.app.NexusRoutesTest --tests com.ojnexus.core.resources.LocalizationResourceTest --no-daemon --console=plain\`; expected all pass.

- [ ] **Step 4: Commit**

\`\`\`powershell
git add app/src/main/java/com/ojnexus/feature/dashboard/DashboardScreen.kt app/src/main/java/com/ojnexus/app/NexusApp.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml app/src/test
git commit -m "feat: add Luogu first-use Dashboard action / 添加洛谷首次使用入口"
\`\`\`

### Task 4: Full verification, documentation, and Release

**Files:**
- Modify: \`README.md\`
- Modify: \`app/build.gradle.kts\`
- Modify: \`docs/ROADMAP.md\`
- Create: \`docs/releases/v0.3.37.md\`
- Create: \`docs/releases/SHA256SUMS-v0.3.37.txt\`
- Modify: this plan to record completion

**Interfaces:** Publish \`versionName=0.3.37\`, \`versionCode=37\`, asset \`OJ-NEXUS-v0.3.37.apk\`, and matching SHA-256 manifest.

- [ ] **Step 1: Update bilingual documentation**

Add Phase 41 at the top of the Roadmap and README status, preserving all previous phases. Write bilingual Release notes covering the Dashboard CTA, \`settings/luogu\` route, no-credential boundary, verification commands, and final APK SHA-256. Never include a keystore path or secret.

- [ ] **Step 2: Run the complete gate**

Run:

\`\`\`powershell
git diff --check
.\\tools\\gradlew-local.bat clean test assembleDebug lintDebug assembleRelease --no-daemon --rerun-tasks --console=plain
\`\`\` 

Expected: \`BUILD SUCCESSFUL\`, zero test failures, and no new lint errors.

- [ ] **Step 3: Install and exercise the Release APK**

Sign with the existing local standard debug keystore without tracking it; install with \`adb -s emulator-5554 install -r\`; launch \`com.ojnexus/.MainActivity\`; verify version metadata, the Luogu setup path, no fatal exception, and \`adb -s emulator-5554 get-state\` returns \`device\`. Do not clear data or stop the emulator.

- [ ] **Step 4: Commit, push, tag, and publish**

Use commit message \`release: prepare Luogu first-use v0.3.37 / 准备洛谷首次使用版本\`, push \`codex/phase-5-arena\`, create annotated tag \`v0.3.37\`, upload both assets with \`gh release create\`, and verify branch/tag SHAs, non-draft/non-prerelease status, remote APK digest, clean worktree, and online emulator.
