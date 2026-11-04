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
