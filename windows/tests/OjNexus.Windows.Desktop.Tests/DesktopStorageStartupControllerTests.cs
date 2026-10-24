using OjNexus.Windows.Core.Storage;
using OjNexus.Windows.Desktop;

namespace OjNexus.Windows.Desktop.Tests;

public sealed class DesktopStorageStartupControllerTests
{
    [Fact]
    public async Task FailedOpenCanRetryAfterSourceBecomesReady()
    {
        var root = NewRoot();
        Directory.CreateDirectory(root);
        var database = Path.Combine(root, "ojnexus.db");
        await File.WriteAllBytesAsync(database, []);
        var controller = new DesktopStorageStartupController(root);

        var blocked = await controller.OpenOrRetryAsync();
        Assert.Equal(DesktopStorageStartupState.Blocked, blocked.State);
        Assert.Equal("INTEGRITY_FAILURE", blocked.FailureCode);
        Assert.Null(blocked.Session);

        File.Delete(database);
        var ready = await controller.OpenOrRetryAsync();
        Assert.Equal(DesktopStorageStartupState.Ready, ready.State);
        Assert.NotNull(ready.Session);
        Assert.Equal(DesktopStorageStartupState.Ready, controller.State);

        await controller.CloseAsync();
        Assert.Equal(DesktopStorageStartupState.Closed, controller.State);
    }

    [Fact]
    public async Task CloseWaitsForAdmittedSessionOperationToDrain()
    {
        var root = NewRoot();
        var controller = new DesktopStorageStartupController(root);
        var opened = await controller.OpenOrRetryAsync();
        Assert.NotNull(opened.Session);
        var operation = opened.Session.BeginOperation();

        var close = controller.CloseAsync();
        Assert.False(close.IsCompleted);
        operation.Dispose();
        await close.WaitAsync(TimeSpan.FromSeconds(5));
        Assert.Equal(DesktopStorageStartupState.Closed, controller.State);

        var reopened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.True(reopened.IsReady, reopened.Failure?.WireCode);
        await reopened.Session!.DisposeAsync();
    }

    [Fact]
    public async Task CloseDisposesSessionReturnedAfterClosingStarted()
    {
        var root = NewRoot();
        var entered = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var release = new TaskCompletionSource<StorageOpenResult>(TaskCreationOptions.RunContinuationsAsynchronously);
        var controller = new DesktopStorageStartupController(root, async (_, _) =>
        {
            entered.TrySetResult();
            return await release.Task;
        });

        var attempt = controller.OpenOrRetryAsync();
        await entered.Task.WaitAsync(TimeSpan.FromSeconds(5));
        var close = controller.CloseAsync();
        Assert.Equal(DesktopStorageStartupState.Closing, controller.State);
        var late = await WindowsStorage.OpenAsync(NewRoot(), null, CancellationToken.None);
        Assert.True(late.IsReady, late.Failure?.WireCode);
        release.TrySetResult(new StorageOpenResult(late.Session, null));

        var attemptResult = await attempt.WaitAsync(TimeSpan.FromSeconds(5));
        Assert.Null(attemptResult.Session);
        await close.WaitAsync(TimeSpan.FromSeconds(5));
        Assert.Equal(DesktopStorageStartupState.Closed, controller.State);

        var sameRootCanBeOpenedAgain = await WindowsStorage.OpenAsync(late.Session!.DataDirectory, null, CancellationToken.None);
        Assert.True(sameRootCanBeOpenedAgain.IsReady, sameRootCanBeOpenedAgain.Failure?.WireCode);
        await sameRootCanBeOpenedAgain.Session!.DisposeAsync();
    }

    [Fact]
    public async Task CloseCallbackFailureKeepsControllerClosingAndRetainsSession()
    {
        var root = NewRoot();
        var controller = new DesktopStorageStartupController(root);
        var opened = await controller.OpenOrRetryAsync();
        Assert.NotNull(opened.Session);
        var operation = opened.Session.BeginOperation();
        using var callback = operation.CancellationToken.Register(
            static () => throw new InvalidOperationException("private callback failure"));

        var close = controller.CloseAsync();
        Assert.False(close.IsCompleted);
        operation.Dispose();

        await Assert.ThrowsAnyAsync<Exception>(async () => await close.WaitAsync(TimeSpan.FromSeconds(5)));
        Assert.Equal(DesktopStorageStartupState.Closing, controller.State);
        Assert.True(controller.HasOwnedSession);
    }

    private static string NewRoot() => Path.Combine(Path.GetTempPath(), "ojnexus-desktop-startup-" + Guid.NewGuid().ToString("N"));
}
