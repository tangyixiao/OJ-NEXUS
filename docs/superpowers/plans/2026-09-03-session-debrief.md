# Session Debrief Implementation Plan

> For agentic workers: use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a terminal-session debrief that classifies attached problems, exposes the latest in-session verdict and existing review state, and routes each row to review or problem details.

**Architecture:** Extend the existing reactive session-progress Room query with latest_verdict and in_review, map those projections into SessionProblem, and derive a pure three-lane debrief model for UI filtering. Render the debrief only in terminal sessions; keep the Phase 62 live board unchanged and use existing navigation routes.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Room/Flow, JUnit/Robolectric, existing NEXUS design tokens and localized Android resources.

**Spec:** docs/superpowers/specs/2026-09-03-session-debrief-design.md

## Global Constraints

- Use the existing single app module and Kotlin/Compose/Material 3 stack.
- Keep local-first: no new network request, background work, credential flow, migration, or session state.
- Keep colors, spacing, typography, shapes, and motion in core/designsystem.
- Add every new UI string to both English and Simplified Chinese resources.
- Preserve accessibility with text state, meaningful descriptions, and at least 48dp action targets.
- Respect NexusTheme.reduceMotion and use existing motion tokens.
- Preserve existing session history, attempts, reviews, and unrelated files.

---

### Task 1: Extend the session progress projection

**Files:**
- Modify: app/src/main/java/com/ojnexus/core/database/dao/SessionDao.kt
- Modify: app/src/main/java/com/ojnexus/core/model/TrainingModels.kt
- Modify: app/src/main/java/com/ojnexus/core/data/repository/TrainingRepository.kt
- Test: app/src/test/java/com/ojnexus/core/data/repository/TrainingRepositorySessionTest.kt

**Interfaces:**
- SessionProblemProgressRow gains latestVerdict: String? and inReview: Boolean.
- SessionProblem gains trailing latestVerdict: Verdict? = null and inReview: Boolean = false parameters.
- TrainingRepository maps a non-null raw verdict with Verdict.fromRaw and leaves a missing verdict null.

- [ ] Write a failing repository test for an in-window AC after a pre-session WA, asserting latestVerdict is AC, attempts is 2, and a scheduled review maps to inReview=true. Extend the existing finished-session test to assert a post-finish AC cannot replace the in-window WA.
- [ ] Run .\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.core.data.repository.TrainingRepositorySessionTest --no-daemon --console=plain. Expected: compilation failure because the new SessionProblem fields do not exist.
- [ ] Add a correlated latest-attempt subquery ordered by timestamp DESC and id DESC, and an EXISTS reviews projection to observeSessionProblemProgress. Include judge and external_id in GROUP BY. Map both fields through SessionProblem.
- [ ] Run the same focused test. Expected: PASS.
- [ ] Commit with message feat: expose session debrief verdicts.

### Task 2: Add pure debrief lanes

**Files:**
- Create: app/src/main/java/com/ojnexus/feature/training/SessionDebrief.kt
- Create: app/src/test/java/com/ojnexus/feature/training/SessionDebriefTest.kt

**Interfaces:**
- enum class SessionDebriefLane { SOLVED, ATTENTION, PENDING }
- data class SessionDebriefPulse(val solved: Int, val attention: Int, val pending: Int)
- fun SessionProblem.debriefLane(): SessionDebriefLane
- fun deriveSessionDebriefPulse(problems: List<SessionProblem>): SessionDebriefPulse
- fun filterSessionDebrief(problems: List<SessionProblem>, lane: SessionDebriefLane?): List<SessionProblem>

- [ ] Write failing tests for solved, attempted-but-unsolved, pending, empty input, pulse counts, null filter, and order-preserving lane filter.
- [ ] Run .\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.training.SessionDebriefTest --no-daemon --console=plain. Expected: compilation failure because the types do not exist.
- [ ] Implement solved-first classification, attempts greater than zero as ATTENTION, and all remaining rows as PENDING. Null filtering returns the original list.
- [ ] Run the focused test. Expected: PASS.
- [ ] Commit with message feat: classify session debrief lanes.

### Task 3: Add terminal debrief UI and localization

**Files:**
- Modify: app/src/main/res/values/strings.xml
- Modify: app/src/main/res/values-zh-rCN/strings.xml
- Modify: app/src/main/java/com/ojnexus/feature/training/SessionScreen.kt
- Create: app/src/test/java/com/ojnexus/feature/training/SessionDebriefUiLayoutTest.kt

**Interfaces:**
- SessionSummaryView calls SessionDebriefPanel(problems, onOpenProblem, onOpenReview).
- SessionDebriefPanel owns a screen-local SessionDebriefLane? and animates filtered content with existing tokens.
- inReview=true calls onOpenReview; otherwise the row calls onOpenProblem.

- [ ] Add both locale pairs for session_debrief_title, session_debrief_all, session_debrief_solved, session_debrief_attention, session_debrief_pending, session_debrief_empty, session_debrief_latest, session_debrief_attempts, session_debrief_open, session_debrief_open_review, and session_debrief_open_cd.
- [ ] Write a failing source regression test asserting SessionScreen wires SessionDebriefPanel in the terminal summary and contains both review and problem action paths.
- [ ] Run .\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.training.SessionDebriefUiLayoutTest --no-daemon --console=plain. Expected: failure because the panel is not wired.
- [ ] Implement the panel after the existing terminal summary metrics: title, four filters, three pulse metrics, filtered rows, latest verdict tag, attempt count, review/details action, localized empty state, and 48dp semantics. Keep SessionProgressBoard in the live view only. Use animateContentSize with snap when reduceMotion is true.
- [ ] Run the focused UI test and compile with .\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.feature.training.SessionDebriefUiLayoutTest --no-daemon --console=plain. Expected: PASS.
- [ ] Commit with message feat: add terminal session debrief.

### Task 4: Wire review navigation and version 0.3.60

**Files:**
- Modify: app/src/main/java/com/ojnexus/app/NexusApp.kt
- Modify: app/src/test/java/com/ojnexus/app/NexusRoutesTest.kt
- Modify: app/build.gradle.kts
- Modify: README.md
- Modify: docs/ROADMAP.md
- Create: docs/releases/v0.3.60.md

**Interfaces:**
- Both session route call sites pass onOpenReview = { problemId -> navController.navigate(NexusRoutes.review(problemId)) } or the existing equivalent route.
- versionName becomes 0.3.60 and versionCode becomes 60.

- [ ] Write the failing route test for NexusRoutes.review(42L) == review/42 when no helper exists.
- [ ] Run .\tools\gradlew-local.bat :app:testDebugUnitTest --tests com.ojnexus.app.NexusRoutesTest --no-daemon --console=plain. Expected: compilation failure if the helper is absent.
- [ ] Add NexusRoutes.review(problemId: Long), wire both session routes, bump package metadata, update current README and ROADMAP status to Phase 63 while retaining history, and add a release note whose verification section is completed after the emulator run.
- [ ] Run focused route and debrief tests. Expected: PASS.
- [ ] Commit with message release: prepare v0.3.60.

### Task 5: Full verification and emulator evidence

**Files:**
- Verify generated app/build/outputs/apk/debug/app-debug.apk and app/build/reports/ artifacts.

- [ ] Run .\tools\gradlew-local.bat test assembleDebug lintDebug --no-daemon --console=plain. Expected: BUILD SUCCESSFUL.
- [ ] Install the APK on emulator-5554 and verify Android reports versionCode=60 and versionName=0.3.60.
- [ ] Exercise a real local session: create it, finish it, open history, verify SESSION DEBRIEF, all four filters, OPEN REVIEW for an existing review row, OPEN for another row, and no new FATAL EXCEPTION in the crash buffer.
- [ ] Capture app/build/reports/ojnexus-session-debrief-v060.png, record APK SHA-256 and runtime evidence in docs/releases/v0.3.60.md, run git diff --check, and commit the verification update with message docs: record v0.3.60 verification.

**Plan complete.**
