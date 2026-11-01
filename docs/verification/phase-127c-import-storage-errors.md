# Phase 127C — Typed import storage errors

Status: VERIFIED for the Phase127C boundary: fresh full local gates, fault-injected
RED/GREEN evidence, an independent scoped review with its findings fixed, and a dedicated
-emulator connected gate passed at `ed7e873`. The full Phase127 remains ACTIVE. No release,
no version change, and no claim about old-engine export compatibility or the full phase
release gate, which stay open.

## Source and scope

- [Design](../superpowers/specs/2026-10-29-phase-127c-import-storage-errors-design.md)
- [Plan](../superpowers/plans/2026-10-29-phase-127c-import-storage-errors.md)
- Base: the Phase127B delivery tip `4c6ea33` on `codex/phase-127-recovery-safety`.
- Commits: `3b66151` (spec), `ff0c6a3` (plan), `6d65852` (typed classification + retryable
  staging), `545aa77` (typed outcome through repository/ViewModel/UI), `ed7e873` (review
  fixes).
- RV001–RV003 validation behavior and all Phase126 projections are unchanged
  (`decideRestoreWorkGeneration` / `decideRestoreRecovery` byte-identical).

## Admission of the defect (RED/GREEN)

Focused run of the new `ImportStorageFaultTest` before the fix: 6 tests, 4 failed at the
present defects. Staging-temp allocation failure (RV004) threw the raw `IOException` out of
`prepareImport` — an unhandled crash path in the settings import flow. Staged-copy (RV005)
and journal-write (RV006) failures returned the untyped `FILE_REPLACEMENT_FAILURE`, and the
journal-write failure left the staged pending file behind, so the next start would block on
orphan evidence instead of allowing the contract's retry. Two pins (pre-existing journal
survival, `INVALID_SQLITE` for garbage input) passed unchanged. One test-side correction
happened during the GREEN phase (the plain-IO case had not armed its `failCopyStream` flag);
its first failing run is retained in `build/green-127c1.log` as the RED for the category
mapping, and the corrected run is the accepted one.

After the fix the fault suite passes 8/8: typed `STORAGE_FULL` (ENOSPC markers, including a
cause-chain case) and `STORAGE_IO` (other I/O), active store untouched, no orphan
pending/journal/journal-temp evidence after any failure, retry convergence to `Staged` for
RV005/RV006 and for the pending-install path, pre-existing journal survival, and the pure
classifier marker table. The existing 23-case 127A fault suite passes unchanged.

## Typed surfacing

`BackupRepository.importFrom` returns the full `RestoreOutcome`; `SettingsViewModel.BackupResult`
carries `RestoreFailure?`; the settings screen maps import failures through the pure
`backupResultLabel`: storage full → `IMPORT FAILED — FREE STORAGE AND RETRY`, storage I/O →
`IMPORT FAILED — STORAGE ERROR`, other import failures → `IMPORT REJECTED — TAP TO DISMISS`,
and export failures keep their own copy (the previous reuse of the export-failure string for
import failures is gone). New keys exist in both resource catalogs with real Chinese
translations; the resource-parity contract is enforced by the full unit gate. The ViewModel
test pins a rejected import surfacing `RestoreFailure.INVALID_SQLITE`; the storage categories
are proven at the coordinator level and their label branches pinned by the pure mapping test,
because the repository constructs its own coordinator and cannot inject a storage fault.

## Independent scoped review

An independent reviewer pass over `ff0c6a3..545aa77` verified RV004–RV006 semantics, the
cleanup policy, control-flow ordering, classifier purity, enum-append safety, boundary
defaults, the typed contract's callers, the label mapping and string parity. Verdict:
SPEC PASS, QUALITY CHANGES_REQUESTED. Its P1 — the `RestoreValidationException` catch was the
only failure path skipping staging cleanup, so a failed pending-install replace left
`oj-nexus-pending-restore.db.tmp` behind and blocked the next start — is fixed in `ed7e873`
with a regression test (pending install failure cleans the temp and stays retryable). The
P2 nits were addressed: the label mapping now checks the operation before the failure
category (structurally total), the "disk full" marker has a classifier test, and the
reviewer's two documentation-only notes (the boundary default added `mkdirs`+`fd.sync` to
the staged copy — durability-positive; and the pre-existing double-stage edge where a second
import before a restart overwrites a staged pending file under the old journal) are recorded
here. After the fixes the fault suite passes 8/8 and the full gates were re-run fresh.

## Full local gate (final head `ed7e873`)

| Check | Actual result |
| --- | --- |
| `tools\gradlew-local.bat testDebugUnitTest --rerun test assembleDebug assembleRelease` | Exit0, BUILD SUCCESSFUL in 1m54s |
| Debug unit XML | 845 tests / 184 suites; zero failures/errors/skips |
| Warnings | Only the two pre-existing JVM warnings (Conscrypt native access, DataStore/protobuf Unsafe) |

## Dedicated emulator connected gate

Serial `emulator-5582`, recreated dedicated AVD `OJNexus_Phase127_Temp` (same API 37.1
playstore ps16k image, pixel_7 profile). Two intermediate runs aborted when the emulator
process died mid-run (31/35 with 0 failures, then 9 tests with one empty-record failure) —
both recorded as failed infrastructure: two idle Gradle daemons were holding ~5.3 GB during
the emulator run. After stopping them and rebooting the emulator, the clean run at
`ed7e873` passed: Exit0, BUILD SUCCESSFUL in 1m32s, actual XML 11 suites / 35 testcases,
zero failures/errors/skips. The emulator was shut down afterwards; no physical device or
user data was touched.

## Honest limits

- Faults are injected I/O exceptions at the file-operations boundary, not real ENOSPC
  hardware faults; no physical device, power-loss, or real disk-full scenario was run.
- The double-stage edge (a second import before a restart overwrites a staged pending file
  under the still-present old journal) is pre-existing protocol behavior, unchanged here.
- The reviewer's speculative chain concern (an ENOSPC exception hidden behind a marker-less
  outer IOException classifying as STORAGE_IO) is documented; no realistic Android file-I/O
  chain does this and the classifier returns on the first IOException in the chain.
- Phase127 remains ACTIVE: functional old-engine (SQLite < 3.27) export compatibility for
  minSdk 26 with real old-engine verification, and the full phase release gate are open.
