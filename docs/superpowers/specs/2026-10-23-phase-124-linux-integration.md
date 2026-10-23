# Phase 124: Linux integration and baseline

Date: 2026-10-23
Status: ACTIVE
Base: `11555095e2a66c8dfc4dd9918118ed81d97720da`
Branch: `codex/phase-124-linux-integration`

## Purpose

Integrate the reviewed Linux Qt/C++ client into the main-based line through a bounded source selection, then establish reproducible Ubuntu 24.04 build, CTest, CLI/offline, Qt offscreen and package-content evidence. The independent source worktree is read-only for this task.

## Source boundary

Source checkout: `D:\AndroidAppCoding\.worktrees\linux-client`, HEAD `5c42103839dabc9390aced748083493ff2385450`. Its HEAD is an ancestor of main and has no Linux-only commits to cherry-pick. Linux source, packaging recipe and workflow exist as untracked files there; five tracked documentation/ignore edits are excluded. Preserve an explicit path/status/SHA-256 manifest before copying. Copy only text files listed in that manifest. Exclude build trees, CPack staging, generated binaries, caches and user configuration.

## Implementation

1. Add only selected `linux/` text files, an Ubuntu 24.04 workflow, and this phase's spec/plan/verification/provenance records.
2. CI uses fixed `ubuntu-24.04`, bounded job timeout, Qt 6 development/runtime modules, CMake/Ninja build, CTest with offscreen Qt, CLI/offline smoke using an isolated data directory, and suitable test reports. No floating Fedora/Arch matrix.
3. Keep public-handle-only behavior and existing CLI exit codes. Do not add credentials or alter architecture/dependencies/API.
4. Run all verification in the managed worktree or a dedicated `/tmp` build directory under the already-running Ubuntu-24.04 WSL distro. Do not alter distro lifecycle or real XDG/user data.
5. Package inspection is allowed; package installation to the host is not. A source packaging recipe may be retained without claiming that distro was tested.

## Completion gates

- Fresh source baseline and CTest counts recorded; fixes only for reproduced defects, with regression coverage.
- CMake configure/build and complete CTest pass; CLI status, isolated state, public-safe-handle sync (bounded network), typed offline failure and cache retention evidence recorded.
- Qt tests explicitly described as offscreen; interactive GUI is separately marked only if WSLg permits a real display session.
- DEB content and dependency metadata inspected if CPack succeeds; no host installation.
- Secret scan, diff check, scope review and scoped commit complete. No push, PR, release or merge in this assignment.
