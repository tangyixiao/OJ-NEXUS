# Phase 124 review fixes: verification

Date: 2026-10-23
Worktree: `C:\Users\tangy\.codex\worktrees\phase-124-linux-integration\AndroidAppCoding`
Branch: `codex/phase-124-linux-integration`

## Review regressions and results

- Cache ownership: the added one-shot regression first failed against the integrated v1 implementation: it read `account-b` where configured `account-a` was expected. The v2 tests now prove account A and one-shot B profiles/ratings/submissions remain separate, removing A retains its history and personal cache, configuring B does not expose A, and shared judge catalogs remain present. A v1 database fixture confirms migration preserves the profile payload and every legacy row; old profile rows keep their validated owner, old rating/submission rows remain `UNASSIGNED`, and public problem/contest rows migrate to `GLOBAL`.
- Operation recovery: same-boot tests now prove a live PID with a mismatched `/proc` start tick and a definitely absent PID are reclaimed as `CANCELLED`; repeated initialization while the current process owns a live operation leaves it `RUNNING`. A v1 `RUNNING` operation with no owner remains `RUNNING` after migration. Runtime ownership uses PID, boot UUID and `/proc` start ticks; uncertain or legacy ownership is not reclaimed.
- App paths: tests use a temporary HOME, compare `ojnexus` and `ojnexus-gui` default database/config/log paths, and verify XDG overrides. `OJ_NEXUS_DATA_DIRECTORY` retains precedence.
- QML/runtime dependencies: the GUI test loads the actual `qrc:/ojnexus/Main.qml` with `QQmlApplicationEngine` under Qt offscreen. CI and the DEB declare QtQml, WorkerScript, QtQuick, Controls, Layouts, Templates and Window QML modules; Ubuntu 24.04's Controls module dependencies do not supply all of these modules. The asynchronous offline sync test waits for worker completion and verifies the visible final line remains `OFFLINE / RETRY WHEN ONLINE` after refresh. This is a QML construction/offscreen check, not interactive UI acceptance.
- CMake/CTest schema expectations were updated to schema version 2.

## Final Linux verification

Environment: WSL distro `Ubuntu-24.04`; Qt 6.4.2, GCC 13.3, CMake 3.28.3, Ninja 1.11.1. Builds used dedicated `/tmp/ojnexus-phase124-*` directories, never the managed worktree. WSLg variables were present (`DISPLAY=:0`, `WAYLAND_DISPLAY=wayland-0`), but `xwininfo` reported an unusable `131072x1` root geometry. No GUI process/window startup evidence was obtained, so interactive desktop acceptance remains unverified; only Qt offscreen tests and actual QML-engine loading are claimed.

| Check | Result |
| --- | --- |
| CMake configure, GUI/tests on | exit 0 |
| CMake build | exit 0, 56/56 Ninja steps |
| CTest, Qt offscreen | exit 0, 8/8 test executables passed (store, adapter, path, CLI, GUI, QML and core/sync suites) |
| Isolated CLI status | exit 0; empty accounts, schema version 2, temporary database path |
| Isolated CLI history | exit 0; empty operation list |
| Isolated CLI config show | exit 0; reported adapters and temporary config/data/log paths |
| DEB creation | exit 0; `ojnexus-0.1.0-Linux.deb` |
| `dpkg-deb -I` / `-c` inspection | exit 0; confirmed QtQml, WorkerScript, QtQuick, Controls, Layouts, Templates and Window QML runtime dependencies and CLI/GUI/desktop/README payload |
| Host package installation | not run |

The isolated CLI environment was `OJ_NEXUS_DATA_DIRECTORY=/tmp/ojnexus-phase124-cli-state`; no real HOME/XDG data or credentials were used. The DEB was inspected only and was not installed.

## Source provenance

The original Linux source worktree at `D:\AndroidAppCoding\.worktrees\linux-client` remained read-only. Recomputed size and SHA-256 for all 41 entries in `phase-124-linux-provenance.md` matched the pre-copy manifest (41/41). The audit at `C:\Users\tangy\AppData\Local\Temp\ojnexus-phase121-platform-audit.md` was read; it was not under the repository's `TEMP` folder. The original manifest remains the integration baseline; these review fixes are a separate worktree diff.
