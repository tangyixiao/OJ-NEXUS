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
