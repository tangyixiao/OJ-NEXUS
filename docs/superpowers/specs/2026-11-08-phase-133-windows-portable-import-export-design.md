# Phase 133 — W · Windows Portable Workspace Import/Export

Status: implementation design under the authorized balanced 121–220 roadmap.
The controller selects these implementation details; they are not additional
user-approved requirements.

Scope: Portable Workspace support in the Windows client — CLI verbs
(`portable inspect` / `portable import` / `portable export`) and a WPF
preview-confirm view — binding the frozen portable contract (`contracts/portable/v1/`,
phase 131). Bounded domain archive in the local SQLite schema (v7→v8),
lossless mixed-golden round trip, transactional rollback on failure/cancel,
CLI JSON counts identical to the WPF counts. The Android client (phase 132)
is the reference consumer; this phase ports the contract, not the Android
code. **The Windows client never opens the Android SQLite database.**

Baseline: main `d1660f8ca8e90749d15741d277bc2416f7f0200c` (phase 132 merged).

## Inputs this design synthesizes

- `docs/LONG_RANGE_PLAN_121_220.md:266-269` — deps 128, 131; scope W Core
  codec/store, CLI and WPF file operations; deliver "CLI 提供 inspect/import/export，
  WPF 提供预览确认；以领域映射落到本端 schema，保留不能编辑的受支持记录而不静默丢弃";
  accept "G-W；混合 golden 包无损往返，暂不能编辑的笔记/session 由领域归档保存；
  取消/失败事务回滚；CLI JSON 与 UI 计数相符，不直接打开 Android SQLite".
- `contracts/portable/v1/` — README, schema.json, check.py, golden corpus
  (15 cases; `pw-p01.json` canonical identity
  `81bb13c1746381f2d7f95ae69e421503dcc898aae823222e8a3caf40d55b8d44`).
- `docs/superpowers/specs/2026-11-06-phase-132-portable-import-export-design.md`
  — the Android consumer's frozen contract shape (`PortableModels.kt`) and the
  bounded-archive pattern this phase mirrors in C#.
- `windows/README.md` + phase 128A — guarded storage startup, the
  `ojnexus.lifecycle.lock`, exit codes 0–7 (must not be renumbered),
  `OJ_NEXUS_DATA_DIRECTORY` test override, CLI/WPF coexistence rules.
- `windows/src/OjNexus.Windows.Core/Storage/SchemaMigrator.cs` —
  `CurrentSchemaVersion = 7`; migrations validate-and-step per version.
- `windows/src/OjNexus.Windows.Core/*.csproj` — net8.0,
  Microsoft.Data.Sqlite 8.0.8, System.Text.Json already used by the adapters.
- `docs/verification/phase-128a-windows-storage-safety.md` — storage-safety
  rules that constrain this phase (preserve-on-failure, no silent replacement,
  exclusive ownership for migrations).
- `AGENTS.md` — conventional commits, no secrets, `BUILD SUCCESSFUL` gate;
  the Windows client continues the established C#/.NET stack of phase 128.

## Strict decode and canonical identity (C#)

One package is one UTF-8 JSON document. `PortableJson` (new, in
`OjNexus.Windows.Core/Portable/`) implements both directions with
`System.Text.Json` primitives:

- **Strict decode** via a hand-driven `Utf8JsonReader` walk (not
  `JsonDocument`, which silently accepts duplicate keys): reject non-UTF-8
  bytes, trailing data, duplicate object keys anywhere (explicit per-object
  key set), comments, NaN/Infinity tokens, and lone surrogate escapes — all
  as `INVALID_INPUT`, matching the Android codec's strictness.
- **Canonical write**: recursively sorted object keys, compact separators,
  Python-compatible string escaping (`"`, `\`, `\b \f \n \r \t`, `\u00xx`
  for other control characters), non-ASCII as raw UTF-8, integer literals
  preserved verbatim, trailing newline. The binding acceptance asserts the
  canonical bytes of `golden/pw-p01.json` hash to
  `81bb13c1746381f2d7f95ae69e421503dcc898aae823222e8a3caf40d55b8d44`
  (`SHA256.HashData`).
- **Validation precedence matches check.py exactly**: SIZE_LIMIT (pre-decode
  64 MiB + 16 KiB strings + 50 000 records) → INVALID_INPUT →
  UNSUPPORTED_VERSION → UNKNOWN_FIELD (closed schema + credential-shaped
  keys) → SIZE_LIMIT (overflow) → PATH_UNSAFE → DUPLICATE_ID →
  MISSING_REFERENCE → COUNTS_MISMATCH. Exactly one reject is raised with the
  wire category, detail and location.

The golden corpus files are consumed **in place** from
`contracts/portable/v1/golden/` as linked `Content` items with
`CopyToOutputDirectory=PreserveNewest` — the contract directory is never
copied or duplicated into the Windows tree.

## Bounded domain archive (schema v7→v8)

Phase 133 adds no domain UI for the five collections. Understood-but-not-
editable records land in two new tables created by `SchemaMigrator` step 8
(exclusive ownership already guaranteed by the phase 128 lock protocol):

- `portable_archive_packages` — one row keyed by `identity` (SHA-256 hex):
  scope_id, source_platform, generator, created_at_epoch_ms, counts_json.
- `portable_archive_records` — one row per record across all five
  collections: identity (FK), collection, stable_id, source_scope_id, origin,
  payload_json (the full record, canonical), source_tag, synthetic flag,
  insertion order. `portable_state` of each record: editable / stored_only /
  rejected — mirroring the Android `PortableArchiveDao` shape.

`PortableArchiveStore` (new) wraps replace-all in one `SqliteConnection`
transaction on the existing guarded connection factory; preview and cancel
perform zero writes; apply is all-or-nothing; export rebuilds canonical bytes
from archived rows and asserts the rebuilt identity equals the stored
manifest identity. `PortableRepository` orchestrates
codec → store → results and exposes preview/apply/export/cancel exactly like
the frozen `PortableWorkspaceRepository` contract on Android (same semantics,
C# naming, `System.Threading` cancellation instead of coroutine cancel).

## CLI surface

New verbs under the existing `CliParser`/`Program` dispatch, `--json`
responses via the existing `ConsoleRenderer`, existing exit codes only:

```
ojnexus.exe portable inspect --file <path> [--json]
ojnexus.exe portable import  --file <path> [--yes] [--json]
ojnexus.exe portable export  --file <path> [--json]
```

- `inspect` is the read-only preview: counts per collection, sources,
  skips, conflicts, editable/storedOnly/rejected counts, identity, reject
  lines (`CATEGORY: detail @ location`) when out of contract. No writes.
- `import` without `--yes` prints the same preview and exits 0 without
  writing (the CLI's confirm gate); `--yes` applies transactionally and
  reports rows upserted/deleted or `identical: true`. Any contract reject →
  no writes, exit 0 with `status=rejected` (a rejected package is a valid
  outcome, not a usage error); file-unreadable → exit 6 (storage) or
  2 (usage) per the existing classifier; internal failure → 7.
- `export` writes canonical bytes to the path; empty archive → explicit
  failure, nothing written.
- Missing `--file` or unknown verb → exit 2 usage.

## WPF surface

A fifth view `PORTABLE` in the existing `MainWindow` shell + `DesktopViewModel`:

- Open/Save file dialogs (`Microsoft.Win32.OpenFileDialog` /
  `SaveFileDialog`, JSON filter) — no SAF equivalent needed.
- Import is two-step: pick → preview panel (the same counts the CLI prints,
  so CLI JSON and UI counts cannot drift — both render from
  `PortablePreview`) → CONFIRM applies, CANCEL discards without writes.
- EXPORT rebuilds and writes via the save dialog; cancel/failed writes show
  a text status line; states are never color-only; uppercase telemetry tone.
- The view reuses the existing MVVM/command pattern of `DesktopViewModel`
  and the storage-startup guard; no new background threads beyond the
  existing dispatch pattern.

## Acceptance mapping (G-W)

| Requirement | Carried by |
| --- | --- |
| Mixed golden round trip lossless (import→apply→export identity) | Core golden round-trip test (T6) |
| Non-editable notes/sessions preserved via domain archive | bounded archive store + repository tests (T2/T3) |
| Cancel/failure transactional rollback | store replace-all transaction + repository cancel tests (T2/T3) |
| CLI JSON and UI counts identical | both render from the same `PortablePreview` model (T4/T5) |
| No direct Android SQLite access | design constraint; review checklist item |
| Golden corpus agreement with check.py (15 cases, exact categories) | codec tests (T1) |

## Review focus

- Preview/cancel zero writes; apply one transaction; export identity-verified.
- Canonical writer byte-parity with Python on the golden package.
- No renumbered exit codes; `portable import` without `--yes` never writes.
- WPF cancel at every stage is a no-op; CLI/WPF counts share one model.
- Storage-safety rules respected (lock protocol, preserve-on-failure,
  v7→v8 under exclusive ownership).
- No TODO stubs; no secrets; tests run green via
  `dotnet test windows/OjNexus.Windows.sln -c Release --no-restore`.
