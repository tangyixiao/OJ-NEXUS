# Phase 131 Portable Workspace v1 — Implementation Plan

> Executed with parallel sub-agents under controller coordination (user-directed). Each task
> owns a disjoint file set in its own worktree/branch; the controller merges, reviews, gates
> and publishes.

**Goal:** deliver `contracts/portable/v1` — package contract (README), machine-checkable
schema, stdlib-only reference checker, and golden packages (positive + negative) with CI
wiring — so phases 132–135 can bind import/export to one honest contract.

**Spec:** `docs/superpowers/specs/2026-11-01-phase-131-portable-workspace-design.md`
(the binding source for every field/rule named below).

## Global constraints

- File ownership is exclusive per task; no task touches another's files.
- `check.py` must be Python 3 stdlib only (no pip installs, no network).
- All golden fixtures are synthetic and marked as such where the schema expects provenance.
- The checker must accept PW-P01 and reject every negative case with the exact error category
  recorded in `golden/cases.json`; any disagreement is a defect in schema/checker/fixture —
  fixed through the controller, never by loosening the check silently.
- Implementers do not push; the controller merges and pushes.

## Task 1 — contract text and schema (files: `contracts/portable/v1/README.md`, `contracts/portable/v1/schema.json`)

Author the human contract and the machine schema exactly as specified in the design doc:
manifest fields and rules, five collections with minimum fields, enums, `extra` extension
rule, unknown-key rejection, day-key/timestamp semantics, capability tiers vocabulary and
counts duty, versioning policy (exact major match; only schema-declared extensions preserved),
duplicate-JSON-key rejection, size limits. `schema.json` is a draft-07 subset document that
the checker implements — keep the subset list in the README (type, required, properties,
additionalProperties: false, enum, pattern, min/max length, items, minItems, maxItems,
integer bounds). README documents the tier vocabulary and the honest-counts duty for 132–135.

## Task 2 — reference checker (files: `contracts/portable/v1/check.py`)

Python 3 stdlib only. Strict JSON decode with duplicate-key rejection; draft-07 subset
validation driven by `schema.json`; contract rules beyond the subset: size limits (package
64 MiB, arrays ≤ 50 000, strings ≤ 16 KiB), reference integrity, per-collection duplicate
detection, counts reconciliation, path-shape rejection on strings, enum admission,
`extra` depth limit, canonical re-encode (sorted keys, fixed separators) with `--identity`
SHA-256 output. CLI: `check.py <file>` (exit 0 valid / 1 invalid with category),
`check.py --cases <cases.json> <golden-dir>` (run the whole corpus), `check.py --identity
<file>`. Deterministic; no locale/clock dependence.

## Task 3 — golden packages (files: `contracts/portable/v1/golden/**`)

`cases.json` plus one minimal package file per case, exactly as enumerated in the design doc:
PW-P01 mixed positive; negatives PW-N01, PW-N02, PW-N03 (malformed), PW-N04, PW-N05, PW-N06,
PW-N09, PW-N10, PW-N11, PW-N13, PW-N15 (truncated), missing-formatVersion, counts-mismatch,
duplicate-JSON-keys. Each case entry cites the G-S duty it covers. Fixtures synthetic.

## Task 4 — CI wiring (files: `.github/workflows/contracts.yml`)

Minimal ubuntu workflow: checkout, python3, run `check.py --cases golden/cases.json golden/`
under `contracts/portable/v1/`, fail on any deviation. Path triggers: `contracts/**` and the
workflow file itself.

## Task 5 — gate, review, delivery (controller)

Run the checker over the corpus locally; every case must match its expectation. Whole-branch
independent review (source + contract text), fixes through the original implementer,
verification record `docs/verification/phase-131-portable-contract.md`, plan ledger + handoff
section, scoped PR. 132–135 bind later; no consumer claims in this phase.

## Review focus

- Schema and checker must agree on every rejection reason (no rule in one but not the other).
- Unknown-key rejection must name the offending key in the error; never a bare category.
- PW-P01 must exercise every collection and every enum value at least once where feasible.
- No silent normalization: absent difficulty stays absent; unknown verdict is a rejection.
- Checker decisions are deterministic (sorted-key canonical form; no dict-order drift).
