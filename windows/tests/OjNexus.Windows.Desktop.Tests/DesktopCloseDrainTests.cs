using OjNexus.Windows.Desktop;

namespace OjNexus.Windows.Desktop.Tests;

public sealed class DesktopCloseDrainTests
{
    [Fact]
    public async Task TryRunAsyncContainsCancellationCallbackFailure()
    {
        var result = await DesktopCloseDrain.TryRunAsync(() =>
            Task.FromException(new AggregateException("private cancellation callback detail")));

        Assert.False(result.Succeeded);
        Assert.Equal("STORAGE_IO_FAILURE", result.FailureCode);
    }

    [Fact]
    public async Task TryRunAsyncContainsSessionDisposalFailure()
    {
        var result = await DesktopCloseDrain.TryRunAsync(() =>
            Task.FromException(new IOException("private disposal detail")));

        Assert.False(result.Succeeded);
        Assert.Equal("STORAGE_IO_FAILURE", result.FailureCode);
    }

    [Fact]
    public async Task CoordinatorCanRetryAfterSynchronousFirstAttemptFailure()
    {
        var coordinator = new DesktopCloseCoordinator();
        var attempts = 0;

        Task Drain()
        {
            attempts++;
            if (attempts == 1) throw new IOException("private synchronous cleanup detail");
            return Task.CompletedTask;
        }

        var firstAttempt = coordinator.StartOrJoinAttempt(Drain);
        var firstResult = await firstAttempt.WaitAsync(TimeSpan.FromSeconds(5));
        Assert.False(firstResult.Succeeded);
        Assert.Equal("STORAGE_IO_FAILURE", firstResult.FailureCode);
        Assert.Same(firstAttempt, coordinator.StartOrJoinAttempt(Drain));

        coordinator.ReleaseFailedAttempt(firstAttempt);
        var secondAttempt = coordinator.StartOrJoinAttempt(Drain);
        var secondResult = await secondAttempt.WaitAsync(TimeSpan.FromSeconds(5));

        Assert.NotSame(firstAttempt, secondAttempt);
        Assert.True(secondResult.Succeeded);
        Assert.Equal(2, attempts);
    }
}
