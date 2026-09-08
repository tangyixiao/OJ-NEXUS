using OjNexus.Windows.Core.Domain;

namespace OjNexus.Windows.Core.Sync;

internal static class ModuleFailureType
{
    public static SyncModuleOutcome Normalize(SyncModuleOutcome outcome)
    {
        ArgumentNullException.ThrowIfNull(outcome);

        return outcome with
        {
            FailureType = outcome.Status == SyncOperationStatus.Success
                ? null
                : Normalize(outcome.FailureType),
        };
    }

    private static string Normalize(string? failureType) => failureType switch
    {
        nameof(SyncError.UnsupportedJudge) => nameof(SyncError.UnsupportedJudge),
        nameof(SyncError.Network) => nameof(SyncError.Network),
        nameof(SyncError.Api) => nameof(SyncError.Api),
        nameof(SyncError.Cancelled) => nameof(SyncError.Cancelled),
        nameof(SyncError.Offline) => nameof(SyncError.Offline),
        nameof(SyncError.InvalidConfiguration) => nameof(SyncError.InvalidConfiguration),
        _ => nameof(SyncError.Api),
    };
}
