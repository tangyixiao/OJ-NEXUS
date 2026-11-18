# Phase 133 �?Windows Portable Workspace Import/Export

Status: **VERIFIED**. The controller's final G-W gate ran on the merged
integration branch at `fa33c89` (round-2 parity head) on 2026-11-18; every gate
below carries a measured value recorded in this document. The independent review
is [phase-133-task6-review.md](phase-133-task6-review.md) (PASS-WITH-OBSERVATIONS;
its P2 fixed in `0e7065b`, round-2 findings F5/F6 fixed in `fa33c89`). The plan
ledger ([phase-133-plan-ledger.md](phase-133-plan-ledger.md))
tracks per-task merge state; the handoff is `docs/PHASE_133_HANDOFF.md`.

## Scope and inputs

Scope: the Windows consumer of the phase 131 portable contract �?Core strict
codec + bounded archive store (schema v7→v8) + repository, CLI
`portable inspect/import/export`, WPF PORTABLE view with preview-confirm,
tests and scripts. Out of scope: domain projection of imported records into
the Windows problems/reviews stores (tiered archive storage instead),
Windows/Linux/Apple consumers other than this phase, Android code, whole-DB
backup.

Inputs / links:

- [Design spec](../superpowers/specs/2026-11-08-phase-133-windows-portable-import-export-design.md)
- [Implementation plan](../superpowers/plans/2026-11-08-phase-133-windows-portable-import-export.md)
- [Plan ledger](phase-133-plan-ledger.md)
- [Contract](../../contracts/portable/v1/README.md) (read-only; golden corpus in place)
- [Phase 132 handoff](../PHASE_132_HANDOFF.md) (Android reference consumer)
- [Phase 128A storage-safety rules](phase-128a-windows-storage-safety.md)
- Merged PRs: #56 (SDD), #58 (codec), #57 (store), #59 (repository),
  #60 (CLI + session bridge), #61 (WPF view)

## G-W coverage

Phase 133 acceptance (`docs/LONG_RANGE_PLAN_121_220.md:266-269`): 混合 golden
包无损往返；暂不能编辑的笔记/session 由领域归档保存；取消/失败事务回滚；CLI
JSON �?UI 计数相符；不直接打开 Android SQLite�?
| Requirement | Carried by | Status |
| --- | --- | --- |
| 15-case golden corpus with exact wire categories | `PortableCodecTests.GoldenCorpus_MatchesCheckerExpectations` | merged (PR #58) |
| Mixed golden round trip lossless (identity pinned `81bb13c1…d44`) | `PortableCodecTests.CanonicalIdentity_OfMixedGoldenPackage_IsPinned` + `PortableRepositoryTests.Reimport_ThenExport_RoundTripsTheGoldenPackage` | merged (PRs #58/#59) |
| Non-editable records preserved via the domain archive | `PortableArchiveStoreTests` + tiered rows in `PortableRepositoryTests.Apply_SwapsTheArchiveWithTieredRows` | merged (PRs #57/#59) |
| Cancel/failure transactional rollback | `PortableArchiveStoreTests.ReplaceAll_FailureRollsBackToPreviousArchive` + cancel no-op semantics | merged (PRs #57/#59) |
| CLI JSON and UI counts parity | both render from the same `PortablePreview` model | merged (PRs #60/#61) |
| No direct Android SQLite access | design constraint; review checklist | pending review |
| CLI confirm gate (`--yes` required) | `PortableCommandTests.Import_WithoutYes_PrintsConfirmationAndWritesNothing` | merged (PR #60) |
| WPF cancel/back-out write-free | `PortableViewModelTests` (cancelled pickers, cancel discard) | merged (PR #61) |

Plan deviations recorded honestly:

- Three implementation agents were stood down at zero output (Write-tool
  rejections/stalls); the controller implemented tasks 1�? and 4�? directly,
  using the agents' verified research verbatim (pattern translations, schema
  bump edit list, session-bridge design, ui-smoke touch points). Agent #2
  (codec) delivered one committed port (`42bd1d5`, `PortableModels.cs`).
- The v8 header stores the generator **object** JSON (`GeneratorJson`) �?  amended within the phase before any release; the alternative (combined
  name-version string) is lossy for export rebuild.
- The repository tests run against the REAL guarded store (integration
  flavor) instead of a fake �?the store is sealed with an internal ctor and
  its transactional behavior is unit-covered separately; a fake would only
  restate its own assumptions.

## Verification gates

Commands the controller runs on the merged integration branch via dotnet +
pwsh (SDK 10.0.401; solution targets net8.0):

| # | Command | Expected outcome | Result |
| --- | --- | --- | --- |
| 1 | `dotnet test windows/OjNexus.Windows.sln -c Release --no-restore` | `Build succeeded`, all tests passed | PASS �?**393 tests / 0 failures** (Core 275, Cli 75, Desktop 43) on round-2 head `fa33c89`; `0e7065b` measured 391/0 pre-round-2 |
| 2 | `dotnet build windows/OjNexus.Windows.sln -c Release --no-restore` | `Build succeeded` (TWE: 0 warnings) | PASS �?clean build, 0 warnings |
| 3 | `pwsh -File windows/scripts/smoke.ps1 -Configuration Release` | CLI `status --json` ready + WPF alive | PASS �?`CLI EXIT CODE: 0`, `{"status":"ready","accountCount":0,...}`, `DESKTOP START: ALIVE WITHIN 15 SECONDS` |
| 4 | `pwsh -File windows/scripts/ui-smoke.ps1 -Configuration Release` | UIA over FIVE pages, `SCREENSHOTS: 5/5`, PORTABLE RENDERED | PASS �?`PORTABLE: RENDERED`, `SCREENSHOTS: 5/5`, `UI SMOKE: PASS` |
| 5 | Independent review of the final diff | findings fixed or recorded | PASS �?[phase-133-task6-review.md](phase-133-task6-review.md): PASS-WITH-OBSERVATIONS; the independent P2 (unanchored integer-literal matching) was FIXED in `0e7065b` with a regression test; F2-F4 accepted P3 observations; round-2 parity findings F5/F6 FIXED in `fa33c89` with regressions (see the review record Round 2). Authorship caveat recorded (controller-authored after agent stalls). |
| 6 | CI (`windows.yml` + `android.yml`) on the delivery PR | green | PASS - Android CI run `44332741408` (release/build/emulator all success) + Windows CI run `44332741442` (build success), both on head `50844ad`; PR #62 merged as `afeadf7` |

Gate history: per-task gates were measured at each merge (PRs #57�?61 bodies
carry their runs); the pre-review full gate measured 390/0 on `193984c`; the
final gate re-measured **391/0** on `0e7065b` after the review's P2 fix
(anchored integer-literal matching + regression test); the round-2 parity
audit re-measured **393/0** + smoke + UIA 5/5 on `fa33c89` (F5/F6 fixes with
two regression tests).

<!-- CONTROLLER-GATE-RESULTS: measured values recorded by the controller on 2026-11-18 -->
Final gate on merged integration tip `0e7065b`: `dotnet test windows/OjNexus.Windows.sln
-c Release --no-restore` �?**391 tests / 0 failures** (Core 273, Cli 75, Desktop 43);
`dotnet build -c Release --no-restore` �?clean under TreatWarningsAsErrors;
`windows/scripts/smoke.ps1` �?CLI EXIT CODE 0 + `{"status":"ready",...}` + DESKTOP START ALIVE;
`windows/scripts/ui-smoke.ps1` �?UI SMOKE PASS, SCREENSHOTS 5/5, PORTABLE RENDERED.
Gate history: 390/0 on `193984c` (pre-review); 391/0 on `0e7065b` after the P2 fix;
393/0 on `fa33c89` after the round-2 F5/F6 fixes (PathUnsafe raw `$` semantics,
`-0` canonicalization).
CI corroboration: delivery PR #62 green - Android CI run `44332741408` (release/build/emulator success) + Windows CI run `44332741442` (build success) on head `50844ad`; merged `afeadf7`.
<!-- END CONTROLLER-GATE-RESULTS -->

## Claims vs non-claims

Claims (once gates pass): the Windows portable import/export consumer of the
phase 131 contract delivered through the merged PRs above �?CLI verbs, WPF
preview-confirm, bounded archive v8, transactional apply, lossless golden
round trip.

Non-claims: no domain projection of imported records into problems/reviews
stores (tiered archive only); no Windows whole-DB backup (128B–D remains
open); no installer/packaging/signing claims; no Linux/Apple consumers.

## Controller sign-off

<!-- CONTROLLER-SIGN-OFF: measured gate results recorded by the controller on 2026-11-18 (re-measured after the round-2 audit) -->
VERIFIED. Final gate on merged `codex/phase-133-windows-portable-import-export` at `fa33c89`
(round-2 parity head, re-measured by the controller before merge):
`dotnet test -c Release` -> **393 tests / 0 failures** (Core 275, Cli 75, Desktop 43);
clean TWE build; `smoke.ps1` -> CLI EXIT CODE 0 + `{"status":"ready",...}` + DESKTOP ALIVE;
`ui-smoke.ps1` -> UI SMOKE PASS / SCREENSHOTS 5/5 / PORTABLE RENDERED. Independent review
PASS-WITH-OBSERVATIONS: integer-literal P2 FIXED (`0e7065b`, regression test
`IntegerLiterals_AreFullStringMatched`); round-2 findings F5/F6 FIXED (`fa33c89`, regressions
`PathUnsafe_MatchesPythonDollarSemantics` + `NegativeZero_CanonicalizesToZero`); F2-F4 accepted
as documented observations. Golden corpus 15/15 + pinned identity `81bb13c1...d44` + full-stack
round trip green. Definition of Done met: code complete, no stubs, compiles, core logic
unit-tested, Loading/Empty/Error/Success handled, UI matches the design system, no obvious
performance problems, CI-equivalent local gate green. Delivery PR #62.
<!-- END CONTROLLER-SIGN-OFF -->
