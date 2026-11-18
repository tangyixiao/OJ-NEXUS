# Phase 134 — Linux Portable Workspace Import/Export — Design

Status: ACTIVE. This design was synthesized by the controller from the phase-134
recon (explorer report, 2026-11-18) and binds the phase 131 portable contract
(`contracts/portable/v1/`) to the Linux client.

Baseline main: `0b17d7c` (phase 133 fully delivered; PRs #62/#66).

## Inputs this design synthesizes

- **Roadmap** (`docs/LONG_RANGE_PLAN_121_220.md:271-274`): PHASE 134 [ ] — L 路
  Linux 可移植性导入导出. Dependencies 129/131; L 的 Core codec/store、CLI 与 Qt
  file dialog. Deliverable: 在 CLI/Qt 完成领域包检查、预览和显式导入；保留源 ID 与
  day key；处理 sandbox/file permission 失败. Acceptance G-L: 混合 golden 包无损
  往返，暂不能编辑的笔记/review/session 有保存状态；错误码一致；路径穿越包不落盘；
  重复导入不翻倍，取消无部分记录.
- **Phase 131 contract** (frozen): `contracts/portable/v1/check.py` (stdlib-only
  oracle, category precedence documented in-file), `schema.json`, golden corpus
  (15 packages + `cases.json`), pinned mixed-golden identity
  `81bb13c1746381f2d7f95ae69e421503dcc898aae823222e8a3caf40d55b8d44`.
- **Android reference** (main): `app/src/main/java/com/ojnexus/core/portable/` +
  `feature/portable/`.
- **Windows reference** (main, phase 133): `windows/src/OjNexus.Windows.Core/Portable/`
  (PortableModels.cs / PortableJson.cs / PortableCodec.cs / PortableRepository.cs /
  PortableArchiveStore.cs), CLI `PortableCommands.cs`, WPF `DesktopViewModel.cs`
  portable section, tests.
- **Linux recon findings** (verified 2026-11-18): C++20/Qt 6.4/QML, CMake+Ninja;
  storage schema v2 current (`storage_schema.cpp`), hand-rolled v1→v2 migration in
  `store.cpp`; guarded-startup store contract; CLI `CommandKind` enum + `ExitCode`
  0–7 contract already matching the cross-platform table (`runner.h:10-14`);
  QML UI = `Main.qml` TabBar (3 pages) over `MainViewModel` (QObject/Q_PROPERTY/
  Q_INVOKABLE); **no file dialog exists yet**; **no portable code** (greenfield);
  **no warning flags** in linux/ CMake; **no screenshot harness** (QML smoke via
  `qmltestrunner` is conditional); **builds impossible natively on this Windows
  host** — the verification vehicle is CI `linux.yml` (ubuntu-24.04, Qt 6.4,
  offscreen platform, ctest, CLI jq smoke, cpack) per
  `docs/verification/phase-129a-linux-storage-startup.md:89-93`.

## Decisions

1. **No QJson anywhere in the portable path.** `QJsonObject` normalizes numbers to
   double and last-wins on duplicate keys — both break the frozen contract. The
   Linux codec hand-rolls a byte-level strict decoder + canonical writer mirroring
   `PortableJson.cs`: BOM/trailing-data/duplicate-key/NaN/Infinity/lone-surrogate
   rejection, depth ≤ 200, canonical form sorted by Unicode **code point** (not
   UTF-16 units), Python-compatible string escaping, `"\n"` final newline,
   `-0` → `0` normalization, arbitrary-precision number literals preserved verbatim
   as text (`PortableValue::Num(string)`), integer-literal classification via
   anchored full-string match (never `toLongLong`).
2. **Regex parity**: `QRegularExpression` is PCRE2 — its default `$` matches before
   a trailing newline, exactly like Python/Java/.NET. Therefore schema patterns get
   the same translation as Windows (`$` → `\z`, `\d` → `[0-9]` literal-parity), and
   **PATH_UNSAFE uses the RAW pattern without translation** (phase-133 round-2
   finding F5). Pattern matching runs with a 1 s match timeout.
3. **Schema v2 → v3**: add `portable_archive_packages` + `portable_archive_records`
   (same column shapes as Windows `SchemaMigrator.cs` v8 tables), following the
   existing validate → migrate → revalidate pattern. The Linux validator rejects
   NOT-NULL columns without defaults — the two new tables are declared in
   `storage_schema.cpp` and validated in the same style; `CurrentSchemaVersion`
   semantics (`schema_metadata` insert value 3) update together with the validator
   (accept v1/v2/v3) and the migration step. FK `records→packages` with
   `ON DELETE CASCADE`; `PRAGMA foreign_keys=ON` already set by the store.
4. **Repository semantics** mirror `PortableRepository.cs`: preview (zero store
   writes), explicit confirm apply (single transaction, replace-all + tiered rows),
   export (canonical bytes from store, `ARCHIVE EMPTY` condition), cancel =
   cooperative pre-write checkpoint (cancel with no apply in flight is a no-op —
   Android-parity), duplicate-ID/conflict/missing-reference/counts rules delegated
   to the codec layer exactly as check.py orders them. `payload_json` is stored
   verbatim as the canonical string.
5. **CLI**: new `CommandKind::PortableInspect/PortableImport/PortableExport` with
   `--file` (inspect/export target) and `--yes` (import confirm gate); `portable
   import` without `--yes` prints the confirmation JSON and writes nothing (exit
   0); rejects exit 0; IO failures exit 6; internal/empty-archive exit 7; usage
   exit 2 — no renumbering of `ExitCode` (contract table unchanged). JSON mode
   renders the same preview model the GUI uses.
6. **QML GUI**: fourth `PORTABLE` tab in `Main.qml`; `MainViewModel` gains portable
   properties/invokables (pick import file → pending bytes → preview panel →
   CONFIRM/CANCEL → apply; export pick → write). First Qt file dialog via
   `QtQuick.Dialogs` `FileDialog` injected as an invokable-backed seam so VM tests
   can stub pickers without dialogs. Every cancel/back-out is write-free; failures
   surface as state (text + counts), never silent drops.
7. **Verification vehicle**: local host cannot build Linux targets. G-L gate =
   (a) contract-level `python3 check.py --cases golden/cases.json golden/` runs on
   the host (works locally if Python present, and in CI), (b) full CI
   `linux.yml` green on the delivery PR (cmake+ctest all targets incl. new
   portable tests, offscreen QML smoke, CLI jq smoke, cpack), (c) an extended CLI
   portable smoke (`linux/scripts/smoke-portable.sh` invoked by `linux.yml` —
   additive one-line workflow change). Warning stance: **no new warning flags this
   phase** (linux/ has none today); recorded as an observation, not a deviation.
8. **Golden corpus access**: CMake copies `contracts/portable/v1/golden/` next to
   the test binaries (`file(COPY ...)`), consumed relative to the test working
   directory — never a copy into `linux/` (single source of truth).

## Out of scope

Domain projection of imported records into Linux domain stores (tiered archive
only, parity with Windows); whole-DB backup; Apple consumer (phase 135); any
renumbering of the shared exit-code table; screenshots/UIA tooling for Qt.
