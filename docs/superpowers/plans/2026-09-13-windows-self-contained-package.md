# Windows Self-Contained Package Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and verify a reproducible unsigned `win-x64` ZIP containing self-contained OJ NEXUS CLI and WPF binaries.

**Architecture:** A PowerShell package orchestrator publishes the existing CLI and Desktop projects into isolated staging directories, copies a tracked plain-text package readme, writes a deterministic SHA-256 manifest, and creates one versioned ZIP. A separate read-only verifier checks layout, self-contained runtime files, manifest hashes, and required executables; the existing CLI/WPF smoke scripts receive the exact staged paths.

**Tech Stack:** PowerShell 7, .NET 8 `dotnet publish`, WPF, Microsoft.Data.Sqlite, existing `smoke.ps1` and `ui-smoke.ps1`.

**Spec:** `docs/superpowers/specs/2026-09-13-windows-self-contained-package-design.md`

## Global Constraints

- Target runtime is `win-x64`; defaults are version `0.1.0` and configuration `Release`.
- Publish uses `SelfContained=true`, `PublishSingleFile=false`, and `--no-restore`; restore is an explicit caller/CI step.
- The package contains no database, temporary smoke data, credentials, cookies, keystores, source code, or build secrets.
- Runtime data remains `%LOCALAPPDATA%\OJ-NEXUS\ojnexus.db`, except for the existing `OJ_NEXUS_DATA_DIRECTORY` automation override.
- The package is a directory bundle plus ZIP, not an MSI, MSIX, installer, signed artifact, Store package, or automatic updater.
- Only exact paths under `windows/artifacts/` may be created or removed by packaging scripts; unrelated worktree files remain untouched.
- Existing Core/CLI/Desktop tests, warning-free Release build, CLI smoke, and WPF/UI Automation boundaries remain mandatory.
- The existing WPF launch smoke keeps its 15-second startup bound; package verification must use that same bound.

---

### Task 1: Add the read-only package contract verifier

**Files:**
- Create: `windows/scripts/verify-package.ps1`

**Interfaces:**
- Consumes: `-PackageRoot` and optional `-ExpectedVersion` parameters; callers pass a package path and a three-part semantic version.
- Produces: exit code `0` only when the package layout, required runtime files, and every SHA-256 entry are valid; otherwise a terminating error and non-zero exit.

- [ ] **Step 1: Write the failing verifier invocation**

Run the verifier before the script exists:

```powershell
pwsh -NoProfile -File windows/scripts/verify-package.ps1 `
  -PackageRoot windows/artifacts/self-contained/missing-package `
  -ExpectedVersion 0.1.0
```

Expected: PowerShell reports that the package root or required manifest is missing and exits non-zero.

- [ ] **Step 2: Implement structural and hash checks**

Implement these exact checks in `verify-package.ps1`:

```powershell
param(
  [Parameter(Mandatory)] [string] $PackageRoot,
  [ValidatePattern('^\d+\.\d+\.\d+$')] [string] $ExpectedVersion
)

$root = (Resolve-Path -LiteralPath $PackageRoot -ErrorAction Stop).Path
$manifest = Join-Path $root 'SHA256SUMS.txt'
$required = @(
  (Join-Path $root 'cli/ojnexus.exe'),
  (Join-Path $root 'cli/coreclr.dll'),
  (Join-Path $root 'desktop/OjNexus.Windows.Desktop.exe'),
  (Join-Path $root 'desktop/coreclr.dll'),
  (Join-Path $root 'README.txt'),
  $manifest
)
foreach ($path in $required) {
  if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
    throw "Package member is missing: $path"
  }
}

$expectedName = "OJ-NEXUS-Windows-v$ExpectedVersion-win-x64"
if ((Split-Path -Leaf $root) -ne $expectedName) {
  throw "Unexpected package directory name. Expected $expectedName."
}

$entries = Get-Content -LiteralPath $manifest | Where-Object { $_.Trim() }
foreach ($entry in $entries) {
  $parts = $entry -split '\s+', 2
  if ($parts.Count -ne 2 -or $parts[0] -notmatch '^[0-9A-Fa-f]{64}$') {
    throw "Invalid SHA256 manifest entry: $entry"
  }
  $relative = $parts[1].TrimStart('*').TrimStart('/').Replace('/', [IO.Path]::DirectorySeparatorChar)
  $file = Join-Path $root $relative
  if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
    throw "Manifest references missing file: $relative"
  }
  $actual = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash
  if ($actual -ine $parts[0]) {
    throw "SHA256 mismatch: $relative"
  }
}
```

The final implementation must additionally reject absolute manifest paths, `..` traversal,
duplicate entries, missing `README.txt`, and any package file not represented by the manifest
except `SHA256SUMS.txt` itself. It must print the verified root and entry count without printing
file contents.

- [ ] **Step 3: Run the negative verifier test again**

Run the command from Step 1. Expected: the new verifier fails cleanly on the missing package.

- [ ] **Step 4: Commit the verifier**

```powershell
git add windows/scripts/verify-package.ps1
git commit -m "test: add Windows package contract verifier"
```

### Task 2: Build the self-contained package

**Files:**
- Create: `windows/scripts/package.ps1`
- Create: `windows/PACKAGE_README.txt`
- Test: `windows/scripts/verify-package.ps1`

**Interfaces:**
- Consumes: `-Version 0.1.0`, `-Configuration Release`, `-Runtime win-x64`, optional `-OutputDirectory`, and optional `-SkipUiSmoke` for non-interactive CI only.
- Produces: `windows/artifacts/self-contained/OJ-NEXUS-Windows-v$Version-win-x64/` and the sibling `OJ-NEXUS-Windows-v$Version-win-x64.zip`.

- [ ] **Step 1: Write the failing package command**

Run the package command before `package.ps1` exists:

```powershell
pwsh -NoProfile -File windows/scripts/package.ps1 -Version 0.1.0 -Configuration Release -Runtime win-x64
```

Expected: PowerShell reports that the package script is missing and exits non-zero.

- [ ] **Step 2: Add the package readme**

Create `windows/PACKAGE_README.txt` containing the package version placeholder supplied by
the script, startup commands for `desktop\OjNexus.Windows.Desktop.exe` and
`cli\ojnexus.exe status --json`, the `%LOCALAPPDATA%\OJ-NEXUS` data path, and these explicit
boundaries: public handles only; no passwords, cookies, sessions, raw HTTP bodies, source code,
custom input, installer behavior, signing, or automatic updates.

- [ ] **Step 3: Implement deterministic publish and staging**

`package.ps1` must:

1. Validate `Version`, `Configuration`, and `Runtime`; require `Runtime` to be `win-x64` for
   this increment.
2. Resolve the repository root and an output directory under `windows/artifacts/`; refuse an
   output path that is not inside `windows/artifacts/`.
3. Publish the two existing projects with:

```powershell
dotnet publish windows/src/OjNexus.Windows.Cli/OjNexus.Windows.Cli.csproj `
  --configuration $Configuration --runtime $Runtime --self-contained true --no-restore `
  -p:PublishSingleFile=false -p:DebugType=None --output $stagingRoot\cli
dotnet publish windows/src/OjNexus.Windows.Desktop/OjNexus.Windows.Desktop.csproj `
  --configuration $Configuration --runtime $Runtime --self-contained true --no-restore `
  -p:PublishSingleFile=false -p:DebugType=None --output $stagingRoot\desktop
```

4. Copy `windows/PACKAGE_README.txt` to the package root as `README.txt`, substituting the
   requested version only in the generated copy.
5. Generate `SHA256SUMS.txt` from sorted package-relative file paths, using `/` separators,
   excluding only the manifest itself.
6. Invoke `verify-package.ps1` against the staging root, then invoke existing `smoke.ps1` with
   the staged CLI and desktop paths. Invoke `ui-smoke.ps1` with the staged desktop path unless
   `-SkipUiSmoke` is explicitly set.
7. Create the ZIP with the versioned package directory as its sole top-level entry. Verify the
   archive by extracting it into a generated temporary directory and running the verifier again.
8. Print exact output paths, package file count, archive path, and SHA-256 of the ZIP. Remove only
   its generated staging verification directory and temporary extraction directory; leave the
   final directory and ZIP in the requested artifact directory.

The script must never call `sync`, never contact an OJ endpoint, never inherit the user's normal
data directory for smoke, and must terminate a WPF process only through the existing smoke scripts.

- [ ] **Step 4: Run the package command and contract checks**

Run:

```powershell
dotnet restore windows/OjNexus.Windows.sln --configfile windows/NuGet.Config
pwsh -NoProfile -File windows/scripts/package.ps1 -Version 0.1.0 -Configuration Release -Runtime win-x64
pwsh -NoProfile -File windows/scripts/verify-package.ps1 `
  -PackageRoot windows/artifacts/self-contained/OJ-NEXUS-Windows-v0.1.0-win-x64 `
  -ExpectedVersion 0.1.0
Get-ChildItem windows/artifacts/self-contained/OJ-NEXUS-Windows-v0.1.0-win-x64.zip
```

Expected: publish succeeds, the verifier reports all hashes valid, package CLI/WPF smoke passes,
and the ZIP contains exactly one versioned root directory.

- [ ] **Step 5: Check package boundary and self-contained evidence**

Run:

```powershell
rg -n -i "password|cookie|session|secret|keystore|source code|custom input|ojnexus\.db" `
  windows/artifacts/self-contained/OJ-NEXUS-Windows-v0.1.0-win-x64/README.txt
Test-Path windows/artifacts/self-contained/OJ-NEXUS-Windows-v0.1.0-win-x64/cli/coreclr.dll
Test-Path windows/artifacts/self-contained/OJ-NEXUS-Windows-v0.1.0-win-x64/desktop/coreclr.dll
```

Expected: the boundary scan finds only the explicitly documented prohibitions, and both runtime
checks return `True`; no database or credential file exists in the package tree.

- [ ] **Step 6: Commit the package implementation**

```powershell
git add windows/scripts/package.ps1 windows/PACKAGE_README.txt
git commit -m "release: add Windows self-contained package"
```

### Task 3: Integrate CI and release documentation

**Files:**
- Modify: `.github/workflows/windows.yml`
- Modify: `windows/README.md`
- Modify: `docs/releases/windows-v0.1.0.md`
- Modify: `docs/ROADMAP.md`

**Interfaces:**
- Consumes: the package script's `-SkipUiSmoke` only if the runner has no interactive desktop; uses the existing restore and solution test/build gates.
- Produces: a CI-uploaded self-contained ZIP and unpacked package/manifest artifacts, with honest evidence boundaries.

- [ ] **Step 1: Update the Windows workflow**

Keep restore, solution test, and Release build unchanged. Add a package step after them:

```yaml
- name: Package self-contained Windows client
  shell: pwsh
  run: >-
    ./windows/scripts/package.ps1 -Version 0.1.0 -Configuration Release -Runtime win-x64
    -SkipUiSmoke
```

Upload `windows/artifacts/self-contained` with `if: always()`. Do not add secrets, signing
steps, external OJ calls, or a second data directory. The workflow comment/doc must say that
headless CI proves package build, hashes, CLI status, and bounded WPF launch; local interactive
UI Automation remains a separate gate when the runner cannot expose a desktop.

- [ ] **Step 2: Update user-facing release docs**

Document the package command, exact output paths, self-contained runtime behavior, ZIP layout,
SHA-256 verification, and unsigned/no-installer boundary. Preserve the existing framework-
dependent artifact notes as historical evidence rather than silently relabeling them.

- [ ] **Step 3: Run static checks**

Run:

```powershell
pwsh -NoProfile -Command "[System.Management.Automation.Language.Parser]::ParseFile('windows/scripts/package.ps1',[ref]$null,[ref]$null) | Out-Null"
pwsh -NoProfile -Command "[System.Management.Automation.Language.Parser]::ParseFile('windows/scripts/verify-package.ps1',[ref]$null,[ref]$null) | Out-Null"
git diff --check
```

Expected: both scripts parse and `git diff --check` is clean.

- [ ] **Step 4: Commit CI and documentation**

```powershell
git add .github/workflows/windows.yml windows/README.md docs/releases/windows-v0.1.0.md docs/ROADMAP.md
git commit -m "docs: record Windows self-contained release artifact"
```

### Task 4: Run the final Windows acceptance audit

**Files:**
- Verify: `windows/artifacts/self-contained/OJ-NEXUS-Windows-v0.1.0-win-x64.zip`
- Verify: `windows/scripts/package.ps1`, `windows/scripts/verify-package.ps1`

**Interfaces:**
- Consumes: committed source, the exact staged package, and existing test/smoke scripts.
- Produces: fresh local evidence for tests, build, package hashes, packaged CLI, packaged WPF, and UI Automation.

- [ ] **Step 1: Run the complete source gate**

```powershell
dotnet test windows/OjNexus.Windows.sln -c Release --no-restore
dotnet build windows/OjNexus.Windows.sln -c Release --no-restore
```

Expected: Core, CLI, and Desktop suites are green; build has zero warnings and zero errors.

- [ ] **Step 2: Rebuild and verify the exact package**

```powershell
pwsh -NoProfile -File windows/scripts/package.ps1 -Version 0.1.0 -Configuration Release -Runtime win-x64
```

Expected: package script verifies the directory and extracted ZIP, runs package CLI/WPF smoke,
and leaves no WPF process or temporary data directory behind.

- [ ] **Step 3: Run interactive UI smoke against packaged WPF**

```powershell
pwsh -NoProfile -File windows/scripts/ui-smoke.ps1 `
  -DesktopPath windows/artifacts/self-contained/OJ-NEXUS-Windows-v0.1.0-win-x64/desktop/OjNexus.Windows.Desktop.exe `
  -OutputDirectory windows/artifacts/self-contained/ui-smoke
```

Expected: `DASHBOARD`, `CONNECTORS`, and `SYNC HISTORY` pass at `900x560`; the three semantic
public-handle names and history filter are found. Screenshot output is reported separately and
is not upgraded to visual acceptance when the session cannot capture windows.

- [ ] **Step 4: Review final status and artifact boundary**

```powershell
git status --short
git diff --check
Get-Process -Name OjNexus.Windows.Desktop -ErrorAction SilentlyContinue
```

Expected: no WPF process remains; only pre-existing ignored/untracked `bin/obj`, Linux worktree,
and handoff/planning files remain outside the committed package changes. Do not stage them.
