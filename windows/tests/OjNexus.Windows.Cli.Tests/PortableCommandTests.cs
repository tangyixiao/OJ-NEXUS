using System.Text.Json;
using OjNexus.Windows.Cli;
using OjNexus.Windows.Core.Storage;

namespace OjNexus.Windows.Cli.Tests;

/// <summary>
/// Contract tests for the `portable` verbs. A rejected package is a valid
/// outcome (exit 0); only transport/storage failures and internal errors
/// carry non-zero exits. Confirm-no-write is asserted through a follow-up
/// export returning ARCHIVE EMPTY.
/// </summary>
public sealed class PortableCommandTests : IDisposable
{
    private const string PackageJson = """
        {"formatVersion":1,"scopeId":"s1","sourcePlatform":"android","generator":{"name":"scope-export","version":"1.0"},"createdAtEpochMs":1,"counts":{"problems":1,"attempts":0,"notes":1,"reviews":0,"sessions":0},"problems":[{"stableId":"p1","sourceScopeId":"s1","origin":"scope","judge":"codeforces","externalId":"1A","title":"T","solved":false,"dayKey":"2026-01-01"}],"attempts":[],"notes":[{"stableId":"n1","sourceScopeId":"s1","origin":"scope","problemId":"p1","keyInsight":"k","implementationNotes":"i","complexity":"c","general":"g","updatedEpochMs":1,"dayKey":"2026-01-01"}],"reviews":[],"sessions":[]}
        """;

    private readonly string _dataDirectory;
    private readonly string _packagePath;
    private readonly string _exportPath;

    public PortableCommandTests()
    {
        _dataDirectory = Path.Combine(Path.GetTempPath(), "ojnexus-cli-tests", Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(_dataDirectory);
        _packagePath = Path.Combine(_dataDirectory, "package.json");
        _exportPath = Path.Combine(_dataDirectory, "export.json");
        File.WriteAllText(_packagePath, PackageJson);
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
    public async Task Inspect_ValidPackage_ShowsCountsIdentityAndCapability()
    {
        var (exitCode, output, error) = await RunAsync(["portable", "inspect", "--file", _packagePath, "--json"]);

        Assert.Equal(0, exitCode);
        Assert.Equal(string.Empty, error.ToString());
        using var document = JsonDocument.Parse(output.ToString());
        Assert.Equal("preview", document.RootElement.GetProperty("status").GetString());
        Assert.Equal("s1", document.RootElement.GetProperty("scopeId").GetString());
        Assert.Equal(1, document.RootElement.GetProperty("counts").GetProperty("problems").GetInt32());
        Assert.Equal(1, document.RootElement.GetProperty("editable").GetInt32());
        Assert.Equal(1, document.RootElement.GetProperty("storedOnly").GetInt32());
        Assert.Equal(0, document.RootElement.GetProperty("rejected").GetInt32());
        Assert.False(string.IsNullOrEmpty(document.RootElement.GetProperty("identity").GetString()));
    }

    [Fact]
    public async Task Import_WithoutYes_PrintsConfirmationAndWritesNothing()
    {
        var (exitCode, output, _) = await RunAsync(["portable", "import", "--file", _packagePath, "--json"]);

        Assert.Equal(0, exitCode);
        using var document = JsonDocument.Parse(output.ToString());
        Assert.Equal("preview", document.RootElement.GetProperty("status").GetString());

        var (exportExit, exportOutput, _) = await RunAsync(["portable", "export", "--file", _exportPath, "--json"]);
        Assert.Equal((int)CliExitCode.GeneralError, exportExit);
        using var exportDocument = JsonDocument.Parse(exportOutput.ToString());
        Assert.Equal("ARCHIVE_EMPTY", exportDocument.RootElement.GetProperty("error").GetString());
    }

    [Fact]
    public async Task Import_WithYes_AppliesAndReimportReportsIdentical()
    {
        var (firstExit, firstOutput, _) = await RunAsync(["portable", "import", "--file", _packagePath, "--yes", "--json"]);
        Assert.Equal(0, firstExit);
        using var firstDocument = JsonDocument.Parse(firstOutput.ToString());
        Assert.Equal("applied", firstDocument.RootElement.GetProperty("status").GetString());
        Assert.Equal(2, firstDocument.RootElement.GetProperty("rowsUpserted").GetInt32());
        Assert.False(firstDocument.RootElement.GetProperty("identical").GetBoolean());

        var (secondExit, secondOutput, _) = await RunAsync(["portable", "import", "--file", _packagePath, "--yes", "--json"]);
        Assert.Equal(0, secondExit);
        using var secondDocument = JsonDocument.Parse(secondOutput.ToString());
        Assert.True(secondDocument.RootElement.GetProperty("identical").GetBoolean());
        Assert.Equal(0, secondDocument.RootElement.GetProperty("rowsUpserted").GetInt32());
    }

    [Fact]
    public async Task Import_OutOfContractPackage_ShowsRejectedWithExitZero()
    {
        var rejectPath = Path.Combine(_dataDirectory, "reject.json");
        await File.WriteAllTextAsync(rejectPath, """{"formatVersion":2}""");

        var (exitCode, output, _) = await RunAsync(["portable", "import", "--file", rejectPath, "--yes", "--json"]);

        Assert.Equal(0, exitCode);
        using var document = JsonDocument.Parse(output.ToString());
        Assert.Equal("rejected", document.RootElement.GetProperty("status").GetString());
        Assert.Equal("UNSUPPORTED_VERSION", document.RootElement.GetProperty("category").GetString());
    }

    [Fact]
    public async Task Export_WithEmptyArchive_ReturnsSevenWithArchiveEmpty()
    {
        var (exitCode, output, _) = await RunAsync(["portable", "export", "--file", _exportPath, "--json"]);

        Assert.Equal((int)CliExitCode.GeneralError, exitCode);
        using var document = JsonDocument.Parse(output.ToString());
        Assert.Equal("ARCHIVE_EMPTY", document.RootElement.GetProperty("error").GetString());
        Assert.False(File.Exists(_exportPath));
    }

    [Fact]
    public async Task Portable_MissingFile_Option_ReturnsUsageError()
    {
        var (exitCode, output, error) = await RunAsync(["portable", "inspect", "--json"]);

        Assert.Equal((int)CliExitCode.InvalidArguments, exitCode);
        Assert.Equal(string.Empty, output.ToString());
        Assert.StartsWith("ARGUMENT ERROR: MISSING REQUIRED OPTION", error.ToString(), StringComparison.Ordinal);
    }

    [Fact]
    public async Task Inspect_MissingPackageFile_ReturnsStorageExit()
    {
        var missing = Path.Combine(_dataDirectory, "missing.json");

        var (exitCode, output, _) = await RunAsync(["portable", "inspect", "--file", missing, "--json"]);

        Assert.Equal((int)CliExitCode.Storage, exitCode);
        using var document = JsonDocument.Parse(output.ToString());
        Assert.Equal("STORAGE_IO_FAILURE", document.RootElement.GetProperty("error").GetString());
    }

    private async Task<(int ExitCode, StringWriter Output, StringWriter Error)> RunAsync(string[] args)
    {
        var output = new StringWriter();
        var error = new StringWriter();
        var exitCode = await Program.RunAsync(args, output, error, CancellationToken.None, _dataDirectory);
        return (exitCode, output, error);
    }
}
