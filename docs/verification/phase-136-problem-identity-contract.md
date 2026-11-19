# Phase 136 — Problem Identity and Retrieval Semantics

Status: **VERIFIED**. Every gate below is measured on the recorded commit or
CI run; the phase is merged to `main`. Authoritative inputs:
[Design](../superpowers/specs/2026-11-19-phase-136-problem-identity-contract-design.md),
[Plan](../superpowers/plans/2026-11-19-phase-136-problem-identity-contract.md),
[Plan ledger](phase-136-plan-ledger.md), independent review
[phase-136-task-review.md](phase-136-task-review.md).

Baseline main: `395bd00` (phases 131-134 delivered). Integration branch:
`codex/phase-136-problem-identity-contract` (merged via PR #79, `ee5d853`);
review fixes + closeout land via the closeout PR on
`codex/phase-136-closeout`.

## Scope and inputs

Freezes `contracts/problems/v1/` as the executable cross-platform contract
for problem identity and retrieval: `(judge, externalId)` exact-byte identity
over the frozen judge set, derived status, ASCII-trim + simple-lowercase
search, AND-combined filters with inclusive difficulty bounds that exclude
unknown difficulty, four sorts each ending in a code-point stable key
tie-break. Vectors are self-consistent (checker recomputes every `expect`)
and every `expect` is hand-derived, not computed from the implementation.

Out of scope: local-UUID storage format, attempt/notes records, sync
transport, UI surfaces (phases 137-140), any runtime code change (none of
`app/`, `windows/`, `linux/`, `apple/` was touched).

## Gate history

| # | Gate | Requirement | Result |
|---|------|-------------|--------|
| 1 | Contract vector gate | all vectors self-consistent | PASS — local `python check.py --cases golden/cases.json golden/` → 25/25 pre-review, **28/28 post-review**; CI "Problem contract gate" SUCCESS on the PR #79 head (runs `44555517238`/`44555527095`) |
| 2 | Checker self-test | trap coverage proven | PASS — 15/15 pre-review, **23/23 post-review** (8 new checks incl. real-gate-path checks 06/14/20/22/23) |
| 3 | Compile hygiene | `py_compile` clean | PASS — locally on every head and in CI |
| 4 | Independent review | findings fixed or dispositioned | PASS — [phase-136-task-review.md](phase-136-task-review.md): PASS-WITH-OBSERVATIONS; F1/F2 (P2) + F3-F7 (P3) all FIXED in the closeout PR with measured evidence; no finding dropped or regraded |
| 5 | No runtime code changed | `app/`, `windows/`, `linux/`, `apple/` untouched | PASS — PR #79 diff contains `contracts/problems/**` + `contracts.yml` + docs only |

## Review-driven contract hardening (no semantic change)

The independent review found two P2s — an undefined search-trim character set
with a real Python-vs-Kotlin divergence (NBSP), and two selftest checks that
mirrored the gate comparison without executing it — plus five P3s. All seven
are fixed in the closeout PR: the trim set is frozen to ASCII whitespace
(vectors 026/027 pin it), the tie-break key's own code-point order is pinned
(vector 028), the null-difficulty group tie is pinned (vector 003 grew a
second null record), optional `difficultySource` is covered, the checker now
rejects unknown fields, unlisted golden files, name/filename mismatches,
wrong cases versions and lone surrogates, and the gate-level selftest checks
drive the real `check._run_gate` path.

## Honest history

- The gate itself caught one hand-derivation typo (vector 010's expect
  included a non-matching record) before first merge — the vectors are
  checked against the implementation, not the reverse.
- The first CI watcher for the release PRs produced a false "all green"
  (output-parse fragility); PR states were verified directly before any
  merge. No merge relied on the watcher's verdict.

## Non-claims

No platform consumes the vectors yet — Android (137), Windows (138), Linux
(139) and Apple (140) adoption is their own acceptance. The contract freezes
semantics only; it does not claim any runtime behavior change on any platform,
nor any UI. Phase 135's Apple file-dialog work is separate and unstarted.

<!-- CONTROLLER-SIGN-OFF: recorded by the controller on 2026-11-19 -->

VERIFIED. Phase 136 delivered to `main` via PR #79 (`ee5d853`) plus the
closeout PR (review fixes F1-F7, verification record, ledger, roadmap tick).
Gates: vector gate 28/28 and selftest 23/23 measured locally on the closeout
head; contract CI gates green on every merged head; independent review
PASS-WITH-OBSERVATIONS with all findings fixed and dispositioned.
