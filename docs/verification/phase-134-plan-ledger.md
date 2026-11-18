# Phase 134 — Plan Ledger (Linux Portable Workspace Import/Export)

Every row records the branch, the PR and the merge commit as observed on the
integration branch `codex/phase-134-linux-portable-import-export`. Rows marked
PENDING are not yet measured; they flip with measured values only after the
corresponding merge or gate.

Baseline: main `0b17d7c` (phase 133 fully delivered; PRs #62/#66).

| # | Task | Branch | PR | Merge commit | Status |
| --- | --- | --- | --- | --- | --- |
| 1 | SDD: design + implementation plan (recon-synthesized) | `codex/phase-134-linux-portable-import-export` | #67 | `18ed9ab` | MERGED |
| 2 | Task 1: strict C++ portable codec + golden corpus/identity tests | `codex/phase-134-task-1-codec` | — | — | PENDING |
| 3 | Task 2: schema v2→v3 portable archive tables + transactional store | `codex/phase-134-task-2-store` | #68 | `783fb82` | MERGED |
| 4 | Task 3: `PortableRepository` orchestration + round trip | `codex/phase-134-task-3-repo` | — | — | PENDING |
| 5 | Task 4: CLI `portable` verbs + exit mapping + smoke script | `codex/phase-134-task-4-cli` | — | — | PENDING |
| 6 | Task 5: QML PORTABLE page + VM + FileDialog seam + tests | `codex/phase-134-task-5-gui` | — | — | PENDING |
| 7 | Task 6: independent review of the final diff; disposition PR if needed | `codex/phase-134-task-6-review` | — | — | PENDING |
| 8 | Task 6: G-L gate (CI `linux.yml` green on delivery head + contract corpus + smoke) ; record/handoff flipped with measured results | integration branch | — | — | PENDING |
| 9 | Task 6: roadmap line 271 ticked; final delivery PR integration→main | integration branch | — | — | PENDING |

Notes:

- Waves: A = rows 2+3 in parallel (codec ∥ store, disjoint files); B = row 4;
  C = rows 5+6 in parallel (CLI ∥ QML); D = row 7 (review, concurrency 1).
- The build/test gate for this phase is CI `linux.yml` (ubuntu-24.04, offscreen
  Qt) — no local Linux toolchain exists on the host (recon-verified).
- The mixed golden identity
  `81bb13c1746381f2d7f95ae69e421503dcc898aae823222e8a3caf40d55b8d44`
  is the binding acceptance target for the canonical writer (same as phases
  132/133).
- Exit-code table 0–7 is shared and immutable; portable verbs reuse it without
  renumbering.
