# OJ NEXUS Windows Client

The Windows vertical slice contains a native WPF desktop client, a scriptable CLI, and one
local-first synchronization core. Both programs reference the Core project directly; the
desktop client does not launch the CLI as a child process.

Current source inventory (2026-10-24): WPF has `DASHBOARD`, `CONNECTORS`, `SYNC HISTORY`, and
`PROBLEMS`. Problem listing is also available through the CLI. Review scheduling and result
recording are currently **CLI + SQLite only** (`review due`, `review mark`); there is no WPF
review page yet. Notes, training sessions, portable backup exchange and learning analytics are
not part of this Windows slice. See the [platform capability ledger](../docs/verification/platform-capabilities.md)
for source and separate build/runtime/release evidence.

## Build and test

From the repository root on Windows:

```powershell
dotnet restore windows/OjNexus.Windows.sln --configfile windows/NuGet.Config --runtime win-x64
dotnet test windows/OjNexus.Windows.sln -c Release --no-restore
dotnet build windows/OjNexus.Windows.sln -c Release --no-restore
pwsh -File windows/scripts/smoke.ps1 -Configuration Release
pwsh -File windows/scripts/ui-smoke.ps1 -Configuration Release
pwsh -File windows/scripts/package.ps1 -Version 0.1.0 -Configuration Release -Runtime win-x64
```

The smoke check prints the exact CLI and desktop paths, runs `status --json`, validates
`status=ready`, verifies the JSON exit code is zero, and keeps the WPF process alive for up to
15 seconds. It uses a generated temporary data directory and removes only that directory when it
finishes. Normal application data remains below `%LOCALAPPDATA%\OJ-NEXUS\ojnexus.db`.

Changing a configured handle uses the judge-specific identity boundary. The account update and
removal of payload snapshots for the old handle happen in one local SQLite transaction; sync
history remains available as an audit trail, while the new handle starts without stale profile,
rating, or submission data.

The UI smoke check starts the real WPF executable with an isolated temporary data directory,
resizes it to the supported minimum `900x560`, and uses Windows UI Automation to visit
`DASHBOARD`, `CONNECTORS`, `SYNC HISTORY`, and `PROBLEMS`. It checks the public-handle editors,
history judge filter, and both problem filters. Screenshot capture is reported separately as
`SCREENSHOTS: 4/4`; a UI-only pass without screenshots does not establish visual acceptance.
Pass a fresh `-OutputDirectory` to preserve each run's four images separately. The smoke does
not exercise CLI review commands or contact an OJ service.

The additional `windows/scripts/storage-acceptance.ps1` harness checks actual blocked startup,
Retry/Exit, and CLI/WPF coexistence using retained isolated data directories. Its manifest and
screenshots are evidence from a particular execution, not a guarantee inferred from having the
script in the repository.

The Dashboard `SYNC ALL` action runs the configured and enabled connectors in order. Disabled or
unconfigured rows are skipped; a cancellation or failed connector leaves its typed operation in
history and reports a non-success batch result without starting an unsolicited retry.

## Commands

```powershell
ojnexus.exe status [--json]
ojnexus.exe sync --judge <codeforces|atcoder|luogu> [--handle <handle>] [--force] [--json]
ojnexus.exe history [--judge <judge>] [--limit <n>] [--json]
ojnexus.exe config show [--json]

ojnexus.exe problems list [--judge <judge>] [--status <status>] [--json]

ojnexus.exe review due [--judge <judge>] [--limit <n>] [--json]

ojnexus.exe review mark --judge <judge> --problem <external-id> --result <PASS|HARD|FAIL|SKIP> [--json]
```

## Exit codes

The numeric exit codes are a cross-platform contract shared with the Linux client, so a given
number means the same thing on both clients. Windows emits the categories it can produce:

| code | meaning |
| --- | --- |
| 0 | success — every requested module succeeded |
| 1 | partial — mixed outcome, or a failure with no more specific category |
| 2 | usage or argument error |
| 3 | unavailable — offline, network failure, or a resource this client cannot obtain |
| 4 | authentication — public access was refused (anonymous authentication limit) |
| 5 | cancelled — the user cancelled the run |
| 6 | storage — guarded startup or a typed local persistence failure |
| 7 | general error — unexpected internal failure |

A run-level error decides the category first; when a run is only partial, the first module that did
not succeed decides it (authentication → 4, network/offline/unsupported judge → 3, otherwise → 1).
Luogu `uid:2`, whose submissions stage is refused for anonymous callers, exits 4; Codeforces
`tourist` exits 0.

Renumbering these values is a breaking change for scripts and CI expectations. The remaining
sections describe the local behaviour behind them.

Only public handles are accepted. The Windows client never asks for or stores OJ passwords,
cookies, main-site sessions, raw HTTP bodies, source code, or custom input. Remote failures remain
typed and local sync history stays readable offline. The `OJ_NEXUS_DATA_DIRECTORY` environment
variable is a test/automation override; without it, the default local application directory is
used.

The Core account boundary trims and validates handles before request construction: Codeforces
allows letters, digits, `-`, `.`, and `_`; AtCoder allows letters, digits, `-`, and `_`; Luogu
allows a numeric UID or the equivalent `uid:<number>` form.
The SQLite and in-memory stores repeat this validation at their read/write boundaries; malformed
local account or operation rows are ignored rather than being projected into a sync request or
the desktop history view.

`data --judge luogu` reads a structured local summary containing the public profile and the
persisted counts for submissions, contests, and problems. An anonymous Luogu submissions request
may remain authentication-gated; the client records that typed failure and never fabricates a
submission count.

The history view shows at most five rows for the selected judge. Failed, partial, cancelled, and
offline operations expose an explicit full-sync `RETRY` action; successful operations do not.
When multiple connectors are syncing, each connector's `CANCEL` action only cancels that
connector; closing the desktop client cancels all active syncs.

Configured connectors can be disabled and re-enabled from the WPF connector view. Disabling
preserves the public handle and local history, blocks new sync requests, and does not silently
re-enable the account when its equivalent handle is saved again.

Each sync operation also retains the public handle that started it. After an account handle is
changed, an older operation remains visible in history under its original identity but is not
projected onto the new connector row and cannot be retried against the changed account. Existing
databases migrate this field transactionally as schema v5.

## Local storage startup and shutdown

CLI and WPF open local storage through the same guarded authority. Existing empty, corrupt,
unrelated, or unsupported-schema databases block startup and remain preserved. An established
workspace whose database is missing also blocks; it is not silently replaced with a fresh one.
A genuinely new workspace is initialized through a validated staging database.

With `--json`, a storage failure returns exit code 6 and exactly one response such as
`{"status":"blocked","error":"INTEGRITY_FAILURE"}`. Human output uses a short uppercase
code without private paths or SQL. The WPF blocked shell displays `LOCAL STORAGE UNAVAILABLE`,
`LOCAL FILES PRESERVED`, the same code, `RETRY OPEN`, and `EXIT`. Retry makes a fresh opening
attempt; it does not repair or delete the original files. Once ready, runtime storage failures
appear in the existing error state.

Ordinary local fixed/removable paths are supported. Network, device, URI, alternate-stream,
reparse-point and hard-linked controlled-file locations are rejected. The permanent
`ojnexus.lifecycle.lock` coordinates cooperating new CLI/WPF processes: normal sessions share
ownership, initialization/migration requires exclusive ownership, and shutdown waits for full
admitted operations including non-cancellable ledger cleanup before releasing ownership.
The lock file's age or existence does not establish that a process is alive. Older binaries
that do not use this protocol cannot be made cooperative by this update.

Phase 128A is startup protection and lifetime management. Consistent backup creation, restore
selection/installation, rollback recovery and persistent generation fencing remain later
128B–D work. An interrupted first initialization may leave `.initialized.tmp` / `ojnexus.init.db`
evidence and produce `RECOVERY_BLOCKED`; automatic recovery of that state is not yet available.
Keep all files intact. Native permission, full-disk and power-loss recovery are separate
acceptance requirements and are not implied by classifier tests.

## Release artifacts

The [Windows v0.1.2-rc.1 prerelease](https://github.com/tangyixiao/OJ-NEXUS/releases/tag/windows-v0.1.2-rc.1)
contains the Phase 128A startup/lifetime changes from `805ce8f`. It is an unsigned portable
candidate; [its verification record](../docs/verification/phase-128a-windows-storage-safety.md)
separates source, actual native checks, packaged assets and remote round trips. Extract into a
short local folder: a deeply nested staging path failed WPF native-library loading during QA.

The package command produces a self-contained `win-x64` directory and ZIP under
`windows/artifacts/self-contained/`:

```text
OJ-NEXUS-Windows-v0.1.0-win-x64/
  cli/ojnexus.exe
  desktop/OjNexus.Windows.Desktop.exe
  README.txt
  SHA256SUMS.txt
OJ-NEXUS-Windows-v0.1.0-win-x64.zip
```

The package carries its .NET runtime and does not require a separately installed .NET 8 runtime.
Run `windows/scripts/verify-package.ps1` with the package root to verify every SHA-256 entry.

Packaging removes the previous package and its own temporary work directory around a run. Those
deletions go through the .NET file APIs and the resulting filesystem state is verified, because
some hosts report a recursive `Remove-Item` as failed even when the directory is already gone,
and this script stops on errors. A path that genuinely could not be removed still fails packaging.

The ZIP is a portable self-contained package. A separate `build-installer.ps1` path produces
an Inno Setup installer; signing is conditional on certificate configuration. A generated
artifact is not itself evidence of trusted signing, clean-VM installation or Store acceptance.

## Signing and installers

`signtool.exe` and `makeappx.exe` ship with the Windows SDK under
`Windows Kits\10\bin\<version>\x64`. Signing a build therefore only needs a trusted code signing
certificate. A certificate generated in memory signs a binary successfully, but the resulting
chain terminates in an untrusted root: that is expected and it cannot remove the "unknown
publisher" prompt, so an internally generated certificate is only useful for exercising the
signing step. Trusted publisher distribution requires the corresponding trusted certificate;
unsigned or test-signed artifacts must be labeled with their actual status and channel limits.

The repository now includes the Inno Setup script, `build-installer.ps1`,
`verify-installer.ps1` and `sign-binaries.ps1`. The Windows CI installs Inno Setup and invokes
the installer build. Local execution still needs the matching toolchain. The presence of these
scripts or a past CI artifact must not be reported as a fresh install/uninstall test on a clean
VM. Installer signing and clean-VM verification remain independent release gates.
