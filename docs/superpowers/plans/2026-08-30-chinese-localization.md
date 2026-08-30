# Simplified Chinese Localization Implementation Plan
> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
**Goal:** Add complete Simplified Chinese Android resources that follow the system locale while preserving English fallback and all user/OJ data.
**Architecture:** Keep `values/strings.xml` as the default and add a mirrored `values-zh-rCN/strings.xml`. Android resource selection handles locale choice; a JVM test compares resource keys and format placeholders.
**Tech Stack:** Android resources, Kotlin/JUnit, standard JDK XML parser, Gradle Android resource processing.
**Spec:** `docs/superpowers/specs/2026-08-30-chinese-localization-design.md`
## Global Constraints
- UI tone remains native Android competitive-programming telemetry UI; no marketing copy.
- OJ names, problem titles, tags, user input, and verdict codes remain unchanged.
- Do not change database schema, sync behavior, theme, navigation, or version number.
- Every new user-visible label is a string resource.
- Run `tools\gradlew-local.bat test assembleDebug lintDebug` before completion.
### Task 1: Add a failing resource-parity test
**Files:** Create `app/src/test/java/com/ojnexus/core/resources/LocalizationResourceTest.kt`.
**Interfaces:** The test reads both XML files, compares resource keys, and compares printf-style placeholder tokens.
- [ ] **Step 1: Write the failing test.** Implement `readStrings("values")`, `readStrings("values-zh-rCN")`, and `formatTokens` using `DocumentBuilderFactory` and `Regex("%(?:\\d+\\$)?[-+0-9.]*[a-zA-Z]")`; assert equal key sets and equal token lists for every key.
- [ ] **Step 2: Verify RED.** Run `tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.core.resources.LocalizationResourceTest`; it must fail because `values-zh-rCN/strings.xml` is absent.
- [ ] **Step 3: Commit the red test.** Run `git add app/src/test/java/com/ojnexus/core/resources/LocalizationResourceTest.kt` and `git commit -m "test: require complete Chinese string resources"`.
### Task 2: Add the complete Simplified Chinese resource set
**Files:** Create `app/src/main/res/values-zh-rCN/strings.xml`; test with `LocalizationResourceTest.kt`.
**Interfaces:** Every key in `app/src/main/res/values/strings.xml` receives a Chinese value with identical printf placeholders.
- [ ] **Step 1: Add translated XML.** Preserve every `name`, format token, meaningful line break, and `AC`/`WA`/`TLE`/`MLE`/`RE`/`CE`/`PE` code. Use “题目”“训练”“复习”“掌握度”“竞赛”“设置”“本地数据” consistently; leave OJ names, titles, tags, and user data unchanged.
- [ ] **Step 2: Verify GREEN.** Run `tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.core.resources.LocalizationResourceTest`; expect PASS.
- [ ] **Step 3: Commit resources.** Run `git add app/src/main/res/values-zh-rCN/strings.xml` and `git commit -m "feat: add simplified Chinese resources"`.
### Task 3: Localize visible generic loading failures
**Files:** Audit the loading fallbacks in Dashboard, Problems, Training, Session, Review Session, Analytics, and Profile; modify only the necessary ViewModels/resources.
**Interfaces:** Use an injected resource-backed string provider or existing app-level accessor so ViewModels do not put `Context` calls in Composables; leave server errors, exception identifiers, data, and logs untouched.
- [ ] **Step 1: Add matching `error_load_failed` resources and replace only literals such as `"Load failed"` that reach user-visible state.**
- [ ] **Step 2: Run `tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.core.resources.LocalizationResourceTest` and `git diff --check`; expect PASS and no new hardcoded UI strings.**
- [ ] **Step 3: Commit with `git add app/src/main/java app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml` and `git commit -m "fix: localize generic loading failures"`.**
### Task 4: Build and verify both locale paths
**Files:** Verify the two string XML files and the localization test; do not change schema, navigation, or version.
**Interfaces:** Produce a verified debug APK and emulator evidence for Simplified Chinese and English locales.
- [ ] **Step 1: Run `tools\gradlew-local.bat clean test assembleDebug lintDebug`; expect `BUILD SUCCESSFUL`, all tests green, no resource/lint errors, and `app/build/outputs/apk/debug/app-debug.apk`.**
- [ ] **Step 2: Install on `Pixel_9`, set Simplified Chinese locale with Android shell controls, relaunch `com.ojnexus`, and inspect Dashboard, Problems, Training, Analytics, Profile, Settings, Command Palette, and Contest/Arena for Chinese static labels; confirm no fatal runtime log.**
- [ ] **Step 3: Restore English locale, relaunch, and confirm primary labels return to `DASHBOARD`, `PROBLEMS`, `TRAINING`, `ANALYTICS`, and `PROFILE`.**
- [ ] **Step 4: Run `git status --short` and `git diff --check`; worktree must be clean. Do not push or create a GitHub Release unless separately requested.**
