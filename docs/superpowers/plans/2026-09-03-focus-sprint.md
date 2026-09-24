# Focus Sprint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a deterministic, editable `FOCUS SPRINT` preset to Training without changing the existing session persistence flow.

**Architecture:** A pure feature helper selects due reviews first and ranked recommendations second. Training renders the plan and passes its IDs plus localized preset defaults into the existing session dialog, which continues to call the current ViewModel and repository transaction.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Room/Flow, JUnit, existing NEXUS design tokens and localization.

**Spec:** `docs/superpowers/specs/2026-09-03-focus-sprint-design.md`

## Global Constraints

- Keep the app native Kotlin/Compose/Material 3; do not add a module or third-party UI library.
- Keep all data local-first; do not add a network call, database migration, credential flow, or new session state.
- Use `NexusSpacing`, `NexusSize`, `NexusRadius`, `NexusMotion`, and `NexusTheme`; no raw feature colors or arbitrary layout literals.
- Route every new UI string through both `values/strings.xml` and `values-zh-rCN/strings.xml`.
- Preserve the existing session dialog, repository transaction, active-session guard, and reduced-motion behavior.

---

### Task 1: Deterministic focus-sprint selector

**Files:**
- Create: `app/src/main/java/com/ojnexus/feature/training/FocusSprint.kt`
- Test: `app/src/test/java/com/ojnexus/feature/training/FocusSprintTest.kt`

**Interfaces:**
- Consumes `ReviewBuckets`, `TrainingRecommendation`, and an optional item limit.
- Produces `FocusSprintPlan(items: List<FocusSprintItem>)`, with `ids`, `dueCount`, and `targetCount` derived from the selected items.
- Exposes `buildFocusSprintPlan(buckets: ReviewBuckets, recommendations: List<TrainingRecommendation>, limit: Int = 5)`.

- [ ] **Step 1: Write the failing tests** for due-first ordering, recommendation fallback, duplicate removal, five-item cap, and empty input.
- [ ] **Step 2: Run the focused test**

Run: `.\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.training.FocusSprintTest --no-daemon --console=plain`

Expected: FAIL because `FocusSprintPlan` and `buildFocusSprintPlan` do not exist.

- [ ] **Step 3: Implement the pure selector** with stable sorting and `coerceAtLeast(0)` limit handling.
- [ ] **Step 4: Run the focused test again** and require `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ojnexus/feature/training/FocusSprint.kt app/src/test/java/com/ojnexus/feature/training/FocusSprintTest.kt
git commit -m "feat: add focus sprint selection plan"
```

### Task 2: Training focus panel and dialog preset

**Files:**
- Modify: `app/src/main/java/com/ojnexus/feature/training/TrainingScreen.kt`
- Modify: `app/src/test/java/com/ojnexus/feature/training/ReviewRunUiLayoutTest.kt` or create `FocusSprintUiLayoutTest.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`

**Interfaces:**
- Consumes `FocusSprintPlan` from `buildFocusSprintPlan(uiState.reviews, uiState.recommendations)`.
- `SessionSection` receives `focusSprintPlan` and an `onFocusSprint` callback.
- `NewSessionDialog` receives `initialType`, `initialDuration`, `initialTag`, and `initialSelectedIds`; manual creation passes the existing defaults.

- [ ] **Step 1: Extend the UI source test first** to require `FOCUS SPRINT`, `initialSelectedIds`, `animateContentSize`, and reduced-motion handling.
- [ ] **Step 2: Run the focused UI test** and require it to fail on the missing panel/preset identifiers.
- [ ] **Step 3: Add the bilingual resource keys** for focus panel labels, source counts, empty state, action description, and preset tag.
- [ ] **Step 4: Implement the panel** using existing section, metric, tag, divider, button, spacing, size, and motion components. Disable launch when `plan.items` is empty and expose `contentDescription` on the action.
- [ ] **Step 5: Wire the inactive session section** to open `NewSessionDialog` in focus mode while retaining the existing manual dialog behavior.
- [ ] **Step 6: Run the focused UI/layout tests** and require `BUILD SUCCESSFUL`.
- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/ojnexus/feature/training/TrainingScreen.kt app/src/test/java/com/ojnexus/feature/training/FocusSprintUiLayoutTest.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml
git commit -m "feat: add focus sprint training panel"
```

### Task 3: Version, documentation, and release verification

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `README.md`
- Modify: `docs/ROADMAP.md`
- Create: `docs/releases/v0.3.64.md`

- [ ] **Step 1: Run the complete existing test suite** before version changes and require `BUILD SUCCESSFUL`.
- [ ] **Step 2: Set `versionCode = 64` and `versionName = "0.3.64"`, update the current README/roadmap identity, and document Phase 66 in English and Simplified Chinese.
- [ ] **Step 3: Run `.\tools\gradlew-local.bat test assembleDebug lintDebug --no-daemon --console=plain` and record the final APK SHA-256.
- [ ] **Step 4: Install the APK on `emulator-5554`, verify package identity, open Training, verify the focus panel and pre-filled dialog, and check logcat for fatal exceptions.
- [ ] **Step 5: Capture `app/build/reports/ojnexus-focus-sprint-v064.png` and record the evidence in `docs/releases/v0.3.64.md`.
- [ ] **Step 6: Commit the release artifacts**

```bash
git add app/build.gradle.kts README.md docs/ROADMAP.md docs/releases/v0.3.64.md
git commit -m "release: prepare v0.3.64"
```

### Task 4: Review and final gate

- [ ] **Step 1:** Review the complete diff against the Phase 65 release commit; verify no credentials, cookies, local paths, migrations, or unrelated changes were added.
- [ ] **Step 2:** Run `git diff --check`, `test assembleDebug lintDebug`, and the focused selector/UI tests on the final HEAD.
- [ ] **Step 3:** Request code review and fix every Critical/Important finding.
- [ ] **Step 4:** Repeat the full gate after all fixes, verify a clean worktree, and report the exact version, APK path, hash, runtime PID, and screenshot path.
