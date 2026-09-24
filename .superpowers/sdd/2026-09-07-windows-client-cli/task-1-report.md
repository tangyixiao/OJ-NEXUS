# Task 1 Report

- Status: complete
- Commits:
  - `ff736ca` `build: scaffold Windows client and CLI solution`
  - `e4724ba` `chore: drop generated Windows build outputs`
- Test summary: `dotnet build windows/OjNexus.Windows.sln --configuration Release -p:RestoreConfigFile=windows/NuGet.Config` passed with 0 warnings and 0 errors.
- Concerns: the host NuGet configuration still advertises a missing local feed, so the successful build needed an explicit restore config file override; the repo-level `windows/NuGet.Config` keeps the solution build pointed at `nuget.org`.
- Report path: `D:\AndroidAppCoding\.superpowers\sdd\2026-09-07-windows-client-cli\task-1-report.md`

Scope completed for Task 1:

- Created `windows/OjNexus.Windows.sln`.
- Added `windows/Directory.Build.props` with nullable reference types, implicit usings, deterministic builds, and `TreatWarningsAsErrors`.
- Created the `net8.0` Core and CLI projects.
- Created the `net8.0-windows` WPF Desktop project with `UseWPF=true`.
- Created the two `net8.0` xUnit test projects.
- Added project references and the root-level build gate.
- Kept the Android app and the untracked 2026-09-06 draft files unchanged.

## Fix Round 1

Files changed in this fix round:

- `windows/src/OjNexus.Windows.Cli/Program.cs`
- `windows/src/OjNexus.Windows.Cli/OjNexus.Windows.Cli.csproj`
- `windows/src/OjNexus.Windows.Desktop/OjNexus.Windows.Desktop.csproj`
- `windows/tests/OjNexus.Windows.Core.Tests/OjNexus.Windows.Core.Tests.csproj`
- `windows/tests/OjNexus.Windows.Cli.Tests/OjNexus.Windows.Cli.Tests.csproj`
- `windows/Directory.Build.props`

Commands run:

- `dotnet build windows/OjNexus.Windows.sln --configuration Release`
- `git reset --soft 3ef0670`

Build output:

- The exact Release build succeeded with 0 warnings and 0 errors.
- Restore and build completed without needing any extra command-line property.

Self-review:

- `OjNexus.Windows.Cli.csproj` now has a console-template entry point in `Program.cs`.
- The required ProjectReference edges are present: CLI → Core, Desktop → Core, Core.Tests → Core, CLI.Tests → CLI.
- `windows/Directory.Build.props` now points restore at `windows/NuGet.Config`, so `dotnet build windows/OjNexus.Windows.sln --configuration Release` is reproducible without extra flags.
- The Task 1 commit history is being consolidated back onto `3ef0670` as a single Task 1 commit, while the two untracked `2026-09-06` drafts remain untouched.
