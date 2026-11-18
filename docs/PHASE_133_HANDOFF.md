# Phase 133 Windows Portable Workspace Import/Export — Handoff

Status: delivered to `main` (delivery PR #62, merge `afeadf7`; CI green on head `50844ad`).
Date: 2026-11-18 (Asia/Shanghai).
Baseline main: `d1660f8` (phase 132 merged).
Integration branch: `codex/phase-133-windows-portable-import-export`.

> Current state at a glance
> Phase 133 shipped the Windows consumer of the phase 131 portable contract:
> a strict C# codec byte-parity with the Python checker (15-case golden
> corpus, identity `81bb13c1…d44`), a bounded archive in the local schema
> (v7→v8), a transactional repository with lossless export, CLI `portable
> inspect/import/export` verbs, and a WPF PORTABLE page with preview-confirm
> — the CLI JSON and the UI render the same `PortablePreview` model, so
> counts cannot drift. Delivered through merged PRs #56–#63 (review record
> PR #63; round-2 parity fixes `fa33c89`), an independent review record, and
> the G-W gate (final gate 393 tests / 0 failures on `fa33c89`, smoke + UIA
> smoke 5/5). Phases 134–135 bind Linux and Apple.

## 1. What shipped

- Core (`OjNexus.Windows.Core/Portable/`): `PortableModels.cs` (frozen
  shapes + `PortableSpec`), `PortableJson.cs` (strict `Utf8JsonReader`
  decode — duplicate keys/trailing data/NaN/lone surrogates/BOM rejected —
  and a canonical writer sorted by Unicode code point with Python escaping),
  `PortableCodec.cs` (check.py-exact precedence; pattern translations
  `$`→`\z`, `\d`→`[0-9]`), `PortableRepository.cs` (preview/apply/export/
  cancel with cooperative pre-write cancel checkpoints).
- Storage: `SchemaMigrator` v7→v8 (`portable_archive_packages` +
  `portable_archive_records`), `PortableArchiveStore` (single-transaction
  replace-all), `WindowsStorageSession.CreatePortableRepository(operation)`
  (the shared bridge for CLI and WPF).
- CLI (`PortableCommands.cs` + parser/dispatch): `portable inspect` (read-only
  preview), `portable import` (confirm gate via `--yes`; rejects exit 0),
  `portable export` (canonical bytes; `ARCHIVE EMPTY` → exit 7).
- WPF: fifth `PORTABLE` page — import file dialog → preview panel (identity,
  counts, sources, capability) → CONFIRM/CANCEL → apply; export save dialog;
  every cancel/back-out write-free; `ui-smoke.ps1` visits and asserts the
  page (5/5 screenshots).
- Tests: 22 new portable tests (codec 16, store 6, repository 8, VM 8 —
  counts overlap; see the verification record), golden corpus + pinned
  identity, transactional rollback, CLI confirm-gate and exit mapping.

## 2. Key decisions and rationale

- **Strict decode by hand-driven `Utf8JsonReader`.** `JsonDocument` accepts
  duplicate keys; the contract rejects them. Canonical writing is manual
  byte emission because `Utf8JsonWriter`'s default encoder escapes HTML-
  sensitive characters Python does not.
- **Pattern translations mirror Android exactly**: terminal unescaped `$` →
  `\z` (Android compiles to the absolute-end anchor) and `\d` → `[0-9]`
  (Java's `\d` is ASCII-only; .NET's matches Unicode digits).
- **All five collections archive; tier is a projection promise.** problems/
  reviews are EDITABLE (live Windows domain UI exists), attempts/notes/
  sessions STORED_ONLY — but every record lands in the bounded archive this
  phase; domain projection is later work. The acceptance's notes/sessions
  requirement is met by the archive itself.
- **`GeneratorJson` header column.** The v8 package header stores the
  canonical generator-object JSON — the combined `name version` display
  string is lossy for export rebuild.
- **Real-store integration tests.** The repository tests run against the
  guarded store through `WindowsStorage.OpenAsync` — the store is sealed
  with an internal ctor and its transactional behavior is already unit
  covered; a fake would only restate its own assumptions.
- **One additive session bridge.** `CreatePortableRepository(operation)`
  shares the operation's permit and the session's factory — the same scope
  pattern the sync store uses — with zero existing Core lines changed.

## 3. How to verify

From the repo root on Windows (dotnet SDK 10.0.401; solution targets
net8.0):

```
dotnet test windows/OjNexus.Windows.sln -c Release --no-restore
dotnet build windows/OjNexus.Windows.sln -c Release --no-restore
pwsh -File windows/scripts/smoke.ps1 -Configuration Release
pwsh -File windows/scripts/ui-smoke.ps1 -Configuration Release
```

Measured on the merged integration tip `fa33c89`: 393 tests / 0 failures, clean TWE
build, CLI ready + WPF alive, UIA 5/5 with PORTABLE rendered. The portable
contract gates still apply: `python3 check.py --cases golden/cases.json
golden/` from `contracts/portable/v1/` (CI workflow `contracts.yml`
unchanged).

## 4. Open work / next phases

- Phases 134–135 bind Linux and Apple consumers; the mixed golden round trip
  remains the shared acceptance vehicle.
- Domain projection of EDITABLE-tier records into the Windows problems/
  reviews stores is deliberately deferred (archive-only this phase).
- Review observations are routed through the review record; P1/P2 would have
  blocked delivery.

## 5. Gotchas for the next agent

- **dotnet + pwsh need ABSOLUTE paths** when invoked through the DSH shell —
  relative paths after a `cd` do not resolve reliably.
- **`TreatWarningsAsErrors` is on** for the whole Windows tree: a warning is
  a build failure.
- **`C# definite-assignment does not honor [DoesNotReturn]`** — helpers that
  always throw must RETURN the exception (`BuildReject`) and call sites must
  `throw` it, or CS0165 fires on pattern-matched locals.
- **UTF-16 iteration breaks astral characters**: emit non-ASCII by code
  point (surrogate pairs written together) or canonical parity breaks.
- **Localization-style parity tests do not exist on Windows** — but the
  `LocalizationResourceTest` pattern (Android) means any future Windows
  string tables must mirror keys.
- **Agents stalled twice on this phase** (Write-tool rejections/stalls); the
  controller implemented directly from the agents' verified research. Keep
  agent briefs code-first with explicit file ownership and hard deadlines.

## 6. Links

- Spec: [docs/superpowers/specs/2026-11-08-phase-133-windows-portable-import-export-design.md](superpowers/specs/2026-11-08-phase-133-windows-portable-import-export-design.md)
- Plan: [docs/superpowers/plans/2026-11-08-phase-133-windows-portable-import-export.md](superpowers/plans/2026-11-08-phase-133-windows-portable-import-export.md)
- Plan ledger: [docs/verification/phase-133-plan-ledger.md](verification/phase-133-plan-ledger.md)
- Verification record: [docs/verification/phase-133-windows-portable-import-export.md](verification/phase-133-windows-portable-import-export.md)
- Independent review: [docs/verification/phase-133-task6-review.md](verification/phase-133-task6-review.md)
- Contract: [contracts/portable/v1/README.md](../contracts/portable/v1/README.md)
- Merged PRs: #56 (SDD), #58 (codec), #57 (store), #59 (repository),
  #60 (CLI + bridge), #61 (WPF view), #63 (review record); delivery PR #62
  to `main` (merge `afeadf7`, CI green: Android run `44332741408` +
  Windows run `44332741442` on head `50844ad`).
- Phase 132 handoff: [docs/PHASE_132_HANDOFF.md](PHASE_132_HANDOFF.md)

Handoff status: this document is the current authority for the Phase 133
Windows delivery. Sections 1–6 are authoritative; earlier phases keep their
own handoff documents.
