# Task 2 Report

Date: 2026-08-30
Branch: `codex/phase-5-arena`

## Scope Completed

Implemented the Luogu local submission-center ViewModel only:

- added `SubmissionCenterViewModel` backed by `LuoguSubmissionCenter`
- exposed `StateFlow<Loadable<SubmissionCenterUiState>>` with `jobs`, `busyRequestIds`, and `actionError`
- used `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)`
- suppressed duplicate `checkResult(requestId)` calls while that request ID is already busy
- preserved cached rows during refresh failures and cleared `actionError` on a later successful retry
- did not touch Compose screens, navigation, resources, auth storage, or unrelated cloud behavior

## Files Changed

- `app/src/main/java/com/ojnexus/feature/submissions/SubmissionCenterViewModel.kt`
- `app/src/test/java/com/ojnexus/feature/submissions/SubmissionCenterViewModelTest.kt`

## TDD / Verification Log

Red step:

- Command: `.\tools\gradlew-local.bat testDebugUnitTest --tests "com.ojnexus.feature.submissions.SubmissionCenterViewModelTest"`
- Result: failed at compile time because `SubmissionCenterViewModel`, `SubmissionCenterUiState`, and `SubmissionCenterActionError` did not exist yet

Intermediate failure after minimal implementation:

- Command: `.\tools\gradlew-local.bat testDebugUnitTest --tests "com.ojnexus.feature.submissions.SubmissionCenterViewModelTest"`
- Result: compiled, then 2 tests failed
- Failure details:
  - `checkResult suppresses duplicate refresh calls while the same request is busy` timed out waiting for busy state
  - `action errors clear on retry while cached rows remain available` failed while observing async state transitions
- Root cause: the test waited on `viewModelScope`-driven state without draining Robolectric's main looper, so async updates were not being observed reliably

Green step:

- Command: `.\tools\gradlew-local.bat testDebugUnitTest --tests "com.ojnexus.feature.submissions.SubmissionCenterViewModelTest"`
- Result: `BUILD SUCCESSFUL`
- Final task output: `4 tests completed, 0 failed`

Self-review checks:

- `git diff --check -- app/src/main/java/com/ojnexus/feature/submissions/SubmissionCenterViewModel.kt app/src/test/java/com/ojnexus/feature/submissions/SubmissionCenterViewModelTest.kt`
- Result: no whitespace errors
- Scope check: only the ViewModel, its unit tests, and this task report were added

## Notes

- The ViewModel keeps row data fully driven by `observeRecentJobs(...)`; refresh actions only track per-request busy state and action error state locally.
- `SubmissionCenterActionError` is intentionally minimal for Task 2: a `Generic(message)` wrapper around refresh failures, matching the brief without introducing resource or screen changes.
- The tests use a fake `LuoguSubmissionCenter` plus a real `MutableStateFlow`, per the brief.
- Robolectric main-looper draining is required in the async wait helpers because the production implementation uses `viewModelScope`.

## Concerns

- I ran only the focused `SubmissionCenterViewModelTest` slice, as required by the brief; I did not run the full `test` or `assembleDebug` suite.
- The Gradle test run emits existing JVM native-access warnings from Conscrypt under Robolectric; they did not fail the build and were not changed here.

## Fix Round 1

Review-driven requirement:

- do not clear a request's existing action error until that same request refresh succeeds
- keep the error visible while that request retry is in flight or fails again
- prevent one request's successful refresh from clearing another request's error

Production changes:

- `SubmissionCenterActionError` is now request-aware through `requestId`
- `checkResult(requestId)` no longer clears `actionError` at retry start
- successful refresh clears the error only when the current visible error belongs to the same `requestId`
- failed refresh still updates the visible error for that request while cached rows remain unchanged

Additional TDD log:

Red step:

- Command: `.\tools\gradlew-local.bat testDebugUnitTest --tests "com.ojnexus.feature.submissions.SubmissionCenterViewModelTest"`
- First result: compile failure because the new request-aware test expectations referenced a `requestId` property that the current `SubmissionCenterActionError.Generic` did not expose yet
- Second result after introducing request-aware errors: 2 failing tests due to test timing assumptions around immediate retry completion
- Root cause: the new tests were trying to observe "retry in flight" on immediate refresh outcomes, so there was no busy window to assert against

Green step:

- Updated the retry tests to use blocking second-attempt refresh helpers
- Command: `.\tools\gradlew-local.bat testDebugUnitTest --tests "com.ojnexus.feature.submissions.SubmissionCenterViewModelTest"`
- Result: `BUILD SUCCESSFUL`
- Final task output: `6 tests completed, 0 failed`

Files updated in fix round:

- `app/src/main/java/com/ojnexus/feature/submissions/SubmissionCenterViewModel.kt`
- `app/src/test/java/com/ojnexus/feature/submissions/SubmissionCenterViewModelTest.kt`
- `.superpowers/sdd/2026-08-30-luogu-submission-center/task-2-report.md`
