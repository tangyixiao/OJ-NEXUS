using OjNexus.Windows.Core.Contracts;
using OjNexus.Windows.Core.Storage;

namespace OjNexus.Windows.Core.Tests;

internal sealed class StorageTestScope : IDisposable
{
    private readonly WindowsStorageSession _session;
    private readonly StorageOperationContext _operation;
    private readonly bool _deleteDirectory;

    public StorageTestScope(string root, bool deleteDirectory = false, OjNexus.Windows.Core.Contracts.IClock? clock = null)
    {
        Root = root;
        _deleteDirectory = deleteDirectory;
        var result = WindowsStorage.OpenAsync(root, clock, CancellationToken.None).GetAwaiter().GetResult();
        _session = result.Session ?? throw new InvalidOperationException(result.Failure?.WireCode ?? "STORAGE_IO_FAILURE");
        _operation = _session.BeginOperation();
        Store = _operation.Store;
    }

    public string Root { get; }
    public string DatabasePath => _session.DatabasePath;
    public ISyncStore Store { get; }

    public void Dispose()
    {
        _operation.Dispose();
        _session.DisposeAsync().AsTask().GetAwaiter().GetResult();
        if (_deleteDirectory && Directory.Exists(Root)) Directory.Delete(Root, recursive: true);
    }
}
