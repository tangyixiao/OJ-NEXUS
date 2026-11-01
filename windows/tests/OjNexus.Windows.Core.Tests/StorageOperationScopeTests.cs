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
        using var callerCancellation = new CancellationTokenSource();
        var account = JudgeAccount.Create(JudgeId.Codeforces, "drain-user");
        var now = DateTimeOffset.Parse("2026-10-24T00:00:00Z");
        var operationAdmitted = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var cleanupStarted = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var releaseCleanup = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var action = Task.Run(async () =>
        {
            await using var context = session.BeginOperation(callerCancellation.Token);
            await context.Store.UpsertAccountAsync(account, CancellationToken.None);
            var id = await context.Store.OpenOperationAsync(account, "generation", now, CancellationToken.None);
            await context.Store.AppendModuleAsync(id, new SyncModuleOutcome("PROFILE", SyncOperationStatus.Success, 1, 1, 0, null), now, CancellationToken.None);
            operationAdmitted.TrySetResult();
            var finalStatus = SyncOperationStatus.Success;
            SyncError? finalError = null;
            try
            {
                await Task.Delay(Timeout.Infinite, context.CancellationToken);
            }
            catch (OperationCanceledException exception) when (callerCancellation.IsCancellationRequested)
            {
                Assert.Equal(context.CancellationToken, exception.CancellationToken);
                finalStatus = SyncOperationStatus.Cancelled;
                finalError = SyncError.Cancelled;
                cleanupStarted.TrySetResult();
                await releaseCleanup.Task;
            }
            finally
            {
                await context.Store.CloseOperationAsync(id, finalStatus, finalError, now, CancellationToken.None);
            }
        });
        await operationAdmitted.Task.WaitAsync(TimeSpan.FromSeconds(5));
        callerCancellation.Cancel();
        await cleanupStarted.Task.WaitAsync(TimeSpan.FromSeconds(5));
        var shutdown = session.DisposeAsync().AsTask();
        Assert.Throws<StorageException>(() => session.BeginOperation());
        Assert.False(shutdown.IsCompleted);
        AssertBusy(Path.Combine(root, "ojnexus.lifecycle.lock"));
        releaseCleanup.TrySetResult();
        await action.WaitAsync(TimeSpan.FromSeconds(5));
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
        Task? callbackShutdown = null;
        using var registration = context.CancellationToken.Register(() =>
        {
            callbackShutdown = scope.DisposeAsync().AsTask();
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

        var start = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var firstRequest = Task.Run(async () =>
        {
            await start.Task;
            return scope.DisposeAsync().AsTask();
        });
        var secondRequest = Task.Run(async () =>
        {
            await start.Task;
            return scope.DisposeAsync().AsTask();
        });
        start.TrySetResult();
        var first = await firstRequest.WaitAsync(TimeSpan.FromSeconds(5));
        var second = await secondRequest.WaitAsync(TimeSpan.FromSeconds(5));
        Assert.Same(first, second);
        Assert.False(first.IsCompleted);
        var cleanup = await context.Store.GetRecentOperationsAsync(null, 0, CancellationToken.None);
        Assert.Empty(cleanup);
        await context.DisposeAsync();
        var callbackFailure = await Assert.ThrowsAsync<AggregateException>(() => first.WaitAsync(TimeSpan.FromSeconds(5)));
        var cancellationFailure = Assert.IsType<AggregateException>(Assert.Single(callbackFailure.InnerExceptions));
        Assert.Equal("synthetic callback fault", Assert.IsType<InvalidOperationException>(Assert.Single(cancellationFailure.InnerExceptions)).Message);
        Assert.True(callbackSawClosed);
        Assert.Same(first, callbackShutdown);
    }

    [Fact]
    public async Task ConcurrentShutdownWithoutActiveWorkSharesCompletedTask()
    {
        var scope = new StorageOperationScope(new InMemorySyncStore());
        var start = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var firstRequest = Task.Run(async () =>
        {
            await start.Task;
            return scope.DisposeAsync().AsTask();
        });
        var secondRequest = Task.Run(async () =>
        {
            await start.Task;
            return scope.DisposeAsync().AsTask();
        });
        start.TrySetResult();
        var first = await firstRequest.WaitAsync(TimeSpan.FromSeconds(5));
        var second = await secondRequest.WaitAsync(TimeSpan.FromSeconds(5));
        Assert.Same(first, second);
        await first.WaitAsync(TimeSpan.FromSeconds(5));
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

    [Fact]
    public async Task EveryClosedStoreEntryFailsBeforeArgumentsOrNoSqlReturnsAndNeverRevives()
    {
        var root = Path.Combine(Path.GetTempPath(), "ojnexus-closed-matrix-" + Guid.NewGuid().ToString("N"));
        var opened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.True(opened.IsReady, opened.Failure?.WireCode);
        var oldSession = opened.Session!;
        var context = oldSession.BeginOperation();
        var retained = context.Store;
        await context.DisposeAsync();
        await oldSession.DisposeAsync();

        var oldSessionClosed = Assert.Throws<StorageException>(() => oldSession.BeginOperation());
        Assert.Equal("SESSION_CLOSED", oldSessionClosed.Failure.WireCode);
        await AssertAllEntriesClosed(retained);

        var nextOpen = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.True(nextOpen.IsReady, nextOpen.Failure?.WireCode);
        await using var nextSession = nextOpen.Session!;
        oldSessionClosed = Assert.Throws<StorageException>(() => oldSession.BeginOperation());
        Assert.Equal("SESSION_CLOSED", oldSessionClosed.Failure.WireCode);
        await AssertAllEntriesClosed(retained);
        await AssertAllEntriesClosed(context.Store);
        Assert.Empty(await nextSession.RunAsync((store, _) => store.GetAccountsAsync(CancellationToken.None), CancellationToken.None));
    }

    private static async Task AssertAllEntriesClosed(ISyncStore store)
    {
        Func<Task>[] entries =
        [
            () => store.GetAccountsAsync(CancellationToken.None),
            () => store.UpsertAccountAsync(null!, CancellationToken.None),
            () => store.GetRecentOperationsAsync(null, 0, CancellationToken.None),
            () => store.GetProblemsAsync(null, null, 0, CancellationToken.None),
            () => store.GetDueReviewsAsync(null, 0, CancellationToken.None),
            () => store.MarkReviewAsync(JudgeId.Codeforces, "", default, DateTimeOffset.MinValue, CancellationToken.None),
            () => store.GetCodeforcesPayloadAsync("", CancellationToken.None),
            () => store.GetAtCoderPayloadAsync("", CancellationToken.None),
            () => store.GetLuoguPayloadAsync("", CancellationToken.None),
            () => store.OpenOperationAsync(null!, null!, DateTimeOffset.MinValue, CancellationToken.None),
            () => store.AppendModuleAsync(0, null!, DateTimeOffset.MinValue, CancellationToken.None),
            () => store.CloseOperationAsync(0, default, null, DateTimeOffset.MinValue, CancellationToken.None)
        ];

        foreach (var entry in entries)
        {
            var failure = await Assert.ThrowsAsync<StorageException>(async () => await entry());
            Assert.Equal("SESSION_CLOSED", failure.Failure.WireCode);
        }
    }

    private static void AssertBusy(string path)
    {
        var failure = Assert.Throws<StorageException>(() => NativeFile.Acquire(path, exclusive: true));
        Assert.Equal("STORAGE_BUSY", failure.Failure.WireCode);
    }
}
