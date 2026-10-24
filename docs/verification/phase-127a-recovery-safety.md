# Phase 127A — Recovery preservation and startup gate

Status: VERIFIED for the Phase127A boundary: runtime task reviews, full local build and
dedicated-emulator gates passed at `690b5f5`; independent whole-branch review passed and
[PR #20](https://github.com/tangyixiao/OJ-NEXUS/pull/20) is open and unmerged. The full Phase127 remains ACTIVE.
No new application release or exhaustive recovery claim is made here.

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

## Delivery and remaining phase scope

- Independent whole-branch review of `b096045..30b87b6` passed with no P0/P1/P2 findings.
  Its sole wording nit was fixed in `67724b1`; the 23-case fault suite and debug build passed
  afterward, and exactly one scoped final re-review passed with zero open findings. Only a
  test method name changed after the full runtime/device gates; assertions and production code
  remained identical. Retained full-run XML is separate from the later focused 23-test XML.
- PR #20 targets `codex/phase-127-android-base`, with the four upstream dependencies stated.
  No PR was merged. Main-target-only workflows do not automatically run for this stacked base;
  local acceptance is not represented as hosted CI.
- Keep strict generation admission, typed import-storage errors and functional old-engine export
  compatibility open. No full Phase127 VERIFIED or new release until those gates independently pass.

## Coordinator task checkpoint

Task1 implementation `6fd0c86` and review fixes `d191af4` passed the independent task review
and scoped re-review. This checkpoint alone was not shipped; application startup wiring and
integrated acceptance were subsequently completed below.

| Check on `d191af4` | Observed result |
| --- | --- |
| Focused restore tests | 50 tests across coordinator, file faults, corpus, decisions, protocol and generation; zero failures/errors/skips |
| Settings restore-status test | 1 test, zero failures/errors/skips |
| `assembleDebug` | BUILD SUCCESSFUL |
| Independent scoped re-review | Spec PASS, quality APPROVED; all six initial findings addressed |

The real SQLite fixture has a committed OLD row present only in WAL, with a main-only negative
control. Normal import proves NEW identity without OLD WAL; rollback proves OLD records and
generation. Materialized INSTALLING, ROLLING_BACK and terminal checkpoints are distinguished
from caught I/O faults. Partial-copy and failure immediately before file sync are injected
boundaries, not process-kill or power-loss experiments. Generation temporary-write failure and
actual atomic-replacement failure are separate cases. Reconstructed blocked starts check source
hashes and zero new recovery operations. No device result is inherited from these JVM tests.

## Startup task and full local build checkpoint

Task2 source `c3638ce`, reviewed fixes `0a220be`, and resource-parity fix `690b5f5` pass the
independent task review and scoped re-reviews. Application resolves recovery before constructing
the graph; Activity, the three actual Workers and public scheduling overloads consume readiness.
Starting retries background work, while Blocked returns `RECOVERY_BLOCKED` without business I/O.
The tests count application preference/file/cache/database/resolver access, poison startup hooks,
exercise all scheduling overloads, and include a real clean-install positive control.

Root independently ran the full gate at `690b5f5`:

| Check | Actual result |
| --- | --- |
| `testDebugUnitTest --rerun test assembleDebug assembleRelease` | Exit0, BUILD SUCCESSFUL in 2m57s |
| Debug unit XML | 829 tests / 181 suites; zero failures/errors/skips |
| Android-test APK assembly | BUILD SUCCESSFUL; device execution recorded separately below |
| Release artifact | Unsigned APK; no version change or release publication |

The first full attempt at `0a220be` failed exactly one of 829 tests: the new recovery keys were
missing from the existing Chinese resource catalog. That failure and all 181 XML suites were
retained before the four-key repair and successful full rerun. Existing translations are unchanged;
the new recovery keys retain the specified uppercase English text in both catalogs.

Process deviation: an actual-startup RED run before wiring was requested in the original plan
but not recorded. The actual Worker guard-removal test did fail and then pass when the guard was
restored. Real coordinator startup fixtures, zero-I/O assertions and clean-install controls were
executed; these prove the final behavior without claiming the missing test-first chronology.

Existing Conscrypt native-access and DataStore/protobuf Unsafe warnings remain. Release compilation
also reports pre-existing nullable/always-true warnings in ContestFocusScreen, ProblemDetailScreen
and SettingsScreen; the component test uses the existing deprecated v1 Compose rule API. These
are recorded warnings, not a claim of pristine compiler output. Logs, XML and APK hashes are
retained with the plan evidence.

## Dedicated device boundary

Created `OJNexus_Phase127_Temp` using the already installed
`system-images;android-37.1;google_apis_playstore_ps16k;x86_64` image and pixel_7 profile.
Only intended serial `emulator-5582` belongs to this phase. Existing Phase122 AVD is retained.
The following preparation history is separate from the executed device results below.

Preparation update: `emulator-5582` confirmed its own AVD name, boot-complete=1 and the Android
package service. Reported SDK37 / Android17, en-US, 1080x2400, 420dpi, font scale1.0. Original Wi-Fi
and mobile-data settings were both enabled; both were disabled only on this dedicated AVD for
offline acceptance preparation and their prior values were saved for restoration.

## Executed emulator acceptance

Root ran `connectedDebugAndroidTest --rerun -Pandroid.injected.device.serial=emulator-5582` at
`690b5f5`: Exit0, BUILD SUCCESSFUL in 1m39s. The actual `testsuites` XML contains 11 suites and
35 testcases, zero failures/errors/skips. These include the 33 existing cases plus blocked and
Starting component cases at 320dp / 200% font scale. Only the owned AVD was connected.

The connected runner removed the package after its run. Root then installed the accepted debug
APK fresh, before the first Activity launch. There were no remaining old test database files to
archive. Root installed a synthetic legacy `ROLLED_BACK` / `ROLLBACK_FAILURE` journal, existing
OLD generation and 67 bytes of rollback evidence while the OJ target was absent. The evidence
sentinel is intentionally not a valid SQLite database or WAL; this scenario checks failed-recovery
preservation and startup blocking. The separate JVM tests use real SQLite/WAL for data correctness.

| Actual Activity scenario | Result |
| --- | --- |
| First cold start | MainActivity launched successfully; PID6254; exact blocked copy displayed |
| HOME then return | Same PID6254; onResume path stayed blocked |
| Force-stop / cold start 2 | New PID6390; blocked copy and evidence preserved |
| Force-stop / cold start 3 | New PID6463; blocked copy and evidence preserved |
| System font scale 2.0 | New PID6567; full copy visible below system bars |
| Every checked start | `oj-nexus.db` and business DataStore file absent; all three evidence SHA-256 values unchanged |
| App-PID crash buffers | No FATAL EXCEPTION / application-process crash entry found |

Screenshots were visually inspected: [default font](assets/phase127a/device-blocked-1.png) and
[200% system font](assets/phase127a/device-font200.png). UI hierarchy captures independently
contained all three exact strings; at default font the title starts at y178 on a 1080x2400 display,
below the status area. No raw path/error text or reset action is exposed.

Original font scale1.0, Wi-Fi1 and mobile-data1 were restored and read back before shutting down
only `OJNexus_Phase127_Temp`; subsequent ADB inventory was empty. The AVD and synthetic evidence
remain available for inspection. No physical device, true power-loss event or APPLIED-generation
device scenario was executed. The valid APPLIED/rollback correctness evidence remains JVM-based.

All database records used in fault tests are synthetic fixture identities. Materialized checkpoint
files and injected I/O exceptions are reported separately from real process termination. No
power-loss durability, physical-device behavior or old-engine compatibility is inferred.
