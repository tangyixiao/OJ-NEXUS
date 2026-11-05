# SDD ledger — plan: docs/superpowers/plans/2026-11-01-phase-131-portable-workspace.md

Spec: docs/superpowers/specs/2026-11-01-phase-131-portable-workspace-design.md
Branch: codex/phase-131-portable-contract; base 46b8ddc; design/plan 0aa419c (on the controller).
Authorization: user-directed continuation plus acceleration of the portable-workspace v1 contract — parallel subagents, many commits, many merged PRs under controller coordination; the controller merges, reviews, gates and publishes, implementers never push.
Ledger worktree: codex/phase-131-ledger (this file sits in a NEW sdd directory; force-added with git add -f because .superpowers/sdd/.gitignore ignores the whole tree).

## Pre-flight

| Tasks | Interface/file checked | Finding/ruling |
| --- | --- | --- |
| toolchain | `python3 --version` | Python 3.14.3 available. `check.py` is stdlib-only (no pip installs, no network). |
| scope | design doc § Explicit non-goals / § Scope | No platform import/export consumer in scope in 131; 132–135 bind to the contract later. Recorded as a non-claim, never a shipped consumer. |
| ancestry | `main` 6eddcf5 vs controller base 46b8ddc | main 6eddcf5 is 2 ahead of the controller base 46b8ddc. |
| remote | `git push` (SSH) / `gh` | SSH remote broken; HTTPS + `gh` works. Implementers do not push — the controller merges and pushes. |
| worktrees | `git worktree list` | Stale worktrees cleaned before fan-out; each task owns one disjoint worktree/branch. |
| PR base | child PR base refs | Child PRs base on the controller branch codex/phase-131-portable-contract, not main. |

## Tasks

| Task | State | Scope / deliverable | Branch / commits |
| --- | --- | --- | --- |
| T1 | complete | contract text + schema: `contracts/portable/v1/README.md`, `contracts/portable/v1/schema.json` | controller `codex/phase-131-portable-contract`, commit `79807b4` |
| T2 | complete | stdlib-only reference checker `contracts/portable/v1/check.py` | controller `codex/phase-131-portable-contract`, commit `acf4f8c` |
| T3 | in progress | golden corpus: `contracts/portable/v1/golden/**` (`cases.json` + positive/negative fixtures) | `codex/phase-131-golden` |
| T4 | in progress | CI wiring: `.github/workflows/contracts.yml` | `codex/phase-131-ci` |
| T5 | in progress | gate, whole-branch review, verification record, developer guide, handoff, this ledger | `codex/phase-131-{selftest,examples,verification,handoff,ledger}` |

T3/T4/T5 execute as parallel subagent PRs, each with a disjoint file scope and its own branch;
the controller merges them into the phase branch. Child PRs (state: in progress):

| Child branch | Disjoint file scope | Deliverable |
| --- | --- | --- |
| `codex/phase-131-golden` | `contracts/portable/v1/golden/**` | golden corpus — `cases.json` plus PW-P01 and the negative fixtures (T3) |
| `codex/phase-131-ci` | `.github/workflows/contracts.yml` | minimal ubuntu CI workflow (T4) |
| `codex/phase-131-selftest` | `contracts/portable/v1/selftest.py` | `check.py` self-tests for PW-N08 (deterministic identity) and PW-N12 (unassigned origin) |
| `codex/phase-131-examples` | developer guide / example package | developer guide for 132–135 import/export consumers |
| `codex/phase-131-verification` | `docs/verification/phase-131-portable-contract.md` | verification record |
| `codex/phase-131-handoff` | handoff section | 132–135 binding handoff |
| `codex/phase-131-ledger` | `.superpowers/sdd/2026-11-01-phase-131-portable-contract/progress.md` | this ledger; force-added (`git add -f`), commit only this file |

T1/T2 execution is recorded with the two known controller commits 79807b4 + acf4f8c. No PR
numbers and no measured gate results are asserted in this ledger worktree; the controller
records those after the gate. Do not force-add scratch files.

## Rulings

- Ruling: one package = one UTF-8 JSON document, single-file container (no ZIP, no archive in v1). This removes the archive-stowaway class; path-shaped strings and duplicate JSON keys are still rejected at the format level.
- Ruling: `check.py` implements a deliberate draft-07 subset driven by `schema.json` (type, required, properties, additionalProperties:false, enum, pattern, min/max length, items, minItems, maxItems, integer bounds); contract rules the subset cannot express live in the checker, never silently in a looser schema.
- Ruling: strict JSON decode with duplicate-key rejection via object_pairs_hook; last-key-wins is treated as an attack, not a default.
- Ruling: future major `formatVersion` → whole-package reject (UNSUPPORTED_VERSION), never a best-effort decode. Minor extensions: only schema-declared fields are preserved losslessly; everything else unknown is rejected with the offending key named.
- Ruling: `extra` (optional object, string keys → JSON scalars/arrays of scalars, depth ≤ 2) is the sole permitted extension point, stored-only semantics, preserved byte-exactly. Any other unknown key on an entity object → UNKNOWN_FIELD reject.
- Ruling: no silent normalization. Absent `difficulty` stays absent (never coerced to 0); an unknown `verdict` value is a rejection, not a coercion; `rawVerdict` is mapped to `OTHER` by the exporter on the way out, never by the importer in the package. Historical day keys are preserved verbatim, never recomputed.
- Ruling: capability tiers (`editable` / `storedOnly` / `rejected`), the three import counts, the never-downgrade-without-reason rule, and lossless import→export round-trip for accepted records are 131 vocabulary/counts duty only; the actual 132–135 binding and round-trip proof are deferred to those phases, not claimed here.
- Ruling: PW-N08 (positive-side re-check that PW-P01 reports the same identity hash) and PW-N12 (`origin: unassigned` accepted but asserted unattributable) are encoded as `check.py` / `selftest.py` self-tests and assertions, not as separate golden package files.

## Gate (objective; measured by the controller, not yet recorded here)

| Check | Required result |
| --- | --- |
| local checker corpus | `check.py --cases golden/cases.json golden/` prints `15 cases, 15 passed, 0 failed` |
| identity determinism | `check.py --identity <file>` yields the same SHA-256 across repeated runs (sorted-key canonical re-encode, no dict-order drift, no locale/clock dependence) |
| selftest.py | PASS (PW-N08 deterministic identity + PW-N12 unassigned-origin assertion) |
| py_compile | clean (`python3 -m py_compile check.py selftest.py`) |
| CI | green — `.github/workflows/contracts.yml` runs the corpus on `contracts/**` and the workflow file itself |
| Android gates | unaffected — no `app/` change in this phase; PR CI runs them anyway |

## Review (whole-branch, independent — source + contract text)

- Whole-branch independent review covers source and contract text; fixes route through the original implementer, never by loosening the checker silently.
- Schema and checker must agree on every rejection reason (no rule present in one but not the other). Every golden negative cites the exact error category in `cases.json`.
- Unknown-key rejection must name the offending key in the error, never a bare category (e.g. a `token` credential-shaped key anywhere → UNKNOWN_FIELD with the key named).
- PW-P01 must exercise every collection (`problems`, `attempts`, `notes`, `reviews`, `sessions`) and every enum value at least once where feasible.
- No silent normalization: absent difficulty stays absent, unknown verdict is a rejection, day keys preserved verbatim.
- Checker decisions are deterministic: sorted-key canonical form with fixed separators, no dict-order drift.

## Claims vs non-claims

Claims (delivered in 131): the package contract (`README.md`), machine-checkable `schema.json`, stdlib-only reference `check.py`, golden positive + negative corpus, CI workflow, developer guide, verification record, handoff and this ledger — one honest contract for 132–135 to bind import/export against.

Non-claims (explicitly out of 131): no platform import/export consumer (132–135 bind later); no encrypted or compressed container; no Android `app/` change; no v2 automatic upgrade path (v2 defines its own stance). The checker is a reference implementation, not a platform proof.

## Final

FINAL: PASS / VERIFIED. The controller ran the final gate on merged
`codex/phase-131-portable-contract` at `4173c0d` on 2026-11-05. Measured results:
golden corpus `15 cases, 15 passed, 0 failed` (exit 0); canonical identity of
`golden/pw-p01.json` = `81bb13c1746381f2d7f95ae69e421503dcc898aae823222e8a3caf40d55b8d44`
identical across two runs; `selftest.py` `7 checks, 7 passed, 0 failed` (exit 0);
`py_compile check.py selftest.py` clean; `.github/workflows/contracts.yml` CI run `42030737524`
all 7 steps success. Independent whole-branch review verdict PASS-WITH-OBSERVATIONS; its two
minor findings (F1/F3 size-overflow category labels, F6 pattern anchoring) were fixed in PR #38
with non-vacuous regressions gated by the self-test and CI. Android `app/` gate unaffected.
Definition of Done met: complete contract, no stubs, checker + corpus + self-test + CI green,
verified documentation, no platform consumer shipped in 131.

Delivery: PRs #31–#38 merged into the controller branch; final scoped PR merges the controller
into `main`. This ledger was force-added with `git add -f` (the `.superpowers/sdd/.gitignore`
ignores the whole tree).
