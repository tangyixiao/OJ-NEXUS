using OjNexus.Windows.Cli;
using OjNexus.Windows.Core.Domain;

namespace OjNexus.Windows.Cli.Tests;

/// <summary>
/// The exit-code numbers are a cross-platform contract shared with the Linux client: the same
/// number must mean the same thing on both, and the Linux enum is renumbered to match. These
/// assertions exist so an accidental renumbering fails here instead of in a user's script.
/// </summary>
public sealed class CliExitCodeContractTests
{
    [Fact]
    public void ExitCodes_MatchTheSharedCrossPlatformContract()
    {
        Assert.Equal(0, (int)CliExitCode.Success);
        Assert.Equal(1, (int)CliExitCode.Partial);
        Assert.Equal(2, (int)CliExitCode.InvalidArguments);
        Assert.Equal(3, (int)CliExitCode.Unavailable);
        Assert.Equal(4, (int)CliExitCode.Authentication);
        Assert.Equal(5, (int)CliExitCode.Cancelled);
        Assert.Equal(6, (int)CliExitCode.Storage);
        Assert.Equal(7, (int)CliExitCode.GeneralError);
    }

    [Fact]
    public void ExitCodes_AreDistinctAndOrderedFromSuccessToFailure()
    {
        var values = Enum.GetValues<CliExitCode>().Select(code => (int)code).ToArray();

        Assert.Equal(values.Length, values.Distinct().Count());
        Assert.Equal(values.OrderBy(value => value).ToArray(), values);
        Assert.Equal(0, values[0]);
    }

    [Fact]
    public void CancellationAndAuthentication_DoNotShareACode()
    {
        // Before the unification Windows reused Partial for authentication limits, which made an
        // authentication boundary indistinguishable from a mixed result.
        Assert.NotEqual((int)CliExitCode.Cancelled, (int)CliExitCode.Authentication);
        Assert.NotEqual((int)CliExitCode.Partial, (int)CliExitCode.Authentication);
        Assert.NotEqual((int)CliExitCode.Unavailable, (int)CliExitCode.Authentication);
    }

    [Fact]
    public void PartialRunWhoseOnlyFailureIsAuthentication_ReportsAuthentication()
    {
        // The real Luogu uid:2 shape: profile, contests and problemset succeed, submissions is
        // refused anonymously. The Linux client reports 4 for this run; Windows must agree.
        var report = ReportWithModules(
            SyncOperationStatus.Partial,
            Module("PROFILE", SyncOperationStatus.Success, null),
            Module("SUBMISSIONS", SyncOperationStatus.Error, nameof(SyncError.Authentication)),
            Module("CONTESTS", SyncOperationStatus.Success, null),
            Module("PROBLEMSET", SyncOperationStatus.Success, null));

        Assert.Equal((int)CliExitCode.Authentication, (int)CliExitCodeMapper.FromReport(report));
    }

    [Fact]
    public void PartialRunTakesTheCategoryOfItsFirstModuleFailure()
    {
        // Same aggregation rule as the Linux client: the first module that did not succeed
        // decides the category, so an unreachable endpoint before the refused one reports 3.
        var networkFirst = ReportWithModules(
            SyncOperationStatus.Partial,
            Module("PROFILE", SyncOperationStatus.Success, null),
            Module("RATING", SyncOperationStatus.Error, nameof(SyncError.Network)),
            Module("SUBMISSIONS", SyncOperationStatus.Error, nameof(SyncError.Authentication)));

        Assert.Equal((int)CliExitCode.Unavailable, (int)CliExitCodeMapper.FromReport(networkFirst));
    }

    [Fact]
    public void PartialRunWithAnUnclassifiedModuleFailure_StaysPartial()
    {
        var report = ReportWithModules(
            SyncOperationStatus.Partial,
            Module("PROFILE", SyncOperationStatus.Success, null),
            Module("SUBMISSIONS", SyncOperationStatus.Error, nameof(SyncError.Api)));

        Assert.Equal((int)CliExitCode.Partial, (int)CliExitCodeMapper.FromReport(report));
    }

    [Fact]
    public void PartialRunWithNoTypedModuleFailure_StaysPartial()
    {
        var report = ReportWithModules(
            SyncOperationStatus.Partial,
            Module("PROFILE", SyncOperationStatus.Success, null),
            Module("SUBMISSIONS", SyncOperationStatus.Error, null));

        Assert.Equal((int)CliExitCode.Partial, (int)CliExitCodeMapper.FromReport(report));
    }

    private static SyncModuleOutcome Module(
        string stage,
        SyncOperationStatus status,
        string? failureType) => new(stage, status, 1, status == SyncOperationStatus.Success ? 1 : 0, 0, failureType);

    private static SyncReport ReportWithModules(
        SyncOperationStatus status,
        params SyncModuleOutcome[] modules) => new(
        new SyncOperation(
            7,
            new JudgeAccount(JudgeId.Codeforces, "tourist"),
            "test",
            DateTimeOffset.Parse("2026-09-21T00:00:00+00:00"),
            DateTimeOffset.Parse("2026-09-21T00:01:00+00:00"),
            status,
            modules),
        status,
        null);
}
