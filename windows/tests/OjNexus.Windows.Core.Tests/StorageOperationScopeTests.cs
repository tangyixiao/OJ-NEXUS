using OjNexus.Windows.Core.Domain;
using OjNexus.Windows.Core.Storage;
using OjNexus.Windows.Core.Sync;
using OjNexus.Windows.Core.Contracts;

namespace OjNexus.Windows.Core.Tests;

public sealed class StorageOperationScopeTests
{
    [Fact]
    public async Task AdmittedPermitCanRunNonCancellableCleanupAfterSessionStartsDraining()
    {
        var root = Path.Combine(Path.GetTempPath(), "ojnexus-drain-" + Guid.NewGuid().ToString("N"));
        var opened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.True(opened.IsReady, opened.Failure?.WireCode);
        var session = opened.Session!;
        var context = session.BeginOperation();
        var shutdown = session.DisposeAsync().AsTask();
        var ledger = await context.Store.GetRecentOperationsAsync(null, 0, CancellationToken.None);
        Assert.Empty(ledger);
        await context.DisposeAsync();
        await shutdown.WaitAsync(TimeSpan.FromSeconds(5));
    }

    [Fact]
    public async Task ClosedPermitWinsOverCancellationAndValidPermitDoesNotInheritItForNoneCalls()
    {
        var root = Path.Combine(Path.GetTempPath(), "ojnexus-permit-" + Guid.NewGuid().ToString("N"));
        var opened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.True(opened.IsReady, opened.Failure?.WireCode);
        await using var session = opened.Session!;
        using var caller = new CancellationTokenSource();
        var context = session.BeginOperation(caller.Token);
        caller.Cancel();
        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => context.Store.GetAccountsAsync(context.CancellationToken));
        var rows = await context.Store.GetAccountsAsync(CancellationToken.None);
        Assert.Empty(rows);
        var retained = context.Store;
        await context.DisposeAsync();
        var closed = await Assert.ThrowsAsync<StorageException>(() => retained.GetAccountsAsync(CancellationToken.None));
        Assert.Equal("SESSION_CLOSED", closed.Failure.WireCode);
    }

    [Fact]
    public async Task ScopeRejectsEarlyReturnAfterClosedContext()
    {
        await using var scope = new StorageOperationScope(new InMemorySyncStore());
        var context = scope.BeginOperation();
        var store = context.Store;
        await context.DisposeAsync();
        var error = await Assert.ThrowsAsync<StorageException>(() => store.GetRecentOperationsAsync(null, 0, CancellationToken.None));
        Assert.Equal("SESSION_CLOSED", error.Failure.WireCode);
    }

    [Fact]
    public async Task CancelledAdmittedSqlOperationCompletesDelayedLedgerCleanupBeforeUnlock()
    {
        var root = Path.Combine(Path.GetTempPath(), "ojnexus-ledger-drain-" + Guid.NewGuid().ToString("N"));
        var opened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.True(opened.IsReady, opened.Failure?.WireCode);
        var session = opened.Session!;
        var context = session.BeginOperation();
        var account = JudgeAccount.Create(JudgeId.Codeforces, "drain-user");
        await context.Store.UpsertAccountAsync(account, CancellationToken.None);
        var now = DateTimeOffset.Parse("2026-10-24T00:00:00Z");
        var id = await context.Store.OpenOperationAsync(account, "generation", now, CancellationToken.None);
        await context.Store.AppendModuleAsync(id, new SyncModuleOutcome("PROFILE", SyncOperationStatus.Success, 1, 1, 0, null), now, CancellationToken.None);

        var cleanupStarted = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var releaseCleanup = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var cleanup = Task.Run(async () =>
        {
            cleanupStarted.TrySetResult();
            await releaseCleanup.Task;
            await context.Store.CloseOperationAsync(id, SyncOperationStatus.Cancelled, SyncError.Cancelled, now, CancellationToken.None);
        });
        await cleanupStarted.Task.WaitAsync(TimeSpan.FromSeconds(5));
        using var callerCancellation = new CancellationTokenSource();
        var cancelledContext = session.BeginOperation(callerCancellation.Token);
        callerCancellation.Cancel();
        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => cancelledContext.Store.GetAccountsAsync(cancelledContext.CancellationToken));
        await cancelledContext.DisposeAsync();

        var shutdown = session.DisposeAsync().AsTask();
        Assert.Throws<StorageException>(() => session.BeginOperation());
        Assert.False(shutdown.IsCompleted);
        AssertBusy(Path.Combine(root, "ojnexus.lifecycle.lock"));
        releaseCleanup.TrySetResult();
        await cleanup.WaitAsync(TimeSpan.FromSeconds(5));
        await context.DisposeAsync();
        await shutdown.WaitAsync(TimeSpan.FromSeconds(5));

        var reopened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.True(reopened.IsReady, reopened.Failure?.WireCode);
        await using var next = reopened.Session!;
        var terminal = await next.RunAsync((store, _) => store.GetRecentOperationsAsync(null, 10, CancellationToken.None), CancellationToken.None);
        Assert.Single(terminal);
        Assert.Equal(SyncOperationStatus.Cancelled, terminal[0].Status);
    }

    [Fact]
    public async Task ConcurrentShutdownCachesCompletionAndContainsThrowingReentrantCancellationCallback()
    {
        var scope = new StorageOperationScope(new InMemorySyncStore());
        var context = scope.BeginOperation();
        var callbackSawClosed = false;
        using var registration = context.CancellationToken.Register(() =>
        {
            try
            {
                _ = scope.BeginOperation();
            }
            catch (StorageException exception) when (exception.Failure.WireCode == "SESSION_CLOSED")
            {
                callbackSawClosed = true;
            }
            throw new InvalidOperationException("synthetic callback fault");
        });

        var first = scope.DisposeAsync().AsTask();
        var second = scope.DisposeAsync().AsTask();
        Assert.Same(first, second);
        Assert.False(first.IsCompleted);
        var cleanup = await context.Store.GetRecentOperationsAsync(null, 0, CancellationToken.None);
        Assert.Empty(cleanup);
        await context.DisposeAsync();
        await Assert.ThrowsAnyAsync<Exception>(() => first.WaitAsync(TimeSpan.FromSeconds(5)));
        Assert.True(callbackSawClosed);
        await Assert.ThrowsAnyAsync<Exception>(() => scope.DisposeAsync().AsTask().WaitAsync(TimeSpan.FromSeconds(5)));
    }

    [Fact]
    public async Task AdmissionRacingShutdownEitherCompletesItsContextOrGetsClosed()
    {
        var scope = new StorageOperationScope(new InMemorySyncStore());
        var start = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var attempts = Enumerable.Range(0, 32).Select(async _ =>
        {
            await start.Task;
            try
            {
                var context = scope.BeginOperation();
                try { await context.Store.GetRecentOperationsAsync(null, 0, CancellationToken.None); }
                finally { await context.DisposeAsync(); }
            }
            catch (StorageException exception) when (exception.Failure.WireCode == "SESSION_CLOSED") { }
        }).ToArray();

        start.TrySetResult();
        var shutdown = scope.DisposeAsync().AsTask();
        await Task.WhenAll(attempts).WaitAsync(TimeSpan.FromSeconds(5));
        await shutdown.WaitAsync(TimeSpan.FromSeconds(5));
    }

    private static void AssertBusy(string path)
    {
        var failure = Assert.Throws<StorageException>(() => NativeFile.Acquire(path, exclusive: true));
        Assert.Equal("STORAGE_BUSY", failure.Failure.WireCode);
    }
}
