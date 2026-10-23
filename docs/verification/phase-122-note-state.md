# Phase 122-a — Android note editor state

Status: **HOST AND SCOPED EMULATOR REVALIDATION PASSED**. Scope is the reproduced note editor state defect in `ProblemDetailViewModel`; this does not complete Phase 142 note conflict recovery/search, broader process-state restoration, or Settings backup work. Phase 122 remains ACTIVE because subsequent backup testing found a separate WAL snapshot defect.

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
- After independent review and test-cleanup repair at `da51c66`, the coordinating lane reran `testDebugUnitTest --rerun test assembleDebug assembleRelease`: **BUILD SUCCESSFUL in 2m26s**, exit 0; fresh XML again **171 suites / 782 tests / 0 failures / 0 errors / 0 skipped**. Log `%TEMP%/phase122-note-reviewed-final.log`. This run supersedes the focused-only limitation for the final test source.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Unsigned Release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`.

Host commands set `JAVA_HOME=D:/Android Studio/jbr` and `ANDROID_HOME=D:/Android` for the process only. No machine path was added to repository configuration. The build printed existing Kotlin warnings and Android native-library strip notices; all required tasks completed successfully.

## Scoped runtime evidence and remaining boundary

The independent validator owned `emulator-5580` (API 37, dedicated disposable AVD). At combined acceptance commit `03948a9`, containing the same note production fix plus the separate Settings snapshot wiring fix, it entered `PHASE122`, continuously appended `_CONTINUOUS_20261023`, and observed the complete text with SAVED. After force-stop and a COLD restart, the complete marker remained visible. Dumps took longer than the debounce, so this is usability and eventual-persistence evidence, not a measured sub-600ms visual timing claim.

That combined APK also passed 33 connected tests. Full device evidence is in the coordinating checkout's `docs/verification/phase-122-android.md`; local XML/screenshots are `%TEMP%/phase122-fixed-notes-continuous.xml`, `phase122-fixed-notes-reopened.xml` and `phase122-fixed-notes.png`. No physical-device, TalkBack or battery acceptance is inferred. Later changes to the regression-test lifecycle do not change the installed production code.
