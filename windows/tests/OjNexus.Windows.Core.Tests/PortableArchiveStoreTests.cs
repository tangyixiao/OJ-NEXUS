using Microsoft.Data.Sqlite;
using OjNexus.Windows.Core.Storage;

namespace OjNexus.Windows.Core.Tests;

public sealed class PortableArchiveStoreTests
{
    private static readonly string FirstIdentity = new('a', 64);
    private static readonly string SecondIdentity = new('c', 64);
    private static readonly string ThirdIdentity = new('e', 64);

    private static readonly PortableArchivePackageRow Package = new(
        Identity: FirstIdentity,
        ScopeId: "scope-1",
        SourcePlatform: "windows",
        GeneratorJson: """{"name":"scope-export","version":"1.0"}""",
        CreatedAtEpochMs: 1_000L,
        CountsJson: """{"notes":1,"problems":2}""");

    private static readonly PortableArchiveRecordRow[] Records =
    [
        new("problems", "cf-1A", "scope-1", "scope", "editable", """{"stableId":"cf-1A"}""", "PORTABLE-V1", false, 0),
        new("problems", "cf-1B", "scope-1", "scope", "editable", """{"stableId":"cf-1B"}""", "PORTABLE-V1", false, 1),
        new("notes", "note-1", "scope-1", "unassigned", "stored_only", """{"stableId":"note-1"}""", "SYNTHETIC-TEST-FIXTURE", true, 2),
    ];

    [Fact]
    public async Task GetPackage_ReturnsNullWhenArchiveIsEmpty()
    {
        using var database = new MigratedDatabase();

        var store = database.CreateStore();
        Assert.Null(await store.GetPackageAsync(CancellationToken.None));
        Assert.Empty(await store.GetRecordsAsync(FirstIdentity, CancellationToken.None));
    }

    [Fact]
    public async Task ReplaceAll_RoundTripsPackageAndRecordsInOrder()
    {
        using var database = new MigratedDatabase();
        var store = database.CreateStore();

        await store.ReplaceAllAsync(Package, Records, CancellationToken.None);

        var package = await store.GetPackageAsync(CancellationToken.None);
        Assert.NotNull(package);
        Assert.Equal(Package, package);
        var records = await store.GetRecordsAsync(FirstIdentity, CancellationToken.None);
        Assert.Equal(Records, records);
    }

    [Fact]
    public async Task ReplaceAll_SwapsTheWholeArchiveWholesale()
    {
        using var database = new MigratedDatabase();
        var store = database.CreateStore();
        await store.ReplaceAllAsync(Package, Records, CancellationToken.None);

        var second = Package with { Identity = SecondIdentity, GeneratorJson = """{"name":"re-import","version":"2.0"}""" };
        var secondRecords = new[] { Records[0] with { SortOrder = 0 } };
        await store.ReplaceAllAsync(second, secondRecords, CancellationToken.None);

        var package = await store.GetPackageAsync(CancellationToken.None);
        Assert.NotNull(package);
        Assert.Equal(second.Identity, package.Identity);
        Assert.Equal(secondRecords, await store.GetRecordsAsync(SecondIdentity, CancellationToken.None));
        Assert.Empty(await store.GetRecordsAsync(FirstIdentity, CancellationToken.None));
    }

    [Fact]
    public async Task ReplaceAll_FailureRollsBackToPreviousArchive()
    {
        using var database = new MigratedDatabase();
        var store = database.CreateStore();
        await store.ReplaceAllAsync(Package, Records, CancellationToken.None);

        var conflicting = Package with { Identity = ThirdIdentity };
        var conflictingRecords = new[]
        {
            Records[0] with { SortOrder = 0 },
            Records[1] with { StableId = Records[0].StableId, SortOrder = 1 },
        };

        await Assert.ThrowsAsync<SqliteException>(
            () => store.ReplaceAllAsync(conflicting, conflictingRecords, CancellationToken.None));

        var package = await store.GetPackageAsync(CancellationToken.None);
        Assert.NotNull(package);
        Assert.Equal(FirstIdentity, package.Identity);
        Assert.Equal(Records, await store.GetRecordsAsync(FirstIdentity, CancellationToken.None));
    }

    [Fact]
    public async Task Clear_RemovesEveryArchivedRow()
    {
        using var database = new MigratedDatabase();
        var store = database.CreateStore();
        await store.ReplaceAllAsync(Package, Records, CancellationToken.None);

        await store.ClearAsync(CancellationToken.None);

        Assert.Null(await store.GetPackageAsync(CancellationToken.None));
        Assert.Empty(await store.GetRecordsAsync(FirstIdentity, CancellationToken.None));
    }

    [Fact]
    public async Task LegacyV7Database_MigratesForwardToV8AndRecreatesPortableTables()
    {
        using var database = new MigratedDatabase();
        ExecuteNonQuery(
            database.DatabasePath,
            """
            DROP TABLE portable_archive_records;
            DROP TABLE portable_archive_packages;
            UPDATE schema_metadata SET value = '7' WHERE key = 'schema_version';
            """);

        SchemaMigrator.Migrate(database.ConnectionString);

        Assert.Equal(9, ReadSchemaVersion(database.DatabasePath));
        var store = database.CreateStore();
        await store.ReplaceAllAsync(Package, Records, CancellationToken.None);
        Assert.NotNull(await store.GetPackageAsync(CancellationToken.None));
    }

    private static void ExecuteNonQuery(string databasePath, string commandText)
    {
        using var connection = new SqliteConnection(new SqliteConnectionStringBuilder
        { DataSource = databasePath, Mode = SqliteOpenMode.ReadWrite, Pooling = false, ForeignKeys = true }.ToString());
        connection.Open();
        using var command = connection.CreateCommand();
        command.CommandText = commandText;
        command.ExecuteNonQuery();
    }

    private static int ReadSchemaVersion(string databasePath)
    {
        using var connection = new SqliteConnection(new SqliteConnectionStringBuilder
        { DataSource = databasePath, Mode = SqliteOpenMode.ReadOnly, Pooling = false }.ToString());
        connection.Open();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT value FROM schema_metadata WHERE key = 'schema_version'";
        return int.Parse(Convert.ToString(command.ExecuteScalar(), System.Globalization.CultureInfo.InvariantCulture)!);
    }

    private sealed class MigratedDatabase : IDisposable
    {
        private readonly string _directoryPath = Path.Combine(Path.GetTempPath(), "oj-nexus-tests", Guid.NewGuid().ToString("N"));
        private readonly OperationOwner _owner = new();

        public MigratedDatabase()
        {
            Directory.CreateDirectory(_directoryPath);
            SchemaMigrator.Migrate(ConnectionString);
        }

        public string DatabasePath => Path.Combine(_directoryPath, "ojnexus.db");

        public string ConnectionString => new SqliteConnectionStringBuilder
        {
            DataSource = DatabasePath,
            ForeignKeys = true,
            Pooling = false,
        }.ToString();

        public PortableArchiveStore CreateStore()
        {
            var permit = new OperationPermit(_owner);
            return new PortableArchiveStore(new SqliteConnectionFactory(DatabasePath, _owner), permit);
        }

        public void Dispose()
        {
            _owner.DisposeAsync().AsTask().GetAwaiter().GetResult();
            if (Directory.Exists(_directoryPath)) Directory.Delete(_directoryPath, recursive: true);
        }
    }
}
