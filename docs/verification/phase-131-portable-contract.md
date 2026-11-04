# Phase 131 — Portable Workspace v1 Contract

Status: IN REVIEW (pending controller gate). The controller has not yet run the final gate on the
merged artifacts, so this record asserts no measured result. No green gate result, no SHA-256
digest, no observed case count, and no PR number is claimed here; every measured value is a marked
placeholder that the controller substitutes after merging. Source base: `acf4f8c` on
`codex/phase-131-verification`. This branch is unmerged; the controller merges and pushes.

Pending measurements: golden corpus result, canonical identity SHA-256 of `pw-p01.json`,
`selftest.py` result, `py_compile` result, and CI workflow status. The Android `./gradlew test` +
`assembleDebug` gate is unaffected by this phase (no `app/` changes) but PR CI runs it anyway.
No debug APK SHA256 is recorded for this phase: Phase 131 builds no application.

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

Controller's review checklist, from the plan's Review focus (lines 66-72). The items below are
the properties the independent whole-branch review checks; each is traceable to the spec,
`schema.json`, and `check.py` (enum violations map to `UNKNOWN_FIELD`, unknown-key errors name
the offending key, `canonical_bytes` uses `sort_keys=True`, and an absent `difficulty` stays
absent). This record marks the checklist as the controller's, and each row stays PENDING until the
controller records its outcome. Fixes go through the original implementer.

| Review item | Focus | Result |
| --- | --- | --- |
| Schema and checker agree on every rejection reason | No rule in one but not the other | PENDING |
| Unknown-key rejection names the offending key | Never a bare category | PENDING |
| `PW-P01` exercises every collection and every enum value | At least once, where feasible | PENDING |
| No silent normalization | Absent `difficulty` stays absent; unknown `verdict` is a rejection | PENDING |
| Deterministic checker decisions | Sorted-key canonical form; no dict-order drift | PENDING |

## Verification gates

Commands run from the repository root. Expected outcomes come from the spec and plan; the Result
column is PENDING for every measured gate because the controller has not yet run the final gate on
the merged artifacts.

| # | Command | Expected outcome | Result |
| --- | --- | --- | --- |
| 1 | `python3 contracts/portable/v1/check.py --cases contracts/portable/v1/golden/cases.json contracts/portable/v1/golden/` | `15 cases, 15 passed, 0 failed`; exit 0 | PENDING |
| 2 | `python3 contracts/portable/v1/check.py --identity contracts/portable/v1/golden/pw-p01.json` (run twice) | identical SHA-256 on both runs | PENDING |
| 3 | `python3 contracts/portable/v1/selftest.py` | PASS (PW-N08 identity stability, PW-N12 unassigned provenance, corpus gate) | PENDING |
| 4 | `python3 -m py_compile contracts/portable/v1/check.py` | clean | PENDING |
| 5 | `.github/workflows/contracts.yml` | CI green on `contracts/**` changes | PENDING |
| 6 | Android `./gradlew test` + `assembleDebug` | unaffected (no `app/` changes); PR CI runs them anyway | PENDING |

The three rows below are the measured values the controller substitutes before marking VERIFIED.
No green gate result, SHA-256 digest, observed case count, or PR number is asserted here.

<!-- CONTROLLER-GATE-RESULTS: replace the three rows below with measured values before marking VERIFIED -->
Golden corpus result (gate 1): <PENDING: paste "N cases, N passed, N failed" and exit code>
Canonical identity SHA-256 of golden/pw-p01.json (gate 2): <PENDING: paste the measured digest>
selftest.py / py_compile / contracts.yml results (gates 3-5): <PENDING: paste the measured outcomes>
<!-- END CONTROLLER-GATE-RESULTS -->

## Claims vs non-claims

Claims (delivered in Phase 131): the versioned contract and JSON schema, the stdlib-only reference
checker, the 15-case golden corpus, the CI workflow, the developer guide and handoff, and this
verification record.

Non-claims: no platform import/export (132-135 bind later), no encrypted or compressed container,
no credentials portability, no v2 upgrade path, and no Android `app/` change. The checker is a
reference implementation, not platform proof.

## Controller sign-off

<!-- CONTROLLER-SIGN-OFF: replace the single line below with measured gate results and the final VERIFIED status -->
<PENDING: controller fills the measured gate results here and sets the final status VERIFIED>
<!-- END CONTROLLER-SIGN-OFF -->
