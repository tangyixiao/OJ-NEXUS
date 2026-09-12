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
pwsh -File windows/scripts/ui-smoke.ps1 -Configuration Release
```

The smoke check prints the exact CLI and desktop paths, runs `status --json`, validates
`status=ready`, verifies the JSON exit code is zero, and keeps the WPF process alive for up to
15 seconds. It uses a generated temporary data directory and removes only that directory when it
finishes. Normal application data remains below `%LOCALAPPDATA%\OJ-NEXUS\ojnexus.db`.

The UI smoke check starts the real WPF executable with an isolated temporary data directory,
resizes it to the supported minimum `900x560`, uses Windows UI Automation to visit all three
views, and checks the public-handle editors and history judge filter. It reports screenshot
capture separately; sessions without a usable interactive desktop may pass the UI checks while
reporting `SCREENSHOTS: 0/3`. Any generated screenshots are written below
`windows/artifacts/ui-smoke/`, which is ignored by Git.

## Commands

```powershell
ojnexus.exe status [--json]
ojnexus.exe sync --judge <codeforces|atcoder|luogu> [--handle <handle>] [--force] [--json]
ojnexus.exe history [--judge <judge>] [--limit <n>] [--json]
ojnexus.exe config show [--json]
```

Only public handles are accepted. The Windows client never asks for or stores OJ passwords,
cookies, main-site sessions, raw HTTP bodies, source code, or custom input. Remote failures remain
typed and local sync history stays readable offline. The `OJ_NEXUS_DATA_DIRECTORY` environment
variable is a test/automation override; without it, the default local application directory is
used.

`data --judge luogu` reads a structured local summary containing the public profile and the
persisted counts for submissions, contests, and problems. An anonymous Luogu submissions request
may remain authentication-gated; the client records that typed failure and never fabricates a
submission count.

The history view shows at most five rows for the selected judge. Failed, partial, cancelled, and
offline operations expose an explicit full-sync `RETRY` action; successful operations do not.
When multiple connectors are syncing, each connector's `CANCEL` action only cancels that
connector; closing the desktop client cancels all active syncs.

## Release artifacts

CI produces framework-dependent `.NET 8` `ojnexus.exe` CLI and WPF directories under `windows/artifacts/`.
They require the matching .NET 8 runtime on the target machine. This milestone does not claim an
installer, code signature, or a store package.
