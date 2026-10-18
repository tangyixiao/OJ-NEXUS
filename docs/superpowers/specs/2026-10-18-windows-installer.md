# Windows installer — spec (2026-10-18)

Phase 100 of docs/LONG_RANGE_PLAN.md (lines 222-226).

## Goal

A signed-or-unsigned Windows installer that produces a start-menu entry and a working
uninstaller for the OJ NEXUS Windows client (CLI + WPF), built by CI, with an install/uninstall
verification path runnable on a clean machine.

## Toolchain decision

Inno Setup 6 (ISCC.exe). Rationale:

- The repo already ships a deterministic zip via `windows/scripts/package.ps1`; the installer is
  an additional distribution channel, not a replacement.
- Inno Setup is script-driven (a single .iss file, text-only, reviewable in git), installs
  per-user or per-machine, and emits an uninstaller registered in Add/Remove Programs.
- MSIX would require signing to install side-loaded reliably on locked-down machines; an
  unsigned Inno Setup installer still installs cleanly, which matches the "signed-or-unsigned"
  constraint.
- ISCC is not present on this host and chocolatey is unavailable, so CI (windows-latest has
  chocolatey) is the only machine that compiles the .iss. Locally the script skips compiling and
  validates inputs instead — never fake results.

## Packaging flow

1. `windows/scripts/package.ps1` keeps producing the deterministic zip (unchanged behavior).
2. New `windows/scripts/installer.iss` — Inno Setup script that:
   - packs the published CLI (`cli\`) and WPF (`desktop\`) trees from the package staging root;
   - creates start-menu shortcuts for the WPF app and the CLI;
   - registers an uninstaller (standard Inno behavior) with `Uninstallable=yes` and
     `CreateUninstallRegKey=yes`;
   - uses `{autopf}` default dir; `PrivilegesRequired=lowest` so a per-user install always works;
   - version comes from `#define MyAppVersion`.
3. New `windows/scripts/build-installer.ps1`:
   - parameterized like package.ps1 (Version, Configuration, Runtime, OutputDirectory,
     SkipUiSmoke, SkipInstaller);
   - reuses package.ps1 by dot-sourcing? No — it invokes it as a child process to keep failure
     isolation, then locates the extracted staging root inside the work output;
   - requires ISCC when available: `iscc /DMyAppVersion=$Version installer.iss` producing
     `OJ-NEXUS-Setup-v$Version.exe`;
   - when ISCC is absent: fails with a clear message unless -SkipInstaller is passed (CI will
     install Inno Setup first);
   - optional signing: if env `OJ_NEXUS_SIGNTOOL_PATH` and `OJ_NEXUS_SIGNING_CERT_THUMBPRINT`
     are both set, run `signtool sign /fd SHA256 /sha1 <thumbprint>` on the installer; otherwise
     emit `SIGNING: SKIPPED (no cert configured)` — never fails the unsigned build (Phase 101
     will build on this).
4. CI (`.github/workflows/windows.yml`) gains an "Install Inno Setup" step
   (`choco install innosetup -y --no-progress`) and an installer job step that runs
   build-installer.ps1 and uploads the resulting .exe as an artifact.

## Verification

- `windows/scripts/verify-installer.ps1`: given a built .exe, asserts it exists, is non-empty,
  and (when Inno Setup is installed) runs `iscc`-free checks: the installer version resource via
  `(Get-Item).VersionInfo` carries the expected version, and a `/HELP` invocation exits 0 — Inno
  installers support `/HELP` without elevation.
- A full silent install/uninstall round trip (`/VERYSILENT /SUPPRESSMSGBOXES` then uninstall from
  the registry uninstall string) requires an interactive session with real shell access; the DoD
  allows "clean VM or sandbox" — this host's DSH sandbox cannot run interactive installers, so
  the round trip is documented as a manual CI-adjacent step, and verify-installer.ps1 covers the
  automatable part honestly (existence, version metadata, /HELP exit code).

## Constraints honored

- No secrets: signing is opt-in via environment variables; thumbprints never committed.
- Hardened deletions: any cleanup uses .NET file APIs (same Remove-PackagePath pattern as
  package.ps1), not Remove-Item.
- No network requests from the app itself; the installer only ships already-built binaries.

## Definition of Done

- build-installer.ps1 runs locally with -SkipInstaller (validates + packages without ISCC).
- CI builds the installer with ISCC and uploads it.
- verify-installer.ps1 validates the artifact (existence/version/HELP) wherever it runs.
- docs/LONG_RANGE_PLAN.md phase 100 marked; ROADMAP section added.
