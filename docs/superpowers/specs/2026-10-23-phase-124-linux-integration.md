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

## Review ruling: cache identity

Cache ownership is upgraded to `(judge, normalized public handle)` in SQLite schema v2. A one-shot `sync --handle B` remains supported and writes B-owned rows while configured account A remains unchanged. Queries and GUI projections filter by the requested account handle; there is no multi-identity UI. `auth remove A` removes only A's account configuration: operation history and A-owned cache remain. Configuring B displays only B-owned cache, never A's.

Version-1 `profiles` rows carry their validated handle and retain that owner. Legacy rating, submission, problem and contest rows lack trustworthy identity, so migration preserves them with an empty/unassigned owner; handle-filtered account views never expose them. No old cache row or operation history is discarded. A later sync rebuilds data for the requested handle.

## Review ruling: running operation ownership

Schema v2 records an operation owner as `(pid, boot id, /proc start ticks)`. `initialize()` cancels a RUNNING operation only when that process identity is explicitly absent: the boot id differs, or the PID is absent (`ESRCH` / no `/proc` entry) or exists with a different start tick. Permission errors, malformed `/proc` data, and legacy rows with no owner are indeterminate and remain RUNNING. A reused PID cannot cause an active operation to be cancelled. Legacy ownerless operations require explicit future recovery tooling; automatic startup recovery will not guess.

## Review ruling: cache scope and paths

SQLite schema v2 scopes personal profiles, ratings and submissions by `(ACCOUNT, judge, normalized public handle)`. CF `contest.list` and `problemset.problems`, AtCoder contest/problem catalogs, and Luogu `contest/list` and `problem/list` are shared public datasets by judge (`GLOBAL`) and are not duplicated by handle. A one-shot sync for handle B remains supported while configured account A stays unchanged. Personal queries filter by requested handle; there is no multi-identity UI. Removing A removes only account configuration; A's owned cache and sync history remain. B cannot read A's personal rows, while both handles use the same public catalogs.

Version-1 profile rows carry a validated handle and retain that owner. Legacy rating and submission rows have no trustworthy owner and migrate intact as `UNASSIGNED`; account views do not attribute them. Legacy problem and contest rows migrate intact as `GLOBAL`. No cache or operation-history row is discarded. Default app data/config use stable `ojnexus` XDG identity irrespective of executable application name. `OJ_NEXUS_DATA_DIRECTORY` remains authoritative and XDG overrides remain honored.

## Review ruling: completion status and QML runtime

Async sync completion refreshes projections before setting the final result line, preserving SUCCESS/OFFLINE/CANCELLED/error feedback. The GUI test loads the actual `qrc:/ojnexus/Main.qml` through `QQmlApplicationEngine` on Qt's offscreen platform; this proves QML construction only and is not interactive desktop acceptance. CI and DEB metadata include the QtQuick Controls, Layouts and Window QML runtime modules.
