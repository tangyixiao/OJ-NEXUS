# Phase 127A — Recovery preservation and startup gate

Status: ACTIVE; runtime implementation and acceptance are in progress. The full Phase127
remains incomplete. No new application release or verified recovery claim is made here.

## Source and scope

- [Design](../superpowers/specs/2026-10-24-phase-127a-recovery-safety-design.md)
- [Plan](../superpowers/plans/2026-10-24-phase-127a-recovery-safety.md)
- Integration base `b096045`, ref `codex/phase-127-android-base`.
- Working branch `codex/phase-127-recovery-safety`; spec/plan initially committed as `9c5ef14`.
- Integration combines accepted Android Phase122 `19f0bbc` with Phase126 `036b333`, depending
  on unmerged PRs #14/#15/#18/#19. No upstream PR branch or main was rewritten.

This delivery boundary fixes source preservation and readiness before the application graph
opens. Protocol, Application/Activity and all three Worker entry gates must pass together.
It does not yet prove strict old/missing-generation worker isolation, typed storage-full import
handling or old SQLite snapshot export compatibility. Those remain required Phase127 work.

## Verified integration baseline

The integration base, before Phase127 runtime edits, passed:

| Check | Result |
| --- | --- |
| `gradlew.bat testDebugUnitTest --rerun test assembleDebug` | Exit0, BUILD SUCCESSFUL in 2m08s |
| Fresh XML | 800 tests, 176 suites; 0 failures/errors/skips |
| Scope of this evidence | Integration baseline only; not acceptance of new recovery code |

The local baseline log is retained with the plan workspace. Existing Conscrypt native-access
and DataStore/protobuf Unsafe deprecation warnings were observed; neither caused a failing test.

## Runtime acceptance still required

- Real coordinator/SQLite/WAL tests: exact generation repair, source-copy failure, partial rollback,
  repeated startup, legacy split sources and unknown recovery evidence.
- Actual Application and Worker readiness entry tests; no app data access while Starting/Blocked.
- DB-free blocked UI, actual Activity launch/resume, and a missing target remaining missing.
- Fresh full unit/build/release/connected gates and independent code reviews.
- Scoped fix PR with explicit integration dependencies. No full Phase127 VERIFIED or new release
  until the relevant remaining gates independently pass.

## Dedicated device boundary

Created `OJNexus_Phase127_Temp` using the already installed
`system-images;android-37.1;google_apis_playstore_ps16k;x86_64` image and pixel_7 profile.
Only intended serial `emulator-5582` belongs to this phase. Existing Phase122 AVD is retained.
AVD creation is preparation, not a boot, application-launch or device-test result. Device startup
and application evidence will be recorded after the actual checks.

All database records used in fault tests are synthetic fixture identities. Materialized checkpoint
files and injected I/O exceptions are reported separately from real process termination. No
power-loss durability, physical-device behavior or old-engine compatibility is inferred.
