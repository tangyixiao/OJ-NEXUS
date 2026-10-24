using Microsoft.Data.Sqlite;
using OjNexus.Windows.Core.Domain;
using OjNexus.Windows.Core.Storage;

namespace OjNexus.Windows.Core.Tests;

public sealed class StorageMigrationTests
{
    [Theory]
    [InlineData(0, false)] [InlineData(0, true)]
    [InlineData(1, false)] [InlineData(1, true)]
    [InlineData(2, false)] [InlineData(2, true)]
    [InlineData(3, false)] [InlineData(3, true)]
    [InlineData(4, false)] [InlineData(4, true)]
    [InlineData(5, false)] [InlineData(5, true)]
    [InlineData(6, false)] [InlineData(6, true)]
    public async Task RecognizedLegacySchemaMigratesWithRowsPreserved(int version, bool markerPresent)
    {
        var root = NewRoot();
        BuildLegacySchema(root, version);
        if (markerPresent) File.WriteAllBytes(Path.Combine(root, "ojnexus.initialized"), "v1\n"u8.ToArray());

        var opened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.True(opened.IsReady, opened.Failure?.WireCode);
        await using var session = opened.Session!;
        var accounts = await session.RunAsync((store, _) => store.GetAccountsAsync(CancellationToken.None), CancellationToken.None);
        var operations = await session.RunAsync((store, _) => store.GetRecentOperationsAsync(null, 10, CancellationToken.None), CancellationToken.None);
        Assert.Equal("legacy-handle", Assert.Single(accounts).Handle);
        var operation = Assert.Single(operations);
        Assert.Equal(7, operation.Id);
        Assert.Equal("legacy-handle", operation.Account.Handle);
        Assert.Equal(SyncOperationStatus.Success, operation.Status);
        Assert.Equal("PROFILE", Assert.Single(operation.Modules).Stage);
        Assert.Equal(7, ReadSchemaVersion(Path.Combine(root, "ojnexus.db")));
    }

    [Fact]
    public async Task ExclusiveContentionPreventsLegacyMigration()
    {
        var root = NewRoot();
        BuildLegacySchema(root, 6);
        using var sharedOwner = NativeFile.Acquire(Path.Combine(root, "ojnexus.lifecycle.lock"), exclusive: false);
        var opened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.Equal("STORAGE_BUSY", opened.Failure?.WireCode);
        Assert.Equal(6, ReadSchemaVersion(Path.Combine(root, "ojnexus.db")));
    }

    [Fact]
    public async Task MigrationSchemaFailureRollsBackAndRetainsOriginalVersion()
    {
        var root = NewRoot();
        BuildLegacySchema(root, 6);
        using (var connection = Open(root))
        using (var command = connection.CreateCommand())
        {
            command.CommandText = "CREATE TABLE reviews (wrong TEXT);";
            command.ExecuteNonQuery();
        }
        var opened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.Equal("MIGRATION_FAILED", opened.Failure?.WireCode);
        Assert.Equal(6, ReadSchemaVersion(Path.Combine(root, "ojnexus.db")));
        Assert.False(TableExists(Path.Combine(root, "ojnexus.db"), "review_log"));
        Assert.False(File.Exists(Path.Combine(root, "ojnexus.initialized")));
    }

    [Fact]
    public async Task ProviderBusyDuringRealMigrationRetainsBusyCodeAndOriginalRows()
    {
        var root = NewRoot();
        BuildLegacySchema(root, 6);
        using var blocker = Open(root);
        using var blockerTransaction = blocker.BeginTransaction(deferred: false);

        var opened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);

        Assert.False(opened.IsReady);
        Assert.Equal("STORAGE_BUSY", opened.Failure?.WireCode);
        Assert.Equal(6, ReadSchemaVersion(Path.Combine(root, "ojnexus.db")));
        Assert.False(TableExists(Path.Combine(root, "ojnexus.db"), "review_log"));
        using var verify = Open(root);
        using var rows = verify.CreateCommand();
        rows.Transaction = null;
        rows.CommandText = "SELECT COUNT(*) FROM sync_modules WHERE operation_id=7 AND stage='PROFILE'";
        Assert.Equal(1L, Convert.ToInt64(rows.ExecuteScalar(), System.Globalization.CultureInfo.InvariantCulture));
        Assert.False(File.Exists(Path.Combine(root, "ojnexus.initialized")));
    }

    private static void BuildLegacySchema(string root, int version)
    {
        Directory.CreateDirectory(root);
        var path = Path.Combine(root, "ojnexus.db");
        SchemaMigrator.Migrate(new SqliteConnectionStringBuilder
        { DataSource = path, Mode = SqliteOpenMode.ReadWriteCreate, Pooling = false, ForeignKeys = true }.ToString());
        using (var connection = Open(root))
        using (var command = connection.CreateCommand())
        {
            command.CommandText = """
                INSERT INTO accounts (judge, handle, enabled) VALUES ('Codeforces', 'legacy-handle', 1);
                INSERT INTO sync_operations (id, judge, handle, data_generation, started_at, finished_at, status, failure_category)
                VALUES (7, 'Codeforces', 'legacy-handle', 'legacy-generation', '2026-09-07T00:00:00Z', '2026-09-07T00:01:00Z', 'Success', NULL);
                INSERT INTO sync_modules (operation_id, stage, status, attempted_count, imported_count, updated_count, failure_type, completed_at)
                VALUES (7, 'PROFILE', 'Success', 1, 1, 0, NULL, '2026-09-07T00:01:00Z');
                """;
            command.ExecuteNonQuery();
        }

        using (var connection = Open(root))
        using (var command = connection.CreateCommand())
        {
            var statements = new List<string>();
            if (version <= 6) statements.Add("DROP TABLE reviews; DROP TABLE review_log;");
            if (version <= 5) statements.Add("DROP TABLE problems;");
            if (version <= 4) statements.Add("ALTER TABLE sync_operations DROP COLUMN handle;");
            if (version <= 3) statements.Add("DROP TABLE luogu_payloads;");
            if (version <= 2) statements.Add("DROP TABLE atcoder_submissions;");
            if (version <= 1)
            {
                statements.Add("DROP TABLE codeforces_submissions; DROP TABLE codeforces_ratings; DROP TABLE codeforces_profiles;");
            }
            statements.Add($"UPDATE schema_metadata SET value='{version}' WHERE key='schema_version';");
            command.CommandText = string.Join(Environment.NewLine, statements);
            command.ExecuteNonQuery();
        }
    }

    private static int ReadSchemaVersion(string path)
    {
        using var connection = new SqliteConnection(new SqliteConnectionStringBuilder { DataSource = path, Mode = SqliteOpenMode.ReadOnly, Pooling = false }.ToString());
        connection.Open();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT value FROM schema_metadata WHERE key='schema_version'";
        return int.Parse(Convert.ToString(command.ExecuteScalar(), System.Globalization.CultureInfo.InvariantCulture)!);
    }

    private static bool TableExists(string path, string table)
    {
        using var connection = new SqliteConnection(new SqliteConnectionStringBuilder { DataSource = path, Mode = SqliteOpenMode.ReadOnly, Pooling = false }.ToString());
        connection.Open();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type='table' AND name=$name)";
        command.Parameters.AddWithValue("$name", table);
        return Convert.ToInt64(command.ExecuteScalar(), System.Globalization.CultureInfo.InvariantCulture) != 0;
    }

    private static SqliteConnection Open(string root)
    {
        var connection = new SqliteConnection(new SqliteConnectionStringBuilder
        { DataSource = Path.Combine(root, "ojnexus.db"), Mode = SqliteOpenMode.ReadWrite, Pooling = false, ForeignKeys = true }.ToString());
        connection.Open();
        return connection;
    }

    private static string NewRoot() => Path.Combine(Path.GetTempPath(), "ojnexus-migration-" + Guid.NewGuid().ToString("N"));
}
