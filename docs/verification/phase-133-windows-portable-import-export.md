# Phase 133 — Windows Portable Workspace Import/Export

Status: **PENDING**. The controller's final G-W gate is running on the merged
integration branch; this record flips to **VERIFIED** only when every gate
below carries a measured value. Until then nothing here is a completion
claim. The plan ledger ([phase-133-plan-ledger.md](phase-133-plan-ledger.md))
tracks per-task merge state; the handoff is `docs/PHASE_133_HANDOFF.md`; the
independent review record will be linked at flip time.

## Scope and inputs

Scope: the Windows consumer of the phase 131 portable contract — Core strict
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
JSON 与 UI 计数相符；不直接打开 Android SQLite。

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
  rejections/stalls); the controller implemented tasks 1–3 and 4–5 directly,
  using the agents' verified research verbatim (pattern translations, schema
  bump edit list, session-bridge design, ui-smoke touch points). Agent #2
  (codec) delivered one committed port (`42bd1d5`, `PortableModels.cs`).
- The v8 header stores the generator **object** JSON (`GeneratorJson`) —
  amended within the phase before any release; the alternative (combined
  name-version string) is lossy for export rebuild.
- The repository tests run against the REAL guarded store (integration
  flavor) instead of a fake — the store is sealed with an internal ctor and
  its transactional behavior is unit-covered separately; a fake would only
  restate its own assumptions.

## Verification gates

Commands the controller runs on the merged integration branch via dotnet +
pwsh (SDK 10.0.401; solution targets net8.0):

| # | Command | Expected outcome | Result |
| --- | --- | --- | --- |
| 1 | `dotnet test windows/OjNexus.Windows.sln -c Release --no-restore` | `Build succeeded`, all tests passed | PASS — **390 tests / 0 failures** (Core 272, Cli 75, Desktop 43; 22 new portable tests) |
| 2 | `dotnet build windows/OjNexus.Windows.sln -c Release --no-restore` | `Build succeeded` (TWE: 0 warnings) | PASS — clean build, 0 warnings |
| 3 | `pwsh -File windows/scripts/smoke.ps1 -Configuration Release` | CLI `status --json` ready + WPF alive | PASS — `CLI EXIT CODE: 0`, `{"status":"ready","accountCount":0,...}`, `DESKTOP START: ALIVE WITHIN 15 SECONDS` |
| 4 | `pwsh -File windows/scripts/ui-smoke.ps1 -Configuration Release` | UIA over FIVE pages, `SCREENSHOTS: 5/5`, PORTABLE RENDERED | PASS — `PORTABLE: RENDERED`, `SCREENSHOTS: 5/5`, `UI SMOKE: PASS` |
| 5 | Independent review of the final diff | findings fixed or recorded | PENDING — reviewer in flight; record linked at flip |
| 6 | CI (`windows.yml`) on the delivery PR | green | PENDING — runs on the delivery PR; run id recorded at merge |

Gate history: per-task gates were measured at each merge (PRs #57–#61 bodies
carry their runs); this final gate ran on the merged integration tip.

<!-- CONTROLLER-GATE-RESULTS: to be filled by the controller with measured values only -->
PENDING — final gate in flight.
<!-- END CONTROLLER-GATE-RESULTS -->

## Claims vs non-claims

Claims (once gates pass): the Windows portable import/export consumer of the
phase 131 contract delivered through the merged PRs above — CLI verbs, WPF
preview-confirm, bounded archive v8, transactional apply, lossless golden
round trip.

Non-claims: no domain projection of imported records into problems/reviews
stores (tiered archive only); no Windows whole-DB backup (128B–D remains
open); no installer/packaging/signing claims; no Linux/Apple consumers.

## Controller sign-off

<!-- CONTROLLER-SIGN-OFF: to be written by the controller at flip time -->
PENDING.
<!-- END CONTROLLER-SIGN-OFF -->
