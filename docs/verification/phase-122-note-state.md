# Phase 122-a — Android note editor state

Status: **HOST VERIFIED; DEVICE REVALIDATION PENDING**. Scope is the reproduced note editor state defect in `ProblemDetailViewModel`; this does not complete Phase 142 note conflict recovery/search, process restoration, or Settings backup work.

## Change and regression coverage

- `ProblemDetailViewModel.state` now combines the stored detail, knowledge relations, note template, editable draft, and saving indicator flows. The screen receives each edit immediately without waiting for a Room/detail emission.
- The draft is seeded from stored notes once. If another upstream emission overtakes a draft-flow notification, the reducer reads the current draft value and keeps user edits.
- The existing 600 ms debounce and repository save remain unchanged. No UI strings, schema, dependencies, or app version were changed.
- Added Room-backed Robolectric tests for immediate state emission, consecutive edits surviving a later stored-detail/knowledge emission, complete Unicode debounce persistence, and observable saving state while a Room transaction holds the write.
- Test cleanup now always releases and joins the transaction blocker, clears the ViewModel through a `ViewModelStore`, and waits for its `onCleared` flush before closing Room. The upstream-emission regression waits for updated detail and knowledge values before asserting draft ownership.

## TDD and host gates

- RED: `:app:testDebugUnitTest --tests 'com.ojnexus.feature.problems.ProblemDetailViewModelTest.state emits note draft edits before detail flow changes'` failed on the expected `ComparisonFailure` at the state assertion while production code still read `notesDraft.value` outside the combined flows. Log: `%TEMP%/phase-122-note-red.log`.
- Focused GREEN: `:app:testDebugUnitTest --tests 'com.ojnexus.feature.problems.ProblemDetailViewModelTest'` passed all 3 new tests. Log: `%TEMP%/phase-122-note-focused-green.log`.
- Review follow-up focused run: same test class passed **3 tests, 0 failures, 0 errors, 0 skipped** after cleanup hardening; class XML suite timestamp `2026-10-23T13:41:43.004Z`. The blocker released and the Gradle test task exited normally. Log: `%TEMP%/phase-122-note-review-focused.log`. The test-only lifecycle edit did not rerun the full suite or APK builds.
- Final gate after the last production edit: `test assembleDebug assembleRelease` — **BUILD SUCCESSFUL** (2026-10-23 21:28 +08:00). Log: `%TEMP%/phase-122-note-final-gates.log`.
- Fresh `testDebugUnitTest` XML: 171 suites, **782 tests, 0 failures, 0 errors, 0 skipped**; newest XML timestamp `2026-10-23T21:28:39.919+08:00` in `app/build/test-results/testDebugUnitTest`.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Unsigned Release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`.

Host commands set `JAVA_HOME=D:/Android Studio/jbr` and `ANDROID_HOME=D:/Android` for the process only. No machine path was added to repository configuration. The build printed existing Kotlin warnings and Android native-library strip notices; all required tasks completed successfully.

## Remaining runtime evidence

No ADB or emulator commands were run from this worktree because the Phase 122 validator owns `emulator-5580`. Re-run the real note editor flow on that validator-controlled device: type multiple characters into the focused field, confirm each character remains visible, wait beyond the debounce, leave/reopen the problem, and verify the complete text is still present. Until that check is recorded by the validator, this result is host verified and device revalidation remains pending.
