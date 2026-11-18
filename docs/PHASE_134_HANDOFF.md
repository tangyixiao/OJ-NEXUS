# Phase 134 Linux Portable Workspace Import/Export — Handoff

Status: delivered to `main` (delivery PR #73, merge `f292ae0`; delivery CI all
five checks success on head `f522b25`).
Date: 2026-11-19 (Asia/Shanghai).
Baseline main: `0b17d7c` (phase 133 merged).
Integration branch: `codex/phase-134-linux-portable-import-export`.

> Current state at a glance
> Phase 134 shipped the Linux consumer of the phase 131 portable contract:
> a strict C++20 codec byte-parity with the Python checker (15-case golden
> corpus, identity `81bb13c1…d44`), a bounded archive in the local schema
> (v2→v3), a transactional repository with lossless export, CLI `portable
> inspect/import/export` verbs, and a QML PORTABLE page with preview-confirm —
> the CLI JSON and the UI render the same `PortablePreview` model, so counts
> cannot drift. Delivered through five merged PRs (#68–#72); review record +
> delivery CI are the closing gates. Phases 135 (Apple) binds next.

## 1. What shipped

- Core (`linux/src/core/include/ojnexus/core/` + `linux/src/core/`):
  `portable_models.{h,cpp}` (frozen shapes + `PortableSpec`), `portable_json.{h,cpp}`
  (hand-rolled byte-level strict decoder — BOM/trailing data/duplicate keys/
  NaN/lone surrogates/invalid UTF-8/depth>200 rejected — and a canonical
  writer sorted by Unicode code point with Python escaping and a trailing
  newline), `portable_codec.{h,cpp}` (check.py-exact precedence; schema
  patterns translate terminal `$`→`\z` and `\d`→`[0-9]`, PATH_UNSAFE uses the
  RAW pattern), `portable_repository.{h,cpp}` (preview/apply/export/cancel
  with cooperative pre-write cancel checkpoints).
- Storage: schema v3 (`portable_archive_packages` + `portable_archive_records`,
  Windows-v8 shapes, FK CASCADE validated by the schema validator), v1→v2→v3
  migration chain, `portable_archive_store.{h,cpp}` (single-transaction
  replace-all) over the guarded `SqliteStore`.
- CLI (`portable_runner.cpp` + parser additions): `portable inspect`
  (read-only preview), `portable import` (confirm gate via `--yes`; rejects
  exit 0), `portable export` (canonical bytes; ARCHIVE EMPTY → exit 7); exit
  codes stay on the shared immutable 0–7 table.
- QML: fourth `PORTABLE` page — import file dialog → preview panel (identity,
  counts, sources, capability) → CONFIRM/CANCEL → apply; export save dialog;
  every cancel/back-out write-free; dialogs injected via
  `requestImportFile`/`requestExportFile` signals so tests stub the pickers.
- Tests: codec (golden corpus + pinned identity + reject categories), store
  (replace/rollback/cascade/migrations), repository (8 cases incl. round trip),
  CLI (7 cases incl. confirm-gate zero-write proof + export hash check), VM
  (4 cases) — 40+ new tests; golden corpus consumed from `contracts/` via
  CMake `file(COPY)`, never copied into `linux/`.

## 2. How to build/test (host reality)

This Windows host has NO Linux toolchain (recon-verified). All build/test
gates run in CI `linux.yml` (ubuntu-24.04, offscreen Qt, `cmake -S linux -B
build/linux -G Ninja -DOJNEXUS_BUILD_GUI=ON -DOJNEXUS_BUILD_TESTS=ON`,
`ctest`, CLI jq smoke, cpack DEB). The contract oracle runs anywhere:
`python check.py --cases golden/cases.json golden/` from
`contracts/portable/v1/` — measured 15/15 on the host.

## 3. Parity decisions to keep

- QString is UTF-16: sort keys by code point (`PortableJson::compareCodePoints`),
  emit astral pairs whole, never QString::compare for canonical ordering.
- No QJson anywhere in the portable path: QJsonDocument normalizes numbers to
  double and last-wins duplicate keys. `payload_json` is stored verbatim.
- PCRE2 `$` matches before a trailing newline like Python/Java/.NET: schema
  patterns still get the terminal-`$`→`\z` translation (literal parity with
  Android), PATH_UNSAFE stays RAW.
- Numbers are text: `Num(literal)` verbatim; `-0`→`0` at decode; integer
  classification is a full-string scan, never an unanchored regex or toLongLong.
- Size limits: MaxStringBytes counts UTF-8 bytes; schema maxLength counts
  QString::size() (UTF-16 units, matching the Android/Windows references).

## 4. Gotchas for the next agent

- **dotnet/pwsh/git need care in the harness shell** — the shell is cmd.exe;
  prefer absolute paths and avoid bash-style loops (`$()`/`for i in`).
- **`std::optional<Rule>` cannot recurse** — the rule tree uses
  `std::shared_ptr<const Rule>` (`RulePtr`) throughout; a by-value recursive
  member is ill-formed.
- **QTest macros bake in a bare `return`** — never use QVERIFY/QFAIL inside a
  function returning a value; use `QTest::qFail(...); return {};`.
- **`PortableArchiveStore` lives in `namespace OjNexus`**, the codec/repository
  in `OjNexus::Portable` — mixing the qualification fails the build.
- **CI apt install of Qt6 is the long pole** (~20–30 min on hosted runners);
  budget for it before assuming a run is stuck.
- **Agents stalled twice this phase** (zero output past hard deadlines); the
  controller implemented all five tasks directly. Keep agent briefs code-first
  with explicit file ownership and hard deadlines, and harvest partial work.
- **No warning flags in linux/ CMake** — deliberate (parity with the existing
  tree); recorded as an observation, not a deviation.

## 5. Open work / next phases

- Phase 135 (Apple) binds the macOS/iOS consumer to the same contract; the
  mixed golden round trip remains the shared acceptance vehicle.
- Domain projection of EDITABLE-tier records into Linux domain stores is
  deliberately deferred (archive-only this phase).
- Review observations are routed through the review record; P1/P2 would block
  delivery.

## 6. Links

- Spec: [docs/superpowers/specs/2026-11-18-phase-134-linux-portable-import-export-design.md](superpowers/specs/2026-11-18-phase-134-linux-portable-import-export-design.md)
- Plan: [docs/superpowers/plans/2026-11-18-phase-134-linux-portable-import-export.md](superpowers/plans/2026-11-18-phase-134-linux-portable-import-export.md)
- Plan ledger: [docs/verification/phase-134-plan-ledger.md](verification/phase-134-plan-ledger.md)
- Verification record: [docs/verification/phase-134-linux-portable-import-export.md](verification/phase-134-linux-portable-import-export.md)
- Independent review: [docs/verification/phase-134-task6-review.md](verification/phase-134-task6-review.md)
- Contract: [contracts/portable/v1/README.md](../contracts/portable/v1/README.md)
- Merged PRs: #68 (store), #69 (codec), #70 (repository), #71 (CLI),
  #72 (QML view); delivery PR #73 to `main` (merge `f292ae0`; delivery CI all
  five checks success on head `f522b25` — Linux `44350847032`, Windows
  `44350847038`/`44350747579`, Android `44350847025`/`44350747566`).
- Phase 133 handoff: [docs/PHASE_133_HANDOFF.md](PHASE_133_HANDOFF.md)

Handoff status: this document is the current authority for the Phase 134
Linux delivery. Sections 1–6 are authoritative; earlier phases keep their own
handoff documents.
