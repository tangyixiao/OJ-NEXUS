# OJ NEXUS Windows Client

The Windows vertical slice contains a native WPF desktop client, a scriptable CLI, and one
local-first synchronization core. The desktop and CLI share the Core project in-process; the
desktop client does not launch the CLI as a child process.

## Build and test

From the repository root on Windows:

```powershell
dotnet restore windows/OjNexus.Windows.sln --configfile windows/NuGet.Config
dotnet test windows/OjNexus.Windows.sln -c Release --no-restore
dotnet build windows/OjNexus.Windows.sln -c Release --no-restore
pwsh -File windows/scripts/smoke.ps1 -Configuration Release
```

The smoke check prints the exact CLI and desktop paths, runs `status --json`, validates
`status=ready`, verifies the JSON exit code is zero, and keeps the WPF process alive for up to
15 seconds. It uses a generated temporary data directory and removes only that directory when it
finishes. Normal application data remains below `%LOCALAPPDATA%\OJ-NEXUS\ojnexus.db`.

## Commands

```powershell
ojnexus status [--json]
ojnexus sync --judge <codeforces|atcoder|luogu> [--handle <handle>] [--force] [--json]
ojnexus history [--judge <judge>] [--limit <n>] [--json]
ojnexus config show [--json]
```

Only public handles are accepted. The Windows client never asks for or stores OJ passwords,
cookies, main-site sessions, raw HTTP bodies, source code, or custom input. Remote failures remain
typed and local sync history stays readable offline. The `OJ_NEXUS_DATA_DIRECTORY` environment
variable is a test/automation override; without it, the default local application directory is
used.

## Release artifacts

CI produces framework-dependent `.NET 8` CLI and WPF directories under `windows/artifacts/`.
They require the matching .NET 8 runtime on the target machine. This milestone does not claim an
installer, code signature, or a store package.
