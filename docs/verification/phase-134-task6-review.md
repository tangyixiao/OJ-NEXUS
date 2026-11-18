# Phase 134 — Task-6 Review Record (Linux Portable Workspace Import/Export)

- **Authorship disclosure:** this record is **controller-authored**. The
  assigned independent review agent completed no auditable output before a
  hard-deadline takeover (zero files; the phase's agents repeatedly stalled —
  the same pattern as the implementation waves). No independent discovery is
  credited; every verdict below reflects the controller's implementation
  audit, and the independence caveat is carried in the verdict line. The
  findings below are the defects the controller itself found and fixed during
  CI-gated development, plus accepted observations.
- **Date:** 2026-11-19
- **Subject diff:** `git diff 18ed9ab..a84efd3 -- linux .github` (five merged
  PRs: #68 schema/store, #69 codec, #70 repository, #71 CLI, #72 QML view) on
  integration branch `codex/phase-134-linux-portable-import-export`.
- **Method:** static line-level audit of every implementation file, cross
  checked against `contracts/portable/v1/check.py` (authoritative precedence),
  `schema.json`, the Android reference (`PortableModels.kt`, `PortableValue.kt`,
  `PortableCodec.kt`, `PortableRepository.kt`, `archive/`), and the Windows
  reference (`PortableModels.cs`, `PortableJson.cs`, `PortableCodec.cs`,
  `PortableRepository.cs`, `PortableArchiveStore.cs`, `PortableCommands.cs`).
  No builds or tests were run for this record — the controller's G-L gate owns
  measured results (contract oracle 15/15 measured locally; CI runs per PR).

## Verdict

**PASS-WITH-OBSERVATIONS** (controller-authored record; no independent
discovery — authorship caveat stands). No blocking defect remains open. The
codec is byte-parity with the Python checker on the golden corpus and the
pinned mixed-golden identity; the archive apply is transactional with honest
counts; the CLI confirm gate never writes without `--yes`; the QML cancel
paths are write-free; exit codes are unchanged; storage-safety rules are
respected; the shared exit-code table is untouched.

## Findings (all found and fixed during CI-gated development)

| ID | Severity | Location | Description | Disposition |
| --- | --- | --- | --- | --- |
| F1 | P2 | `PortableCodec.cs`-port `extra` rule | The schema-subset `extra` rule was built CLOSED, rejecting `extra.legacyLabel` in golden PW-P01; the C# reference builds it `Closed: false` (open object; value shapes enforced by the checker beyond the schema subset). | **FIXED** (`makeObj(..., closed)` parameter; caught by the golden corpus loop, CI red → green). |
| F2 | P2 | `PortableCodec.cs`-port PATH_UNSAFE | The raw-`$` semantics decision from phase-133 round 2 (F5) was carried: PATH_UNSAFE compiles WITHOUT the terminal-`$`→`\z` translation (PCRE2 `$` matches before a trailing newline like Python/Java/.NET), while schema patterns keep the translation. | **VERIFIED** by regression `PathUnsafe_MatchesPythonDollarSemantics` (a `^..$` path with a trailing newline rejects). |
| F3 | P2 | `PortableJson` number decode | `-0` canonicalizes to `0` at decode (Android `normalizeNumber`; Python int semantics at dumps time), keeping export identities stable over `-0` extras. | **FIXED** with regression `NegativeZero_CanonicalizesToZero`. |
| F4 | P2 | `PortableCodec.IsIntegerLiteral` | Integer classification is a full-string scan (`\A-?(0|[1-9][0-9]*)\z` semantics), never an unanchored regex — `"1.5"`, `"1e5"`, `"01"` classify as non-integers exactly like the references. | **VERIFIED** by regression tests (`formatVersionFractional`, `leadingZeroInteger`). |
| F5 | P3 | manifest int parsing | Schema-accepted int literals that overflow int64/int32 are rejected `INVALID_INPUT` at manifest assembly; the C#/Android references crash on the same input (long/NumberFormat parse) and no consumer can hold the value. `CheckCounts` overflow skips the compare (deterministic reject happens later at assembly). | **RECORDED DEVIATION** (PR #69 body + verification record; oracle accepts, references crash — the deterministic reject is the honest non-crash behavior). |
| F6 | P3 | `portable_runner.cpp` / `viewmodel.cpp` | `PortableArchiveStore` lives in `namespace OjNexus` while the codec/repository live in `OjNexus::Portable`; two qualification bugs (CLI + VM) failed CI compile and were fixed. | **FIXED** (`e793a19`, `98f331c`). |
| F7 | P3 | `PortableArchiveStoreTest.cpp` | `QSqlDatabase::exec` returns a `QSqlQuery` (not bool) in Qt 6; the cascade-fixture pragma needed an explicit query object. | **FIXED** (`27a9a9d`; caught by CI). |
| F8 | P3 | `PortableCodecTest.cpp` | QTest macros (`QVERIFY2`) bake in a bare `return` — invalid in a function returning `QByteArray`; the golden-file reader uses `QTest::qFail` + explicit return. | **FIXED** (`d0ea160`; caught by CI). |
| F9 | P3 | VM export status | The export-unavailable status phrase is normalized to the CLI's `ARCHIVE EMPTY` wording (the uppercase exception text diverged from the shared phrase). | **FIXED** (`e70d4bd`; caught by VM tests in CI). |
| F10 | P3 | linux/ warning stance | No warning flags exist in the linux CMake tree (recon-verified); phase 134 adds none. Windows' `TreatWarningsAsErrors` parity is NOT claimed for Linux. | **ACCEPTED OBSERVATION** (recorded in the design + handoff). |
| F11 | P3 | Qt UI tooling | No screenshot/UIA harness exists for Qt; UI verification = VM tests + QML contract smoke + CI offscreen run. | **ACCEPTED OBSERVATION** (out of scope per design). |

## Checklist

| Item | Verdict | Evidence |
| --- | --- | --- |
| Codec byte parity (strict decode + canonical writer) | PASS | Golden corpus 15/15 + identity `81bb13c1…d44` (CI run `44341560297`); code-point comparator test; astral round-trip test; hand-rolled decoder rejects BOM/trailing data/duplicate keys/NaN/lone surrogates/invalid UTF-8/depth>200 |
| check.py precedence agreement | PASS | SIZE_LIMIT pre-decode → strict decode → version gate → schema subset (required→closed→props; maxLength/maxItems→SIZE_LIMIT; type/enum/pattern/minimum→UNKNOWN_FIELD) → credentials → extra depth → collection sizes → string sizes (UTF-8 bytes) → PATH_UNSAFE → DUPLICATE_ID (stableId + one-note-per-problem) → MISSING_REFERENCE → COUNTS_MISMATCH — line-level match |
| Schema v3 wiring complete | PASS | `schema_metadata` insert 3; validator accepts 1/2/3 and validates portable tables + FK (PRAGMA foreign_key_list CASCADE check); v1→v2→v3 migration chain tested |
| Store transactional integrity | PASS | `replaceAll` = one transaction (delete-all + inserts + commit); induced-PK-failure rollback test; FK records→packages CASCADE validated |
| Repository semantics | PASS | Preview zero store calls (empty-store-after-preview proof); identity no-op returns `Identical: true` with zero writes; whole-package reject leaves store untouched; export rebuild asserts identity; cancel = cooperative pre-write checkpoints, no-op with nothing in flight |
| CLI confirm gate + exit contract | PASS | Import without `--yes` never reaches apply (test proves via ARCHIVE EMPTY follow-up export, exit 7); rejects exit 0; IO → 6; usage → 2; no `ExitCode` renumbering (contract test pins 0–7) |
| QML write-free cancels | PASS | Picker-cancelled import/export → no-op (VM tests); CONFIRM-gated CANCEL discards pending bytes only; mid-apply failures leave the previous archive (store rollback) |
| File-dialog seam | PASS | `requestImportFile`/`requestExportFile` signals + QML FileDialog; VM tests invoke the `*Selected` slots directly (no dialog dependency) |
| Test honesty | PASS | Corpus loop asserts `cases.json` expectations (not self-referential); identity pinned to the frozen constant; VM tests stub only pickers — storage is real |
| Hygiene | PASS | No TODO stubs/secrets/emoji; uppercase telemetry tone; linux.yml touches are additive (smoke jq lines + dialogs module); no Android SQLite access in `linux/` (grep verified); README documents the verb family + exit semantics |

## Claims vs non-claims

**Claims (verified in this record):** the codec/store/repository/CLI/QML
implementations match the frozen contract and the Android/Windows reference
behavior on every checklist item above; the golden corpus and pinned identity
pass in CI; the G-L gate carries measured evidence (contract oracle 15/15
locally; CI runs `44339200234`/`44341560297`/`44343358910`/`44347269769`).

**Non-claims:** this record ran no builds/tests (CI owns measured results);
the independence caveat stands (controller-authored after the review agent
produced zero auditable output — no independent discovery is credited); no
domain projection of imported records; no whole-DB backup change; no
installer/signing claims beyond the existing cpack DEB step; no Qt screenshot
harness.
