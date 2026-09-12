using System.Text.Json;
using OjNexus.Windows.Cli;

namespace OjNexus.Windows.Cli.Tests;

public sealed class ProgramTests : IDisposable
{
    private readonly string _dataDirectory;

    public ProgramTests()
    {
        _dataDirectory = Path.Combine(Path.GetTempPath(), "ojnexus-cli-tests", Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(_dataDirectory);
    }

    public void Dispose()
    {
        try
        {
            Directory.Delete(_dataDirectory, recursive: true);
        }
        catch (IOException)
        {
        }
    }

    [Fact]
    public async Task RunAsync_StatusJson_ReportsReadySchemaAndSuccessExit()
    {
        var (exitCode, output, error) = await RunAsync(["status", "--json"]);

        Assert.Equal(0, exitCode);
        Assert.Equal(string.Empty, error.ToString());
        using var document = JsonDocument.Parse(output.ToString());
        Assert.Equal("ready", document.RootElement.GetProperty("status").GetString());
        Assert.Equal(0, document.RootElement.GetProperty("accountCount").GetInt32());
        Assert.Equal(JsonValueKind.Null, document.RootElement.GetProperty("lastSync").ValueKind);
        Assert.True(File.Exists(System.IO.Path.Combine(_dataDirectory, "ojnexus.db")));
    }

    [Fact]
    public async Task RunAsync_HistoryJson_ReportsEmptyOperationsAndSuccessExit()
    {
        var (exitCode, output, error) = await RunAsync(["history", "--json"]);

        Assert.Equal(0, exitCode);
        Assert.Equal(string.Empty, error.ToString());
        using var document = JsonDocument.Parse(output.ToString());
        Assert.Empty(document.RootElement.GetProperty("operations").EnumerateArray());
    }

    [Fact]
    public async Task RunAsync_SyncWithoutAvailableAdapter_ReportsUnavailableAndTypedError()
    {
        var output = new StringWriter();
        var error = new StringWriter();

        var exitCode = await Program.RunAsync(
            ["sync", "--judge", "codeforces", "--handle", "tourist", "--json"],
            output,
            error,
            CancellationToken.None,
            _dataDirectory,
            new Dictionary<OjNexus.Windows.Core.Domain.JudgeId, OjNexus.Windows.Core.Contracts.IJudgeAdapter>());

        Assert.Equal((int)CliExitCode.Unavailable, exitCode);
        Assert.Equal(string.Empty, error.ToString());
        using var document = JsonDocument.Parse(output.ToString());
        Assert.Contains("error", document.RootElement.GetProperty("status").GetString(), StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public async Task RunAsync_CancelledSync_ReportsCancelledExitCode()
    {
        using var cancellationSource = new CancellationTokenSource();
        cancellationSource.Cancel();

        var exitCode = await Program.RunAsync(
            ["sync", "--judge", "codeforces", "--handle", "tourist", "--json"],
            new StringWriter(),
            new StringWriter(),
            cancellationSource.Token);

        Assert.Equal((int)CliExitCode.Cancelled, exitCode);
    }

    [Fact]
    public async Task RunAsync_StorageFailure_SanitizesErrorWithoutStackTrace()
    {
        File.WriteAllBytes(Path.Combine(_dataDirectory, "ojnexus.db"), [0x00, 0x01, 0x02]);

        var (exitCode, output, error) = await RunAsync(["status", "--json"]);

        Assert.Equal((int)CliExitCode.GeneralError, exitCode);
        Assert.Equal(string.Empty, output.ToString());
        Assert.StartsWith("CLI ERROR:", error.ToString(), StringComparison.Ordinal);
        Assert.DoesNotContain("at OjNexus", error.ToString(), StringComparison.Ordinal);
        Assert.DoesNotContain("Exception", error.ToString(), StringComparison.Ordinal);
    }

    [Fact]
    public async Task RunAsync_UnparsableCommand_WritesArgumentErrorWithoutStackTrace()
    {
        var error = new StringWriter();

        var exitCode = await Program.RunAsync(["sync", "--judge"], new StringWriter(), error, CancellationToken.None);

        Assert.Equal((int)CliExitCode.InvalidArguments, exitCode);
        var errorText = error.ToString();
        Assert.StartsWith("ARGUMENT ERROR:", errorText, StringComparison.Ordinal);
        Assert.DoesNotContain("at OjNexus", errorText, StringComparison.Ordinal);
    }

    private async Task<(int ExitCode, StringWriter Output, StringWriter Error)> RunAsync(string[] args)
    {
        var output = new StringWriter();
        var error = new StringWriter();
        var exitCode = await Program.RunAsync(args, output, error, CancellationToken.None, _dataDirectory);
        return (exitCode, output, error);
    }
}
