using OjNexus.Windows.Core.Domain;

namespace OjNexus.Windows.Cli;

/// <summary>
/// Process exit codes shared with the Linux client. The numeric values are a cross-platform
/// contract — the same number means the same thing on every OJ NEXUS CLI — so renumbering is a
/// breaking change for scripts and CI expectations.
///
/// A platform only emits the categories it can actually produce. <see cref="Storage"/> is declared
/// for contract completeness even though the Windows client has no storage failure category today.
/// </summary>
public enum CliExitCode
{
    /// <summary>Every requested module succeeded.</summary>
    Success = 0,

    /// <summary>Mixed outcome, or a failure with no more specific category.</summary>
    Partial = 1,

    /// <summary>Usage or argument error; nothing was attempted.</summary>
    InvalidArguments = 2,

    /// <summary>Offline or network failure, or a resource this client cannot obtain.</summary>
    Unavailable = 3,

    /// <summary>Public access was refused (anonymous authentication limit).</summary>
    Authentication = 4,

    /// <summary>The user cancelled the run.</summary>
    Cancelled = 5,

    /// <summary>Local storage failure. Linux only; declared here to keep the numbering shared.</summary>
    Storage = 6,

    /// <summary>Unexpected internal error.</summary>
    GeneralError = 7,
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

        // A run-level error wins. When a run is only partial the whole-run error is null, so the
        // category comes from the first module that did not succeed — the same rule the Linux
        // client applies when it collapses a partial run into one typed error. Without this, the
        // identical Luogu run reported Authentication (4) on Linux and Partial (1) here.
        var category = report.Error ?? FirstModuleFailure(report);

        // Unreachable wins over refused: a report that never reached the judge cannot carry a
        // typed refusal, and the useful advice for automation is "retry later".
        if (report.Status == SyncOperationStatus.Offline || category is SyncError.Network or SyncError.Offline or SyncError.UnsupportedJudge)
        {
            return CliExitCode.Unavailable;
        }

        // Anonymous access being refused is a capability boundary of the public-data contract,
        // not a client fault, so it must not collapse into Partial.
        if (category == SyncError.Authentication)
        {
            return CliExitCode.Authentication;
        }

        return report.Status == SyncOperationStatus.Success ? CliExitCode.Success : CliExitCode.Partial;
    }

    private static SyncError? FirstModuleFailure(SyncReport report)
    {
        foreach (var module in report.Operation.Modules)
        {
            if (module.Status == SyncOperationStatus.Success)
            {
                continue;
            }

            return string.IsNullOrEmpty(module.FailureType)
                ? SyncError.Api
                : Enum.TryParse<SyncError>(module.FailureType, out var typed) ? typed : SyncError.Api;
        }

        return null;
    }
}
