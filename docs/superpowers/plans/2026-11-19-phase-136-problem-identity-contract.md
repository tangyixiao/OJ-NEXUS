# Phase 136 — Problem Identity and Retrieval Semantics — Implementation Plan

Status: ACTIVE. Binds the design
(`specs/2026-11-19-phase-136-problem-identity-contract-design.md`) to an
ordered, verifiable task list. Single integration branch
`codex/phase-136-problem-identity-contract`; every task ends in a PR to it;
the arc ends with a delivery PR to `main`.

Baseline main: `395bd00`.

## Task waves

### Wave 1 (controller-authored, Python runs natively on this host)

- **T1 — contract core** (`contracts/problems/v1/`):
  - `README.md`: frozen semantics per design decisions 2-8, with the
    rationale and the Android file:line anchors.
  - `check.py`: stdlib-only reference implementation
    (`filter_and_sort(dataset, query) -> [key strings]`) plus the vector gate
    (`--cases golden/cases.json golden/`) that recomputes every `expect`.
  - `selftest.py`: asserts the checker rejects mutated vectors (wrong order,
    wrong filter membership, 0-coerced difficulty, case-folded identity, UTF-16
    tie-break order) — trap coverage proof.
  - Acceptance: `python3 check.py --cases golden/cases.json golden/` and
    `python3 selftest.py` green locally; `python3 -m py_compile` clean.
- **T2 — golden vectors** (`contracts/problems/v1/golden/`): `cases.json` +
  dataset/vector files covering, each as at least one dedicated vector:
  1. same title, different OJ — both survive, never merged;
  2. same number, different OJ — both survive, never merged;
  3. missing difficulty never sorts as 0 (a null-difficulty problem must sort
     after every known difficulty, and above the tie-break order);
  4. bounded range filter excludes unknown difficulty;
  5. DIFFICULTY tie at equal difficulty resolves by key code-point order;
  6. UPDATED tie at equal `updatedAt` resolves by key code-point order;
  7. TITLE tie (same lowered title) and the astral-plane code-point pin
     (UTF-16-naive order fails this vector);
  8. ATTEMPTS tie;
  9. search: trim, simple-lowercase substring on title and externalId, `ß`
     boundary (query `ss` must NOT match `ß`);
  10. tag filter exact case-sensitive membership; favoriteOnly; judge filter;
  11. status filter on the derived value: REVIEW-overrides-solved vector;
  12. AND-combination and empty-query pass-through.
- **T3 — CI gate**: extend `.github/workflows/contracts.yml` with the
  problem-contract job (py_compile + vector run + self-test), keeping the
  existing portable job untouched. Verified by CI on the PR.

### Wave 2

- **T4 — independent review** (fresh-context reviewer, read-only): audit
  checker semantics against the design and the Android reference; findings
  fixed or dispositioned; review record
  `docs/verification/phase-136-task-review.md`.
- **T5 — verification record + closeout**:
  `docs/verification/phase-136-problem-identity-contract.md` (measured gates,
  honest history), plan ledger
  `docs/verification/phase-136-plan-ledger.md`, roadmap tick, delivery PR to
  `main`.

## File ownership (exclusive)

- T1/T2/T3: `contracts/problems/v1/**`, `.github/workflows/contracts.yml`.
- T4/T5: `docs/verification/phase-136-*`, `docs/LONG_RANGE_PLAN_121_220.md`.

## Acceptance (G-S, measured)

- Vector gate + self-test green locally (Python 3.x) and in CI contracts job.
- Every acceptance clause of the roadmap entry has at least one vector that
  fails if the clause is violated (mapped in the verification record).
- No runtime code changed: `app/`, `windows/`, `linux/`, `apple/` untouched.
