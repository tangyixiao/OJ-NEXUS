# Phase 127B — Strict worker generation admission and queue replacement

Status: VERIFIED for the Phase127B boundary: fresh full local build, admission RED/GREEN
evidence and a dedicated-emulator connected gate passed at `e468fee`. The full Phase127
remains ACTIVE. No application release, no version change, and no claim about typed
import-storage errors or old-engine export compatibility, which stay open.

## Source and scope

- [Design](../superpowers/specs/2026-10-27-phase-127b-worker-generation-design.md)
- [Plan](../superpowers/plans/2026-10-27-phase-127b-worker-generation.md)
- Base `5c6a6e7` on `codex/phase-127-recovery-safety` (Phase127A delivery head, PR #20 open).
- Commits: `c44c200` (spec), `bedcbd8` (plan), `e468fee` (implementation).
- The Phase126 classifier `decideRestoreWorkGeneration` and the `RestoreWorkDecision` values
  are unchanged; `RestoreWorkGenerationTest` and the RV020–RV022 projections pass untouched.
  Only the consuming policy changed: all three workers and the backup scheduler.

## Admission behavior (RED/GREEN)

Focused run of the new `RecoveryGenerationAdmissionTest` before implementation: 5 tests,
4 failed at the present defects — unstamped judge sync proceeded with no refusal marker,
`recordStaleGeneration(null)` recorded nothing, an unstamped Luogu poll proceeded into the
result path, and the backup worker ignored any stamp (plain success). The stale-sync case
already behaved correctly and passed as a regression pin. Log retained as
`build/red-admission.log` (machine-local, not committed).

After implementation the same suite passes 6/6: stale and unstamped sync refusals return
`STALE_DATA_GENERATION` / `UNSTAMPED_DATA_GENERATION` with exactly one auditable
`STALE_GENERATION` ledger row (empty `data_generation` for unstamped, `STALE_DATA_GENERATION`
error marker) and zero coordinator operations; Luogu refusals leave submission jobs
untouched; backup refusals return the markers before any preference read and the preferences
DataStore file stays absent; `scheduledBackupInput` pins the scheduler stamp contract;
`recordStaleGeneration` still refuses a same-generation call. Focused totals: 8 suites,
22 tests, zero failures/errors/skips.

## Full local gate

| Check | Actual result |
| --- | --- |
| `tools\gradlew-local.bat testDebugUnitTest --rerun test assembleDebug assembleRelease` | Exit0, BUILD SUCCESSFUL in 3m15s |
| Debug unit XML | 835 tests / 182 suites; zero failures/errors/skips |
| Warnings | Only the two pre-existing JVM warnings (Conscrypt native access, DataStore/protobuf Unsafe); no new production compiler warnings observed |

The inherited 127A evidence (829/181 at `690b5f5`) is baseline only; the 835/182 totals above
are this slice's fresh run and include the six new admission tests.

## Dedicated emulator connected gate

The `OJNexus_Phase127_Temp` AVD from 127A no longer existed on this host; it was recreated
with the same image (`system-images;android-37.1;google_apis_playstore_ps16k;x86_64`,
pixel_7 profile) and booted as the only connected device on serial `emulator-5582`.

- First connected attempt aborted: the emulator process died mid-run after 28 of 35 tests
  with one empty-record failure (`ConnectorCenterComposeTest emptyCenterDisablesSyncAll`,
  no message/stack — a device-death artifact, not an accepted assertion result). Log retained
  as `build/connected-127b.log`; this run is recorded as failed infrastructure.
- Full re-run after a clean reboot and settle period: Exit0, BUILD SUCCESSFUL in 1m23s.
  Actual XML: 11 suites, 35 testcases, zero failures/errors/skips — the same 35-case suite
  shape as the 127A gate (33 existing cases plus the two recovery component cases).
- The emulator was shut down cleanly afterwards (`adb devices` empty); the AVD and its
  synthetic state remain for inspection. No physical device and no user data were touched.

## Honest limits

- `ExistingPeriodicWorkPolicy.KEEP → UPDATE` queue replacement is enforced in code and its
  safety net (strict refusal of stale/unstamped runs) is tested, but no WorkManager
  integration test asserts the replacement itself: `androidx.work:work-testing` is not a
  dependency and none was added for this slice.
- The backup-refusal test asserts DataStore-file absence as the "no status write" proof; it
  does not intercept DataStore internals.
- First connected run was aborted infrastructure; only the clean re-run is acceptance
  evidence. No physical device, power-loss, or APPLIED-generation device scenario was run.
- Phase127 remains ACTIVE: typed import-storage errors (ENOSPC/IO), functional old-engine
  export compatibility for minSdk 26, and the full phase release gate are still open.
