using Microsoft.Data.Sqlite;
using OjNexus.Windows.Core.Domain;
using OjNexus.Windows.Core.Storage;

namespace OjNexus.Windows.Core.Tests;

public sealed class SqliteSyncStoreTests
{
    [Fact]
    public void Migrate_FreshDatabase_SetsSchemaVersionOne()
    {
        using var database = new TemporaryDatabase();

        Assert.Equal("1", ReadSchemaVersion(database.DatabasePath));
    }

    [Fact]
    public void Migrate_RepeatsWithoutChangingSchemaVersionOrExistingData()
    {
        using var database = new TemporaryDatabase();
        ExecuteNonQuery(
            database.DatabasePath,
            "INSERT INTO accounts (judge, handle, enabled) VALUES ('Codeforces', 'tourist', 1)");

        SchemaMigrator.Migrate(database.ConnectionString);

        Assert.Equal("1", ReadSchemaVersion(database.DatabasePath));
        Assert.Equal("tourist", ReadAccountHandle(database.DatabasePath, "Codeforces"));
    }

    [Fact]
    public void Migrate_RejectsDatabaseVersionNewerThanSupportedWithoutChangingIt()
    {
        using var database = new TemporaryDatabaseDirectory();
        ExecuteNonQuery(database.DatabasePath, "CREATE TABLE schema_metadata (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)");
        ExecuteNonQuery(database.DatabasePath, "INSERT INTO schema_metadata (key, value) VALUES ('schema_version', '2')");

        Assert.Throws<InvalidOperationException>(() => SchemaMigrator.Migrate(database.ConnectionString));

        Assert.Equal("2", ReadSchemaVersion(database.DatabasePath));
        Assert.False(TableExists(database.DatabasePath, "accounts"));
    }

    [Fact]
    public void Migrate_RejectsIncompleteCurrentVersionSchema()
    {
        using var database = new TemporaryDatabaseDirectory();
        ExecuteNonQuery(database.DatabasePath, "CREATE TABLE schema_metadata (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)");
        ExecuteNonQuery(database.DatabasePath, "INSERT INTO schema_metadata (key, value) VALUES ('schema_version', '1')");

        Assert.Throws<InvalidOperationException>(() => SchemaMigrator.Migrate(database.ConnectionString));

        Assert.False(TableExists(database.DatabasePath, "accounts"));
    }

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
        var otherJudgeAccount = JudgeAccount.Create(JudgeId.AtCoder, "second");
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

        for (var index = 0; index < 25; index++)
        {
            var operationId = await database.Store.OpenOperationAsync(otherJudgeAccount, $"other-{index}", startedAt.AddMinutes(index), CancellationToken.None);
            await database.Store.CloseOperationAsync(operationId, SyncOperationStatus.Success, null, startedAt.AddMinutes(index + 1), CancellationToken.None);
        }

        var operations = await database.Store.GetRecentOperationsAsync(JudgeId.Codeforces, 100, CancellationToken.None);
        var otherJudgeOperations = await database.Store.GetRecentOperationsAsync(JudgeId.AtCoder, 100, CancellationToken.None);

        Assert.Equal(25, operations.Count);
        Assert.DoesNotContain(operations, operation => operation.Id == completedIds[0]);
        Assert.Equal(activeIds.OrderByDescending(id => id), operations.Take(5).Select(operation => operation.Id));
        Assert.Equal(20, operations.Count(operation => operation.FinishedAt is not null));
        Assert.Equal(5, operations.Count(operation => operation.FinishedAt is null));
        Assert.Equal(0L, CountModules(database.DatabasePath, completedIds[0]));
        Assert.Equal(20, otherJudgeOperations.Count);
        Assert.All(otherJudgeOperations, operation => Assert.Equal(JudgeId.AtCoder, operation.Account.Judge));
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

    private static string? ReadSchemaVersion(string databasePath) => ReadString(databasePath, "SELECT value FROM schema_metadata WHERE key = 'schema_version'");

    private static string? ReadAccountHandle(string databasePath, string judge)
    {
        using var connection = OpenDirectConnection(databasePath);
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT handle FROM accounts WHERE judge = $judge";
        command.Parameters.AddWithValue("$judge", judge);
        return command.ExecuteScalar() as string;
    }

    private static bool TableExists(string databasePath, string tableName)
    {
        using var connection = OpenDirectConnection(databasePath);
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = $tableName)";
        command.Parameters.AddWithValue("$tableName", tableName);
        return Convert.ToInt64(command.ExecuteScalar()) != 0;
    }

    private static string? ReadString(string databasePath, string commandText)
    {
        using var connection = OpenDirectConnection(databasePath);
        using var command = connection.CreateCommand();
        command.CommandText = commandText;
        return command.ExecuteScalar() as string;
    }

    private static void ExecuteNonQuery(string databasePath, string commandText)
    {
        using var connection = OpenDirectConnection(databasePath);
        using var command = connection.CreateCommand();
        command.CommandText = commandText;
        command.ExecuteNonQuery();
    }

    private static SqliteConnection OpenDirectConnection(string databasePath)
    {
        var connection = new SqliteConnection($"Data Source={databasePath};Pooling=False");
        connection.Open();
        return connection;
    }

    private class TemporaryDatabaseDirectory : IDisposable
    {
        private readonly string _directoryPath = Path.Combine(Path.GetTempPath(), "oj-nexus-tests", Guid.NewGuid().ToString("N"));

        public TemporaryDatabaseDirectory()
        {
            Directory.CreateDirectory(_directoryPath);
        }

        public string DatabasePath => Path.Combine(_directoryPath, "ojnexus.db");

        public string ConnectionString => new SqliteConnectionStringBuilder
        {
            DataSource = DatabasePath,
            ForeignKeys = true,
        }.ToString();

        public void Dispose()
        {
            SqliteConnection.ClearAllPools();
            if (Directory.Exists(_directoryPath))
            {
                Directory.Delete(_directoryPath, recursive: true);
            }
        }
    }

    private sealed class TemporaryDatabase : TemporaryDatabaseDirectory
    {
        public TemporaryDatabase()
        {
            var factory = new SqliteConnectionFactory(Path.GetDirectoryName(DatabasePath));
            Store = new SqliteSyncStore(factory);
            Assert.Equal(DatabasePath, factory.DatabasePath);
        }

        public SqliteSyncStore Store { get; }
    }
}
