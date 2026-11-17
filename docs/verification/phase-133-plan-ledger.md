# Phase 133 — Plan Ledger (Windows Portable Workspace Import/Export)

Controller-maintained ledger for
[the phase 133 plan](../superpowers/plans/2026-11-08-phase-133-windows-portable-import-export.md).
Every row records the branch, the PR and the merge commit as observed on the
integration branch `codex/phase-133-windows-portable-import-export`. Rows
marked PENDING are not yet measured; they flip with measured values only
after the corresponding merge or gate.

Baseline: main `15cd876` (phase 132 merged; SDD PR #56 merge commit).

| # | Task | Branch | PR | Merge commit | Status |
| --- | --- | --- | --- | --- | --- |
| 1 | SDD: spec + implementation plan (explorer-synthesized) | `codex/phase-133-windows-portable-import-export` | #56 | `15cd876` | MERGED |
| 2 | Task 1: strict C# portable codec + golden corpus tests | `codex/phase-133-task-1-codec` | #58 | `05766da` | MERGED |
| 3 | Task 2: schema v8 portable archive tables + store | `codex/phase-133-task-2-store` | #57 | `085ec07` | MERGED |
| 4 | Task 3: `PortableRepository` orchestration | `codex/phase-133-task-3-repo` | #59 | `2818209` | MERGED |
| 5 | Task 4: CLI `portable` verbs + README + session bridge | `codex/phase-133-task-4-cli` | #60 | `4873aaa` | MERGED |
| 6 | Task 5: WPF PORTABLE view + UIA smoke extension | `codex/phase-133-task-5-wpf` | #61 | `0f45b55` | MERGED |
| 7 | Task 6: independent review of the final diff; disposition PR if needed | `codex/phase-133-task-6-review` | — | — | PENDING |
| 8 | Task 6: G-W gate `dotnet test` + `dotnet build` + smoke + ui-smoke → green; verification record + handoff flipped with measured results | integration branch | — | — | PENDING |
| 9 | Task 6: roadmap line 266 ticked; final delivery PR integration→main | integration branch | — | — | PENDING |

Notes:

- Waves: A = rows 2+3 in parallel (codec ∥ store, disjoint files); B = row 4;
  C = rows 5+6 in parallel (CLI ∥ WPF); D = row 7 (review, concurrency 1).
- Constraints carried from recon: **TreatWarningsAsErrors** (warnings fail
  builds), exit codes 0–7 immutable, `SchemaMigrator.cs` owned exclusively by
  row 3, `DesktopViewModel.cs` + `ui-smoke.ps1` by row 6, golden files
  consumed as linked Content from `contracts/` (never copied), all DB access
  through the guarded `WindowsStorage` authority.
- The mixed golden identity `81bb13c1746381f2d7f95ae69e421503dcc898aae823222e8a3caf40d55b8d44`
  is the binding acceptance target for the canonical writer.
