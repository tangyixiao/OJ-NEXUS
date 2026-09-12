# OJ NEXUS Windows v0.1.0 vertical slice

This note records the first native Windows client and CLI vertical slice plus its self-contained
`win-x64` distribution bundle. It is not a claim that an installer, code signature, or store
package has been published.

## Included

- Native WPF client with `DASHBOARD`, `CONNECTORS`, and `SYNC HISTORY` views.
- `SYNC HISTORY` is bounded to five visible rows, supports judge filtering, and exposes full-sync
  retry for typed failed, partial, cancelled, and offline operations.
- Connector cancellation is isolated per active sync; cancelling one connector does not cancel
  another, while client shutdown cancels all active syncs.
- Typed operation failures survive local refresh, so `ERROR`, `OFFLINE`, and partial module
  failures retain an actionable reason in the desktop state.
- `SYNC HISTORY` includes the allowlisted failure reason in each module summary, for example
  `SUBMISSIONS:ERROR (NETWORK)`, while full operation failures show `ERROR:<CATEGORY>`.
- Scriptable `ojnexus.exe` `status`, `sync`, `history`, `data`, and `config show` CLI commands.
- Shared .NET 8 Core SQLite ledger with typed sync outcomes and public-data-only adapters for
  Codeforces, AtCoder, and Luogu.
- Self-contained `win-x64` directory and ZIP package with the CLI, WPF client, runtime files,
  package readme, and SHA-256 manifest.
- Schema v4 persists a structured Luogu public payload summary; `data --judge luogu` returns the
  profile and locally known counts without storing raw HTTP bodies.
- Bounded local history, cancellation, and redacted error presentation.

## Local verification

The following commands were run from `D:\AndroidAppCoding` after the Windows client commit:

```powershell
dotnet restore windows/OjNexus.Windows.sln --configfile windows/NuGet.Config --runtime win-x64
dotnet test windows/OjNexus.Windows.sln -c Release --no-restore
dotnet build windows/OjNexus.Windows.sln -c Release --no-restore
pwsh -File windows/scripts/smoke.ps1 -Configuration Release
pwsh -File windows/scripts/ui-smoke.ps1 -Configuration Release
pwsh -File windows/scripts/package.ps1 -Version 0.1.0 -Configuration Release -Runtime win-x64
```

Observed test totals: Core 55/55, CLI 28/28, Desktop 12/12. The Release build completed with 0
warnings and 0 errors. The smoke script uses a generated temporary data directory, validates the
CLI `status --json` response and exit code 0, then keeps the WPF executable alive within its
15-second bound before terminating that exact test process. The UI smoke script then starts the
real WPF process at the supported minimum `900x560` size, visits `DASHBOARD`, `CONNECTORS`, and
`SYNC HISTORY` through UI Automation, finds the semantically named `CODEFORCES PUBLIC HANDLE`,
`ATCODER PUBLIC HANDLE`, and `LUOGU PUBLIC HANDLE` editors plus 1 history filter, and
cleans up its exact temporary data directory. On this host it reported `SCREENSHOTS: 0/3` because
the PowerShell session had no usable interactive desktop capture handle; screenshot capture is
reported separately and is not treated as a fabricated visual pass. The package command then
verified the staged and extracted self-contained package, with `662` hashed package files and
ZIP SHA-256 `8A16154B3F5E02BBC28BD1FE5792704ED7CDC40BE04B03062D626773D077DD95`.
A separate final UI smoke against the packaged WPF executable completed the UI Automation checks
with `UI SMOKE: PASS` at `900x560`, but its three `PrintWindow` captures were rejected as
identical/unrendered and therefore reported `SCREENSHOTS: 0/3`. This is control-tree evidence,
not a human visual acceptance.

A separate local-only public smoke used `uid:2` with a temporary data directory. Luogu sync
returned the expected partial result (3 of 4 public stages succeeded because anonymous
`SUBMISSIONS` remains authentication-gated), and the subsequent `data --judge luogu --json`
returned exit code 0 with profile `lzn`, `CONTESTS: 20`, `PROBLEMS: 50`, and
`SUBMISSIONS: 0`. The temporary directory was removed after the check.

## Boundary

Windows data defaults to `%LOCALAPPDATA%\OJ-NEXUS\ojnexus.db`. Only public handles are stored;
passwords, cookies, sessions, raw HTTP bodies, source code, and custom input are not persisted.
The current package is self-contained but unsigned. Installer, signing, store packaging, and
enlarged text-scale acceptance are not included in this note. The UI Automation smoke is a
reusable minimum-size control/render probe, not a replacement for a human visual review on a
normal desktop.
