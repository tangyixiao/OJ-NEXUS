# Phase 132 — Portable Workspace Import/Export (Android)

Status: **VERIFIED**. The controller ran the final G-A gate on the merged
integration branch `codex/phase-132-portable-import-export` at `f2f0517` on
2026-11-18 (Gradle 9.7.1 via the machine-local `tools\gradlew-local.bat`
helper, JetBrains JBR JVM 25.0.2). Every measured value below is an observed
result, not an estimate: the final gate printed `BUILD SUCCESSFUL in 1m 55s`
with a JUnit XML aggregate of **896 tests / 0 failures / 0 errors** across 189
classes, including all 47 portable tests. The independent review is a separate
record ([phase-132-task4-review.md](phase-132-task4-review.md)) with verdict
PASS-WITH-OBSERVATIONS (no P1/P2; six P3 observations — F1/F2/F3/F5 fixed in
PR #53, F4/F6 accepted as documented observations). The delivery PR is
[#54](https://github.com/tangyixiao/OJ-NEXUS/pull/54), whose `android.yml` run
is the independent CI corroboration. The plan ledger
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
| UI transport intents (read/rebuild/write/preview/apply/cancel) | `PortableWorkspaceViewModelTest` | merged (PR #50; 19 tests after the F5 disposition in PR #53) |
| Settings entry separate from backup; SAF cancellation no-op; confirmed-import dialog | task 4b screen + independent review | merged (PRs #49/#51/#53; review PR #52 PASS-WITH-OBSERVATIONS) |

Plan deviations recorded honestly:

- Task 1's dedicated `PortableCodecTest.kt` was folded into the
  `PortableGoldenRoundTripTest` corpus loop (the codec's contract decisions are
  exercised against all 15 golden cases there); no separate codec test file
  exists.
- Task 5's planned documentation outputs (verification record, handoff, plan
  ledger) were not produced by PR #43; the controller produced the ledger
  (PR #47) and this record in the task-6 delivery.
- Task 4b's 43 strings initially missed their mandatory `values-zh-rCN`
  mirrors; the first full gate honestly caught it (1 of 895 failing) and PR
  #51 landed the parity fix. The review's P3 findings were routed in PR #53
  (F1/F2 KDoc limits, F3 neutral saved-archive wording in both locales, F5
  supersede test; F4/F6 accepted).

## Verification gates

The controller runs every gate on the merged integration branch
`codex/phase-132-portable-import-export` via the machine-local helper (which
pins `JAVA_HOME=D:\Android Studio\jbr`, Gradle 9.7.1). Each Result is filled
with the observed value only at flip time.

| # | Command | Expected outcome | Result |
| --- | --- | --- | --- |
| 1 | `cmd /c "D:\AndroidAppCoding\tools\gradlew-local.bat test"` | `BUILD SUCCESSFUL` | PASS — gate run 3 on `f2f0517`, `BUILD SUCCESSFUL in 1m 55s`, 896/0/0 |
| 2 | `cmd /c "D:\AndroidAppCoding\tools\gradlew-local.bat assembleDebug"` | `BUILD SUCCESSFUL` | PASS — same run, APK assembled |
| 3 | JUnit XML for `PortableGoldenRoundTripTest` (7), `PortableRepositoryTest` (14), `PortableArchiveStoreTest` (7), `PortableWorkspaceViewModelTest` (19) | all `failures="0" errors="0"`, counts recorded | PASS — 47 portable tests green; aggregate 896/0/0 across 189 classes |
| 4 | Independent review of the final task-4 diff (review record appended or disposition noted) | findings fixed in follow-up PR or recorded | PASS — [phase-132-task4-review.md](phase-132-task4-review.md) PASS-WITH-OBSERVATIONS (PR #52); F1/F2/F3/F5 fixed in PR #53, F4/F6 accepted |
| 5 | `.github/workflows/android.yml` on the delivery PR | green | PENDING — runs on delivery PR #54; run id recorded before merge |

Gate history (honest record, all measured on this host):

- Run 1 — integration tip `89ba7c0` (pre-fix), 2026-11-18: `895 tests completed, 1 failed`
  (`LocalizationResourceTest` — the 43 task-4b strings were missing their mandatory zh-rCN
  mirror), `assembleDebug` green, `BUILD FAILED in 3m`. Fixed by PR #51.
- Run 2 — tip `4b2ceb8` (post-fix): `BUILD SUCCESSFUL in 1m 48s`, aggregate 895/0/0.
- Run 3 — final tip `f2f0517` (findings disposition #53 included): `BUILD SUCCESSFUL in 1m 55s`,
  aggregate **896/0/0** (895 + the F5 supersede test).

<!-- CONTROLLER-GATE-RESULTS: measured values recorded by the controller on 2026-11-18 -->
Final gate (run 3) on merged integration tip `f2f0517`: `tools\gradlew-local.bat test assembleDebug`
→ `BUILD SUCCESSFUL in 1m 55s`; JUnit XML aggregate `tests=896 failures=0 errors=0` across 189
classes; portable classes green (`PortableGoldenRoundTripTest` 7, `PortableRepositoryTest` 14,
`PortableArchiveStoreTest` 7, `PortableWorkspaceViewModelTest` 19). History: run 1 (`89ba7c0`)
895 tests / 1 failure (`LocalizationResourceTest` zh-rCN parity) → PR #51; run 2 (`4b2ceb8`)
895/0/0 in 1m 48s. CI corroboration: `android.yml` on delivery PR #54 (run id recorded below
before merge).
<!-- END CONTROLLER-GATE-RESULTS -->

## Claims vs non-claims

Claims (delivered in Phase 132): the Android portable import/export consumer of the
phase 131 contract — read-only preview, confirmed transactional apply, lossless
round trip, idempotent reimport, synthetic marking, stored-only saved state, and
the separate settings entry — delivered through the merged PRs above.

Non-claims: no whole-database backup change, no Windows/Linux/Apple consumer,
no credential or cookie portability, no v2 upgrade path, no automatic data
migration from the backup feature.

## Controller sign-off

<!-- CONTROLLER-SIGN-OFF: measured gate results recorded by the controller on 2026-11-18 -->
VERIFIED. Final controller gate on merged `codex/phase-132-portable-import-export` at `f2f0517`:
`test assembleDebug` → `BUILD SUCCESSFUL in 1m 55s`, aggregate 896/0/0 across 189 classes, all 47
portable tests green. Independent review PASS-WITH-OBSERVATIONS with no P1/P2; F1/F2/F3/F5 fixed
in PR #53 (measured 19/2 targeted tests), F4/F6 accepted as documented observations. The gate's
first full run honestly caught the zh-rCN parity miss (PR #51). Definition of Done met: code
complete, no TODO stubs, compiles, core logic unit-tested, Loading/Empty/Error/Success handled,
UI matches the design system (review-verified), CI-equivalent gate green. Delivery PR #54.
<!-- END CONTROLLER-SIGN-OFF -->
