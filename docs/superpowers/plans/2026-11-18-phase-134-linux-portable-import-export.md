# Phase 134 — Linux Portable Workspace Import/Export — Implementation Plan

Design: [../specs/2026-11-18-phase-134-linux-portable-import-export-design.md](../specs/2026-11-18-phase-134-linux-portable-import-export-design.md).
Integration branch: `codex/phase-134-linux-portable-import-export`. One PR per task
into the integration branch; one delivery PR integration → main. Agent briefs are
code-first (commit a compiling skeleton within the first few tool calls) with hard
deadlines — the controller takes over any task stalled at zero output.

| # | Task | Branch | Owns (exclusive) | PR |
| --- | --- | --- | --- | --- |
| 1 | Strict portable codec: `PortableModels`/`PortableJson`/`PortableCodec` (C++20), golden corpus + identity tests | `codex/phase-134-task-1-codec` | `linux/src/core/portable/` (new: models/json/codec .h+.cpp), `linux/tests/core/Portable{Json,Codec}Test.cpp` (new), `linux/src/core/CMakeLists.txt` + `linux/tests/CMakeLists.txt` (additions only), golden `file(COPY)` rule | #T1 |
| 2 | Schema v2→v3 migration + `PortableArchiveStore` (transactional replace-all) | `codex/phase-134-task-2-store` | `linux/src/core/storage_schema.cpp`, `linux/src/core/store.{h,cpp}`, `linux/src/core/storage/portable_archive_store.{h,cpp}` (new), `linux/tests/store/PortableArchiveStoreTest.cpp` (new) | #T2 |
| 3 | `PortableRepository` (preview/apply/export/cancel) + golden round-trip + duplicate-id/rule tests | `codex/phase-134-task-3-repo` | `linux/src/core/portable/portable_repository.{h,cpp}` (new), `linux/tests/core/PortableRepositoryTest.cpp` (new), tests CMake registration for it | #T3 |
| 4 | CLI `portable inspect/import/export` + exit mapping + `smoke-portable.sh` (+ one-line `linux.yml` addition) | `codex/phase-134-task-4-cli` | `linux/src/cli/**` (`command_parser.{h,cpp}`, `runner.{h,cpp}`), `linux/tests/cli/**`, `linux/scripts/smoke-portable.sh` (new), `.github/workflows/linux.yml` (additive line only) | #T4 |
| 5 | QML PORTABLE page + `MainViewModel` portable state + `FileDialog` seam + VM/QML tests | `codex/phase-134-task-5-gui` | `linux/src/gui/**` (`viewmodel.{h,cpp}`, `qml/Main.qml`, `main.cpp` if context props change), `linux/tests/gui/**`, `linux/tests/qml/tst_main.qml` | #T5 |
| 6 | Independent review of the final diff + findings disposition; G-L gate; verification record + handoff + ledger; roadmap tick; delivery PR | controller + `codex/phase-134-task-6-review` | docs only | #T6+ |

Waves (concurrency ≤ 3 at all times): A = 1 ∥ 2; B = 3; C = 4 ∥ 5; D = 6.
Merge order enforces dependencies: 3 after 1+2; 4 and 5 after 3.

## Per-task gates

- Every task PR carries a CI (`linux.yml`) green run on its head — that IS the
  build/test gate for this phase (no local Linux toolchain exists on the host).
- T1 additionally runs the contract corpus locally where Python exists:
  `python3 contracts/portable/v1/check.py --cases contracts/portable/v1/golden/cases.json contracts/portable/v1/golden/` must stay green (contract unchanged).
- T2 gate: ctest store tests incl. induced-failure rollback; schema validator
  accepts v1/v2/v3 and rejects unknown.
- T3 gate: ctest repository tests incl. mixed-golden import→apply→export identity
  (pinned `81bb13c1…d44`), duplicate-import idempotence, cancel-no-write.
- T4 gate: ctest cli tests incl. exit-code contract slot extended for portable
  mapping; `--yes` confirm gate proves zero writes on reject.
- T5 gate: ctest gui VM tests (stubbed pickers, real store) + QML smoke.
- T6 (delivery): full CI green on the delivery PR head; verification record with
  measured evidence; handoff; plan ledger; roadmap line 271 tick.

## Agent brief contract (learned from phase 133)

- Code-first: commit a compiling skeleton early; iterate.
- File ownership from the table above is exclusive; cross-file coordination goes
  through the controller.
- Hard deadline per task; controller takes over stalled tasks directly (the
  agents' verified research is still harvested).
- Never edit workflows to make checks pass; the only workflow change this phase is
  T4's additive smoke line.
