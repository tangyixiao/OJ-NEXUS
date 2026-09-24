# Live Session Board Implementation Plan

> For agentic workers: REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with review checkpoints.

Goal: Make active and historical training sessions actionable through reactive per-problem progress, pulse metrics, and direct problem navigation.

Architecture: Add a read-only Room projection that aggregates attempts inside each session's persisted time window. Map it through TrainingRepository into the existing SessionProblem model, expose it from SessionViewModel, and render a shared queue/pulse in the existing Session screen. Navigation reuses problem/{id}; no new table, state, network call, or route is introduced.

Tech Stack: Kotlin, Room DAO projections, Coroutines Flow/StateFlow, Jetpack Compose, Material 3, JUnit/Robolectric.

Spec: docs/superpowers/specs/2026-09-03-live-session-board-design.md

## Global Constraints

- Keep the native Kotlin + Compose Material 3 stack and existing single app module.
- Preserve SessionStateMachine; this phase is read-only progress presentation.
- Do not change the Room schema version or add a migration.
- Put every new UI string in both values/strings.xml and values-zh-rCN/strings.xml.
- Use NexusTheme, NexusSpacing, NexusSize, NexusRadius, and NexusMotion; no raw colors or looping effects.
- Keep missing and empty data explicit in text; state must not be conveyed by color alone.
- Verify with test, assembleDebug, and lintDebug, then install and launch on emulator-5554.

---

### Task 1: Add the reactive session-progress projection

Files:
- Modify: app/src/main/java/com/ojnexus/core/database/dao/SessionDao.kt
- Modify: app/src/main/java/com/ojnexus/core/data/repository/TrainingRepository.kt
- Test: app/src/test/java/com/ojnexus/core/data/repository/TrainingRepositorySessionTest.kt

Interfaces:
- Produce internal DAO projection SessionProblemProgressRow(problemId: Long, title: String, difficulty: Int?, attempts: Int, solved: Boolean).
- Produce TrainingRepository.observeSessionProblems(sessionId: Long): Flow<List<SessionProblem>>.

- [ ] Step 1: Write the failing repository test. Add a Robolectric test that creates two problems, starts a session containing both, inserts a WA and an AC after the session starts, inserts a pre-session attempt, and collects observeSessionProblems(sessionId).first(). Assert attempts are [2, 1], solved is [true, false], and the old attempt is excluded. Add a second test that starts a session with no problem IDs and asserts an empty list.
- [ ] Step 2: Run the focused test and confirm it fails. Run .\tools\gradlew-local.bat testDebugUnitTest --tests "com.ojnexus.core.data.repository.TrainingRepositorySessionTest" --no-daemon --console=plain. Expected: compilation failure because the new projection and repository method do not exist.
- [ ] Step 3: Add the DAO projection. Declare SessionProblemProgressRow above SessionDao and add observeSessionProblemProgress(sessionId: Long): Flow<List<SessionProblemProgressRow>> with a query joining training_session_problems, training_sessions, and problems, left-joining attempts on the same problem and requiring a.timestamp >= session.started_at plus (session.finished_at IS NULL OR a.timestamp <= session.finished_at). Select p.id AS problem_id, p.title, p.difficulty, COUNT(a.id) AS attempts, and CASE WHEN MAX(CASE WHEN a.verdict = 'AC' THEN 1 ELSE 0 END) = 1 THEN 1 ELSE 0 END AS solved; group by problem identity and order by link.problem_id ASC.
- [ ] Step 4: Map the flow in TrainingRepository. Map each row to SessionProblem; do not catch projection errors to an empty list, so the existing screen Loadable.Failed path remains meaningful. If Room requires a Long count, use a Long projection and map with a non-negative toInt().
- [ ] Step 5: Run focused tests and commit. Rerun the focused test and expect PASS for window, solved, attempted, pending, and empty cases. Commit feat: expose reactive session problem progress with only the DAO, repository, and test files.

### Task 2: Add pure pulse derivation and ViewModel state

Files:
- Create: app/src/main/java/com/ojnexus/feature/training/SessionProgress.kt
- Create: app/src/test/java/com/ojnexus/feature/training/SessionProgressTest.kt
- Modify: app/src/main/java/com/ojnexus/feature/training/SessionViewModel.kt

Interfaces:
- Produce SessionProgressPulse(total: Int, solved: Int, attempted: Int, pending: Int).
- Produce deriveSessionProgressPulse(problems: List<SessionProblem>): SessionProgressPulse.
- Add problems: List<SessionProblem> to SessionSurfaceState.
- Expose sessionProblems: StateFlow<List<SessionProblem>> from SessionViewModel and include it in state.

- [ ] Step 1: Write failing pure tests. Test emptyList() -> SessionProgressPulse(0, 0, 0, 0), and a three-row list (0 attempts, 2 non-AC attempts, 1 AC attempt) -> SessionProgressPulse(3, 1, 2, 1). Assert solved is a subset of attempted and pending means zero attempts.
- [ ] Step 2: Run the focused test and confirm it fails. Run .\tools\gradlew-local.bat testDebugUnitTest --tests "com.ojnexus.feature.training.SessionProgressTest" --no-daemon --console=plain. Expected: compilation failure because the new helper does not exist.
- [ ] Step 3: Implement the helper and ViewModel flow. Use only collection counts in SessionProgress.kt. In SessionViewModel, flat-map sessionFlow: emit emptyList() only when no session exists; otherwise collect trainingRepository.observeSessionProblems(session.id). Combine that flow with the existing session, live count, summary, and action-error streams, and put the list into SessionSurfaceState. Keep the existing suspend summary calculation unchanged.
- [ ] Step 4: Run both focused tests and commit. Expect PASS for the pure pulse and repository suites. Commit feat: model live session progress.

### Task 3: Render the Session Pulse and Problem Queue

Files:
- Modify: app/src/main/java/com/ojnexus/feature/training/SessionScreen.kt
- Modify: app/src/main/java/com/ojnexus/app/NexusApp.kt
- Modify: app/src/main/res/values/strings.xml
- Modify: app/src/main/res/values-zh-rCN/strings.xml
- Test: extend existing training/navigation test coverage with route-level assertions

Interfaces:
- Change SessionScreen to accept onOpenProblem: (Long) -> Unit.
- Pass problems: List<SessionProblem> and onOpenProblem into both live and terminal renderers.
- Add private SessionProgressBoard(problems: List<SessionProblem>, onOpenProblem: (Long) -> Unit).

- [ ] Step 1: Add mirrored resources. Add English keys session_section_pulse, session_progress_solved, session_progress_attempted, session_progress_pending, session_progress_complete, session_queue_title, session_queue_empty, session_problem_open, and session_problem_open_cd. Use Chinese values 会话脉冲, 已解决, 已尝试, 待处理, %1$d / %2$d 已解决, 题目队列, 未附加题目, 打开, and 打开题目详情.
- [ ] Step 2: Add route-level test coverage. Assert the existing problem/{id} route builder receives the selected session problem ID, and assert the pulse helper has no divide-by-zero path for an empty queue. Keep the test pure; do not fake a Compose screenshot.
- [ ] Step 3: Implement the board. Render a NexusSection pulse with total, solved, attempted, and pending metrics plus one thin determinate rail. Use solved.toFloat() / total only for total > 0; otherwise use the neutral border color and explicit empty text. Animate rail width with NexusMotion.DURATION_NORMAL, using snap() for reduced motion. Render a queue with status rail, judge/external ID, title, attempts, localized status text, and an OPEN control with Role.Button, content description, and minimum touch target. Use only named design tokens and the existing theme.
- [ ] Step 4: Place and wire the board. Put it after session metrics in the live view and after summary metrics in the terminal view, preserving all lifecycle and summary controls. Wire both NexusApp Session routes to navController.navigate("problem/$problemId"); add no destination.
- [ ] Step 5: Run focused UI/resource tests and commit. Run training package tests plus test. Expect no missing resources, no localization parity failure, and no Compose compile errors. Commit feat: add live session problem board.

### Task 4: Release metadata, verification, and runtime evidence

Files:
- Modify: app/build.gradle.kts
- Modify: README.md
- Modify: docs/ROADMAP.md
- Create: docs/releases/v0.3.59.md

- [ ] Step 1: Advance the package identity. Change only the current identity to versionCode = 59 and versionName = "0.3.59"; preserve historical release text.
- [ ] Step 2: Update current documentation. Make the top status sections describe Phase 62 Live Session Board and exact identity 0.3.59 / 59. Add bilingual release notes listing reactive progress, direct navigation, no-migration boundary, and verification. Fill APK metadata and checksum only from the completed runtime commands.
- [ ] Step 3: Run the full gate. Run .\tools\gradlew-local.bat test assembleDebug lintDebug --no-daemon --console=plain and git diff --check; expect BUILD SUCCESSFUL and no diff-check error.
- [ ] Step 4: Install and inspect. Install app-debug.apk on emulator-5554; read dumpsys package com.ojnexus and verify versionCode=59 / versionName=0.3.59; record Get-FileHash -Algorithm SHA256. Launch the app, verify the honest Training empty state if no session exists, and clear/read crash logcat. If a local session exists, verify its queue and OPEN route. Never inject demo data solely for evidence.
- [ ] Step 5: Record and commit. Fill docs/releases/v0.3.59.md with actual build, package, checksum, runtime, and fatal-log evidence. Run status/log/diff checks, inspect for secrets and unrelated changes, then commit release: prepare live session board v0.3.59.
- [ ] Step 6: Final branch check. Run git status --short --branch and git diff HEAD~4..HEAD --check; expect a clean worktree and no diff errors. Do not push or merge without the user's explicit choice.
