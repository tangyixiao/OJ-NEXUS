# Windows installer — plan (2026-10-18)

Spec: docs/superpowers/specs/2026-10-18-windows-installer.md

## Steps

1. Probe the host for ISCC/choco (done: both absent — CI is the compile surface).
2. Write windows/scripts/installer.iss (Inno Setup 6, PrivilegesRequired=lowest, start-menu
   shortcuts, uninstaller, {autopf} default, version via #define).
3. Write windows/scripts/build-installer.ps1 — runs package.ps1, then ISCC when present,
   optional signtool behind OJ_NEXUS_SIGNTOOL_PATH + OJ_NEXUS_SIGNING_CERT_THUMBPRINT,
   .NET-API cleanup only.
4. Write windows/scripts/verify-installer.ps1 — existence/version-resource/HELP checks.
5. CI: windows.yml gains choco innosetup install + installer build + artifact upload.
6. Local gate: build-installer.ps1 -SkipInstaller end-to-end (validates package path),
   verify-installer.ps1 against the zip artifacts (skips installer-only checks with an honest
   message when no .exe exists yet — CI produces it).
7. Bookkeeping: plan Result, docs/LONG_RANGE_PLAN.md ✅, ROADMAP section, commit feat:.

## Result

Completed 2026-10-18. Host probe: ISCC absent, choco absent — CI (windows-latest) is the compile
surface; local runs use -SkipInstaller honestly instead of faking. Shipped:
- windows/scripts/installer.iss — Inno Setup 6, PrivilegesRequired=lowest, {autopf} default dir,
  start-menu + desktop shortcuts, uninstaller registered, version via /DMyAppVersion, source
  staging root via /DSourceRoot (package.ps1 layout: cli\ + desktop\ + README + SHA256SUMS).
- windows/scripts/build-installer.ps1 — runs package.ps1 first, locates ISCC under Program
  Files/PATH, compiles with /O staging output, moves the single produced exe to the artifacts
  root; optional signing strictly behind OJ_NEXUS_SIGNTOOL_PATH + OJ_NEXUS_SIGNING_CERT_THUMBPRINT
  (signtool sign /fd SHA256 + /tr timestamp + verify /pa, else "SIGNING: SKIPPED"); all cleanup
  through .NET file APIs (Remove-InstallerPath, same contract as package.ps1).
- windows/scripts/verify-installer.ps1 — installer mode (existence, non-empty, version resource
  carries expected version; silent install/uninstall round trip documented as a manual clean-VM
  step, /HELP noted as the elevation-free check) and package mode (cli + desktop payload present).
- .github/workflows/windows.yml — choco install innosetup, build-installer.ps1 step, installer
  artifact upload (OJ-NEXUS-Setup-*.exe).
Local gate: build-installer.ps1 -Version 0.1.0 -SkipUiSmoke -SkipInstaller end-to-end OK (662
files verified in extract, 663 staged, deterministic zip SHA256 logged). First run failed with
NETSDK1047 — package.ps1 publishes with --no-restore, so a win-x64 runtime restore
(`dotnet restore windows/OjNexus.Windows.sln --configfile windows/NuGet.Config --runtime win-x64`)
is a prerequisite on machines that have not restored that RID; CI already had the step.

