# Phase 123-b — WPF Problems screen smoke

Date: 2026-10-23

Worktree: `C:\Users\tangy\.codex\worktrees\phase-123-review-clock\AndroidAppCoding`

Branch: `codex/phase-123-problems-acceptance`

Baseline: `8bca81b9c6d2fcd6c4d19637d9799c688404861d`

## Scope and result

The existing WPF Problems view was reachable but absent from `windows/scripts/ui-smoke.ps1`.
The smoke now navigates to the real `PROBLEMS` control, verifies `LOCAL PROBLEM LEDGER`,
checks the two visible judge/status filters and the isolated empty state `NO PROBLEMS RECORDED`,
and requires a fourth, distinct screenshot. The existing Dashboard, Connectors and Sync History
checks remain in place. The WPF application already supported an in-process Problems screenshot;
no product code or capture implementation change was needed.

The smoke uses a unique temporary data directory, restores the process-level
`OJ_NEXUS_DATA_DIRECTORY`, terminates and disposes the launched WPF process, and removes its
temporary database directory in `finally`. The UI run left no `OjNexus.Windows.Desktop` process.

## Fresh Windows gates

Environment: Windows x64; .NET SDK 10.0.401 with .NET 8 runtimes installed.

| Command | Exit | Result |
| --- | ---: | --- |
| `dotnet test windows/OjNexus.Windows.sln -c Release --no-restore --logger trx --results-directory %TEMP%\phase-123-problems-test-results` | 0 | 167 passed, 0 failed, 0 skipped: Core 81, CLI 65, Desktop 21; all three TRX counters independently inspected |
| `dotnet build windows/OjNexus.Windows.sln -c Release --no-restore --warnaserror` | 0 | Release solution build; 0 warnings, 0 errors |
| `windows/scripts/smoke.ps1 -Configuration Release` | 0 | isolated CLI `status --json` returned `ready`, exit 0; desktop stayed alive through its 15-second startup window |
| `windows/scripts/ui-smoke.ps1 -Configuration Release -OutputDirectory %TEMP%\phase-123-problems-ui-04b7dd51a8cd4e429813a62fdc563ab9` | 0 | Dashboard, Connectors lifecycle, Sync History and Problems passed; filters=2, empty-state passed, screenshots=4/4 |

The four actual WPF-rendered screenshots are under
`C:\Users\tangy\AppData\Local\Temp\phase-123-problems-ui-04b7dd51a8cd4e429813a62fdc563ab9`:

| View | Bytes | SHA-256 |
| --- | ---: | --- |
| Dashboard | 36,419 | `9690443E7F89A4084E2B31DF2942FCCB89C571A41B82E80A45D1D05AB5052D0C` |
| Connectors | 47,660 | `B1F6DA151BE46D31C2FEE09597D477D1B7178093BA15A8B112F558793E2788B8` |
| Sync History | 31,313 | `6BC643EEB09C85B5882FA43FE18DAB107DEB4E55BB772DC4885AED46E828A1B0` |
| Problems | 30,719 | `BD2C8360F3ACAA5E010DD6077D065FB80C0D997BF8D8A13F991F5C12DC6EE988` |

The smoke itself checked all four files exist and have distinct SHA-256 values. The Problems
capture shows the real page title, ledger label, both filters and empty-state text. The empty
state is expected because the smoke starts with a fresh local database and performs no sync.
The existing independent CLI multi-process persistence evidence from the coordinating lane
was not recreated here.

## Distribution limits

This is source-based Windows test/build and local WPF smoke evidence only. Clean-VM installation,
upgrade/uninstall behavior and binary signing remain unverified. No package or release artifact
was rebuilt or changed; the already-published unsigned RC remains the same artifact and this
follow-up makes no new release qualification claim. Remote CI for this follow-up was not run.
