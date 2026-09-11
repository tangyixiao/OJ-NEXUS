using OjNexus.Windows.Core.Domain;

namespace OjNexus.Windows.Cli;

public enum CliExitCode
{
    Success = 0,
    Partial = 1,
    InvalidArguments = 2,
    Unavailable = 3,
    Cancelled = 4,
    GeneralError = 5,
}

public static class CliExitCodeMapper
{
    public static CliExitCode FromReport(SyncReport report)
    {
        ArgumentNullException.ThrowIfNull(report);

        if (report.Status == SyncOperationStatus.Cancelled || report.Error == SyncError.Cancelled)
        {
            return CliExitCode.Cancelled;
        }

        if (report.Error == SyncError.InvalidConfiguration)
        {
            return CliExitCode.InvalidArguments;
        }

        if (report.Status == SyncOperationStatus.Offline || report.Error is SyncError.Network or SyncError.Offline or SyncError.UnsupportedJudge)
        {
            return CliExitCode.Unavailable;
        }

        return report.Status == SyncOperationStatus.Success ? CliExitCode.Success : CliExitCode.Partial;
    }
}
