using System.Text;
using Microsoft.Data.Sqlite;
using OjNexus.Windows.Core.Portable;
using OjNexus.Windows.Core.Storage;

namespace OjNexus.Windows.Core.Tests.Portable;

/// <summary>
/// Repository tests run against the REAL guarded store (integration flavor):
/// the store's own unit tests cover its transactional behavior, and faking a
/// sealed SQLite-backed class would only restate the fake's own assumptions.
/// </summary>
public sealed class PortableRepositoryTests
{
    private const string PackageJson = """
        {"formatVersion":1,"scopeId":"s1","sourcePlatform":"android","generator":{"name":"scope-export","version":"1.0"},"createdAtEpochMs":1,"counts":{"problems":1,"attempts":0,"notes":1,"reviews":0,"sessions":0},"problems":[{"stableId":"p1","sourceScopeId":"s1","origin":"scope","judge":"codeforces","externalId":"1A","title":"T","solved":false,"dayKey":"2026-01-01"}],"attempts":[],"notes":[{"stableId":"n1","sourceScopeId":"s1","origin":"scope","problemId":"p1","keyInsight":"k","implementationNotes":"i","complexity":"c","general":"g","updatedEpochMs":1,"dayKey":"2026-01-01","extra":{"synthetic":true}}],"reviews":[],"sessions":[]}
        """;

    [Fact]
    public async Task Preview_IsPureReadAndNeverTouchesTheStore()
    {
        using var database = new MigratedDatabase();
        var repository = database.CreateRepository();

        var preview = await repository.PreviewAsync(Encoding.UTF8.GetBytes(PackageJson), CancellationToken.None);

        Assert.Equal("s1", preview.ScopeId);
        Assert.Equal(1, preview.EditableCount); // problems have live domain UI
        Assert.Equal(1, preview.StoredOnlyCount); // notes are stored-only
        Assert.True(preview.AcceptAll);
        Assert.Empty(preview.Conflicts);
        Assert.Null(await database.PeekPackageAsync());
    }

    [Fact]
    public async Task Apply_SwapsTheArchiveWithTieredRows()
    {
        using var database = new MigratedDatabase();
        var repository = database.CreateRepository();

        var result = await repository.ApplyAsync(Encoding.UTF8.GetBytes(PackageJson), CancellationToken.None);

        Assert.False(result.Identical);
        Assert.Equal(2, result.RowsUpserted); // 2 rows: 1 problem + 1 note
        Assert.Equal(0, result.RowsDeleted);
        Assert.Equal(1, result.EditableCount);
        Assert.Equal(1, result.StoredOnlyCount);
        Assert.Equal(0, result.RejectedCount);
        var package = await database.PeekPackageAsync();
        Assert.NotNull(package);
        var records = await database.PeekRecordsAsync(package!.Identity);
        Assert.Equal(2, records.Count);
        Assert.Equal(new[] { "editable", "stored_only" }, records.Select(r => r.State).ToArray());
        Assert.All(records, r => Assert.Equal("SYNTHETIC-TEST-FIXTURE", r.SourceTag));
    }

    [Fact]
    public async Task Reimport_OfIdenticalIdentity_IsANoOp()
    {
        using var database = new MigratedDatabase();
        var repository = database.CreateRepository();
        await repository.ApplyAsync(Encoding.UTF8.GetBytes(PackageJson), CancellationToken.None);

        var result = await repository.ApplyAsync(Encoding.UTF8.GetBytes(PackageJson), CancellationToken.None);

        Assert.True(result.Identical);
        Assert.Equal(0, result.RowsUpserted);
        Assert.Equal(0, result.RowsDeleted);
        var package = await database.PeekPackageAsync();
        var records = await database.PeekRecordsAsync(package!.Identity);
        Assert.Equal(2, records.Count); // still the original two rows, not doubled
    }

    [Fact]
    public async Task ContractReject_LeavesTheStoreUntouched()
    {
        using var database = new MigratedDatabase();
        var repository = database.CreateRepository();
        await repository.ApplyAsync(Encoding.UTF8.GetBytes(PackageJson), CancellationToken.None);

        var reject = """{"formatVersion":2}"""u8.ToArray();
        await Assert.ThrowsAsync<PortableContractException>(
            () => repository.ApplyAsync(reject, CancellationToken.None));

        var package = await database.PeekPackageAsync();
        Assert.NotNull(package);
        var records = await database.PeekRecordsAsync(package!.Identity);
        Assert.Equal(2, records.Count);
    }

    [Fact]
    public async Task Cancel_WithNoInFlightApply_IsANoOpThatNeverWrites()
    {
        // Android semantics: Cancel aborts the CURRENT transfer. A cancel with
        // no transfer in flight is a no-op — the next apply runs normally.
        using var database = new MigratedDatabase();
        var repository = database.CreateRepository();
        repository.Cancel();

        var result = await repository.ApplyAsync(Encoding.UTF8.GetBytes(PackageJson), CancellationToken.None);

        Assert.False(result.Identical);
        Assert.Equal(2, result.RowsUpserted);
        Assert.NotNull(await database.PeekPackageAsync());
    }

    [Fact]
    public async Task Export_RebuildsCanonicalBytesWithTheStoredIdentity()
    {
        using var database = new MigratedDatabase();
        var repository = database.CreateRepository();
        var preview = await repository.PreviewAsync(Encoding.UTF8.GetBytes(PackageJson), CancellationToken.None);
        await repository.ApplyAsync(Encoding.UTF8.GetBytes(PackageJson), CancellationToken.None);

        var rebuilt = await repository.ExportAsync(CancellationToken.None);

        var identity = Convert.ToHexString(System.Security.Cryptography.SHA256.HashData(rebuilt)).ToLowerInvariant();
        Assert.Equal(preview.Identity, identity);
    }

    [Fact]
    public async Task Export_OnAnEmptyArchive_ThrowsHonestly()
    {
        using var database = new MigratedDatabase();
        var repository = database.CreateRepository();

        await Assert.ThrowsAsync<InvalidOperationException>(
            () => repository.ExportAsync(CancellationToken.None));
    }

    [Fact]
    public async Task Reimport_ThenExport_RoundTripsTheGoldenPackage()
    {
        using var database = new MigratedDatabase();
        var repository = database.CreateRepository();
        var golden = File.ReadAllBytes(GoldenPackagePath());
        var preview = await repository.PreviewAsync(golden, CancellationToken.None);
        await repository.ApplyAsync(golden, CancellationToken.None);

        var exported = await repository.ExportAsync(CancellationToken.None);

        var identity = Convert.ToHexString(System.Security.Cryptography.SHA256.HashData(exported)).ToLowerInvariant();
        Assert.Equal(preview.Identity, identity);
    }

    private static string GoldenPackagePath() =>
        Path.Combine(AppContext.BaseDirectory, "portable", "golden", "pw-p01.json");

    private static void ExecuteNonQuery(string databasePath, string commandText)
    {
        using var connection = new SqliteConnection(new SqliteConnectionStringBuilder
        { DataSource = databasePath, Mode = SqliteOpenMode.ReadWrite, Pooling = false, ForeignKeys = true }.ToString());
        connection.Open();
        using var command = connection.CreateCommand();
        command.CommandText = commandText;
        command.ExecuteNonQuery();
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

        public PortableRepository CreateRepository()
        {
            var permit = new OperationPermit(_owner);
            var store = new PortableArchiveStore(new SqliteConnectionFactory(DatabasePath, _owner), permit);
            return new PortableRepository(new PortableCodec(), store);
        }

        public async Task<PortableArchivePackageRow?> PeekPackageAsync()
        {
            var permit = new OperationPermit(_owner);
            var store = new PortableArchiveStore(new SqliteConnectionFactory(DatabasePath, _owner), permit);
            return await store.GetPackageAsync(CancellationToken.None);
        }

        public async Task<IReadOnlyList<PortableArchiveRecordRow>> PeekRecordsAsync(string identity)
        {
            var permit = new OperationPermit(_owner);
            var store = new PortableArchiveStore(new SqliteConnectionFactory(DatabasePath, _owner), permit);
            return await store.GetRecordsAsync(identity, CancellationToken.None);
        }

        public void Dispose()
        {
            _owner.DisposeAsync().AsTask().GetAwaiter().GetResult();
            if (Directory.Exists(_directoryPath)) Directory.Delete(_directoryPath, recursive: true);
        }
    }
}
