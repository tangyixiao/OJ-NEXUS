using System.Security.Cryptography;
using System.Text;
using Microsoft.Data.Sqlite;
using OjNexus.Windows.Core.Storage;

namespace OjNexus.Windows.Core.Tests;

public sealed class StorageInventoryTests
{
    private static readonly string[] IncompleteNames =
    [
        "ojnexus.initialized.tmp", "ojnexus.init.db", "ojnexus.init.db-wal", "ojnexus.init.db-shm",
        "ojnexus.init.db-journal", "ojnexus.restore.snapshot", "ojnexus.restore.pending"
    ];

    [Theory]
    [MemberData(nameof(IncompleteInventoryCases))]
    public async Task IncompleteEvidenceWithoutDatabaseNeverCreatesAReplacement(string name, bool directory)
    {
        var root = NewRoot(); Directory.CreateDirectory(root);
        AddEvidence(root, name, directory);
        var before = HashEvidence(root, name);
        var opened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.False(opened.IsReady);
        Assert.Equal("RECOVERY_BLOCKED", opened.Failure?.WireCode);
        Assert.False(File.Exists(Path.Combine(root, "ojnexus.db")));
        Assert.Equal(before, HashEvidence(root, name));
    }

    public static IEnumerable<object[]> IncompleteInventoryCases() => IncompleteNames
        .SelectMany(name => new[] { new object[] { name, false }, new object[] { name, true } });

    [Theory]
    [InlineData("ojnexus.db-wal")]
    [InlineData("ojnexus.db-shm")]
    [InlineData("ojnexus.db-journal")]
    public async Task MissingDatabaseWithMainCompanionIsRecoverySourceMissing(string name)
    {
        var root = NewRoot(); Directory.CreateDirectory(root);
        AddEvidence(root, name, directory: false);
        var before = HashEvidence(root, name);
        var opened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.Equal("RECOVERY_SOURCE_MISSING", opened.Failure?.WireCode);
        Assert.Equal(before, HashEvidence(root, name));
        Assert.False(File.Exists(Path.Combine(root, "ojnexus.db")));
    }

    [Theory]
    [InlineData("v1\n")]
    [InlineData("malformed")]
    public async Task MissingDatabaseWithMarkerIsRecoverySourceMissing(string marker)
    {
        var root = NewRoot();
        Directory.CreateDirectory(root);
        var path = Path.Combine(root, "ojnexus.initialized");
        await File.WriteAllTextAsync(path, marker);
        var before = HashEvidence(root, "ojnexus.initialized");

        var opened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);

        Assert.Equal("RECOVERY_SOURCE_MISSING", opened.Failure?.WireCode);
        Assert.False(File.Exists(Path.Combine(root, "ojnexus.db")));
        Assert.Equal(before, HashEvidence(root, "ojnexus.initialized"));
    }

    [Theory]
    [MemberData(nameof(IncompleteInventoryCases))]
    public async Task IncompleteEvidenceBlocksHealthyMarkedDatabase(string name, bool directory)
    {
        var root = NewRoot();
        CreateCurrentDatabase(root);
        WriteMarker(root);
        AddEvidence(root, name, directory);
        var before = HashEvidence(root, name);
        var databaseHash = HashEvidence(root, "ojnexus.db");

        var opened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);

        Assert.False(opened.IsReady);
        Assert.Equal("RECOVERY_BLOCKED", opened.Failure?.WireCode);
        Assert.Equal(before, HashEvidence(root, name));
        Assert.Equal(databaseHash, HashEvidence(root, "ojnexus.db"));
    }

    [Theory]
    [MemberData(nameof(IncompleteInventoryCases))]
    public async Task IncompleteEvidenceBlocksHealthyMarkerlessDatabaseWithoutEnrollment(string name, bool directory)
    {
        var root = NewRoot();
        CreateCurrentDatabase(root);
        AddEvidence(root, name, directory);
        var evidenceHash = HashEvidence(root, name);
        var databaseHash = HashEvidence(root, "ojnexus.db");

        var opened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);

        Assert.False(opened.IsReady);
        Assert.Equal("RECOVERY_BLOCKED", opened.Failure?.WireCode);
        Assert.False(File.Exists(Path.Combine(root, "ojnexus.initialized")));
        Assert.Equal(evidenceHash, HashEvidence(root, name));
        Assert.Equal(databaseHash, HashEvidence(root, "ojnexus.db"));
    }

    [Theory]
    [InlineData("\uFEFFv1\n")]
    [InlineData("v1\nextra")]
    [InlineData("")]
    public async Task MalformedMarkerIsPreservedAndBlocks(string marker)
    {
        var root = NewRoot();
        CreateCurrentDatabase(root);
        await File.WriteAllTextAsync(Path.Combine(root, "ojnexus.initialized"), marker);
        var before = HashEvidence(root, "ojnexus.initialized");
        var databaseHash = HashEvidence(root, "ojnexus.db");

        var opened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);

        Assert.Equal("RECOVERY_BLOCKED", opened.Failure?.WireCode);
        Assert.Equal(before, HashEvidence(root, "ojnexus.initialized"));
        Assert.Equal(databaseHash, HashEvidence(root, "ojnexus.db"));
    }

    [Fact]
    public async Task OversizedMarkerIsPreservedAndBlocks()
    {
        var root = NewRoot();
        CreateCurrentDatabase(root);
        var marker = Path.Combine(root, "ojnexus.initialized");
        var contents = new string('x', 64 * 1024);
        await File.WriteAllTextAsync(marker, contents);
        var databaseHash = HashEvidence(root, "ojnexus.db");

        var opened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);

        Assert.Equal("RECOVERY_BLOCKED", opened.Failure?.WireCode);
        Assert.Equal(contents.Length, new FileInfo(marker).Length);
        Assert.Equal(databaseHash, HashEvidence(root, "ojnexus.db"));
    }

    [Fact]
    public async Task UnrelatedFilesDoNotPreventFreshInitialization()
    {
        var root = NewRoot(); Directory.CreateDirectory(root);
        await File.WriteAllTextAsync(Path.Combine(root, "notes.txt"), "keep me");
        var opened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.True(opened.IsReady, opened.Failure?.WireCode);
        await opened.Session!.DisposeAsync();
        Assert.Equal("keep me", await File.ReadAllTextAsync(Path.Combine(root, "notes.txt")));
    }

    [Fact]
    public async Task ValidCurrentMarkerlessDatabaseEnrollsWithoutChangingDatabaseBytes()
    {
        var root = NewRoot();
        CreateCurrentDatabase(root);
        var databaseHash = HashEvidence(root, "ojnexus.db");
        var opened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.True(opened.IsReady, opened.Failure?.WireCode);
        await opened.Session!.DisposeAsync();
        Assert.Equal(databaseHash, HashEvidence(root, "ojnexus.db"));
        Assert.Equal("v1\n", await File.ReadAllTextAsync(Path.Combine(root, "ojnexus.initialized")));
    }

    [Fact]
    public async Task ActiveValidWalDoesNotPreventMarkerEnrollment()
    {
        var root = NewRoot();
        CreateCurrentDatabase(root);
        using var writer = new SqliteConnection(new SqliteConnectionStringBuilder
        {
            DataSource = Path.Combine(root, "ojnexus.db"),
            Mode = SqliteOpenMode.ReadWrite,
            Pooling = false
        }.ToString());
        writer.Open();
        using (var mode = writer.CreateCommand())
        {
            mode.CommandText = "PRAGMA journal_mode=WAL";
            Assert.Equal("wal", Convert.ToString(mode.ExecuteScalar(), System.Globalization.CultureInfo.InvariantCulture), ignoreCase: true);
        }
        using (var write = writer.CreateCommand())
        {
            write.CommandText = "UPDATE schema_metadata SET value=value WHERE key='schema_version'";
            write.ExecuteNonQuery();
        }
        Assert.True(File.Exists(Path.Combine(root, "ojnexus.db-wal")));

        var opened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);

        Assert.True(opened.IsReady, opened.Failure?.WireCode);
        await opened.Session!.DisposeAsync();
        Assert.Equal("v1\n", File.ReadAllText(Path.Combine(root, "ojnexus.initialized")));
    }

    [Fact]
    public async Task MixedCaseControlledNameFailsClosedWithoutCreatingShadowDatabase()
    {
        var root = NewRoot(); Directory.CreateDirectory(root);
        var shadow = Path.Combine(root, "OJNEXUS.DB");
        await File.WriteAllBytesAsync(shadow, []);
        var opened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.Equal("UNSUPPORTED_LOCATION", opened.Failure?.WireCode);
        Assert.Equal(0, new FileInfo(shadow).Length);
        Assert.False(File.Exists(Path.Combine(root, "ojnexus.initialized")));
    }

    private static void CreateCurrentDatabase(string root)
    {
        Directory.CreateDirectory(root);
        SchemaMigrator.Migrate(new SqliteConnectionStringBuilder
        {
            DataSource = Path.Combine(root, "ojnexus.db"),
            Mode = SqliteOpenMode.ReadWriteCreate,
            Pooling = false,
            ForeignKeys = true
        }.ToString());
    }

    private static void WriteMarker(string root) => File.WriteAllBytes(Path.Combine(root, "ojnexus.initialized"), "v1\n"u8.ToArray());

    private static void AddEvidence(string root, string name, bool directory)
    {
        var path = Path.Combine(root, name);
        if (directory) Directory.CreateDirectory(path);
        else File.WriteAllBytes(path, [0x65, 0x76, 0x69, 0x64]);
    }

    private static byte[] HashEvidence(string root, string name)
    {
        var path = Path.Combine(root, name);
        if (Directory.Exists(path)) return SHA256.HashData(Encoding.UTF8.GetBytes(string.Join("|", Directory.GetFileSystemEntries(path))));
        return SHA256.HashData(File.ReadAllBytes(path));
    }

    private static string NewRoot() => Path.Combine(Path.GetTempPath(), "ojnexus-inventory-" + Guid.NewGuid().ToString("N"));
}
