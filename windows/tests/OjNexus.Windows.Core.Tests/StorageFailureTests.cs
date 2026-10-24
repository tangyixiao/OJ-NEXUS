using System.ComponentModel;
using System.Security.Cryptography;
using Microsoft.Data.Sqlite;
using OjNexus.Windows.Core.Storage;

namespace OjNexus.Windows.Core.Tests;

public sealed class StorageFailureTests
{
    [Theory]
    [InlineData(32, "STORAGE_BUSY")]
    [InlineData(33, "STORAGE_BUSY")]
    [InlineData(5, "PERMISSION_DENIED")]
    [InlineData(112, "STORAGE_FULL")]
    [InlineData(39, "STORAGE_FULL")]
    public void Win32CodesRetainStableStorageCategories(int nativeCode, string wireCode)
    {
        var failure = StorageExceptionClassifier.Classify(new Win32Exception(nativeCode), "test");
        Assert.Equal(wireCode, failure.Failure.WireCode);
    }

    [Theory]
    [InlineData(5, 5, "STORAGE_BUSY")]
    [InlineData(6, 6, "STORAGE_BUSY")]
    [InlineData(13, 13, "STORAGE_FULL")]
    [InlineData(11, 11, "INTEGRITY_FAILURE")]
    [InlineData(26, 26, "INTEGRITY_FAILURE")]
    [InlineData(3, 3, "PERMISSION_DENIED")]
    [InlineData(8, 8, "PERMISSION_DENIED")]
    [InlineData(8, 264, "RECOVERY_BLOCKED")]
    [InlineData(8, 776, "RECOVERY_BLOCKED")]
    [InlineData(8, 520, "STORAGE_BUSY")]
    public void SqlitePrimaryAndExtendedCodesRetainStableCategories(int primary, int extended, string wireCode)
    {
        var failure = StorageExceptionClassifier.Classify(new SqliteException("synthetic provider case", primary, extended), "test");
        Assert.Equal(wireCode, failure.Failure.WireCode);
    }

    [Theory]
    [InlineData("zero", "INTEGRITY_FAILURE")]
    [InlineData("short", "INTEGRITY_FAILURE")]
    [InlineData("unrelated", "INTEGRITY_FAILURE")]
    [InlineData("absent-metadata", "INTEGRITY_FAILURE")]
    [InlineData("invalid-metadata", "INTEGRITY_FAILURE")]
    [InlineData("metadata-only-v0", "INTEGRITY_FAILURE")]
    [InlineData("future99", "UNSUPPORTED_SCHEMA")]
    [InlineData("corrupt-header", "INTEGRITY_FAILURE")]
    [InlineData("damaged-page", "INTEGRITY_FAILURE")]
    [InlineData("foreign-key", "INTEGRITY_FAILURE")]
    [InlineData("metadata-missing-key", "INTEGRITY_FAILURE")]
    [InlineData("metadata-missing-value", "INTEGRITY_FAILURE")]
    [InlineData("metadata-key-not-primary", "INTEGRITY_FAILURE")]
    [InlineData("metadata-value-nullable", "INTEGRITY_FAILURE")]
    public async Task DamagedOrUnknownDatabaseIsPreservedWithoutMarkerEnrollment(string fixture, string expectedCode)
    {
        var root = NewRoot();
        Directory.CreateDirectory(root);
        var database = Path.Combine(root, "ojnexus.db");
        await CreateDatabaseFixture(fixture, root, database);
        byte[] before;
        using (var stream = File.OpenRead(database)) before = SHA256.HashData(stream);

        var opened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);

        Assert.False(opened.IsReady);
        Assert.Equal(expectedCode, opened.Failure?.WireCode);
        byte[] after;
        using (var stream = File.OpenRead(database)) after = SHA256.HashData(stream);
        Assert.Equal(before, after);
        Assert.False(File.Exists(Path.Combine(root, "ojnexus.initialized")));
    }

    private static async Task CreateDatabaseFixture(string fixture, string root, string database)
    {
        if (fixture == "zero")
        {
            await File.WriteAllBytesAsync(database, []);
            return;
        }
        if (fixture == "short")
        {
            await File.WriteAllBytesAsync(database, "SQLite"u8.ToArray());
            return;
        }

        using (var connection = new SqliteConnection(new SqliteConnectionStringBuilder
        {
            DataSource = database,
            Mode = SqliteOpenMode.ReadWriteCreate,
            Pooling = false
        }.ToString()))
        {
            connection.Open();
            using var command = connection.CreateCommand();
            command.CommandText = fixture switch
            {
                "unrelated" or "absent-metadata" => "CREATE TABLE random_data (value TEXT); INSERT INTO random_data VALUES ('keep');",
                "invalid-metadata" => "CREATE TABLE schema_metadata (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL); INSERT INTO schema_metadata VALUES ('schema_version','unknown');",
                "metadata-missing-key" => "CREATE TABLE schema_metadata (value TEXT NOT NULL); INSERT INTO schema_metadata VALUES ('0');",
                "metadata-missing-value" => "CREATE TABLE schema_metadata (key TEXT NOT NULL PRIMARY KEY); INSERT INTO schema_metadata VALUES ('schema_version');",
                "metadata-key-not-primary" => "CREATE TABLE schema_metadata (key TEXT NOT NULL, value TEXT NOT NULL); INSERT INTO schema_metadata VALUES ('schema_version','7');",
                "metadata-value-nullable" => "CREATE TABLE schema_metadata (key TEXT NOT NULL PRIMARY KEY, value TEXT); INSERT INTO schema_metadata VALUES ('schema_version','7');",
                "metadata-only-v0" => "CREATE TABLE schema_metadata (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL); INSERT INTO schema_metadata VALUES ('schema_version','0');",
                "future99" => "CREATE TABLE schema_metadata (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL); INSERT INTO schema_metadata VALUES ('schema_version','99');",
                _ => string.Empty
            };
            command.ExecuteNonQuery();
        }

        if (fixture == "corrupt-header")
        {
            await using var stream = new FileStream(database, FileMode.Open, FileAccess.Write, FileShare.Read);
            stream.Position = 0;
            await stream.WriteAsync(new byte[] { 0x58 });
            await stream.FlushAsync();
        }
        else if (fixture == "damaged-page")
        {
            SchemaMigrator.Migrate(new SqliteConnectionStringBuilder
            {
                DataSource = database,
                Mode = SqliteOpenMode.ReadWriteCreate,
                Pooling = false,
                ForeignKeys = true
            }.ToString());
            long rootPage;
            long pageSize;
            using (var connection = new SqliteConnection(new SqliteConnectionStringBuilder { DataSource = database, Mode = SqliteOpenMode.ReadOnly, Pooling = false }.ToString()))
            {
                connection.Open();
                using var page = connection.CreateCommand();
                page.CommandText = "SELECT rootpage FROM sqlite_master WHERE type='table' AND name='accounts'";
                rootPage = Convert.ToInt64(page.ExecuteScalar(), System.Globalization.CultureInfo.InvariantCulture);
                using var size = connection.CreateCommand();
                size.CommandText = "PRAGMA page_size";
                pageSize = Convert.ToInt64(size.ExecuteScalar(), System.Globalization.CultureInfo.InvariantCulture);
            }
            using var stream = new FileStream(database, FileMode.Open, FileAccess.Write, FileShare.Read);
            stream.Position = (rootPage - 1) * pageSize;
            stream.WriteByte(0xff);
            stream.Flush(true);
        }
        else if (fixture == "foreign-key")
        {
            SchemaMigrator.Migrate(new SqliteConnectionStringBuilder
            {
                DataSource = database,
                Mode = SqliteOpenMode.ReadWriteCreate,
                Pooling = false,
                ForeignKeys = true
            }.ToString());
            using var connection = new SqliteConnection(new SqliteConnectionStringBuilder { DataSource = database, Pooling = false }.ToString());
            connection.Open();
            using var command = connection.CreateCommand();
            command.CommandText = "PRAGMA foreign_keys=OFF; INSERT INTO sync_operations (judge,data_generation,started_at,status,handle) VALUES ('missing','g','2026-10-24T00:00:00Z','Error','missing');";
            command.ExecuteNonQuery();
        }
    }

    private static string NewRoot() => Path.Combine(Path.GetTempPath(), "ojnexus-failure-test-" + Guid.NewGuid().ToString("N"));
}
