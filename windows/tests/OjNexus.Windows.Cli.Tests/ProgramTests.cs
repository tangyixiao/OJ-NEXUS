using System.Text.Json;
using OjNexus.Windows.Cli;
using OjNexus.Windows.Core.Domain;
using OjNexus.Windows.Core.Storage;
using OjNexus.Windows.Core.Contracts;

namespace OjNexus.Windows.Cli.Tests;

public sealed class ProgramTests : IDisposable
{
    private readonly string _dataDirectory;
    private readonly List<(WindowsStorageSession Session, StorageOperationContext Operation)> _ownedStorage = [];

    public ProgramTests()
    {
        _dataDirectory = Path.Combine(Path.GetTempPath(), "ojnexus-cli-tests", Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(_dataDirectory);
    }

    public void Dispose()
    {
        foreach (var (session, operation) in _ownedStorage)
        {
            operation.Dispose();
            session.DisposeAsync().AsTask().GetAwaiter().GetResult();
        }
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

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task RunAsync_ExistingZeroByteDatabase_IsBlockedWithoutMutation(bool json)
    {
        File.WriteAllBytes(Path.Combine(_dataDirectory, "ojnexus.db"), []);
        var before = File.ReadAllBytes(Path.Combine(_dataDirectory, "ojnexus.db"));
        var (exitCode, output, error) = await RunAsync(json ? ["status", "--json"] : ["status"]);

        Assert.Equal(6, exitCode);
        Assert.Equal(before, File.ReadAllBytes(Path.Combine(_dataDirectory, "ojnexus.db")));
        if (json)
        {
            using var document = JsonDocument.Parse(output.ToString());
            Assert.Equal("blocked", document.RootElement.GetProperty("status").GetString());
            Assert.Equal("INTEGRITY_FAILURE", document.RootElement.GetProperty("error").GetString());
        }
        else
        {
            Assert.Contains("INTEGRITY_FAILURE", output.ToString(), StringComparison.Ordinal);
        }
        Assert.DoesNotContain(_dataDirectory, output.ToString() + error, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public async Task RunAsync_MissingEstablishedDatabase_IsBlockedWithWireJson()
    {
        File.WriteAllText(Path.Combine(_dataDirectory, "ojnexus.initialized"), "v1\n");
        var (exitCode, output, error) = await RunAsync(["status", "--json"]);

        Assert.Equal(6, exitCode);
        using var document = JsonDocument.Parse(output.ToString());
        Assert.Equal("blocked", document.RootElement.GetProperty("status").GetString());
        Assert.Equal("RECOVERY_SOURCE_MISSING", document.RootElement.GetProperty("error").GetString());
        Assert.DoesNotContain(_dataDirectory, output.ToString() + error, StringComparison.OrdinalIgnoreCase);
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
    public async Task RunAsync_DataJson_ReadsPersistedAtCoderPayload()
    {
        var store = CreateStore();
        var account = JudgeAccount.Create(JudgeId.AtCoder, "tourist");
        var timestamp = DateTimeOffset.Parse("2026-09-12T01:02:03+00:00");
        var operationId = await store.OpenOperationAsync(account, "generation-1", timestamp, CancellationToken.None);
        await store.AppendModuleAsync(
            operationId,
            new SyncModuleOutcome(
                "SUBMISSIONS",
                SyncOperationStatus.Success,
                1,
                1,
                0,
                null,
                new AtCoderSubmissionsPayload("tourist", [new AtCoderSubmission(11, 100, "abc100_a", "abc100", "C++", 100, 10, "AC", 1)])),
            timestamp,
            CancellationToken.None);

        var (exitCode, output, error) = await RunAsync(["data", "--judge", "atcoder", "--handle", "tourist", "--json"]);

        Assert.Equal(0, exitCode);
        Assert.Equal(string.Empty, error.ToString());
        using var document = JsonDocument.Parse(output.ToString());
        Assert.Equal("AtCoder", document.RootElement.GetProperty("judge").GetString());
        Assert.Equal("abc100_a", document.RootElement.GetProperty("submissions")[0].GetProperty("problemId").GetString());
    }

    [Fact]
    public async Task RunAsync_DataJson_ReadsPersistedLuoguPayloadSummary()
    {
        var store = CreateStore();
        var account = JudgeAccount.Create(JudgeId.Luogu, "uid:2");
        var timestamp = DateTimeOffset.Parse("2026-09-13T01:02:03+00:00");
        var operationId = await store.OpenOperationAsync(account, "generation-1", timestamp, CancellationToken.None);
        await store.AppendModuleAsync(
            operationId,
            new SyncModuleOutcome(
                "PROFILE",
                SyncOperationStatus.Success,
                1,
                1,
                0,
                null,
                new LuoguProfilePayload("uid:2", 2, "demo", 1200)),
            timestamp,
            CancellationToken.None);
        await store.AppendModuleAsync(
            operationId,
            new SyncModuleOutcome(
                "PROBLEMSET",
                SyncOperationStatus.Success,
                5,
                5,
                0,
                null,
                new LuoguCollectionPayload("uid:2", "PROBLEMSET", 5)),
            timestamp,
            CancellationToken.None);

        var (exitCode, output, error) = await RunAsync(["data", "--judge", "luogu", "--handle", "uid:2", "--json"]);

        Assert.Equal(0, exitCode);
        Assert.Equal(string.Empty, error.ToString());
        using var document = JsonDocument.Parse(output.ToString());
        Assert.Equal("Luogu", document.RootElement.GetProperty("judge").GetString());
        Assert.Equal("demo", document.RootElement.GetProperty("profile").GetProperty("displayName").GetString());
        Assert.Equal(5, document.RootElement.GetProperty("problems").GetInt32());
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

        Assert.Equal((int)CliExitCode.Storage, exitCode);
        using var document = JsonDocument.Parse(output.ToString());
        Assert.Equal("blocked", document.RootElement.GetProperty("status").GetString());
        Assert.Equal("INTEGRITY_FAILURE", document.RootElement.GetProperty("error").GetString());
        Assert.DoesNotContain("at OjNexus", output.ToString() + error);
        Assert.DoesNotContain("Exception", output.ToString() + error);
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

    [Fact]
    public async Task RunAsync_ProblemsListJson_ReadsDerivedProblemLedger()
    {
        var store = CreateStore();
        var timestamp = DateTimeOffset.Parse("2026-09-12T01:02:03+00:00");
        var cfAccount = JudgeAccount.Create(JudgeId.Codeforces, "tourist");
        var cfOperation = await store.OpenOperationAsync(cfAccount, "generation-1", timestamp, CancellationToken.None);
        await store.AppendModuleAsync(
            cfOperation,
            new SyncModuleOutcome(
                "SUBMISSIONS",
                SyncOperationStatus.Success,
                2,
                2,
                0,
                null,
                new CodeforcesSubmissionsPayload("tourist",
                [
                    new CodeforcesSubmission(1, 2134, "C", "Watermelon", "OK", "GNU C++17", 5, 15, 262144, 1_700_000_000),
                    new CodeforcesSubmission(2, 2134, "D", "Theatre Square", "WRONG_ANSWER", "GNU C++17", 0, 0, 262144, 1_700_000_100),
                ])),
            timestamp,
            CancellationToken.None);
        var atAccount = JudgeAccount.Create(JudgeId.AtCoder, "chokudai");
        var atOperation = await store.OpenOperationAsync(atAccount, "generation-2", timestamp, CancellationToken.None);
        await store.AppendModuleAsync(
            atOperation,
            new SyncModuleOutcome(
                "SUBMISSIONS",
                SyncOperationStatus.Success,
                1,
                1,
                0,
                null,
                new AtCoderSubmissionsPayload("chokudai", [new AtCoderSubmission(11, 100, "abc100_a", "abc100", "C++", 100, 10, "AC", 1)])),
            timestamp,
            CancellationToken.None);

        var (exitCode, output, error) = await RunAsync(["problems", "list", "--json"]);

        Assert.Equal(0, exitCode);
        Assert.Equal(string.Empty, error.ToString());
        using var document = JsonDocument.Parse(output.ToString());
        var problems = document.RootElement.GetProperty("problems");
        Assert.Equal(3, problems.GetArrayLength());
        Assert.Collection(
            problems.EnumerateArray(),
            first =>
            {
                Assert.Equal("AtCoder", first.GetProperty("judge").GetString());
                Assert.Equal("abc100+abc100_a", first.GetProperty("externalId").GetString());
                Assert.Equal("https://atcoder.jp/contests/abc100/tasks/abc100_a", first.GetProperty("url").GetString());
                Assert.True(first.GetProperty("solved").GetBoolean());
            },
            second =>
            {
                Assert.Equal("Codeforces", second.GetProperty("judge").GetString());
                Assert.Equal("2134C", second.GetProperty("externalId").GetString());
                Assert.True(second.GetProperty("solved").GetBoolean());
            },
            third =>
            {
                Assert.Equal("Codeforces", third.GetProperty("judge").GetString());
                Assert.False(third.GetProperty("solved").GetBoolean());
            });
    }

    [Fact]
    public async Task RunAsync_ProblemsListFilteredByJudgeAndStatus()
    {
        var store = CreateStore();
        var timestamp = DateTimeOffset.Parse("2026-09-12T01:02:03+00:00");
        var cfAccount = JudgeAccount.Create(JudgeId.Codeforces, "tourist");
        var cfOperation = await store.OpenOperationAsync(cfAccount, "generation-1", timestamp, CancellationToken.None);
        await store.AppendModuleAsync(
            cfOperation,
            new SyncModuleOutcome(
                "SUBMISSIONS",
                SyncOperationStatus.Success,
                2,
                2,
                0,
                null,
                new CodeforcesSubmissionsPayload("tourist",
                [
                    new CodeforcesSubmission(1, 2134, "C", "Watermelon", "OK", "GNU C++17", 5, 15, 262144, 1_700_000_000),
                    new CodeforcesSubmission(2, 2134, "D", "Theatre Square", "WRONG_ANSWER", "GNU C++17", 0, 0, 262144, 1_700_000_100),
                ])),
            timestamp,
            CancellationToken.None);

        var (exitCode, output, error) = await RunAsync(["problems", "list", "--judge", "codeforces", "--status", "attempted"]);

        Assert.Equal(0, exitCode);
        Assert.Equal(string.Empty, error.ToString());
        var lines = output.ToString().Split(Environment.NewLine, StringSplitOptions.RemoveEmptyEntries);
        var line = Assert.Single(lines);
        Assert.Contains("2134D", line, StringComparison.Ordinal);
        Assert.Contains("[ATTEMPTED]", line, StringComparison.Ordinal);
    }

    [Fact]
    public async Task RunAsync_ProblemsListEmpty_ReportsEmptyAndSuccessExit()
    {
        var (exitCode, output, error) = await RunAsync(["problems", "list", "--json"]);

        Assert.Equal(0, exitCode);
        Assert.Equal(string.Empty, error.ToString());
        using var document = JsonDocument.Parse(output.ToString());
        Assert.Empty(document.RootElement.GetProperty("problems").EnumerateArray());
    }

    [Fact]
    public async Task RunAsync_ProblemsInvalidStatus_WritesArgumentError()
    {
        var (exitCode, output, error) = await RunAsync(["problems", "list", "--status", "great"]);

        Assert.Equal((int)CliExitCode.InvalidArguments, exitCode);
        Assert.Equal(string.Empty, output.ToString());
        Assert.StartsWith("ARGUMENT ERROR:", error.ToString(), StringComparison.Ordinal);
    }

    [Fact]
    public async Task RunAsync_ReviewMarkThenDue_RoundsTripThroughStore()
    {
        var markedAt = DateTimeOffset.Parse("2026-09-12T01:02:03+00:00");
        var (markExit, markOutput, markError) = await RunAsync([
            "review", "mark", "--judge", "codeforces", "--problem", "2134/C", "--result", "fail",
            "--json",
        ]);

        Assert.Equal(0, markExit);
        Assert.Equal(string.Empty, markError.ToString());
        using (var markDocument = JsonDocument.Parse(markOutput.ToString()))
        {
            Assert.True(markDocument.RootElement.GetProperty("marked").GetBoolean());
            Assert.Equal("FAIL", markDocument.RootElement.GetProperty("result").GetString());
        }

        var store = CreateStore();
        // A second mark stamped in the past moves the due date into the past on purpose.
        await store.MarkReviewAsync(JudgeId.Codeforces, "2134/C", ReviewResult.Pass, markedAt, CancellationToken.None);

        var (dueExit, dueOutput, dueError) = await RunAsync(["review", "due", "--json"]);

        Assert.Equal(0, dueExit);
        Assert.Equal(string.Empty, dueError.ToString());
        using var dueDocument = JsonDocument.Parse(dueOutput.ToString());
        var reviews = dueDocument.RootElement.GetProperty("reviews");
        var row = Assert.Single(reviews.EnumerateArray());
        Assert.Equal("Codeforces", row.GetProperty("judge").GetString());
        Assert.Equal("2134/C", row.GetProperty("externalId").GetString());
        Assert.Equal(1, row.GetProperty("stage").GetInt32());
        Assert.Equal("PASS", row.GetProperty("lastResult").GetString());
    }

    [Fact]
    public async Task RunAsync_ReviewDueHuman_ReportsEmptyAndSuccessExit()
    {
        var (exitCode, output, error) = await RunAsync(["review", "due"]);

        Assert.Equal(0, exitCode);
        Assert.Equal(string.Empty, error.ToString());
        Assert.Contains("REVIEWS DUE: EMPTY", output.ToString(), StringComparison.Ordinal);
    }

    [Fact]
    public async Task RunAsync_ReviewDueBadLimit_WritesArgumentError()
    {
        var (exitCode, output, error) = await RunAsync(["review", "due", "--limit", "0"]);

        Assert.Equal((int)CliExitCode.InvalidArguments, exitCode);
        Assert.Equal(string.Empty, output.ToString());
        Assert.StartsWith("ARGUMENT ERROR:", error.ToString(), StringComparison.Ordinal);
    }

    private async Task<(int ExitCode, StringWriter Output, StringWriter Error)> RunAsync(string[] args)
    {
        var output = new StringWriter();
        var error = new StringWriter();
        var exitCode = await Program.RunAsync(args, output, error, CancellationToken.None, _dataDirectory);
        return (exitCode, output, error);
    }

    private ISyncStore CreateStore()
    {
        var opened = WindowsStorage.OpenAsync(_dataDirectory, null, CancellationToken.None).GetAwaiter().GetResult();
        var session = opened.Session ?? throw new InvalidOperationException(opened.Failure?.WireCode);
        var operation = session.BeginOperation();
        _ownedStorage.Add((session, operation));
        return operation.Store;
    }
}
