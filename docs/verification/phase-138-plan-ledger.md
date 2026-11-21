# Phase 138 — Plan Ledger

Tracking ledger for the phase-138 arc (Windows native problem search and
virtualization). Rows flip from PENDING to observed facts (branch, PR, merge
commit, measured gates) as each lands. Convention per phases 133-137.

| # | Item | Planned evidence | Status |
|---|---|---|---|
| 1 | SDD + plan merged to main | PR to main, design + plan files | PENDING |
| 2 | T1 Core: contract domain + schema v9 + query layer | commits on `codex/phase-138-windows-problem-search`, build green | PENDING |
| 3 | T2 Core.Tests: version sweep + 28-vector parity + store tests | parity 28/28 measured locally | PENDING |
| 4 | T3 Cli: parser/renderer/program + tests | Cli tests green locally | PENDING |
| 5 | T4 Desktop: VM/XAML/virtualization/smoke + tests | Desktop tests + ui-smoke green locally | PENDING |
| 6 | T5 local G-W gate | full sln `dotnet test` Release + build + package + installer + verify, measured | PENDING |
| 7 | Independent review of the final diff | review record + disposition PR | PENDING |
| 8 | Delivery PR merged to main | PR number + merge commit + CI run ids | PENDING |
