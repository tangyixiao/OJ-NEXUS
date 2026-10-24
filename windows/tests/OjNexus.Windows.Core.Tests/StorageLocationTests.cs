using OjNexus.Windows.Core.Storage;

namespace OjNexus.Windows.Core.Tests;

public sealed class StorageLocationTests
{
    [Theory]
    [InlineData("\\\\server\\share")]
    [InlineData("\\\\?\\C:\\device")]
    [InlineData("\\\\.\\pipe\\ojnexus")]
    [InlineData("C:relative")]
    [InlineData("file:///C:/data")]
    [InlineData("C:\\folder\\file:stream")]
    public async Task AmbiguousOrNonLocalLocationsAreRejectedBeforeCreation(string path)
    {
        var opened = await WindowsStorage.OpenAsync(path, null, CancellationToken.None);
        Assert.False(opened.IsReady);
        Assert.Equal("UNSUPPORTED_LOCATION", opened.Failure?.WireCode);
    }

    [Theory]
    [InlineData(" trailing ")]
    [InlineData("trailing.")]
    public async Task TrailingDotOrSpaceNameIsRejected(string suffix)
    {
        var path = Path.Combine(Path.GetTempPath(), "ojnexus-location-" + Guid.NewGuid().ToString("N") + suffix);
        var opened = await WindowsStorage.OpenAsync(path, null, CancellationToken.None);
        Assert.Equal("UNSUPPORTED_LOCATION", opened.Failure?.WireCode);
        Assert.False(Directory.Exists(path));
    }

    [Fact]
    public async Task DatabaseHardLinkIsDetectedByNativeLinkCount()
    {
        var root = NewRoot();
        Directory.CreateDirectory(root);
        SchemaMigrator.Migrate(new Microsoft.Data.Sqlite.SqliteConnectionStringBuilder
        {
            DataSource = Path.Combine(root, "ojnexus.db"),
            Mode = Microsoft.Data.Sqlite.SqliteOpenMode.ReadWriteCreate,
            Pooling = false
        }.ToString());
        NativeFile.CreateHardLink(Path.Combine(root, "alias.db"), Path.Combine(root, "ojnexus.db"));
        var opened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.Equal("UNSUPPORTED_LOCATION", opened.Failure?.WireCode);
    }

    [Fact]
    public async Task LifecycleLockHardLinkIsDetectedBeforeAcquisition()
    {
        var root = NewRoot(); Directory.CreateDirectory(root);
        var lockPath = Path.Combine(root, "ojnexus.lifecycle.lock");
        await File.WriteAllBytesAsync(lockPath, []);
        NativeFile.CreateHardLink(Path.Combine(root, "alias.lock"), lockPath);
        var opened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.Equal("UNSUPPORTED_LOCATION", opened.Failure?.WireCode);
    }

    [Fact]
    public async Task ControlledReparseEntryIsRejectedBeforeOpeningDatabase()
    {
        var root = NewRoot();
        Directory.CreateDirectory(root);
        var target = Path.Combine(root, "target");
        Directory.CreateDirectory(target);
        var link = Path.Combine(root, "ojnexus.restore.link");
        Directory.CreateSymbolicLink(link, target);
        var opened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.Equal("UNSUPPORTED_LOCATION", opened.Failure?.WireCode);
        Assert.False(File.Exists(Path.Combine(root, "ojnexus.db")));
    }

    [Fact]
    public async Task EveryControlledFileReparseEntryIsRejectedBeforeOpeningDatabase()
    {
        string[] names =
        [
            "ojnexus.db", "ojnexus.lifecycle.lock", "ojnexus.initialized", "ojnexus.initialized.tmp",
            "ojnexus.init.db", "ojnexus.db-wal", "ojnexus.db-shm", "ojnexus.db-journal",
            "ojnexus.init.db-wal", "ojnexus.init.db-shm", "ojnexus.init.db-journal", "ojnexus.restore.file"
        ];

        foreach (var name in names)
        {
            var fileRoot = NewRoot();
            Directory.CreateDirectory(fileRoot);
            var targetFile = Path.Combine(Path.GetTempPath(), "ojnexus-target-" + Guid.NewGuid().ToString("N"));
            await File.WriteAllTextAsync(targetFile, "target");
            File.CreateSymbolicLink(Path.Combine(fileRoot, name), targetFile);
            var fileResult = await WindowsStorage.OpenAsync(fileRoot, null, CancellationToken.None);
            Assert.False(fileResult.IsReady);
            Assert.Equal("UNSUPPORTED_LOCATION", fileResult.Failure?.WireCode);

            var directoryRoot = NewRoot();
            Directory.CreateDirectory(directoryRoot);
            var targetDirectory = Path.Combine(Path.GetTempPath(), "ojnexus-target-dir-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(targetDirectory);
            Directory.CreateSymbolicLink(Path.Combine(directoryRoot, name), targetDirectory);
            var directoryResult = await WindowsStorage.OpenAsync(directoryRoot, null, CancellationToken.None);
            Assert.False(directoryResult.IsReady);
            Assert.Equal("UNSUPPORTED_LOCATION", directoryResult.Failure?.WireCode);
        }
    }

    private static string NewRoot() => Path.Combine(Path.GetTempPath(), "ojnexus-location-" + Guid.NewGuid().ToString("N"));
}
