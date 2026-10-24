using OjNexus.Windows.Core.Storage;
using OjNexus.Windows.Core.Sync;

namespace OjNexus.Windows.Core.Tests;

public sealed class SqliteConnectionLeaseTests
{
    [Fact]
    public async Task NativeLeaseOutlivesContextAndKeepsShutdownPendingUntilClosed()
    {
        var (root, lifecycle) = await CreateInitializedRoot();
        var owner = new OperationOwner(() => { lifecycle.Dispose(); return ValueTask.CompletedTask; });
        var factory = new SqliteConnectionFactory(Path.Combine(root, "ojnexus.db"), owner);
        var context = owner.Begin(permit => new SqliteSyncStore(factory, permit), CancellationToken.None);
        var lease = factory.OpenLease(context.Permit);
        await context.DisposeAsync();
        var shutdown = owner.DisposeAsync().AsTask();
        Assert.False(shutdown.IsCompleted);
        AssertBusy(Path.Combine(root, "ojnexus.lifecycle.lock"));
        lease.Dispose();
        await shutdown.WaitAsync(TimeSpan.FromSeconds(5));
        using var exclusive = NativeFile.Acquire(Path.Combine(root, "ojnexus.lifecycle.lock"), exclusive: true);
    }

    [Fact]
    public async Task FailedNativeCloseRetainsReservationUntilSuccessfulRetry()
    {
        var (root, lifecycle) = await CreateInitializedRoot();
        var failClose = true;
        var owner = new OperationOwner(() => { lifecycle.Dispose(); return ValueTask.CompletedTask; });
        var factory = new SqliteConnectionFactory(Path.Combine(root, "ojnexus.db"), owner, () =>
        {
            if (failClose) throw new IOException("injected close failure");
        });
        var context = owner.Begin(permit => new SqliteSyncStore(factory, permit), CancellationToken.None);
        var lease = factory.OpenLease(context.Permit);
        await context.DisposeAsync();
        Assert.Throws<IOException>(lease.Dispose);
        var shutdown = owner.DisposeAsync().AsTask();
        Assert.False(shutdown.IsCompleted);
        AssertBusy(Path.Combine(root, "ojnexus.lifecycle.lock"));
        failClose = false;
        lease.Dispose();
        await shutdown.WaitAsync(TimeSpan.FromSeconds(5));
    }

    [Fact]
    public async Task FailedOpenReleasesNativeReservationAndForeignPermitIsRejected()
    {
        var (root, lifecycle) = await CreateInitializedRoot();
        var owner = new OperationOwner(() => { lifecycle.Dispose(); return ValueTask.CompletedTask; });
        var factory = new SqliteConnectionFactory(Path.Combine(root, "absent.db"), owner);
        var context = owner.Begin(_ => new InMemorySyncStore(), CancellationToken.None);
        Assert.ThrowsAny<Exception>(() => factory.OpenLease(context.Permit));
        var secondOwner = new OperationOwner();
        var foreign = secondOwner.Begin(_ => new InMemorySyncStore(), CancellationToken.None);
        Assert.Throws<StorageException>(() => factory.OpenLease(foreign.Permit));
        await foreign.DisposeAsync();
        await context.DisposeAsync();
        await owner.DisposeAsync().AsTask().WaitAsync(TimeSpan.FromSeconds(5));
    }

    [Fact]
    public async Task OneOperationCanRunSimultaneousSqlCallsOnIndependentLeases()
    {
        var root = Path.Combine(Path.GetTempPath(), "ojnexus-parallel-sql-" + Guid.NewGuid().ToString("N"));
        var opened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.True(opened.IsReady, opened.Failure?.WireCode);
        await using var session = opened.Session!;
        var context = session.BeginOperation();

        var accounts = context.Store.GetAccountsAsync(CancellationToken.None);
        var operations = context.Store.GetRecentOperationsAsync(null, 10, CancellationToken.None);
        var problems = context.Store.GetProblemsAsync(null, null, 10, CancellationToken.None);
        var reviews = context.Store.GetDueReviewsAsync(null, 10, CancellationToken.None);
        await Task.WhenAll(accounts, operations, problems, reviews);

        Assert.Empty(await accounts);
        Assert.Empty(await operations);
        Assert.Empty(await problems);
        Assert.Empty(await reviews);
        await context.DisposeAsync();
    }

    private static async Task<(string Root, FileStream Lifecycle)> CreateInitializedRoot()
    {
        var root = Path.Combine(Path.GetTempPath(), "ojnexus-native-lease-" + Guid.NewGuid().ToString("N"));
        var opened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.True(opened.IsReady, opened.Failure?.WireCode);
        var session = opened.Session!;
        var lifecyclePath = Path.Combine(root, "ojnexus.lifecycle.lock");
        var held = NativeFile.Acquire(lifecyclePath, exclusive: false);
        await session.DisposeAsync();
        return (root, held);
    }

    private static void AssertBusy(string path)
    {
        var error = Assert.Throws<StorageException>(() => NativeFile.Acquire(path, exclusive: true));
        Assert.Equal("STORAGE_BUSY", error.Failure.WireCode);
    }
}
