# Phase 136 — Plan Ledger

Working record of the phase-136 arc (problem identity and retrieval
semantics contract). Rows flip only on observed, verifiable events; PENDING
rows name the event that will flip them.

Branch: `codex/phase-136-problem-identity-contract` (merged to main via
PR #79, merge commit `ee5d853`); closeout on
`codex/phase-136-closeout` (review fixes + docs). Baseline main: `395bd00`.

| # | Task | Evidence | Status |
|---|------|----------|--------|
| 1 | SDD + implementation plan merged | commits `9272e58` (design + plan) on the integration branch; PR #79 CI all checks success; merged as `ee5d853` | merged (PR #79) |
| 2 | T1 contract core (`check.py` reference implementation + `selftest.py` + `README.md`) | commit `f922e7a`; local gate PASS (25/25 vectors pre-review); selftest 15/15 pre-review; CI "Problem contract gate" SUCCESS (runs `44555517238`/`44555527095`) | merged (PR #79) |
| 3 | T2 golden vectors (expect hand-derived, not computed from the reference implementation) | commit `f922e7a` with `golden/cases.json` + vector files; one hand-derivation typo (vector 010) caught by the gate itself before merge; corpus grew to 28 vectors in closeout (F1/F4 vectors + hardening) | merged (PR #79) |
| 4 | T3 CI gate extension (`.github/workflows/contracts.yml` problems job) | commit `bbab9e6`; CI "Problem contract gate" SUCCESS on the PR head; portable job untouched and still green | merged (PR #79) |
| 5 | T4 independent review (fresh-context reviewer; gate + selftest + reference mode run; 12+ vectors hand re-derived; verdict PASS-WITH-OBSERVATIONS) | review record `phase-136-task-review.md`; findings F1/F2 (P2) + F3-F7 (P3) all FIXED in the closeout PR: post-fix gate **28/28**, selftest **23/23**, `py_compile` clean | merged (closeout PR) |
| 6 | T5 closeout: verification record, this ledger, roadmap tick, delivery PR to `main` | verification record `phase-136-problem-identity-contract.md` (VERIFIED sign-off); roadmap PHASE 136 ticked; delivered via the closeout PR | merged (closeout PR) |

## Deviations

- The plan sequenced T4 review before closeout docs; the review agent
  completed after the contract implementation had already merged via PR #79
  (implementation and review were not gated on each other), so the review
  findings landed as a separate closeout PR rather than fixes to an unmerged
  branch. File ownership was respected throughout (`contracts/problems/v1/**`
  + `contracts.yml` + `docs/verification/phase-136-*` + roadmap only; no
  runtime code touched).
