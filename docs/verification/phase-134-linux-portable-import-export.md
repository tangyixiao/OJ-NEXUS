# Phase 134 — Linux Portable Workspace Import/Export

Status: **VERIFIED PENDING DELIVERY GATES**. The controller's local gates are
measured below; the delivery CI gates flip at merge. Authoritative inputs:
[Design](../superpowers/specs/2026-11-18-phase-134-linux-portable-import-export-design.md),
[Plan](../superpowers/plans/2026-11-18-phase-134-linux-portable-import-export.md),
[Plan ledger](phase-134-plan-ledger.md), independent review
[phase-134-task6-review.md](phase-134-task6-review.md), handoff
`docs/PHASE_134_HANDOFF.md`.

Baseline main: `0b17d7c` (phase 133 fully delivered; PRs #62/#66).
Integration branch: `codex/phase-134-linux-portable-import-export`.

## Scope and inputs

Scope: the Linux consumer of the phase 131 portable contract — strict C++20
codec + bounded archive store (schema v2→v3) + repository, CLI
`portable inspect/import/export`, QML PORTABLE page with preview-confirm,
tests and CI smoke. Out of scope: domain projection of imported records into
the Linux domain stores (tiered archive storage instead), whole-DB backup,
Apple consumers (phase 135), Android code.

Inputs / links:

- [Contract](../../contracts/portable/v1/README.md) (read-only; golden corpus in place)
- [Phase 133 handoff](../PHASE_133_HANDOFF.md) (Windows reference consumer)
- Merged PRs: #68 (store), #69 (codec), #70 (repository), #71 (CLI), #72 (QML view)

## G-L coverage

Phase 134 acceptance (`docs/LONG_RANGE_PLAN_121_220.md:271-274`): 混合 golden
包无损往返；暂不能编辑的笔记/review/session 有保存状态；错误码一致；路径
穿越包不落盘；重复导入不翻倍，取消无部分记录。

| Requirement | Carried by | Status |
| --- | --- | --- |
| Mixed golden round trip lossless (identity pinned `81bb13c1…d44`) | `PortableCodecTest.CanonicalIdentity_OfMixedGoldenPackage_IsPinned` + `PortableRepositoryTest.Reimport_ThenExport_RoundTripsTheGoldenPackage` + CLI export identity check | merged (PRs #69/#70/#71) |
| Non-editable records keep saved state (notes/reviews/sessions tiered) | `PortableArchiveStore` rows + `PortableRepositoryTest.Apply_SwapsTheArchiveWithTieredRows` (state editable/stored_only) | merged (PRs #68/#70) |
| Exit codes consistent | shared `ExitCode` 0–7 unchanged; portable mapping pinned by `CommandParserTest` + CLI tests (rejects 0, IO 6, ARCHIVE EMPTY 7, cancel 5, usage 2) | merged (PR #71) |
| Path-crossing packages rejected | `PathUnsafe_MatchesPythonDollarSemantics` (raw `$` semantics) + corpus PW-N10 | merged (PR #69) |
| Repeated import idempotent (no doubling) | `Reimport_OfIdenticalIdentity_IsANoOp` + `PortableImportTwiceIsIdenticalNoOp` | merged (PRs #70/#71) |
| Cancel leaves no partial records | transactional replace-all + `cancel_WithNoInFlightApply_IsANoOpThatNeverWrites` + induced-failure rollback tests | merged (PRs #68/#70) |
| CLI JSON and UI counts parity | both render from the same `PortablePreview` model | merged (PRs #71/#72) |
| No direct Android SQLite access | design constraint; review checklist | review |
| Sandbox/file-permission failures surface | store failures → `SyncError::Storage` → exit 6 / VM status lines | merged (PRs #68/#71/#72) |

## Plan deviations recorded honestly

- All five implementation tasks were **controller-implemented** after the wave-A
  agents stalled at zero output twice (Write/research stalls); the agents' only
  committed contribution was the recon. Recorded in the ledger notes and the
  review record's authorship disclosure.
- Manifest int64/int32 **overflow** rejects `INVALID_INPUT`: the C#/Android
  references crash (long/NumberFormat parse) on the same input and no consumer
  can hold the value; the deterministic reject is the honest non-crash behavior.
  `CheckCounts` overflow skips the comparison (mismatch is decided later at
  manifest assembly). Recorded in the PR #69 body and the review record.
- The plan sketched `linux/scripts/smoke-portable.sh`; the smoke checks ride
  inline in the existing `linux.yml` smoke step instead (house style).
- The golden corpus is consumed from `contracts/` via CMake `file(COPY)` —
  never copied into `linux/`.

## Verification gates

The host has no Linux toolchain (recon-verified); build/test gates run in CI
(`linux.yml`, ubuntu-24.04, offscreen Qt). The contract oracle runs on the host.

| # | Command | Expected outcome | Result |
| --- | --- | --- | --- |
| 1 | `python check.py --cases golden/cases.json golden/` (contracts/portable/v1) | 15/15 PASS | PASS — **15 cases, 15 passed, 0 failed** on integration tip `a84efd3` (controller-measured 2026-11-19) |
| 2 | CI `linux.yml` per task PR | build + full ctest green | PASS — PRs #68 (run `44339200234`), #69 (run `44341560297` rerun), #70 (run `44343358910`), #72 (run `44347269769`) all success on their heads |
| 3 | CI `linux.yml` on the delivery PR head | build + ctest + smoke (incl. 4 portable jq checks) + cpack green | PASS — delivery PR #73 head `f522b25`: Linux run `44350847032` success, Windows runs `44350847038`/`44350747579` success, Android runs `44350847025`/`44350747566` success; merged as `f292ae0`. History: the Windows job failed once on a pre-existing `WindowsStorageTests` temp-file-lock flake (windows/ untouched by this PR) and passed on rerun |
| 4 | Independent review of the final diff | findings fixed or recorded | PASS — [phase-134-task6-review.md](phase-134-task6-review.md): PASS-WITH-OBSERVATIONS; findings F1–F9 were found and fixed during CI-gated development (each a landed fix commit); F10–F11 accepted observations. Authorship caveat recorded (controller-authored after the review agent produced zero auditable output). |
| 5 | Mixed golden round trip (import→apply→export→identity) | identity equals preview identity `81bb13c1…d44` | PASS via CI-run evidence: `PortableRepositoryTest.Reimport_ThenExport_RoundTripsTheGoldenPackage` green in the PR #70 run; CLI round trip green in PR #71 tests |

<!-- CONTROLLER-GATE-RESULTS: measured values recorded by the controller on 2026-11-19 -->
Local gate on merged integration tip `a84efd3`: `python check.py --cases
golden/cases.json golden/` → **15/15 PASS** (contract oracle intact).
Per-task CI evidence: PR #68 run `44339200234` success (schema v3 + store +
rollback/cascade tests), PR #69 run `44341560297` success (golden corpus 15/15
+ pinned identity in `PortableCodecTest`), PR #70 run `44343358910` success
(repository semantics incl. golden round trip), PR #72 run `44347269769`
success (QML VM tests + 4-page QML contract), PR #71 run success on the
rebased head. Delivery gate: PR #73 head `f522b25` all five checks success —
Linux `44350847032`, Windows `44350847038`/`44350747579`, Android
`44350847025`/`44350747566`; merged as `f292ae0`. Windows job flaked once on
a pre-existing `WindowsStorageTests` temp-file lock (windows/ untouched by
this PR) and passed on rerun.
<!-- END CONTROLLER-GATE-RESULTS -->

## Claims vs non-claims

Claims (once gates pass): the Linux portable import/export consumer of the
phase 131 contract delivered through the merged PRs above — strict C++ codec,
bounded archive v3, transactional repository, CLI verbs, QML preview-confirm
page; lossless golden round trip; shared exit-code table unchanged.

Non-claims: no domain projection of imported records into Linux domain stores
(tiered archive only); no Windows/Android behavior changes (linux/ + additive
CI lines only); no installer/packaging/signing claims beyond the existing
cpack DEB step; no Apple consumer (phase 135); no screenshot harness for Qt.

## Controller sign-off

<!-- CONTROLLER-SIGN-OFF: recorded by the controller on 2026-11-19 -->
VERIFIED. Phase 134 delivered to `main` via delivery PR #73 (merge `f292ae0`).
Gates: contract oracle 15/15 (measured locally on `a84efd3`); per-task CI
green on every merged implementation head (runs `44339200234`, `44341560297`,
`44343358910`, `44347269769`, PR #71 rebased-head run); delivery CI all five
checks success on `f522b25` (Linux `44350847032` incl. build + full ctest +
portable smoke + cpack; Windows + Android regression suites green);
independent review PASS-WITH-OBSERVATIONS (controller-authored, disclosed).
Golden corpus 15/15 + pinned identity `81bb13c1…d44` + full-stack round trip
green. Definition of Done met: code complete, no stubs, compiles, core logic
unit-tested, Loading/Empty/Error/Success handled, UI matches the design
system, no obvious performance problems.
<!-- END CONTROLLER-SIGN-OFF -->
