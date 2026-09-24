# OJ NEXUS Windows Client and CLI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native WPF Windows client and a scriptable Windows CLI backed by one tested local-first synchronization core.

**Architecture:** Add an independent `.NET 8` solution under `windows/`. `OjNexus.Windows.Core` owns portable models, SQLite storage, adapter contracts, sync orchestration, and output projections. The CLI and WPF desktop client reference Core directly and never communicate through a child process.

**Tech Stack:** C#; .NET 8; WPF; `Microsoft.Data.Sqlite`; xUnit; `System.Text.Json`; `HttpClient`; PowerShell smoke scripts; GitHub Actions on `windows-latest`.

**Spec:** `docs/superpowers/specs/2026-09-07-windows-client-cli-design.md`

## Global Constraints

- Keep the existing Android `app/` module unchanged except for shared documentation links when required.
- Store Windows data below `%LOCALAPPDATA%\OJ-NEXUS\` and never open the Android Room database.
- Accept public OJ handles only; never request or persist passwords, cookies, main-site sessions, or raw HTTP bodies.
- Keep judge-specific DTOs and HTTP behavior behind `IJudgeAdapter`; Core, CLI, and Desktop consume judge-agnostic models.
- Use WPF as the native Windows UI; do not add Electron, WebView, Flutter, or React Native.
- Keep human-facing UI and CLI copy uppercase and telemetry-style; `--json` output is stable and machine-readable.
- Do not use fake remote results in product code; all network tests use deterministic local handlers.
- Every task ends with its focused test command and one logical commit.

---

### Task 1: Scaffold the Windows solution and build gates

**Files:**
- Create: `windows/OjNexus.Windows.sln`
- Create: `windows/Directory.Build.props`
- Create: `windows/src/OjNexus.Windows.Core/OjNexus.Windows.Core.csproj`
- Create: `windows/src/OjNexus.Windows.Cli/OjNexus.Windows.Cli.csproj`
- Create: `windows/src/OjNexus.Windows.Desktop/OjNexus.Windows.Desktop.csproj`
- Create: `windows/tests/OjNexus.Windows.Core.Tests/OjNexus.Windows.Core.Tests.csproj`
- Create: `windows/tests/OjNexus.Windows.Cli.Tests/OjNexus.Windows.Cli.Tests.csproj`

**Interfaces:**
- Produces a solution that later tasks can build with `dotnet build windows/OjNexus.Windows.sln` and test with `dotnet test windows/OjNexus.Windows.sln`.

- [ ] **Step 1: Create the solution and project files**

Use `dotnet new sln`, `dotnet new classlib`, `dotnet new console`, `dotnet new wpf`, and `dotnet new xunit` inside `windows/`. Set all projects to `net8.0`, except Desktop which targets `net8.0-windows` with `<UseWPF>true</UseWPF>`. Add project references from CLI/Desktop to Core and from both test projects to their target project.

- [ ] **Step 2: Add shared build properties**

Set nullable reference types, implicit usings, deterministic builds, and `TreatWarningsAsErrors` in `windows/Directory.Build.props`. Keep the Android root Gradle build independent.

- [ ] **Step 3: Run the scaffold gate**

Run: `dotnet build windows/OjNexus.Windows.sln --configuration Release`

Expected: the solution builds with no warnings treated as errors.

- [ ] **Step 4: Commit**

```text
git add windows
git commit -m "build: scaffold Windows client and CLI solution"
```

### Task 2: Define portable domain models and adapter contracts

**Files:**
- Create: `windows/src/OjNexus.Windows.Core/Domain/JudgeId.cs`
- Create: `windows/src/OjNexus.Windows.Core/Domain/JudgeAccount.cs`
- Create: `windows/src/OjNexus.Windows.Core/Domain/SyncModels.cs`
- Create: `windows/src/OjNexus.Windows.Core/Domain/SyncError.cs`
- Create: `windows/src/OjNexus.Windows.Core/Contracts/IJudgeAdapter.cs`
- Create: `windows/src/OjNexus.Windows.Core/Contracts/ISyncStore.cs`
- Create: `windows/src/OjNexus.Windows.Core/Contracts/IClock.cs`
- Create: `windows/tests/OjNexus.Windows.Core.Tests/DomainContractTests.cs`

**Interfaces:**
- `enum JudgeId { Codeforces, AtCoder, Luogu }` and `static bool JudgeIdParser.TryParse(string value, out JudgeId judge)`.
- `record JudgeAccount(JudgeId Judge, string Handle, bool Enabled = true)`.
- `enum SyncOperationStatus { Running, Success, Partial, Error, Cancelled, Offline }`.
- `enum SyncError { UnsupportedJudge, Network, Api, Cancelled, Offline, InvalidConfiguration }`.
- `record SyncModuleOutcome(string Stage, SyncOperationStatus Status, int AttemptedCount, int ImportedCount, int UpdatedCount, string? FailureType)`.
- `record SyncOperation(long Id, JudgeAccount Account, string DataGeneration, DateTimeOffset StartedAt, DateTimeOffset? FinishedAt, SyncOperationStatus Status, IReadOnlyList<SyncModuleOutcome> Modules)`.
- `record SyncReport(SyncOperation Operation, SyncOperationStatus Status, SyncError? Error)`.
- `interface IJudgeAdapter { JudgeId Judge { get; } Task<IReadOnlyList<SyncModuleOutcome>> SyncAsync(JudgeAccount account, CancellationToken cancellationToken); }`.
- `interface ISyncStore { Task<IReadOnlyList<JudgeAccount>> GetAccountsAsync(CancellationToken cancellationToken); Task UpsertAccountAsync(JudgeAccount account, CancellationToken cancellationToken); Task<IReadOnlyList<SyncOperation>> GetRecentOperationsAsync(JudgeId? judge, int limit, CancellationToken cancellationToken); Task<long> OpenOperationAsync(JudgeAccount account, string dataGeneration, DateTimeOffset startedAt, CancellationToken cancellationToken); Task AppendModuleAsync(long operationId, SyncModuleOutcome outcome, DateTimeOffset completedAt, CancellationToken cancellationToken); Task CloseOperationAsync(long operationId, SyncOperationStatus status, SyncError? error, DateTimeOffset finishedAt, CancellationToken cancellationToken); }`.
- `interface IClock { DateTimeOffset UtcNow { get; } }`.

- [ ] **Step 1: Write failing model tests**

Assert that all three judge IDs parse case-insensitively through `JudgeIdParser.TryParse(string, out JudgeId)`, invalid IDs return `false`, and `SyncOperation` preserves module order supplied by the store.

- [ ] **Step 2: Implement the models and contracts**

Use immutable records/enums. Normalize handles with `Trim()` and reject blank values through `JudgeAccount.Create(JudgeId, string)` returning a typed `Result` or throwing `ArgumentException` only at the boundary.

- [ ] **Step 3: Run Core tests**

Run: `dotnet test windows/tests/OjNexus.Windows.Core.Tests/OjNexus.Windows.Core.Tests.csproj`

Expected: all domain tests pass without network access.

- [ ] **Step 4: Commit**

```text
git add windows/src/OjNexus.Windows.Core windows/tests/OjNexus.Windows.Core.Tests
git commit -m "feat: define Windows sync domain contracts"
```

### Task 3: Implement the bounded SQLite store and migrations

**Files:**
- Modify: `windows/src/OjNexus.Windows.Core/OjNexus.Windows.Core.csproj`
- Create: `windows/src/OjNexus.Windows.Core/Storage/WindowsPaths.cs`
- Create: `windows/src/OjNexus.Windows.Core/Storage/SqliteConnectionFactory.cs`
- Create: `windows/src/OjNexus.Windows.Core/Storage/SqliteSyncStore.cs`
- Create: `windows/src/OjNexus.Windows.Core/Storage/SchemaMigrator.cs`
- Create: `windows/tests/OjNexus.Windows.Core.Tests/SqliteSyncStoreTests.cs`

**Interfaces:**
- `WindowsPaths.GetDataDirectory(string? rootOverride = null)` returns `%LOCALAPPDATA%\OJ-NEXUS` by default and allows tests to inject a temp directory.
- `SqliteSyncStore` implements `ISyncStore` and keeps the newest 20 completed operations per judge while retaining active operations.
- Tables are `schema_metadata`, `accounts`, `sync_operations`, and `sync_modules`; module rows cascade on operation deletion and are uniquely keyed by `(operation_id, stage)`.

- [ ] **Step 1: Add SQLite dependency and write failing store tests**

Add `Microsoft.Data.Sqlite` version `8.0.8`. Test opening a temp database, inserting an account, opening an operation, appending two modules, closing it, reading it back, deterministic ordering, and retention of 20 completed rows plus all active rows.

- [ ] **Step 2: Implement schema version 1**

Create tables with explicit SQL migrations. Store typed status and failure category strings, not exception messages or response bodies. Use parameterized commands for every value.

- [ ] **Step 3: Implement retention and cancellation-safe writes**

Use a transaction for each module append and operation close. Prune only completed rows after close, ordered by `started_at DESC, id DESC` per judge.

- [ ] **Step 4: Run store tests**

Run: `dotnet test windows/tests/OjNexus.Windows.Core.Tests/OjNexus.Windows.Core.Tests.csproj --filter FullyQualifiedName~SqliteSyncStoreTests`

Expected: migration, CRUD, cascade, ordering, and retention tests pass.

- [ ] **Step 5: Commit**

```text
git add windows/src/OjNexus.Windows.Core windows/tests/OjNexus.Windows.Core.Tests
git commit -m "feat: add bounded Windows SQLite sync store"
```

### Task 4: Implement sync orchestration and typed reports

**Files:**
- Create: `windows/src/OjNexus.Windows.Core/Sync/SyncService.cs`
- Create: `windows/src/OjNexus.Windows.Core/Sync/SyncReportProjector.cs`
- Create: `windows/src/OjNexus.Windows.Core/Sync/InMemorySyncStore.cs`
- Create: `windows/tests/OjNexus.Windows.Core.Tests/SyncServiceTests.cs`

**Interfaces:**
- `SyncService(IReadOnlyDictionary<JudgeId, IJudgeAdapter> adapters, ISyncStore store, IClock clock, Func<string> dataGeneration)`.
- `Task<SyncReport> RunAsync(JudgeAccount account, bool force, CancellationToken cancellationToken)`.
- `SyncReportProjector.ToJson(SyncReport report)` emits stable camelCase JSON with operation status, judge, module counts, and error type.

- [ ] **Step 1: Write failing service tests**

Cover success, partial module failure, adapter exception mapped to `Network`, cancellation mapped to `Cancelled`, missing adapter mapped to `UnsupportedJudge`, duplicate concurrent calls for one judge/account, and no fabricated module results when the adapter fails before returning.

- [ ] **Step 2: Implement the operation lifecycle**

Reject disabled accounts unless `force` is true, open one operation, call exactly one adapter, append returned modules in order, close with the mapped typed status, and preserve the operation in the store on every terminal path. Use a per-account semaphore for duplicate safety.

- [ ] **Step 3: Add deterministic JSON projection**

Serialize only public fields and typed categories. Never serialize exception text, stack traces, HTTP bodies, or credentials.

- [ ] **Step 4: Run service tests**

Run: `dotnet test windows/tests/OjNexus.Windows.Core.Tests/OjNexus.Windows.Core.Tests.csproj --filter FullyQualifiedName~SyncServiceTests`

Expected: all lifecycle and concurrency tests pass offline.

- [ ] **Step 5: Commit**

```text
git add windows/src/OjNexus.Windows.Core windows/tests/OjNexus.Windows.Core.Tests
git commit -m "feat: orchestrate Windows sync operations"
```

### Task 5: Build the CLI command contract

**Files:**
- Modify: `windows/src/OjNexus.Windows.Cli/Program.cs`
- Create: `windows/src/OjNexus.Windows.Cli/CliParser.cs`
- Create: `windows/src/OjNexus.Windows.Cli/CliExitCode.cs`
- Create: `windows/src/OjNexus.Windows.Cli/ConsoleRenderer.cs`
- Create: `windows/src/OjNexus.Windows.Cli/Bootstrap.cs`
- Create: `windows/tests/OjNexus.Windows.Cli.Tests/CliParserTests.cs`
- Create: `windows/tests/OjNexus.Windows.Cli.Tests/ConsoleRendererTests.cs`

**Interfaces:**
- `CliParser.Parse(string[] args)` returns a typed `CliCommand` for `status`, `sync`, `history`, or `config show`.
- `CliExitCode`: `Success=0`, `Partial=1`, `InvalidArguments=2`, `Unavailable=3`, `Cancelled=4`.
- `ConsoleRenderer.RenderHuman(...)` uses uppercase telemetry output; `RenderJson(...)` delegates to Core projections.

- [ ] **Step 1: Write failing parser and renderer tests**

Test required `--judge` for sync, positive `--limit`, unknown command rejection, `--json`, redacted `config show`, and exact exit code mapping for success/partial/invalid/unavailable/cancelled.

- [ ] **Step 2: Implement the parser without a third-party command package**

Support `status [--json]`, `sync --judge <judge> [--handle <handle>] [--force] [--json]`, `history [--judge <judge>] [--limit <n>] [--json]`, and `config show [--json]`. Reject unknown flags with an actionable error and exit code 2.

- [ ] **Step 3: Wire commands to Core**

Construct `WindowsPaths`, `SqliteSyncStore`, the adapter registry, and `SyncService` in `Bootstrap`. `status` and `history` must work without network; `sync` must return code 3 for unavailable network.

- [ ] **Step 4: Run CLI tests and a real local smoke command**

Run: `dotnet test windows/tests/OjNexus.Windows.Cli.Tests/OjNexus.Windows.Cli.Tests.csproj`

Then run: `dotnet run --project windows/src/OjNexus.Windows.Cli -- status --json`

Expected: tests pass and the command returns valid JSON with exit code 0, even with an empty local store.

- [ ] **Step 5: Commit**

```text
git add windows/src/OjNexus.Windows.Cli windows/tests/OjNexus.Windows.Cli.Tests
git commit -m "feat: add Windows OJ NEXUS CLI contract"
```

### Task 6: Add the Codeforces public adapter

**Files:**
- Modify: `windows/src/OjNexus.Windows.Core/OjNexus.Windows.Core.csproj`
- Create: `windows/src/OjNexus.Windows.Core/Adapters/Codeforces/CodeforcesAdapter.cs`
- Create: `windows/src/OjNexus.Windows.Core/Adapters/Codeforces/CodeforcesDtos.cs`
- Create: `windows/src/OjNexus.Windows.Core/Adapters/HttpPublicClient.cs`
- Create: `windows/tests/OjNexus.Windows.Core.Tests/CodeforcesAdapterTests.cs`

**Interfaces:**
- `CodeforcesAdapter(HttpClient client)` implements `IJudgeAdapter` with `JudgeId.Codeforces`.
- Public API calls are `user.info`, `user.rating`, and `user.status`; responses map to Core module outcomes without exposing DTOs.

- [ ] **Step 1: Write deterministic HTTP handler tests**

Use a custom `HttpMessageHandler` returning local JSON fixtures for successful profile/rating/submission responses, HTTP failure, malformed JSON, and cancellation. Assert request URIs contain the normalized public handle and never contain credentials.

- [ ] **Step 2: Implement DTOs and adapter mapping**

Use `System.Text.Json` with explicit property names. Map each endpoint to a module with attempted/imported/updated counts and typed failures. Treat non-OK Codeforces API status as `Api` and transport failure as `Network`.

- [ ] **Step 3: Integrate the adapter registry**

Register Codeforces in the CLI/Desktop bootstrap while leaving AtCoder and Luogu extension points explicit and unregistered until their adapters are implemented.

- [ ] **Step 4: Run adapter tests**

Run: `dotnet test windows/tests/OjNexus.Windows.Core.Tests/OjNexus.Windows.Core.Tests.csproj --filter FullyQualifiedName~CodeforcesAdapterTests`

Expected: all tests pass without contacting Codeforces.

- [ ] **Step 5: Commit**

```text
git add windows/src/OjNexus.Windows.Core windows/tests/OjNexus.Windows.Core.Tests
git commit -m "feat: add Codeforces public Windows adapter"
```

### Task 7: Build the native WPF desktop shell

**Files:**
- Create: `windows/src/OjNexus.Windows.Desktop/App.xaml`
- Create: `windows/src/OjNexus.Windows.Desktop/App.xaml.cs`
- Create: `windows/src/OjNexus.Windows.Desktop/MainWindow.xaml`
- Create: `windows/src/OjNexus.Windows.Desktop/MainWindow.xaml.cs`
- Create: `windows/src/OjNexus.Windows.Desktop/Theme/Colors.xaml`
- Create: `windows/src/OjNexus.Windows.Desktop/Theme/Styles.xaml`
- Create: `windows/src/OjNexus.Windows.Desktop/ViewModels/MainViewModel.cs`
- Create: `windows/src/OjNexus.Windows.Desktop/ViewModels/DashboardViewModel.cs`
- Create: `windows/src/OjNexus.Windows.Desktop/ViewModels/ConnectorsViewModel.cs`
- Create: `windows/src/OjNexus.Windows.Desktop/ViewModels/SyncHistoryViewModel.cs`
- Create: `windows/src/OjNexus.Windows.Desktop/Views/DashboardView.xaml`
- Create: `windows/src/OjNexus.Windows.Desktop/Views/ConnectorsView.xaml`
- Create: `windows/src/OjNexus.Windows.Desktop/Views/SyncHistoryView.xaml`
- Create: `windows/tests/OjNexus.Windows.Core.Tests/DesktopViewModelTests.cs`

**Interfaces:**
- `MainViewModel` exposes `CurrentPage`, `IsBusy`, `StatusText`, and navigation commands.
- `DashboardViewModel` exposes local freshness, connected judge count, and latest operation signal.
- `ConnectorsViewModel` exposes accounts, adapter availability, current stage, and `SyncCommand`.
- `SyncHistoryViewModel` exposes at most five recent rows per selected judge and a typed retry command.

- [ ] **Step 1: Write view-model tests**

Test offline empty state, loading-to-success, partial/error/offline state labels, duplicate sync suppression, cancellation, and enlarged text-safe data projection.

- [ ] **Step 2: Implement the view models against Core**

Use `INotifyPropertyChanged`, `ICommand`, and `CancellationTokenSource`. Keep all I/O in Core services; view models only translate reports into display state.

- [ ] **Step 3: Implement the WPF shell and resources**

Use a navigation rail and three views: `DASHBOARD`, `CONNECTORS`, and `SYNC HISTORY`. Define all colors, spacing, typography, and control styles in resource dictionaries. Use one NEXUS BLUE accent, dark background, restrained 4–12px radii, and text-bearing status indicators.

- [ ] **Step 4: Run Desktop build and view-model tests**

Run: `dotnet test windows/tests/OjNexus.Windows.Core.Tests/OjNexus.Windows.Core.Tests.csproj --filter FullyQualifiedName~DesktopViewModelTests`

Then run: `dotnet build windows/src/OjNexus.Windows.Desktop/OjNexus.Windows.Desktop.csproj --configuration Release`

Expected: tests pass and WPF produces a Release executable.

- [ ] **Step 5: Commit**

```text
git add windows/src/OjNexus.Windows.Desktop windows/tests/OjNexus.Windows.Core.Tests
git commit -m "feat: add native Windows OJ NEXUS shell"
```

### Task 8: Add Windows CI, packaging, and smoke verification

**Files:**
- Create: `.github/workflows/windows.yml`
- Create: `windows/scripts/smoke.ps1`
- Create: `windows/README.md`
- Modify: `README.md`
- Modify: `docs/ROADMAP.md`
- Create: `docs/releases/windows-v0.1.0.md`

**Interfaces:**
- CI runs `dotnet restore`, `dotnet test windows/OjNexus.Windows.sln`, and `dotnet build windows/OjNexus.Windows.sln --configuration Release` on `windows-latest`.
- `smoke.ps1` runs the built CLI `status --json`, checks exit code 0 and parseable JSON, then launches the WPF executable with a bounded timeout and terminates only that test process.

- [ ] **Step 1: Write the smoke script assertions**

The script must fail if the CLI binary is missing, JSON cannot parse, the exit code is non-zero, or the desktop process fails to start within 15 seconds. It must print the actual paths and exit code.

- [ ] **Step 2: Implement the Windows workflow**

Use `actions/checkout@v4`, `actions/setup-dotnet@v4` with `dotnet-version: 8.0.x`, `actions/cache@v4` for NuGet packages, and `actions/upload-artifact@v4` for Release binaries and test reports. Do not add secrets or real OJ endpoint calls.

- [ ] **Step 3: Add packaging and docs**

Publish framework-dependent CLI/Desktop binaries to `windows/artifacts/` in CI. Document commands, data path, public-data boundary, and exact local verification output. Keep Android release history intact.

- [ ] **Step 4: Run the complete Windows gate**

Run:

```powershell
dotnet test windows/OjNexus.Windows.sln
dotnet build windows/OjNexus.Windows.sln --configuration Release
powershell -ExecutionPolicy Bypass -File windows/scripts/smoke.ps1 -Configuration Release
```

Expected: all tests pass, Release binaries exist, CLI status returns valid JSON with exit code 0, and the desktop process launches successfully.

- [ ] **Step 5: Commit**

```text
git add .github/workflows/windows.yml windows README.md docs/ROADMAP.md docs/releases/windows-v0.1.0.md
git commit -m "release: publish Windows client and CLI v0.1.0"
```

## Final acceptance audit

- [ ] `windows/OjNexus.Windows.sln` contains Core, CLI, Desktop, and both test projects.
- [ ] The store is under `%LOCALAPPDATA%\OJ-NEXUS`, bounded, migrated, and independent from Android Room.
- [ ] CLI `status`, `sync`, `history`, and `config show` work with documented exit codes and JSON output.
- [ ] Codeforces public sync is real, tested with local HTTP fixtures, and never requires credentials.
- [ ] WPF Dashboard, Connectors, and Sync History views render local/offline/error/success states.
- [ ] CI runs tests, Release build, and the smoke script on Windows.
- [ ] `dotnet test`, Release build, smoke script, and actual CLI/desktop launch output are recorded in the Windows release note.
