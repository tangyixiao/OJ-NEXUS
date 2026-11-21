# Phase 138 — Plan Ledger

Tracking ledger for the phase-138 arc (Windows native problem search and
virtualization). Rows flip from PENDING to observed facts (branch, PR, merge
commit, measured gates) as each lands. Convention per phases 133-137.

| # | Item | Planned evidence | Status |
|---|---|---|---|
| 1 | SDD + plan merged to main | PR to main, design + plan files | PENDING |
| 2 | T1 Core: contract domain + schema v9 + query layer | commit `c0618d9` (+ `0e23d21` CLI call-site shape), build TWE-clean | DONE — c0618d9 |
| 3 | T2 Core.Tests: version sweep + 28-vector parity + store tests | commits `89dea15`; parity 28/28 measured locally; Core 307/307 | DONE — 89dea15 |
| 4 | T3 Cli: parser/renderer/program + tests | commit `0e23d21` (subagent T3), Cli 99/99 measured locally | DONE — 0e23d21 |
| 5 | T4 Desktop: VM/XAML/virtualization/smoke + tests | commit `e2f5c33`; Desktop 49/49 + ui-smoke PASS (FILTERS=3, CLEAR-FILTERS=PASS) measured locally | DONE — e2f5c33 |
| 6 | T5 local G-W gate | full sln `dotnet test` Release + build + package + installer + verify, measured | PENDING |
| 7 | Independent review of the final diff | review record + disposition PR | PENDING |
| 8 | Delivery PR merged to main | PR number + merge commit + CI run ids | PENDING |
