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
}
