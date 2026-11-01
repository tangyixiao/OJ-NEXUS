# Phase 128A final fix report

Date: 2026-10-24. Branch: `codex/phase-128-storage-safety`. Starting HEAD: `59583d9662b826928f502b4937ff591fcb945f53` (the reviewed base named by `final-fix-brief.md`). This report and the scoped fix are consolidated into one commit; its exact SHA is the final branch HEAD recorded by the completion message.

## Scope and changes

- P2 readiness barrier: added `windows/scripts/storage-acceptance-readiness.ps1` and use it for the damaged-store heading/code and Busy heading/code. The native route now waits for the required final name with a bounded timeout, reports expected and actual values, and keeps the subsequent enabled/text assertions. No fixed delay or product runtime switch was added.
- P2 regression: `windows/tests/StorageAcceptanceReadiness.Tests.ps1` exercises an initially `CHECKING` value that transitions to `INTEGRITY_FAILURE`, plus a wrong final code that must time out with both expected and actual values.
- P3 cleanup diagnostics: `WindowsStorageTests.CleanupChildrenAsync` reports each owned helper PID, final exit status, stop confirmation, stdout/stderr excerpts (maximum 4096 characters each), and cleanup errors to xUnit output. It still attempts graceful release, then kills only the owned child, and records stop failures without throwing over the original assertion.
- No product code, schema, dependency, platform, screenshot behavior, or unrelated UIA behavior was changed. Existing untracked `TestResults` directories were left unstaged.

## Verification

Acceptance parser and focused P2 regression passed. The scoped Core native-storage tests passed 22/22. The full Windows Release solution build passed with zero warnings and zero errors.

The actual Release native acceptance reached and passed both relevant damaged-store routes: CLI returned exit 6 with `INTEGRITY_FAILURE` and preserved the empty database's SHA-256; WPF displayed `LOCAL STORAGE UNAVAILABLE` / `INTEGRITY_FAILURE`, kept the Retry control enabled, hid normal navigation, captured the blocked screen, exited cleanly, and preserved the same original bytes. The harness then stopped at the later Busy Retry lookup (`RetryStorageOpen` absent from the UIA tree). This is the separate previously disclosed UIA issue, preserved as a failed run; it is not attributed to the readiness fix and was not modified here.

The native manifest records source HEAD `59583d9662b826928f502b4937ff591fcb945f53` and these Release artifact SHA-256 values:

| Artifact | SHA-256 |
| --- | --- |
| `ojnexus.exe` | `83F2072D688AA4C6AE5C7E30D134F244875FD8BA1FB4A9886EFF5DF287CFD157` |
| `ojnexus.dll` | `FADD3CD6ED04831B8FAD6AA5CE62B9E8473A0AC82532B02833530D08BC124ACE` |
| `OjNexus.Windows.Core.dll` | `51C04203BDAA6612EA24CFBC365F36B7FA3B3F617C849F0C687520E4F28F97FC` |
| `OjNexus.Windows.Desktop.exe` | `CD2B329C73951E454D795471D04A34B6771B6A9AB76961DDCF439760FD0BE5EF` |
| `OjNexus.Windows.Desktop.dll` | `263BF83F6154690D872B95F037A85DFA8191537631CF142687DC2D5119909839` |
| `OjNexus.Windows.StorageTestHost.dll` | `24613DDAA51C58D43D7E9999D2BA44D405CBC6FA45A887EE7C104C583EDDB9E9` |

Unique TRX: `C:\Users\tangy\AppData\Local\Temp\ojnexus-phase128a-final-3cc0a7d227f54a52a290fb255681a7e1\phase128a-final-core-storage.trx`. It contains the xUnit cleanup diagnostics. Full command stdout was retained at the evidence paths below and is reproduced verbatim here.

### PowerShell parser and readiness regression

Command: parse `windows/scripts/storage-acceptance.ps1`, then run `pwsh -NoProfile -File windows/tests/StorageAcceptanceReadiness.Tests.ps1`.

```text
Acceptance parser: PASS
Storage acceptance readiness regression: PASS (CHECKING -> INTEGRITY_FAILURE after 3 reads; wrong code rejected with actual value)
```

Retained stdout: `C:\Users\tangy\AppData\Local\Temp\ojnexus-phase128a-final-readiness-f64ec80e013248e0a28f0d8196c58762\readiness.stdout.txt`.

### Core native storage tests

Command: `dotnet test windows/tests/OjNexus.Windows.Core.Tests/OjNexus.Windows.Core.Tests.csproj --filter FullyQualifiedName~WindowsStorageTests --logger "trx;LogFileName=phase128a-final-core-storage.trx" --results-directory C:\Users\tangy\AppData\Local\Temp\ojnexus-phase128a-final-3cc0a7d227f54a52a290fb255681a7e1`.

```text
  正在确定要还原的项目…
  已还原 C:\Users\tangy\.codex\worktrees\phase-123-review-clock\AndroidAppCoding\windows\src\OjNexus.Windows.Core\OjNexus.Windows.Core.csproj (用时 2.13 秒)。
  已还原 C:\Users\tangy\.codex\worktrees\phase-123-review-clock\AndroidAppCoding\windows\tests\OjNexus.Windows.StorageTestHost\OjNexus.Windows.StorageTestHost.csproj (用时 2.13 秒)。
  已还原 C:\Users\tangy\.codex\worktrees\phase-123-review-clock\AndroidAppCoding\windows\tests\OjNexus.Windows.Core.Tests\OjNexus.Windows.Core.Tests.csproj (用时 2.33 秒)。
  OjNexus.Windows.Core -> C:\Users\tangy\.codex\worktrees\phase-123-review-clock\AndroidAppCoding\windows\src\OjNexus.Windows.Core\bin\Debug\net8.0\OjNexus.Windows.Core.dll
  OjNexus.Windows.StorageTestHost -> C:\Users\tangy\.codex\worktrees\phase-123-review-clock\AndroidAppCoding\windows\tests\OjNexus.Windows.StorageTestHost\bin\Debug\net8.0\OjNexus.Windows.StorageTestHost.dll
  OjNexus.Windows.Core.Tests -> C:\Users\tangy\.codex\worktrees\phase-123-review-clock\AndroidAppCoding\windows\tests\OjNexus.Windows.Core.Tests\bin\Debug\net8.0\OjNexus.Windows.Core.Tests.dll
C:\Users\tangy\.codex\worktrees\phase-123-review-clock\AndroidAppCoding\windows\tests\OjNexus.Windows.Core.Tests\bin\Debug\net8.0\OjNexus.Windows.Core.Tests.dll (.NETCoreApp,Version=v8.0)的测试运行
总共 1 个测试文件与指定模式相匹配。
结果文件: C:\Users\tangy\AppData\Local\Temp\ojnexus-phase128a-final-3cc0a7d227f54a52a290fb255681a7e1\phase128a-final-core-storage.trx

已通过! - 失败:     0，通过:    22，已跳过:     0，总计:    22，持续时间: 3 s - OjNexus.Windows.Core.Tests.dll (net8.0)
```

### Release build

Command: `dotnet build windows/OjNexus.Windows.sln --configuration Release --warnaserror`.

```text
  正在确定要还原的项目…
  已还原 C:\Users\tangy\.codex\worktrees\phase-123-review-clock\AndroidAppCoding\windows\src\OjNexus.Windows.Desktop\OjNexus.Windows.Desktop.csproj (用时 618 毫秒)。
  已还原 C:\Users\tangy\.codex\worktrees\phase-123-review-clock\AndroidAppCoding\windows\src\OjNexus.Windows.Cli\OjNexus.Windows.Cli.csproj (用时 618 毫秒)。
  已还原 C:\Users\tangy\.codex\worktrees\phase-123-review-clock\AndroidAppCoding\windows\tests\OjNexus.Windows.Desktop.Tests\OjNexus.Windows.Desktop.Tests.csproj (用时 665 毫秒)。
  已还原 C:\Users\tangy\.codex\worktrees\phase-123-review-clock\AndroidAppCoding\windows\tests\OjNexus.Windows.Cli.Tests\OjNexus.Windows.Cli.Tests.csproj (用时 674 毫秒)。
  3 个项目(共 7 个)是最新的，无法还原。
  OjNexus.Windows.Core -> C:\Users\tangy\.codex\worktrees\phase-123-review-clock\AndroidAppCoding\windows\src\OjNexus.Windows.Core\bin\Release\net8.0\OjNexus.Windows.Core.dll
  OjNexus.Windows.StorageTestHost -> C:\Users\tangy\.codex\worktrees\phase-123-review-clock\AndroidAppCoding\windows\tests\OjNexus.Windows.StorageTestHost\bin\Release\net8.0\OjNexus.Windows.StorageTestHost.dll
  OjNexus.Windows.Cli -> C:\Users\tangy\.codex\worktrees\phase-123-review-clock\AndroidAppCoding\windows\src\OjNexus.Windows.Cli\bin\Release\net8.0\ojnexus.dll
  OjNexus.Windows.Desktop -> C:\Users\tangy\.codex\worktrees\phase-123-review-clock\AndroidAppCoding\windows\src\OjNexus.Windows.Desktop\bin\Release\net8.0-windows\OjNexus.Windows.Desktop.dll
  OjNexus.Windows.Cli.Tests -> C:\Users\tangy\.codex\worktrees\phase-123-review-clock\AndroidAppCoding\windows\tests\OjNexus.Windows.Cli.Tests\bin\Release\net8.0\OjNexus.Windows.Cli.Tests.dll
  OjNexus.Windows.Core.Tests -> C:\Users\tangy\.codex\worktrees\phase-123-review-clock\AndroidAppCoding\windows\tests\OjNexus.Windows.Core.Tests\bin\Release\net8.0\OjNexus.Windows.Core.Tests.dll
  OjNexus.Windows.Desktop.Tests -> C:\Users\tangy\.codex\worktrees\phase-123-review-clock\AndroidAppCoding\windows\tests\OjNexus.Windows.Desktop.Tests\bin\Release\net8.0-windows\OjNexus.Windows.Desktop.Tests.dll

已成功生成。
    0 个警告
    0 个错误

已用时间 00:00:11.18
```

Retained stdout: `C:\Users\tangy\AppData\Local\Temp\ojnexus-phase128a-final-build-81e18bbd01274eeaaeef40a0b8e32ffd\release-build.stdout.txt`.

### Actual Release native acceptance

Command: `pwsh -NoProfile -ExecutionPolicy Bypass -File windows/scripts/storage-acceptance.ps1 -Configuration Release`.

The retained manifest is `C:\Users\tangy\AppData\Local\Temp\ojnexus-storage-acceptance-057d8e43e3bd4a228c44b3d9c4651e21\manifest.json`; native process evidence and screenshot are in its `evidenceDirectory`. Its `cli-zero` and `wpf-blocked-exit` records are PASS with the original zero-byte database hash unchanged. The overall harness record is FAIL because its later Busy scenario could not find `RetryStorageOpen` in the UIA tree.

```text
Exception: C:\Users\tangy\.codex\worktrees\phase-123-review-clock\AndroidAppCoding\windows\scripts\storage-acceptance.ps1:160
Line |
 160 |      throw "AutomationId did not appear: $id; diagnostic: $diagnosticP …
     |      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     | AutomationId did not appear: RetryStorageOpen; diagnostic:
     | C:\Users\tangy\AppData\Local\Temp\ojnexus-storage-acceptance-057d8e43e3bd4a228c44b3d9c4651e21\ui-automation-diagnostics.txt; UIA:
```

Retained stdout: `C:\Users\tangy\AppData\Local\Temp\ojnexus-phase128a-final-native-30cebc12620748a0b7c4b4af93d5c7a0.stdout.txt`. This failed run remains intact and is not relabelled as a full acceptance pass.
