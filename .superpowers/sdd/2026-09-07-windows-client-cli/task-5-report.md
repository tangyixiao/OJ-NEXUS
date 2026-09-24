# Task 5 report: Windows OJ NEXUS CLI contract

## Delivered

- Added typed parsing for `status`, `sync`, `history`, and `config show`, including strict option validation and actionable invalid-argument errors.
- Added documented CLI exit codes: success `0`, partial `1`, invalid arguments/configuration `2`, unavailable network or adapter `3`, and cancelled `4`.
- Added uppercase telemetry-style human output plus stable camelCase JSON output. Sync JSON delegates to `SyncReportProjector`; configuration output is restricted to public local paths and public judge handles.
- Added a local bootstrap that uses `WindowsPaths`, `SqliteConnectionFactory`, `SqliteSyncStore`, an explicit adapter registry, and `SyncService`. Status and history use the local SQLite store only. Until a judge adapter is registered, sync records the typed unavailable state and exits `3`.
- Added deterministic CLI parser/renderer tests for required judge and positive history limits, unknown commands and flags, JSON parsing, redacted configuration, telemetry output, and all numeric exit-code mappings.

## TDD evidence

- RED: `C:\Program Files\dotnet\dotnet.exe test windows/tests/OjNexus.Windows.Cli.Tests/OjNexus.Windows.Cli.Tests.csproj --no-restore` failed because the CLI contract types did not exist.
- GREEN: the same focused command passed 15 tests after the implementation.

## Verification

- `C:\Program Files\dotnet\dotnet.exe run --no-restore --project windows/src/OjNexus.Windows.Cli -- status --json`
  - Output: `{"status":"ready","accountCount":0,"lastSync":null}`
  - Exit code: `0`
- `C:\Program Files\dotnet\dotnet.exe test windows/tests/OjNexus.Windows.Cli.Tests/OjNexus.Windows.Cli.Tests.csproj --no-restore`
  - Passed: 15; failed: 0; skipped: 0.
- `C:\Program Files\dotnet\dotnet.exe test windows/OjNexus.Windows.sln --configuration Release --no-restore`
  - Core passed: 36; CLI passed: 15; failed: 0.
- `C:\Program Files\dotnet\dotnet.exe build windows/OjNexus.Windows.sln --configuration Release --no-restore`
  - Succeeded with 0 warnings and 0 errors.

## Environment note

The first smoke invocation observed stale untracked CLI build outputs that predated the Core SQLite package restore, so its dependency manifest omitted `Microsoft.Data.Sqlite`. Rebuilding the CLI with the already-restored assets regenerated the manifest and copied the runtime dependencies. No source dependency change was needed.

## Scope

Only the Task 5 CLI source files, CLI tests, and this requested task report are included. Android files, existing drafts, and generated `bin`/`obj` directories remain unstaged.
