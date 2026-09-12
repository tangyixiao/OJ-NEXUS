using Microsoft.Data.Sqlite;
using OjNexus.Windows.Core.Contracts;
using OjNexus.Windows.Core.Domain;
using OjNexus.Windows.Core.Storage;
using OjNexus.Windows.Core.Sync;

namespace OjNexus.Windows.Core.Tests;

public sealed class AtCoderPayloadPersistenceTests
{
    [Fact]
    public async Task Store_RoundTripsAndReplacesAtCoderSubmissionSnapshot()
    {
        using var database = new TemporaryDatabase();
        var account = JudgeAccount.Create(JudgeId.AtCoder, "tourist");
        var timestamp = DateTimeOffset.Parse("2026-09-12T01:02:03+00:00");
        var operationId = await database.Store.OpenOperationAsync(account, "generation-1", timestamp, CancellationToken.None);
        var first = new AtCoderSubmission(11, 100, "abc100_a", "abc100", "C++", 100, 10, "AC", 1);

        await database.Store.AppendModuleAsync(
            operationId,
            new SyncModuleOutcome("SUBMISSIONS", SyncOperationStatus.Success, 1, 1, 0, null, new AtCoderSubmissionsPayload("tourist", [first])),
            timestamp,
            CancellationToken.None);

        var snapshot = await database.Store.GetAtCoderPayloadAsync(" tourist ", CancellationToken.None);
        Assert.Equal([first], snapshot.Submissions);

        await database.Store.AppendModuleAsync(
            operationId,
            new SyncModuleOutcome("SUBMISSIONS", SyncOperationStatus.Success, 0, 0, 0, null, new AtCoderSubmissionsPayload("tourist", [])),
            timestamp.AddMinutes(1),
            CancellationToken.None);

        Assert.Empty((await database.Store.GetAtCoderPayloadAsync("tourist", CancellationToken.None)).Submissions);
    }

    private sealed class TemporaryDatabase : IDisposable
    {
        private readonly string _directoryPath = Path.Combine(Path.GetTempPath(), "oj-nexus-atcoder-tests", Guid.NewGuid().ToString("N"));

        public TemporaryDatabase()
        {
            Directory.CreateDirectory(_directoryPath);
            Store = new SqliteSyncStore(new SqliteConnectionFactory(_directoryPath));
        }

        public SqliteSyncStore Store { get; }

        public void Dispose()
        {
            SqliteConnection.ClearAllPools();
            Directory.Delete(_directoryPath, recursive: true);
        }
    }
}
