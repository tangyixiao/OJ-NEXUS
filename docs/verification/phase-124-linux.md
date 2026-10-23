# Phase 124 Linux integration verification

Date: 2026-10-23 (Asia/Shanghai)
Worktree: `C:\Users\tangy\.codex\worktrees\phase-124-linux-integration\AndroidAppCoding`
Base: `11555095e2a66c8dfc4dd9918118ed81d97720da`
Branch: `codex/phase-124-linux-integration`
Source: `D:\AndroidAppCoding\.worktrees\linux-client` at `5c42103839dabc9390aced748083493ff2385450`

## Source and scope

The source HEAD is an ancestor of the planned main base. There are no Linux-only commits to cherry-pick. The source worktree had five modified tracked files (`.gitignore`, `AGENTS.md`, `docs/ARCHITECTURE.md`, `docs/DATA_SAFETY.md`, `docs/PRODUCT_SPEC.md`) and untracked Linux source plus `.github/workflows/linux.yml`. Those five modified files were not copied. Forty Linux text files and the workflow were selected and recorded with pre-copy status, byte size and SHA-256 in [phase-124-linux-provenance.md](phase-124-linux-provenance.md). Rechecking the original source produced **41 hashes checked, 0 mismatches**. The original worktree status remains as observed; this task did not write there.

`D:\AndroidAppCoding\TEMP\ojnexus-phase121-platform-audit.md` was absent. The plan and source audit were used as available, with no claim that the missing audit file was read.

## Environment and commands

Executed inside the already-running WSL distro, without starting or stopping any distro. Environment: Ubuntu 24.04.4 LTS, GCC 13.3.0, CMake 3.28.3, Ninja 1.11.1, Qt 6.4.2. GUI display variables were empty (`DISPLAY`, `WAYLAND_DISPLAY`), so no interactive GUI launch was available. All Qt tests used `QT_QPA_PLATFORM=offscreen`; that is test evidence, not interactive GUI evidence.

Build used `/tmp/ojnexus-phase124-final` and did not touch the original Linux build directory:

| Command | Result |
| --- | --- |
| `cmake -S linux -B /tmp/ojnexus-phase124-final -G Ninja -DOJNEXUS_BUILD_GUI=ON -DOJNEXUS_BUILD_TESTS=ON` | exit 0; configured Qt GUI and tests |
| `cmake --build /tmp/ojnexus-phase124-final --parallel 4` | exit 0; all 54 build steps completed |
| `QT_QPA_PLATFORM=offscreen ctest --test-dir /tmp/ojnexus-phase124-final --output-on-failure --output-junit TEMP/phase124-ctest.xml` | exit 0; 8/8 CTest targets passed |
| `cpack --config /tmp/ojnexus-phase124-final/CPackConfig.cmake -G DEB` | exit 0; produced `ojnexus-0.1.0-Linux.deb` |
| `dpkg-deb -I` and `dpkg-deb -c` on the DEB | exit 0; metadata, dependencies and payload inspected |

The DEB declares Qt 6 Core, GUI, Network, QML, Quick, Quick Controls 2 and SQLite driver dependencies. Contents are CLI, GUI, desktop entry and README. The package was not installed. The included `linux/packaging/PKGBUILD` is retained as a recipe only; Arch support was not built or asserted.

CLI smoke used `OJ_NEXUS_DATA_DIRECTORY=/tmp/ojnexus-phase124-smoke`. `status --json`, `history --json`, and `config show --json` each returned exit 0 and reported the isolated data root. No public accounts or credentials were inserted into this basic smoke.

## Public sync and offline cache behavior

Using only the public Codeforces handle `tourist`, a bounded live sync returned exit 0. PROFILE (1), RATING (310), SUBMISSIONS (1000), CONTESTS (2161), and PROBLEMSET (11467) all reported SUCCESS. This is live public response evidence for this run, not a general uptime claim.

With the same isolated data directory and the local proxy environment pointed at `127.0.0.1:9`, the client reported an OFFLINE operation and `OFFLINE` typed failure for each module. A subsequent `status --json` still showed the previous profile timestamp, 310 rating changes and 1000 submissions; history retained both the successful and offline operation. This confirms this run preserved cached data across the forced offline sync. The sync test's function-level CLI contract is covered by the passing CLI suite (offline maps to exit 3). WSL denied `unshare -n` (`Operation not permitted`, exit 1); the process-local proxy was used for the offline test instead and did not modify host networking.

## Integration workflow and remaining gate

`.github/workflows/linux.yml` is restricted to fixed `ubuntu-24.04` with a 30-minute job timeout. It installs explicit Qt 6/CMake/Ninja dependencies, builds with CMake, runs offscreen CTest and CLI isolated-storage smoke, builds a DEB, and uploads the JUnit CTest report. The former floating Fedora/Arch matrix was removed. No run of the hosted GitHub workflow is claimed.

The interactive GUI gate remains **unverified / unavailable** on this WSL environment because both display variables were empty. No GUI screenshot or desktop interaction evidence is claimed. A future Linux desktop/WSLg session must perform that gate separately.

## Final checks

- [x] CMake configure and all targets build.
- [x] Full CTest suite, including typed offline behavior and offscreen Qt/QML tests.
- [x] Isolated CLI smoke; bounded live public sync; forced typed offline result and cache retention.
- [x] DEB build and payload/dependency inspection without installation.
- [x] Source provenance hashes rechecked; original source worktree not edited.
- [ ] Interactive desktop GUI: unavailable in current WSL session.
- [ ] Hosted Ubuntu CI run: not run from this task.
- [ ] Scoped commit: pending final review.
