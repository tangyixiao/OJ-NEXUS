# OJ NEXUS Windows Client and CLI Design

## Decision

OJ NEXUS will add a native Windows desktop client and a Windows command-line tool as a separate
`.NET 8` solution under `windows/`. The existing Android `app/` module remains the mobile product
and is not converted into a multiplatform module in this milestone.

The desktop client will use WPF because it is a native Windows UI stack, can be built from the
installed .NET SDK without requiring a separate Windows App SDK toolchain, and supports the existing
dark, uppercase, telemetry-style visual language. The CLI and desktop client will call the same
in-process Core library; the desktop client will not start the CLI as a child process.

## Goals

- Provide a buildable native Windows desktop shell for OJ NEXUS.
- Provide a scriptable CLI for local status, public-data synchronization, and operation history.
- Keep desktop and CLI behavior aligned through shared models and sync services.
- Preserve the local-first and public-data-only boundary: no passwords, cookies, or main-site
  sessions.
- Establish an adapter boundary so Codeforces, AtCoder, and Luogu remain isolated from shared
  Windows UI and command handling.
- Make the first vertical slice independently testable and runnable before adding feature parity.

## Non-goals for the first vertical slice

- Directly opening or migrating the Android Room database.
- Reimplementing every Android screen or WorkManager behavior.
- Automatic submission, main-site login, cookie/session import, or credential scraping.
- Cloud synchronization or cross-device state replication.
- Electron, WebView, Flutter, or React Native shells.

The long-term target remains Windows parity for the useful local-first OJ command-center features.
The first slice establishes the native shell, shared local ledger, CLI contract, and one complete
public sync path; additional judge adapters and screens extend the same boundaries.

## Repository layout

```text
windows/
  OjNexus.Windows.sln
  src/
    OjNexus.Windows.Core/
    OjNexus.Windows.Cli/
    OjNexus.Windows.Desktop/
  tests/
    OjNexus.Windows.Core.Tests/
    OjNexus.Windows.Cli.Tests/
```

`OjNexus.Windows.Core` targets `net8.0` and contains portable domain, storage, sync orchestration,
adapter contracts, typed errors, and output projections. `OjNexus.Windows.Cli` targets `net8.0` and
uses a small in-repository parser for the four initial commands, avoiding a command-line package
until the contract needs one. `OjNexus.Windows.Desktop` targets `net8.0-windows`, enables WPF, and
references Core directly. Tests target `net8.0` and do not require a network connection.

## Core boundaries

The Core library exposes these stable concepts:

- `JudgeId`, `JudgeAccount`, and judge-agnostic problem/submission summary models.
- `SyncOperation`, `SyncModuleOutcome`, and `SyncOperationStatus` for the local ledger.
- `IJudgeAdapter` for judge-specific public API requests.
- `ISyncStore` for local configuration, accounts, and bounded operation history.
- `SyncService` for cancellation-aware orchestration and deterministic module recording.
- `SyncError` and `CliExitCode` for typed failures without leaking raw secrets or stack traces into
  normal user output.

Every adapter receives a normalized public handle and a cancellation token. Adapter DTOs do not
cross into Desktop or CLI projects. Sync results are persisted as typed metadata and counts; raw
HTTP bodies and raw exception messages are not persisted.

## Local data and configuration

Windows data is stored below `%LOCALAPPDATA%\OJ-NEXUS\`:

- `ojnexus.db` — SQLite local state and bounded sync history.
- `config.json` — non-secret preferences and selected account handles.
- `logs/` — opt-in diagnostic logs with redaction applied before writing.

The first slice accepts public handles only. `config show` and status output must never print fields
that could become credentials. Database schema changes use explicit migrations and a temporary
backup before replacement. Android data is not touched.

## CLI contract

The initial executable is `ojnexus` with these commands:

- `ojnexus status [--json]` — local accounts, last sync status, and data freshness.
- `ojnexus sync --judge <judge> [--handle <handle>] [--force] [--json]` — user-triggered public
  sync with typed module results.
- `ojnexus history [--judge <judge>] [--limit <n>] [--json]` — bounded operation history.
- `ojnexus config show [--json]` — redacted local configuration.

Human output uses the NEXUS uppercase telemetry style. `--json` emits a documented stable shape for
automation. Exit code 0 means success, 1 means partial or recoverable sync failure, 2 means invalid
arguments/configuration, and 3 means unavailable network or adapter data. A cancelled operation
returns a distinct non-zero code and remains visible in history.

## Desktop experience

The WPF shell uses a navigation rail and three initial views:

- `DASHBOARD` — local freshness, connected judge count, and the latest operation signal.
- `CONNECTORS` — public handles, adapter availability, sync action, and current operation stage.
- `SYNC HISTORY` — bounded operations with module outcomes, typed status, and retry affordances.

The UI is dark-first, uppercase, single-accent NEXUS BLUE, and uses restrained radii and hairline
dividers. It must remain usable offline. Sync runs are cancellable, duplicate-safe, and visibly
transition through loading, success, partial, error, and offline states. No looping or decorative
animation is needed; reduced-motion behavior is the default.

## Data flow

```text
CLI command or WPF action
          |
          v
      SyncService ---- cancellation / typed errors
          |
          +---- IJudgeAdapter (Codeforces, then AtCoder/Luogu)
          |
          +---- ISyncStore ---- SQLite ledger and local projections
          |
          +---- SyncReport ---- CLI output or WPF State
```

The first complete vertical slice will implement the existing Codeforces public adapter end to end.
AtCoder and Luogu adapters will then be added behind the same `IJudgeAdapter` boundary without
changing the CLI contract or WPF screens. No fake remote results are allowed; offline and
unavailable states are explicit.

## Testing and acceptance

- Core unit tests cover adapter isolation, deterministic operation ordering, bounded history,
  cancellation, typed error mapping, and database migrations.
- CLI tests cover parsing, redacted config output, JSON stability, exit codes, and no-network
  offline behavior.
- Desktop tests cover view-model state transitions and smoke-render the three initial views at a
  narrow window width and enlarged text scale.
- `dotnet test windows/OjNexus.Windows.sln` must pass without network access.
- `dotnet build windows/OjNexus.Windows.sln --configuration Release` must produce the CLI and WPF
  binaries.
- A local smoke check must run `ojnexus status` and launch the desktop executable, recording the
  actual output and exit code.

## Risks and mitigations

- Android and Windows data models can drift. Keep the Windows store independent first, and share
  only explicitly portable contracts after tests prove compatibility.
- Public OJ endpoints can be unavailable. Adapter errors stay typed and the UI/CLI retain local
  history without fabricating results.
- WPF styling can drift from Android. Reuse the same named token vocabulary in a Windows resource
  dictionary and keep visual acceptance focused on hierarchy, spacing, and state semantics.
- A growing Core project can absorb UI concerns. Keep WPF references out of Core and enforce this
  with project-reference checks in CI.
