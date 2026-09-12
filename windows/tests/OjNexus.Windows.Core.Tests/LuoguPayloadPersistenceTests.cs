using Microsoft.Data.Sqlite;
using OjNexus.Windows.Core.Domain;
using OjNexus.Windows.Core.Storage;

namespace OjNexus.Windows.Core.Tests;

public sealed class LuoguPayloadPersistenceTests
{
    [Fact]
    public async Task Store_RoundTripsAndMergesStructuredLuoguPayloadSummary()
    {
        using var database = new TemporaryDatabase();
        var account = JudgeAccount.Create(JudgeId.Luogu, "uid:2");
        var timestamp = DateTimeOffset.Parse("2026-09-13T01:02:03+00:00");
        var operationId = await database.Store.OpenOperationAsync(account, "generation-1", timestamp, CancellationToken.None);

        await database.Store.AppendModuleAsync(
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
        await database.Store.AppendModuleAsync(
            operationId,
            new SyncModuleOutcome(
                "SUBMISSIONS",
                SyncOperationStatus.Success,
                3,
                3,
                0,
                null,
                new LuoguCollectionPayload("uid:2", "SUBMISSIONS", 3)),
            timestamp,
            CancellationToken.None);
        await database.Store.AppendModuleAsync(
            operationId,
            new SyncModuleOutcome(
                "CONTESTS",
                SyncOperationStatus.Success,
                4,
                4,
                0,
                null,
                new LuoguCollectionPayload("uid:2", "CONTESTS", 4)),
            timestamp,
            CancellationToken.None);
        await database.Store.AppendModuleAsync(
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

        var snapshot = await database.Store.GetLuoguPayloadAsync(" uid:2 ", CancellationToken.None);

        Assert.Equal(
            new LuoguProfilePayload("uid:2", 2, "demo", 1200),
            snapshot.Profile);
        Assert.Equal(3, snapshot.SubmissionsCount);
        Assert.Equal(4, snapshot.ContestsCount);
        Assert.Equal(5, snapshot.ProblemsCount);

        await database.Store.AppendModuleAsync(
            operationId,
            new SyncModuleOutcome(
                "SUBMISSIONS",
                SyncOperationStatus.Success,
                0,
                0,
                0,
                null,
                new LuoguCollectionPayload("uid:2", "SUBMISSIONS", 0)),
            timestamp.AddMinutes(1),
            CancellationToken.None);

        var replaced = await database.Store.GetLuoguPayloadAsync("uid:2", CancellationToken.None);
        Assert.Equal(0, replaced.SubmissionsCount);
        Assert.Equal(4, replaced.ContestsCount);
        Assert.Equal(5, replaced.ProblemsCount);
    }

    private sealed class TemporaryDatabase : IDisposable
    {
        private readonly string _directoryPath = Path.Combine(Path.GetTempPath(), "oj-nexus-luogu-payload-tests", Guid.NewGuid().ToString("N"));

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
