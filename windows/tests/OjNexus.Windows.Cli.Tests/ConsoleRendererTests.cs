using System.Text.Json;
using OjNexus.Windows.Cli;
using OjNexus.Windows.Core.Domain;

namespace OjNexus.Windows.Cli.Tests;

public sealed class ConsoleRendererTests
{
    [Fact]
    public void RenderConfigJson_ContainsOnlyPublicConfiguration()
    {
        var output = ConsoleRenderer.RenderJson(new CliConfig(
            @"C:\Users\example\AppData\Local\OJ-NEXUS",
            @"C:\Users\example\AppData\Local\OJ-NEXUS\ojnexus.db",
            [new JudgeAccount(JudgeId.Codeforces, "tourist")]));

        using var document = JsonDocument.Parse(output);
        Assert.Equal(@"C:\Users\example\AppData\Local\OJ-NEXUS", document.RootElement.GetProperty("dataDirectory").GetString());
        Assert.Equal("Codeforces", document.RootElement.GetProperty("accounts")[0].GetProperty("judge").GetString());
        Assert.DoesNotContain("password", output, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("token", output, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("cookie", output, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("session", output, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("rawHttp", output, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void RenderHuman_UsesUppercaseTelemetryStyle()
    {
        var output = ConsoleRenderer.RenderHuman(new CliStatus(2, null));

        Assert.Equal(string.Join(Environment.NewLine, "STATUS: READY", "ACCOUNTS: 2", "LAST SYNC: NONE"), output);
    }

    [Fact]
    public void RenderAtCoderPayloadJson_UsesStablePublicFields()
    {
        var output = ConsoleRenderer.RenderJson(new AtCoderPayloadSnapshot(
            [new AtCoderSubmission(11, 100, "abc100_a", "abc100", "C++", 100, 10, "AC", 1)]));

        using var document = JsonDocument.Parse(output);
        Assert.Equal("AtCoder", document.RootElement.GetProperty("judge").GetString());
        Assert.Equal("abc100_a", document.RootElement.GetProperty("submissions")[0].GetProperty("problemId").GetString());
        Assert.DoesNotContain("password", output, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("cookie", output, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("rawHttp", output, StringComparison.OrdinalIgnoreCase);
    }

    [Theory]
    [InlineData(SyncOperationStatus.Success, null, 0)]
    [InlineData(SyncOperationStatus.Partial, null, 1)]
    [InlineData(SyncOperationStatus.Error, SyncError.InvalidConfiguration, 2)]
    [InlineData(SyncOperationStatus.Offline, SyncError.Offline, 3)]
    [InlineData(SyncOperationStatus.Cancelled, SyncError.Cancelled, 4)]
    public void FromReport_MapsEveryDocumentedExitCategory(
        SyncOperationStatus status,
        SyncError? error,
        int expected)
    {
        var report = new SyncReport(CreateOperation(status), status, error);

        Assert.Equal(expected, (int)CliExitCodeMapper.FromReport(report));
    }

    private static SyncOperation CreateOperation(SyncOperationStatus status) => new(
        7,
        new JudgeAccount(JudgeId.Codeforces, "tourist"),
        "test",
        DateTimeOffset.Parse("2026-09-08T00:00:00+00:00"),
        DateTimeOffset.Parse("2026-09-08T00:01:00+00:00"),
        status,
        []);
}
