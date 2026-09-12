using Microsoft.Data.Sqlite;
using OjNexus.Windows.Core.Contracts;
using OjNexus.Windows.Core.Domain;
using OjNexus.Windows.Core.Storage;
using OjNexus.Windows.Core.Sync;

namespace OjNexus.Windows.Core.Tests;

public sealed class CodeforcesPayloadPersistenceTests
{
    [Fact]
    public void FreshDatabase_UsesSchemaVersionFourAndCreatesPayloadTables()
    {
        using var database = new TemporaryDatabase();

        using var connection = new SqliteConnection($"Data Source={database.DatabasePath};Pooling=False");
        connection.Open();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT value FROM schema_metadata WHERE key = 'schema_version'";

        Assert.Equal("4", command.ExecuteScalar()?.ToString());
        Assert.Equal(
            new[] { "accounts", "atcoder_submissions", "codeforces_profiles", "codeforces_ratings", "codeforces_submissions", "luogu_payloads", "schema_metadata", "sync_modules", "sync_operations" },
            ReadTableNames(connection));
    }

    [Fact]
    public async Task Store_RoundTripsCodeforcesPayloadByHandle()
    {
        using var database = new TemporaryDatabase();
        var account = JudgeAccount.Create(JudgeId.Codeforces, "tourist");
        var completedAt = DateTimeOffset.Parse("2026-09-12T01:02:03+00:00");
        var operationId = await database.Store.OpenOperationAsync(account, "generation-1", completedAt, CancellationToken.None);

        await database.Store.AppendModuleAsync(
            operationId,
            new SyncModuleOutcome(
                "PROFILE",
                SyncOperationStatus.Success,
                1,
                1,
                1,
                null,
                new CodeforcesProfilePayload("tourist", 3800, "legend", 4000, "legend")),
            completedAt,
            CancellationToken.None);
        await database.Store.AppendModuleAsync(
            operationId,
            new SyncModuleOutcome(
                "RATING",
                SyncOperationStatus.Success,
                1,
                1,
                1,
                null,
                new CodeforcesRatingsPayload(
                    "tourist",
                    [new CodeforcesRating(99, "Sample Round", 1, 1_725_000_000, 3700, 3800)])),
            completedAt,
            CancellationToken.None);
        await database.Store.AppendModuleAsync(
            operationId,
            new SyncModuleOutcome(
                "SUBMISSIONS",
                SyncOperationStatus.Success,
                1,
                1,
                1,
                null,
                new CodeforcesSubmissionsPayload(
                    "tourist",
                    [new CodeforcesSubmission(123, 99, "A", "Sample", "OK", "GNU C++17", 10, 42, 1024, 1_725_000_001)])),
            completedAt,
            CancellationToken.None);

        var snapshot = await database.Store.GetCodeforcesPayloadAsync("tourist", CancellationToken.None);

        Assert.Equal(new CodeforcesProfilePayload("tourist", 3800, "legend", 4000, "legend"), snapshot.Profile);
        Assert.Equal([new CodeforcesRating(99, "Sample Round", 1, 1_725_000_000, 3700, 3800)], snapshot.Ratings);
        Assert.Equal([new CodeforcesSubmission(123, 99, "A", "Sample", "OK", "GNU C++17", 10, 42, 1024, 1_725_000_001)], snapshot.Submissions);

        await database.Store.AppendModuleAsync(
            operationId,
            new SyncModuleOutcome("RATING", SyncOperationStatus.Success, 0, 0, 0, null, new CodeforcesRatingsPayload("tourist", [])),
            completedAt.AddMinutes(1),
            CancellationToken.None);
        await database.Store.AppendModuleAsync(
            operationId,
            new SyncModuleOutcome("SUBMISSIONS", SyncOperationStatus.Success, 0, 0, 0, null, new CodeforcesSubmissionsPayload("tourist", [])),
            completedAt.AddMinutes(1),
            CancellationToken.None);

        var replacedSnapshot = await database.Store.GetCodeforcesPayloadAsync(" tourist ", CancellationToken.None);
        Assert.Empty(replacedSnapshot.Ratings);
        Assert.Empty(replacedSnapshot.Submissions);
    }

    [Fact]
    public async Task SyncService_PersistsPayloadOnlyAfterSuccessfulModuleWrite()
    {
        using var database = new TemporaryDatabase();
        var account = JudgeAccount.Create(JudgeId.Codeforces, "tourist");
        var adapter = new PayloadAdapter(
            new SyncModuleOutcome(
                "PROFILE",
                SyncOperationStatus.Success,
                1,
                1,
                1,
                null,
                new CodeforcesProfilePayload("tourist", 3800, "legend", 4000, "legend")));
        var service = new SyncService(
            new Dictionary<JudgeId, IJudgeAdapter> { [JudgeId.Codeforces] = adapter },
            database.Store,
            new FixedClock(DateTimeOffset.Parse("2026-09-12T01:02:03+00:00")),
            () => "generation-1");

        var report = await service.RunAsync(account, force: false, CancellationToken.None);
        var snapshot = await database.Store.GetCodeforcesPayloadAsync("tourist", CancellationToken.None);

        Assert.Equal(SyncOperationStatus.Success, report.Status);
        Assert.Equal(new CodeforcesProfilePayload("tourist", 3800, "legend", 4000, "legend"), snapshot.Profile);
    }

    private static IReadOnlyList<string> ReadTableNames(SqliteConnection connection)
    {
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name";
        using var reader = command.ExecuteReader();
        var names = new List<string>();
        while (reader.Read())
        {
            names.Add(reader.GetString(0));
        }

        return names;
    }

    private sealed class PayloadAdapter(SyncModuleOutcome outcome) : IJudgeAdapter
    {
        public JudgeId Judge => JudgeId.Codeforces;

        public Task<IReadOnlyList<SyncModuleOutcome>> SyncAsync(JudgeAccount account, CancellationToken cancellationToken) =>
            Task.FromResult<IReadOnlyList<SyncModuleOutcome>>([outcome]);
    }

    private sealed class FixedClock(DateTimeOffset utcNow) : IClock
    {
        public DateTimeOffset UtcNow { get; } = utcNow;
    }

    private sealed class TemporaryDatabase : IDisposable
    {
        private readonly string _directoryPath = Path.Combine(Path.GetTempPath(), "oj-nexus-payload-tests", Guid.NewGuid().ToString("N"));

        public TemporaryDatabase()
        {
            Directory.CreateDirectory(_directoryPath);
            Store = new SqliteSyncStore(new SqliteConnectionFactory(_directoryPath));
        }

        public string DatabasePath => Path.Combine(_directoryPath, "ojnexus.db");

        public SqliteSyncStore Store { get; }

        public void Dispose()
        {
            SqliteConnection.ClearAllPools();
            Directory.Delete(_directoryPath, recursive: true);
        }
    }
}
