# Phase 138 — Plan Ledger

Tracking ledger for the phase-138 arc (Windows native problem search and
virtualization). Rows flip from PENDING to observed facts (branch, PR, merge
commit, measured gates) as each lands. Convention per phases 133-137.

| # | Item | Planned evidence | Status |
|---|---|---|---|
| 1 | SDD + plan merged to main | PR #90 (evolved to carry the full implementation), design + plan files | DONE — PR #90 |
| 2 | T1 Core: contract domain + schema v9 + query layer | commit `c0618d9` (+ `0e23d21` CLI call-site shape), build TWE-clean | DONE — c0618d9 |
| 3 | T2 Core.Tests: version sweep + 28-vector parity + store tests | commits `89dea15`; parity 28/28 measured locally; Core 307/307 | DONE — 89dea15 |
| 4 | T3 Cli: parser/renderer/program + tests | commit `0e23d21` (subagent T3), Cli 99/99 measured locally | DONE — 0e23d21 |
| 5 | T4 Desktop: VM/XAML/virtualization/smoke + tests | commit `e2f5c33`; Desktop 49/49 + ui-smoke PASS (FILTERS=3, CLEAR-FILTERS=PASS) measured locally | DONE — e2f5c33 |
| 6 | T5 local G-W gate | full sln build 0 err/0 warn (TWE) + 455/455 tests (Core 307 incl. parity 28/28, Cli 99, Desktop 49) + ui-smoke PASS; measured post-disposition on `dfe7b7e` | DONE — measured |
| 7 | Independent review of the final diff | PASS-WITH-OBSERVATIONS, 7 P3 findings all fixed in `dfe7b7e`; [review record](phase-138-task6-review.md) | DONE — dfe7b7e |
| 8 | Delivery PR merged to main | PR #90, merge `8ea64d7`; CI on `dfe7b7e` all four checks success (check-runs 136899987890/136899987790/136899987620/136899987243) | DONE — 8ea64d7 |
