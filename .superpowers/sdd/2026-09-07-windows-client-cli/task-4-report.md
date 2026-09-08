# Task 4 report: Windows sync orchestration and typed reports

## Scope

- Added `SyncService`, backed by one in-process adapter lookup and a per-judge/normalized-handle semaphore.
- Added `InMemorySyncStore`, a deterministic, thread-safe `ISyncStore` implementation for service tests and local callers.
- Added `SyncReportProjector` with a stable camelCase JSON boundary containing only operation ID/status, judge, module counts, report status, and the typed error category.
- Added offline service tests for success, partial results, adapter failures, cancellation, unsupported judges, disabled accounts, duplicate calls, and JSON redaction/stability.
- No Android file or generated `bin`/`obj` output was staged.

## Design rulings

- A disabled account still opens and closes exactly one operation with `Error` / `InvalidConfiguration`; `force` bypasses only the disabled-account rejection. This follows the Task 4 requirement to preserve every terminal operation while rejecting disabled accounts unless forced.
- A module list returned by an adapter is persisted in its supplied order. If the adapter fails before it returns a list, the operation closes with the typed `Network` error and has no fabricated modules.
- Cancellation affects the adapter call and sequencing, but operation persistence uses `CancellationToken.None` after opening. This guarantees an opened operation can be closed as `Cancelled` even when the caller cancels its token.
- `SyncModuleOutcome.FailureType` remains the existing Task 2 contract field for store persistence, but the JSON projector intentionally omits it because it is not a typed report-level error and must not become an exception/raw-HTTP disclosure path.

## TDD evidence

### RED

```powershell
& 'C:\Program Files\dotnet\dotnet.exe' test windows\tests\OjNexus.Windows.Core.Tests\OjNexus.Windows.Core.Tests.csproj --no-restore --filter FullyQualifiedName~SyncServiceTests
```

The first run failed at compile time because `OjNexus.Windows.Core.Sync` and `SyncService` did not exist:

```text
error CS0234: namespace "OjNexus.Windows.Core" does not contain namespace "Sync"
error CS0246: type or namespace name "SyncService" could not be found
```

### GREEN

The first implementation run identified a test-double error: `Task.FromCanceled` was supplied an active token, which produces an argument exception and correctly exercised the generic adapter-exception mapping. The cancelling double was changed to wait for a caller-owned cancellation token, and the service maps `OperationCanceledException` to `Cancelled` only when that caller token is actually cancelled.

The focused command then passed:

```text
Passed! - Failed: 0, Passed: 8, Skipped: 0, Total: 8
```

## Full Windows verification

```powershell
& 'C:\Program Files\dotnet\dotnet.exe' test windows\OjNexus.Windows.sln --no-restore
& 'C:\Program Files\dotnet\dotnet.exe' build windows\OjNexus.Windows.sln --no-restore
```

Results:

```text
Core tests: Failed: 0, Passed: 31, Skipped: 0, Total: 31
CLI tests: no tests discovered (existing project state)
Build: 0 warnings, 0 errors
```

## Safety review

- The service catches only cancellation and adapter failures, maps them to `SyncError` enums, and never records exception text.
- The projector omits account handles, data-generation strings, module failure text, exception details, raw HTTP data, and credentials.
- The service invokes at most one resolved adapter per allowed run and serializes only matching judge/account calls; different accounts do not share the semaphore.

## Fix round: Important review findings

### Decisions

- `SyncModuleOutcome.FailureType` remains source-compatible, but `SyncService` converts every non-success module result to a canonical `SyncError` enum name before storing or returning it in a report. Known enum names remain canonical; `null`, arbitrary adapter text, exception messages, HTTP bodies, and credentials all become the safe fallback `Api`. Successful modules always store `null`.
- Per-account gate acquisition now calls `SemaphoreSlim.WaitAsync(cancellationToken)`. Cancellation while queued propagates immediately before `RunExclusiveAsync`, so it cannot open an operation or invoke an adapter. An operation already opened still uses non-cancelled persistence calls so that it can close as `Cancelled`.
- `InMemorySyncStore` now has the same `(operationId, stage)` upsert semantics as `SqliteSyncStore`: a duplicate stage replaces the existing item in place, preserving first-insert order.

### RED/GREEN evidence

The new focused test group initially failed because a production SQLite readback exposed the raw adapter text:

```text
Expected: "Api"
Actual:   "authorization=Bearer credential-value; body=secret HTTP body"
```

Under the old gate implementation, the queued-cancellation regression also remained blocked behind the first request, leaving its test host running until it was explicitly terminated. After the three minimal changes, the focused command passed:

```powershell
& 'C:\Program Files\dotnet\dotnet.exe' test windows\tests\OjNexus.Windows.Core.Tests\OjNexus.Windows.Core.Tests.csproj --no-restore --filter "FullyQualifiedName~SyncServiceTests|FullyQualifiedName~SqliteSyncStoreTests"
```

```text
Passed! - Failed: 0, Passed: 25, Skipped: 0, Total: 25
```

The regression coverage includes a real temporary `SqliteSyncStore` run through `SyncService` with bearer/HTTP-body fixture text and verifies both the stored module value and projected JSON omit it; a deterministic second queued request cancellation with one active operation only; and production SQLite duplicate-stage replacement.

### Release verification after fixes

```powershell
& 'C:\Program Files\dotnet\dotnet.exe' test windows\OjNexus.Windows.sln --configuration Release --no-restore
& 'C:\Program Files\dotnet\dotnet.exe' build windows\OjNexus.Windows.sln --configuration Release --no-restore
```

```text
Core tests: Failed: 0, Passed: 34, Skipped: 0, Total: 34
CLI tests: no tests discovered (existing project state)
Release build: 0 warnings, 0 errors
```

## Fix round 2: failure-category and duplicate-stage hardening

### Decisions

- `ModuleFailureType` is the single explicit `switch` allowlist for module failure categories. It accepts only the named `SyncError` categories and maps every other value, including numeric enum-shaped text such as `401`, to `Api`. Successful module outcomes always have a `null` failure type.
- The service normalizes adapter outcomes before persistence and before constructing its report. Both `InMemorySyncStore` and `SqliteSyncStore` independently normalize direct `AppendModuleAsync` calls; SQLite also normalizes module values on read as a defense for legacy data.
- Duplicate module stages use one rule everywhere: remove the previous stage and append its replacement. Thus adapter output `A, B, A` has one latest `A` and ordered stages `B, A`. SQLite performs the delete and insert inside its existing append transaction, so `rowid` makes the ordering deterministic even when all completion timestamps are equal.
- Queued cancellation behavior is unchanged: gate acquisition still observes the caller token, so a queued cancelled request does not open an operation or invoke an adapter.

### TDD evidence

The new focused regressions failed against the prior implementation:

```text
Duplicate-stage report: expected [B, A], actual [A, B, A]
Duplicate-stage SQLite readback: expected [B, A], actual [A, B]
Direct SQLite write: sensitive raw text and 401 were read back instead of Api
```

After the minimal normalization and remove/reappend changes, the focused command passed:

```powershell
& 'C:\Program Files\dotnet\dotnet.exe' test windows\tests\OjNexus.Windows.Core.Tests\OjNexus.Windows.Core.Tests.csproj --no-restore --filter "FullyQualifiedName~SyncServiceTests|FullyQualifiedName~SqliteSyncStoreTests"
```

```text
Passed: 27, Failed: 0, Skipped: 0
```

Coverage now includes a production SQLite direct-write test with both a bearer/body fixture and `401`, asserting persisted and read module values are only `Api`; and an A,B,A regression that compares the service report, `InMemorySyncStore`, and real SQLite order and latest A contents.

### Release verification

```powershell
& 'C:\Program Files\dotnet\dotnet.exe' test windows\OjNexus.Windows.sln --configuration Release --no-restore
& 'C:\Program Files\dotnet\dotnet.exe' build windows\OjNexus.Windows.sln --configuration Release --no-restore
```

```text
Core tests: Passed: 36, Failed: 0, Skipped: 0
CLI tests: no tests discovered (existing project state)
Release build: 0 warnings, 0 errors
```
