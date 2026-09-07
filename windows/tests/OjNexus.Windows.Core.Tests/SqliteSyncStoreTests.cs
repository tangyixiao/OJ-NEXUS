using Microsoft.Data.Sqlite;
using OjNexus.Windows.Core.Domain;
using OjNexus.Windows.Core.Storage;

namespace OjNexus.Windows.Core.Tests;

public sealed class SqliteSyncStoreTests
{
    [Fact]
    public void Migrate_CreatesOnlyTheRequiredSchemaTables()
    {
        using var database = new TemporaryDatabase();

        using var connection = new SqliteConnection($"Data Source={database.DatabasePath};Pooling=False");
        connection.Open();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name";
        using var reader = command.ExecuteReader();
        var tableNames = new List<string>();
        while (reader.Read())
        {
            tableNames.Add(reader.GetString(0));
        }

        Assert.Equal(new[] { "accounts", "schema_metadata", "sync_modules", "sync_operations" }, tableNames);
    }

    [Fact]
    public async Task Store_RoundTripsAccountOperationAndModules()
    {
        using var database = new TemporaryDatabase();
        var account = JudgeAccount.Create(JudgeId.AtCoder, " tourist ");
        var startedAt = DateTimeOffset.Parse("2026-09-07T01:02:03+00:00");
        var completedAt = startedAt.AddMinutes(1);

        await database.Store.UpsertAccountAsync(account, CancellationToken.None);
        var operationId = await database.Store.OpenOperationAsync(account, "generation-9", startedAt, CancellationToken.None);
        await database.Store.AppendModuleAsync(operationId, new SyncModuleOutcome("PROFILE", SyncOperationStatus.Success, 2, 1, 1, null), completedAt, CancellationToken.None);
        await database.Store.AppendModuleAsync(operationId, new SyncModuleOutcome("SUBMISSIONS", SyncOperationStatus.Error, 3, 1, 0, "Api"), completedAt, CancellationToken.None);
        await database.Store.CloseOperationAsync(operationId, SyncOperationStatus.Partial, SyncError.Network, completedAt, CancellationToken.None);

        var accounts = await database.Store.GetAccountsAsync(CancellationToken.None);
        var operations = await database.Store.GetRecentOperationsAsync(JudgeId.AtCoder, 10, CancellationToken.None);

        Assert.Equal(new[] { account }, accounts);
        var operation = Assert.Single(operations);
        Assert.Equal(operationId, operation.Id);
        Assert.Equal("generation-9", operation.DataGeneration);
        Assert.Equal(startedAt, operation.StartedAt);
        Assert.Equal(completedAt, operation.FinishedAt);
        Assert.Equal(SyncOperationStatus.Partial, operation.Status);
        Assert.Collection(
            operation.Modules,
            profile => Assert.Equal(new SyncModuleOutcome("PROFILE", SyncOperationStatus.Success, 2, 1, 1, null), profile),
            submissions => Assert.Equal(new SyncModuleOutcome("SUBMISSIONS", SyncOperationStatus.Error, 3, 1, 0, "Api"), submissions));
        Assert.Equal("Network", ReadFailureCategory(database.DatabasePath, operationId));
    }

    [Fact]
    public async Task Store_OrdersRecentOperationsByStartedAtThenIdDescending()
    {
        using var database = new TemporaryDatabase();
        var account = JudgeAccount.Create(JudgeId.Luogu, "nexus");
        var startedAt = DateTimeOffset.Parse("2026-09-07T02:00:00+00:00");

        var firstId = await database.Store.OpenOperationAsync(account, "first", startedAt, CancellationToken.None);
        var secondId = await database.Store.OpenOperationAsync(account, "second", startedAt, CancellationToken.None);

        var operations = await database.Store.GetRecentOperationsAsync(JudgeId.Luogu, 10, CancellationToken.None);

        Assert.Equal(new[] { secondId, firstId }, operations.Select(operation => operation.Id));
    }

    [Fact]
    public async Task Store_RetainsTwentyCompletedOperationsAndAllActiveOperationsPerJudge()
    {
        using var database = new TemporaryDatabase();
        var account = JudgeAccount.Create(JudgeId.Codeforces, "tourist");
        var startedAt = DateTimeOffset.Parse("2026-09-07T03:00:00+00:00");
        var completedIds = new List<long>();

        for (var index = 0; index < 25; index++)
        {
            var operationId = await database.Store.OpenOperationAsync(account, $"completed-{index}", startedAt.AddMinutes(index), CancellationToken.None);
            completedIds.Add(operationId);
            await database.Store.AppendModuleAsync(operationId, new SyncModuleOutcome("PROFILE", SyncOperationStatus.Success, 1, 1, 0, null), startedAt.AddMinutes(index + 1), CancellationToken.None);
            await database.Store.CloseOperationAsync(operationId, SyncOperationStatus.Success, null, startedAt.AddMinutes(index + 1), CancellationToken.None);
        }

        var activeIds = new List<long>();
        for (var index = 0; index < 5; index++)
        {
            activeIds.Add(await database.Store.OpenOperationAsync(account, $"active-{index}", startedAt.AddDays(1).AddMinutes(index), CancellationToken.None));
        }

        var operations = await database.Store.GetRecentOperationsAsync(JudgeId.Codeforces, 100, CancellationToken.None);

        Assert.Equal(25, operations.Count);
        Assert.DoesNotContain(operations, operation => operation.Id == completedIds[0]);
        Assert.Equal(activeIds.OrderByDescending(id => id), operations.Take(5).Select(operation => operation.Id));
        Assert.Equal(20, operations.Count(operation => operation.FinishedAt is not null));
        Assert.Equal(5, operations.Count(operation => operation.FinishedAt is null));
        Assert.Equal(0L, CountModules(database.DatabasePath, completedIds[0]));
    }

    private static string? ReadFailureCategory(string databasePath, long operationId)
    {
        using var connection = new SqliteConnection($"Data Source={databasePath};Pooling=False");
        connection.Open();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT failure_category FROM sync_operations WHERE id = $operationId";
        command.Parameters.AddWithValue("$operationId", operationId);
        return command.ExecuteScalar() as string;
    }

    private static long CountModules(string databasePath, long operationId)
    {
        using var connection = new SqliteConnection($"Data Source={databasePath};Pooling=False");
        connection.Open();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT COUNT(*) FROM sync_modules WHERE operation_id = $operationId";
        command.Parameters.AddWithValue("$operationId", operationId);
        return (long)command.ExecuteScalar()!;
    }

    private sealed class TemporaryDatabase : IDisposable
    {
        private readonly string _directoryPath = Path.Combine(Path.GetTempPath(), "oj-nexus-tests", Guid.NewGuid().ToString("N"));

        public TemporaryDatabase()
        {
            var factory = new SqliteConnectionFactory(_directoryPath);
            Store = new SqliteSyncStore(factory);
            DatabasePath = factory.DatabasePath;
        }

        public string DatabasePath { get; }

        public SqliteSyncStore Store { get; }

        public void Dispose()
        {
            SqliteConnection.ClearAllPools();
            if (Directory.Exists(_directoryPath))
            {
                Directory.Delete(_directoryPath, recursive: true);
            }
        }
    }
}
