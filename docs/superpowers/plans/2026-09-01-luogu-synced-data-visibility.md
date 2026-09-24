# Luogu Synced Data Visibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make synchronized Luogu Rating data visible in Analytics and remove stale Profile guidance.

**Architecture:** Extend the existing pure Analytics empty-state predicate to include non-empty per-judge Rating histories. Simplify Profile’s rated-contest row to depend on the aggregate count rather than Codeforces account presence, with localized fallback copy.

**Tech Stack:** Kotlin, Jetpack Compose, Coroutines/Flow, JUnit.

**Spec:** `docs/superpowers/specs/2026-09-01-luogu-synced-data-visibility-design.md`

## Global Constraints

- Keep public Luogu sync local-first and public-data-only.
- Do not add passwords, cookies, sessions, CSRF login, cloud accounts, or cross-device sync.
- Do not fabricate attempts or alter OpenApp submission behavior.
- Keep all user-facing strings in English and `values-zh-rCN` resources.

### Task 1: Include Rating history in Analytics content detection

**Files:**
- Modify: `app/src/main/java/com/ojnexus/feature/analytics/AnalyticsViewModel.kt`
- Test: `app/src/test/java/com/ojnexus/feature/analytics/RatingHistoryMapTest.kt`

**Interfaces:**
- Produces `internal fun analyticsHasData(totals: Totals, ratingHistories: Map<JudgeId, List<RatingChangeEntity>>): Boolean`.

- [ ] **Step 1: Write the failing test**

Use zero `Totals` and a one-entry Luogu history; assert `analyticsHasData` is true. Also assert
zero totals plus an empty history remains no data.

- [ ] **Step 2: Run the focused test and verify the failure**

Run `.\tools\gradlew-local.bat testDebugUnitTest --tests com.ojnexus.feature.analytics.RatingHistoryMapTest --no-daemon --console=plain`.
Expected: compilation failure until the helper exists.

- [ ] **Step 3: Implement the predicate**

Define `analyticsHasData` as local attempts > 0, local problems > 0, or any non-empty Rating
history; define `analyticsHasNoData` as its negation and use it for `AnalyticsUiState.isEmpty`.

- [ ] **Step 4: Run the focused test**

Run the same command and require `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the Analytics change**

Run `git add app/src/main/java/com/ojnexus/feature/analytics/AnalyticsViewModel.kt app/src/test/java/com/ojnexus/feature/analytics/RatingHistoryMapTest.kt && git commit -m "feat: show rating-only analytics / 显示仅 Rating 的分析数据"`.

### Task 2: Make Profile’s Rated contest summary judge-independent

**Files:**
- Modify: `app/src/main/java/com/ojnexus/feature/profile/ProfileScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`

- [ ] **Step 1: Render from aggregate history**

Show `RATED CONTESTS` and `state.ratedContests` whenever the count is positive. Otherwise show
the localized no-history label. Do not use `cfAccount` as the visibility gate.

- [ ] **Step 2: Replace stale copy**

Keep the legacy resource name for compatibility but change its English/Chinese wording to a
Settings instruction, and add `profile_no_rated_contests` in both locales for the neutral fallback.

- [ ] **Step 3: Run the Profile and Analytics tests**

Run `.\tools\gradlew-local.bat testDebugUnitTest --tests com.ojnexus.feature.profile.* --tests com.ojnexus.feature.analytics.* --no-daemon --console=plain` and require success.

- [ ] **Step 4: Commit the Profile change**

Run `git add app/src/main/java/com/ojnexus/feature/profile/ProfileScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml && git commit -m "fix: surface synced judge history in profile / 修复 Profile 同步历史展示"`.

### Task 3: Document, verify, and publish

**Files:**
- Modify: `README.md`
- Modify: `docs/ROADMAP.md`
- Create: `docs/releases/v0.3.32.md`

- [ ] **Step 1: Add bilingual Phase 36 and release notes**

Preserve every earlier phase entry and state that Rating-only data no longer enters Analytics
empty state, while the no-password/no-cookie/no-cloud boundary remains unchanged.

- [ ] **Step 2: Run the full gate**

Run `git diff --check; .\tools\gradlew-local.bat clean test assembleDebug lintDebug --no-daemon --rerun-tasks --console=plain` and require `BUILD SUCCESSFUL`.

- [ ] **Step 3: Install and inspect the current emulator**

Install the final APK, launch `com.ojnexus/.MainActivity`, verify the app is resumed, and keep
`emulator-5554` online.

- [ ] **Step 4: Publish v0.3.32 and verify**

Push the branch, create/push tag `v0.3.32`, attach the APK with `gh release create`, and compare
the Release asset digest with the local SHA256. Confirm the remote branch, tag, and local HEAD
match and the worktree is clean.
