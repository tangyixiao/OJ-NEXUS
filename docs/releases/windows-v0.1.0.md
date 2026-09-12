# OJ NEXUS Windows v0.1.0 vertical slice

This note records the first native Windows client and CLI vertical slice. It is a framework-
dependent validation artifact, not a claim that an installer, code signature, or store package
has been published.

## Included

- Native WPF client with `DASHBOARD`, `CONNECTORS`, and `SYNC HISTORY` views.
- `SYNC HISTORY` is bounded to five visible rows, supports judge filtering, and exposes full-sync
  retry for typed failed, partial, cancelled, and offline operations.
- Connector cancellation is isolated per active sync; cancelling one connector does not cancel
  another, while client shutdown cancels all active syncs.
- Typed operation failures survive local refresh, so `ERROR`, `OFFLINE`, and partial module
  failures retain an actionable reason in the desktop state.
- Scriptable `ojnexus.exe` `status`, `sync`, `history`, `data`, and `config show` CLI commands.
- Shared .NET 8 Core SQLite ledger with typed sync outcomes and public-data-only adapters for
  Codeforces, AtCoder, and Luogu.
- Bounded local history, cancellation, and redacted error presentation.

## Local verification

The following commands were run from `D:\AndroidAppCoding` after the Windows client commit:

```powershell
dotnet restore windows/OjNexus.Windows.sln --configfile windows/NuGet.Config
dotnet test windows/OjNexus.Windows.sln -c Release --no-restore
dotnet build windows/OjNexus.Windows.sln -c Release --no-restore
pwsh -File windows/scripts/smoke.ps1 -Configuration Release
```

Observed test totals: Core 53/53, CLI 27/27, Desktop 12/12. The Release build completed with 0
warnings and 0 errors. The smoke script uses a generated temporary data directory, validates the
CLI `status --json` response and exit code 0, then keeps the WPF executable alive within its
15-second bound before terminating that exact test process.

## Boundary

Windows data defaults to `%LOCALAPPDATA%\OJ-NEXUS\ojnexus.db`. Only public handles are stored;
passwords, cookies, sessions, raw HTTP bodies, source code, and custom input are not persisted.
The current artifacts are framework-dependent. Installer, signing, store packaging, and manual
per-control visual acceptance are not included in this note.
