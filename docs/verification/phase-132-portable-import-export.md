# Phase 132 — Portable Workspace Import/Export (Android)

Status: **PENDING**. The controller has not yet run the final G-A gate on the
merged integration branch; this record flips to **VERIFIED** only after every
gate below carries a measured result. Until then, nothing in this document is
a completion claim. The plan ledger
([phase-132-plan-ledger.md](phase-132-plan-ledger.md)) tracks per-task merge
state; the handoff is `docs/PHASE_132_HANDOFF.md`.

## Scope and inputs

Scope: Phase 132 deliverables only. In scope: the frozen portable API
(`PortableModels.kt`, `PortableValue.kt`, `PortableArchiveStore.kt`,
`PortableWorkspaceRepository.kt`), the strict canonical JSON codec
(`PortableCodec.kt`), the Room bounded archive store + migration 15→16
(`archive/`, `Migration15To16.kt`), the transactional
`PortableRepository`, the `PortableWorkspaceViewModel`, the
`PortableWorkspaceScreen` with SAF launchers and the confirmed-import dialog,
the settings entry and `PORTABLE_WORKSPACE` navigation destination, the
independent golden round-trip test, `AppContainer` wiring, and the phase
documentation. Out of scope: whole-database backup (`BackupRepository` and its
UI are untouched), Windows/Linux/Apple consumers (phases 133-135), encrypted
or compressed containers, credentials portability, v2 upgrade paths.

Inputs / links:

- [Design spec](../superpowers/specs/2026-11-06-phase-132-portable-import-export-design.md)
- [Implementation plan](../superpowers/plans/2026-11-06-phase-132-portable-import-export.md)
- [Plan ledger](phase-132-plan-ledger.md)
- [Contract README](../../contracts/portable/v1/README.md) (read-only for this phase)
- [Phase 131 verification record](phase-131-portable-contract.md)
- [Developer guide](../PORTABLE_WORKSPACE.md)
- Merged PRs: #41 (foundation + codec), #42 (store + migration), #44
  (repository), #43 (round trip), #46 (AppContainer wiring), #47 (ledger)

## Adoption and G-A coverage

Phase 132 acceptance (docs/LONG_RANGE_PLAN_121_220.md line 261, duty G-A):
Android 可移植导入导出 — the Portable Workspace entry separate from whole-DB
backup, read-only preview with counts/sources/skips/conflicts, user-confirmed
transactional apply, cancellable SAF access, lossless round trip of the mixed
golden package, idempotent reimport, out-of-contract rejection, synthetic test
source markers, and saved state for stored-only records.

Coverage mapping (every row cites the test that carries it; measured counts
are recorded at flip time):

| Requirement | Carried by | Status |
| --- | --- | --- |
| Closed schema rejects all 15 golden cases with exact wire categories | `PortableGoldenRoundTripTest` corpus loop over `golden/cases.json` | merged (PR #43) |
| Mixed golden import→apply→export identity is byte-identical; reimport idempotent | `PortableGoldenRoundTripTest` (`golden package round trip is byte identical` and siblings) | merged (PR #43) |
| Preview/cancel perform zero DB writes | `PortableRepositoryTest` (fake store records every mutation) | merged (PR #44) |
| Apply is a single transactional replaceAll; whole-package rejection; identical-identity no-op | `PortableRepositoryTest` | merged (PR #44) |
| Synthetic records marked `SYNTHETIC-TEST-FIXTURE`; stored-only saved state | `PortableRepositoryTest` + `PortableArchiveStoreTest` | merged (PRs #42/#44) |
| Room store load/replace/clear; migration 15→16 | `PortableArchiveStoreTest` (Robolectric in-memory) | merged (PR #42) |
| UI transport intents (read/rebuild/write/preview/apply/cancel) | `PortableWorkspaceViewModelTest` | PENDING (task 4a) |
| Settings entry separate from backup; SAF cancellation no-op; confirmed-import dialog | task 4b screen + review pass | PENDING (task 4b + task 6 review) |

Plan deviations recorded honestly:

- Task 1's dedicated `PortableCodecTest.kt` was folded into the
  `PortableGoldenRoundTripTest` corpus loop (the codec's contract decisions are
  exercised against all 15 golden cases there); no separate codec test file
  exists.
- Task 5's planned documentation outputs (verification record, handoff, plan
  ledger) were not produced by PR #43; the controller produced the ledger
  (PR #47) and this record in the task-6 delivery.

## Verification gates

The controller runs every gate on the merged integration branch
`codex/phase-132-portable-import-export` via the machine-local helper (which
pins `JAVA_HOME=D:\Android Studio\jbr`, Gradle 9.7.1). Each Result is filled
with the observed value only at flip time.

| # | Command | Expected outcome | Result |
| --- | --- | --- | --- |
| 1 | `cmd /c "D:\AndroidAppCoding\tools\gradlew-local.bat test"` | `BUILD SUCCESSFUL` | PENDING |
| 2 | `cmd /c "D:\AndroidAppCoding\tools\gradlew-local.bat assembleDebug"` | `BUILD SUCCESSFUL` | PENDING |
| 3 | JUnit XML for `PortableGoldenRoundTripTest`, `PortableRepositoryTest`, `PortableArchiveStoreTest`, `PortableWorkspaceViewModelTest` | all `failures="0" errors="0"`, counts recorded | PENDING |
| 4 | Independent review of the final task-4 diff (review record appended or disposition noted) | findings fixed in follow-up PR or recorded | PENDING |
| 5 | `.github/workflows/android.yml` on the delivery merge to `main` | green | PENDING |

<!-- CONTROLLER-GATE-RESULTS: to be filled by the controller with measured values only -->
PENDING — no gate has been run yet.
<!-- END CONTROLLER-GATE-RESULTS -->

## Claims vs non-claims

Claims (once gates pass): the Android portable import/export consumer of the
phase 131 contract — read-only preview, confirmed transactional apply, lossless
round trip, idempotent reimport, synthetic marking, stored-only saved state, and
the separate settings entry — delivered through the merged PRs above.

Non-claims: no whole-database backup change, no Windows/Linux/Apple consumer,
no credential or cookie portability, no v2 upgrade path, no automatic data
migration from the backup feature.

## Controller sign-off

<!-- CONTROLLER-SIGN-OFF: to be written by the controller at flip time -->
PENDING.
<!-- END CONTROLLER-SIGN-OFF -->
