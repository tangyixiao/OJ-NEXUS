using OjNexus.Windows.Core.Storage;

namespace OjNexus.Windows.Desktop;

internal sealed record DesktopCloseDrainResult(bool Succeeded, string? FailureCode);

internal static class DesktopCloseDrain
{
    public static async Task<DesktopCloseDrainResult> TryRunAsync(Func<Task> drain)
    {
        ArgumentNullException.ThrowIfNull(drain);
        try
        {
            await drain();
            return new(true, null);
        }
        catch (StorageException exception)
        {
            return new(false, exception.Failure.WireCode);
        }
        catch (Exception)
        {
            return new(false, "STORAGE_IO_FAILURE");
        }
    }
}

internal sealed class DesktopCloseCoordinator
{
    private readonly object _gate = new();
    private Task<DesktopCloseDrainResult>? _attempt;

    public Task<DesktopCloseDrainResult> StartOrJoinAttempt(Func<Task> drain)
    {
        ArgumentNullException.ThrowIfNull(drain);
        lock (_gate)
        {
            if (_attempt is not null) return _attempt;

            var completion = new TaskCompletionSource<DesktopCloseDrainResult>(TaskCreationOptions.RunContinuationsAsynchronously);
            _attempt = completion.Task;
            _ = CompleteAttemptAsync(drain, completion);
            return completion.Task;
        }
    }

    public void ReleaseFailedAttempt(Task<DesktopCloseDrainResult> attempt)
    {
        ArgumentNullException.ThrowIfNull(attempt);
        if (!attempt.IsCompleted) return;
        if (attempt.IsCompletedSuccessfully && attempt.Result.Succeeded) return;

        lock (_gate)
        {
            if (ReferenceEquals(_attempt, attempt)) _attempt = null;
        }
    }

    private static async Task CompleteAttemptAsync(
        Func<Task> drain,
        TaskCompletionSource<DesktopCloseDrainResult> completion)
    {
        completion.TrySetResult(await DesktopCloseDrain.TryRunAsync(drain));
    }
}
