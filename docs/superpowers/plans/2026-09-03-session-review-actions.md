# Session Review Actions — Implementation Plan

## Scope

Implement the Phase 64 terminal debrief action defined in
`docs/superpowers/specs/2026-09-03-session-review-actions-design.md`.

## Tasks

### 1. Add the pure candidate selector

- Update `SessionDebrief.kt` with `sessionReviewCandidates`.
- Add tests for lane filtering, solved exclusion, existing review exclusion, and stable order.
- Run the focused unit test and confirm it fails before implementation, then passes.

### 2. Add atomic repository scheduling

- Add `scheduleReviews(problemIds: List<Long>): DataResult<Int>` to `ReviewRepository`.
- Validate every problem first, then insert stage-0 rows in one Room transaction.
- Add repository tests for batch success, empty no-op, and rollback on an unknown ID.
- Run the focused repository tests before continuing.

### 3. Wire ViewModel and debrief UI

- Inject `ReviewRepository` into `SessionViewModel` and its factory.
- Add the schedule action and failure handling while preserving reactive `sessionProblems`.
- Pass the callback through `SessionSummaryView` to `SessionDebriefPanel`.
- Add localized English and Simplified Chinese strings.
- Render the count/action or quiet ready status using existing design tokens and reduced-motion
  behavior; keep row navigation unchanged.
- Extend the source-layout test.

### 4. Release metadata and documentation

- Advance the current package identity to `versionCode=62`, `versionName=0.3.62`.
- Update only current-status references; preserve historical release text.
- Add `docs/releases/v0.3.62.md` after verification with actual build, runtime, and APK hash
  evidence.
- Commit each logical task with `feat:`, `test:`, `release:`, or `docs:` messages.

### 5. Verify end to end

- Run `git diff --check`.
- Run `./tools/gradlew-local.bat test assembleDebug lintDebug --no-daemon --console=plain`.
- Install the debug APK on `emulator-5554`, verify Android package identity, and exercise the
  finished-session debrief action with existing local data.
- Check crash log output and final `git status --short --branch` before reporting the phase.

