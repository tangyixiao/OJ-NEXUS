# Phase 131 — Portable Workspace v1 Contract

Status: **VERIFIED**. The controller ran the final gate on the merged controller branch
`codex/phase-131-portable-contract` at `4173c0d` on 2026-11-05 (Python 3.14.3 locally; CI on
ubuntu-latest with `python3` from `actions/setup-python@v5`). Every measured value below is an
observed result, not an estimate. The 15-case golden corpus passes, the canonical identity is
stable across runs, the checker self-test passes 7/7 checks, compilation is clean, and the
`Portable workspace contract` workflow is green on the merged branch (run `42030737524`, all
7 steps success). The independent review is a separate record
([phase-131-schema-checker-review.md](phase-131-schema-checker-review.md)); its two minor
findings (F1/F3 category labels, F6 pattern anchoring) were fixed on `codex/phase-131-hardening`
(PR #38) with non-vacuous regressions, and its disposition is recorded there.

The Android `./gradlew test` + `assembleDebug` gate is unaffected by this phase (no `app/`
changes); PR CI runs it anyway. The most recent Android CI runs on `main` are green. No debug
APK SHA256 is recorded for this phase: Phase 131 builds no application. Source base for this
record: `acf4f8c` on `codex/phase-131-verification`; the controller merges and pushes.

## Scope and inputs

Scope: Phase 131 deliverables only. In scope: `contracts/portable/v1/` (README, `schema.json`,
`check.py`, `golden/`, `selftest.py`), `.github/workflows/contracts.yml`,
`docs/PORTABLE_WORKSPACE.md`, `docs/PHASE_131_HANDOFF.md`, this record, and the SDD ledger.
Out of scope: no `app/` changes and no platform consumer. Phases 132-135 bind consumers later;
this phase makes no consumer claim. The checker is a reference implementation, not platform proof.

Inputs / links:

- [Design spec](../superpowers/specs/2026-11-01-phase-131-portable-workspace-design.md)
- [Implementation plan](../superpowers/plans/2026-11-01-phase-131-portable-workspace.md)
- [Contract README](../../contracts/portable/v1/README.md)
- [JSON schema](../../contracts/portable/v1/schema.json)
- [Reference checker](../../contracts/portable/v1/check.py)
- [Golden case index](../../contracts/portable/v1/golden/cases.json)
- [Checker self-test](../../contracts/portable/v1/selftest.py)
- [CI workflow](../../.github/workflows/contracts.yml)
- [Developer guide](../PORTABLE_WORKSPACE.md)
- [Phase 131 handoff](../PHASE_131_HANDOFF.md)
- [Long-range plan: G-S duty and Phase 131 acceptance](../LONG_RANGE_PLAN_121_220.md)
- [House style: Phase 126 recovery vectors](phase-126-recovery-vectors.md)

## Adoption and G-S coverage

G-S duty (D:/AndroidAppCoding/docs/LONG_RANGE_PLAN_121_220.md:104): "契约/schema 检查、golden
fixture 正反例、所有受影响消费者回归；文档期检查编号/依赖/链接/范围".

Phase 131 acceptance (D:/AndroidAppCoding/docs/LONG_RANGE_PLAN_121_220.md:258): "G-S；覆盖未知字
段/版本、缺失引用、重复 ID、大小限制与路径穿越；同包重复导入幂等，同 ID 异内容必须显式处理。未知
关键语义拒绝；仅 schema 明确允许的扩展无损保留。141/146/151/191 的新语义需版本化并同步更新
codec/golden；旧包身份不明保持 unassigned，不能归给当前账号。".

The acceptance line requires coverage of 未知字段/版本, 缺失引用, 重复 ID, 大小限制 and 路径穿越.
`PW-P01` is the positive fixture; the remaining ids are negative fixtures. All cases cite the
G-S duty they cover. `PW-N08` (identity stability) and `PW-N12` (unassigned provenance) are
positive-side properties asserted by `selftest.py`, not files in the corpus. Error categories are
the eight declared in the contract README: `INVALID_INPUT`, `UNSUPPORTED_VERSION`,
`UNKNOWN_FIELD`, `DUPLICATE_ID`, `MISSING_REFERENCE`, `SIZE_LIMIT`, `PATH_UNSAFE`,
`COUNTS_MISMATCH`.

| Case id | Expected result | Error category | G-S duty covered |
| --- | --- | --- | --- |
| `PW-P01` | valid (positive fixture: mixed problems, two `sourcePlatform`s, note, two reviews, one session, one `extra` extension, one `unassigned` record) | none (accept) | golden fixture 正例; exercises every collection and enum where feasible; 同包重复导入幂等 baseline |
| `PW-N01` | reject: unknown entity field not under `extra` | `UNKNOWN_FIELD` | 未知字段; 未知关键语义拒绝 (契约/schema 检查) |
| `PW-N02` | reject: `formatVersion` = 2 | `UNSUPPORTED_VERSION` | 版本 (未知字段/版本); 契约/schema 检查 |
| `PW-N03` | reject: malformed JSON | `INVALID_INPUT` | golden fixture 反例; 契约/schema 检查 |
| `PW-N04` | reject: unknown manifest key | `UNKNOWN_FIELD` | 未知字段; 契约/schema 检查 |
| `PW-N05` | reject: attempt references absent `problemId` | `MISSING_REFERENCE` | 缺失引用; 契约/schema 检查 |
| `PW-N06` | reject: duplicate problem `stableId` | `DUPLICATE_ID` | 重复 ID; 同 ID 异内容必须显式处理 |
| `PW-N09` | reject: record with a 16 KiB string over limit | `SIZE_LIMIT` | 大小限制; 契约/schema 检查 |
| `PW-N10` | reject: path-shaped string in a free-text field | `PATH_UNSAFE` | 路径穿越; 契约/schema 检查 |
| `PW-N11` | reject: unknown `verdict` value | `UNKNOWN_FIELD` (enum violation) | 未知字段; 未知关键语义拒绝; no silent normalization |
| `PW-N13` | reject: credential-shaped key `token` anywhere | `UNKNOWN_FIELD` | 未知字段; 排除凭据; 契约/schema 检查 |
| `PW-N15` | reject: truncated bytes | `INVALID_INPUT` | golden fixture 反例; 契约/schema 检查 |
| `missing-formatVersion` | reject: manifest missing `formatVersion` | `INVALID_INPUT` | 版本; 契约/schema 检查 |
| `counts-mismatch` | reject: `counts` declares 3, actual 2 | `COUNTS_MISMATCH` | golden fixture 反例; 契约/schema 检查 |
| `duplicate-JSON-keys` | reject: duplicate JSON object keys | `INVALID_INPUT` | golden fixture 反例; 契约/schema 检查 |

The document-phase component of the G-S duty ("文档期检查编号/依赖/链接/范围") is covered by the
numbered inputs/links above and by the whole-branch independent review checklist below. The
consumer-regression component ("所有受影响消费者回归") is not claimed in Phase 131: the plan fixes
132-135 as the phases that bind platform consumers, so no affected consumer exists yet. This record
does not assert consumer regression.

## Schema/checker agreement review

Controller's review checklist, from the plan's Review focus (lines 66-72). Each item was
checked by the independent whole-branch review
([phase-131-schema-checker-review.md](phase-131-schema-checker-review.md), verdict
PASS-WITH-OBSERVATIONS) and re-verified by the controller gate on the merged branch. The review's
two minor findings (F1/F3 size-overflow category labels, F6 pattern anchoring) were fixed in
PR #38 with non-vacuous regressions; see the disposition section appended to the review record.
Every row below is an observed outcome.

| Review item | Focus | Result |
| --- | --- | --- |
| Schema and checker agree on every rejection reason | No rule in one but not the other | PASS (Q1; 23 patterns, all supported keywords, no unenforced keyword) |
| Unknown-key rejection names the offending key | Never a bare category | PASS (Q3; `PW-N01`/`PW-N04` name the unknown key) |
| `PW-P01` exercises every collection and every enum value | At least once, where feasible | PASS (where feasible) — 5 collections; all 3 judges, all 8 verdicts, both origins; `extra` + one `unassigned`; other enum values (`lastResult`/session `type`/`state`) are schema-validated, `PW-N11` rejects an unknown verdict |
| No silent normalization | Absent `difficulty` stays absent; unknown `verdict` is a rejection | PASS (Q4; `PW-N11` rejects `BANANA`, absent fields stay absent) |
| Deterministic checker decisions | Sorted-key canonical form; no dict-order drift | PASS (`--identity` stable across runs; `selftest.py` PW-N08; digest `81bb13c1…d44`) |

## Verification gates

Commands run from `contracts/portable/v1/` for the Python gates (Python 3.14.3 locally; CI uses
`python3` from `actions/setup-python@v5` on ubuntu-latest). The controller ran every gate on the
merged controller branch `codex/phase-131-portable-contract` at `4173c0d` on 2026-11-05. Each
Result is an observed outcome; the same commands are wired into `.github/workflows/contracts.yml`
and were confirmed green in CI run `42030737524`.

| # | Command | Expected outcome | Result |
| --- | --- | --- | --- |
| 1 | `python3 check.py --cases golden/cases.json golden/` | `15 cases, 15 passed, 0 failed`; exit 0 | PASS — `15 cases, 15 passed, 0 failed`, exit 0 |
| 2 | `python3 check.py --identity golden/pw-p01.json` (run twice) | identical SHA-256 on both runs | PASS — both `81bb13c1746381f2d7f95ae69e421503dcc898aae823222e8a3caf40d55b8d44` |
| 3 | `python3 selftest.py` | PASS (PW-N08 identity stability, PW-N12 unassigned provenance, corpus gate, F6/F1-F3 regressions) | PASS — `7 checks, 7 passed, 0 failed`, exit 0 |
| 4 | `python3 -m py_compile check.py selftest.py` | clean | PASS — exit 0, no `SyntaxWarning` |
| 5 | `.github/workflows/contracts.yml` | CI green on `contracts/**` changes | PASS — run `42030737524` on `4173c0d`, all 7 steps success |
| 6 | Android `./gradlew test` + `assembleDebug` | unaffected (no `app/` changes); PR CI runs them anyway | PASS (unaffected) — recent Android CI runs on `main` are all success |

Measured controller gate results:

<!-- CONTROLLER-GATE-RESULTS: measured values recorded by the controller on 2026-11-05 -->
Golden corpus result (gate 1): `15 cases, 15 passed, 0 failed`, exit 0.
Canonical identity SHA-256 of `golden/pw-p01.json` (gate 2): `81bb13c1746381f2d7f95ae69e421503dcc898aae823222e8a3caf40d55b8d44` (identical on two runs).
selftest.py / py_compile / contracts.yml results (gates 3-5): `7 checks, 7 passed, 0 failed` exit 0; `py_compile` exit 0; CI run `42030737524` all 7 steps success.
<!-- END CONTROLLER-GATE-RESULTS -->

## Claims vs non-claims

Claims (delivered in Phase 131): the versioned contract and JSON schema, the stdlib-only reference
checker, the 15-case golden corpus, the CI workflow, the developer guide and handoff, and this
verification record.

Non-claims: no platform import/export (132-135 bind later), no encrypted or compressed container,
no credentials portability, no v2 upgrade path, and no Android `app/` change. The checker is a
reference implementation, not platform proof.

## Controller sign-off

<!-- CONTROLLER-SIGN-OFF: measured gate results recorded by the controller on 2026-11-05 -->
VERIFIED. Controller gate on merged `codex/phase-131-portable-contract` at `4173c0d`:
golden corpus `15 cases, 15 passed, 0 failed` (exit 0); canonical identity
`81bb13c1746381f2d7f95ae69e421503dcc898aae823222e8a3caf40d55b8d44` identical across two runs;
`selftest.py` `7 checks, 7 passed, 0 failed` (exit 0); `py_compile` clean; `contracts.yml`
CI run `42030737524` all 7 steps success. Independent review PASS-WITH-OBSERVATIONS with its
two minor findings fixed in PR #38 (non-vacuous regressions). Android gate unaffected.
Definition of Done met: contract complete, no stubs, checker + corpus + self-test green,
CI green, docs verified, no platform consumer shipped in 131.
<!-- END CONTROLLER-SIGN-OFF -->
