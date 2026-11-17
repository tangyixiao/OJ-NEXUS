# Phase 133 A · Windows Portable Workspace Import/Export — Implementation Plan

> Executed with parallel sub-agents under controller coordination
> (user-directed). Each task owns a disjoint file set in its own worktree/
> branch; the controller merges, reviews, gates and publishes.

**Goal:** ship Windows Portable Workspace support — CLI `portable
inspect/import/export`, a WPF preview-confirm view, a bounded archive in the
local schema (v7→v8) — with lossless mixed-golden round trip, transactional
rollback, CLI/WPF count parity, and no Android-SQLite access. Deliver onto
`main` through many merged PRs and an independent review.

**Spec:** `docs/superpowers/specs/2026-11-08-phase-133-windows-portable-import-export-design.md`

**Baseline:** main `d1660f8ca8e90749d15741d277bc2416f7f0200c` (phase 132).

## Global constraints

- `AGENTS.md` rules apply where they generalize (conventional commits, no
  secrets, no TODO stubs, every stage ends with a green gate); the Windows
  client continues its established C#/.NET 8 stack — no new frameworks.
- `windows/Directory.Build.props` enables **TreatWarningsAsErrors**: every
  task must compile warning-free; a warning is a build failure.
- `contracts/portable/v1/` is READ-ONLY. Golden files are consumed as linked
  `Content` items, never copied into `windows/`.
- Exit codes 0–7 are a cross-platform contract: no renumbering; new `portable`
  verbs reuse them.
- `windows/README.md` command/exit-code sections must be updated by the CLI
  task (append-only additions).
- `Storage/SchemaMigrator.cs` is owned exclusively by Task 2; the schema
  steps to version 8 under the existing lock protocol.
- `DesktopViewModel.cs` is owned exclusively by Task 5.
- Every task builds/tests green via
  `dotnet test windows/OjNexus.Windows.sln -c Release --no-restore`
  (restore once with `--configfile windows/NuGet.Config --runtime win-x64`)
  before pushing; measured results go in the PR body.
- PR bodies include the dsh badge line. No force push. No workflow-file edits.

## Task 1 — strict JSON codec + contract validator (wave A)

**Files (exclusive):** `windows/src/OjNexus.Windows.Core/Portable/PortableJson.cs`,
`windows/src/OjNexus.Windows.Core/Portable/PortableCodec.cs`,
`windows/src/OjNexus.Windows.Core/Portable/PortableModels.cs`,
`windows/tests/OjNexus.Windows.Core.Tests/Portable/PortableCodecTests.cs`,
and the csproj edits adding the golden `Content` links
(`OjNexus.Windows.Core.csproj`).

Implement `PortableJson` (strict `Utf8JsonReader` decode with duplicate-key
rejection; canonical sorted-key writer with Python-compatible escaping and
trailing newline) and `PortableCodec` (validate/preview/identity with exact
check.py precedence and the eight wire categories). `PortableModels.cs`
mirrors the frozen Android shapes (`PortableRejectCategory`, `PortableTier`,
`PortablePreview`, `PortableApplyResult`, `PortableReject`). Tests: all 15
golden cases with expected categories, canonical identity of
`golden/pw-p01.json` equals
`81bb13c1746381f2d7f95ae69e421503dcc898aae823222e8a3caf40d55b8d44`,
duplicate-key/NaN/trailing-data rejections. No Storage dependency.

## Task 2 — bounded archive store + schema v8 (wave A)

**Files (exclusive):**
`windows/src/OjNexus.Windows.Core/Storage/PortableArchiveStore.cs`,
`windows/src/OjNexus.Windows.Core/Storage/SchemaMigrator.cs` (v7→v8 step),
`windows/tests/OjNexus.Windows.Core.Tests/Storage/PortableArchiveStoreTests.cs`.

`SchemaMigrator` gains step 8 creating `portable_archive_packages` +
`portable_archive_records` (columns per the spec) under exclusive ownership.
`PortableArchiveStore`: `replaceAll` in one transaction, read-only load,
clear, per-record state (editable/stored_only/rejected). Tests use a
temporary database through the existing connection factory patterns
(`OJ_NEXUS_DATA_DIRECTORY` override), covering load/replace/clear, migration
v7→v8 on a seeded v7 database, and transactional rollback on induced failure.

## Task 3 — repository orchestration (wave B, after A merges)

**Files (exclusive):**
`windows/src/OjNexus.Windows.Core/Portable/PortableRepository.cs`,
`windows/tests/OjNexus.Windows.Core.Tests/Portable/PortableRepositoryTests.cs`.

`PortableRepository(codec, store)`: read-only `preview`, confirmed
transactional `apply` (revalidate from bytes, identity no-op, whole-package
reject, one `replaceAll`), identity-verified `export`, write-free `cancel`.
Unit tests with a fake store: preview/cancel DB immutability, idempotent
reimport, whole-package rejection, rollback on induced store failure.

## Task 4 — CLI verbs (wave C, after B merges)

**Files (exclusive):**
`windows/src/OjNexus.Windows.Cli/Program.cs` (dispatch),
`windows/src/OjNexus.Windows.Cli/CliParser.cs`,
`windows/src/OjNexus.Windows.Cli/CliExitCode.cs` (mapping only if needed),
`windows/src/OjNexus.Windows.Cli/PortableCommands.cs` (new),
`windows/tests/OjNexus.Windows.Cli.Tests/PortableCommandTests.cs`,
`windows/README.md` (append `portable` verbs to Commands section).

Implement inspect/import/export per the spec: inspect read-only; import
without `--yes` never writes; `--yes` applies; export writes canonical bytes;
rejects exit 0 with `status=rejected`; usage → 2; storage-classified failures
→ 6. CLI JSON responses carry the same counts the WPF view renders.

## Task 5 — WPF portable view (wave C, after B merges)

**Files (exclusive):**
`windows/src/OjNexus.Windows.Desktop/MainWindow.xaml` (fifth view),
`windows/src/OjNexus.Windows.Desktop/DesktopViewModel.cs` (portable state +
commands),
`windows/src/OjNexus.Windows.Desktop/PortableView.xaml` + `.xaml.cs` if the
shell splits views per its existing pattern,
`windows/tests/OjNexus.Windows.Desktop.Tests/PortableViewModelTests.cs`,
`windows/scripts/ui-smoke.ps1` (extend the per-page switch + assertions for
the new PORTABLE page; AutomationProperties names required).

Pick → preview (same `PortablePreview` model as the CLI) → CONFIRM/CANCEL
dialog; SaveFileDialog export; every cancel/failure a write-free text status;
uppercase telemetry tone matching the existing shell.

## Task 6 — golden round trip, review, gate, delivery (controller)

1. Controller commits the SDD + this plan + the frozen contract mapping notes
   as the integration PR (base main); task branches cut from the integration
   branch after each merge, PRs base = integration branch, merged in order.
2. Golden round-trip test (controller commit on the integration branch):
   import→apply→export of `golden/pw-p01.json` reproduces the canonical
   identity; reimport idempotent; preview/cancel zero writes.
3. Independent review subagent audits the final diff (house review record);
   findings fixed in a follow-up PR if needed.
4. Gate (G-W): `dotnet test windows/OjNexus.Windows.sln -c Release --no-restore`
   + `dotnet build windows/OjNexus.Windows.sln -c Release --no-restore` →
   `Build succeeded` with 0 failed tests; `pwsh -File windows/scripts/smoke.ps1
   -Configuration Release` and `pwsh -File windows/scripts/ui-smoke.ps1
   -Configuration Release` green (UI changed → smoke + new page per the G-W
   duty); Windows CI (`windows.yml`) green on the delivery PR. Verification
   record + handoff + ledger + roadmap line 266 ticked; final delivery PR to
   `main` (badge in body), merged via `gh`.

## Review focus

- Preview/cancel zero writes; apply one transaction; export identity-verified.
- Canonical writer byte-parity with Python on the golden package; all 15
  golden cases with exact wire categories.
- `portable import` without `--yes` never writes; exit codes unchanged.
- CLI JSON and WPF counts render from one model; no drift possible.
- WPF cancel at every stage a no-op; storage-safety rules respected
  (lock protocol, exclusive-ownership migration, preserve-on-failure).
- No TODO stubs; no secrets; tests green before every push.
