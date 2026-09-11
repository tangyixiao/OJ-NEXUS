using OjNexus.Windows.Core.Contracts;
using OjNexus.Windows.Core.Domain;
using OjNexus.Windows.Core.Storage;
using OjNexus.Windows.Core.Sync;

namespace OjNexus.Windows.Cli;

public sealed class Bootstrap
{
    private Bootstrap(string dataDirectory, SqliteConnectionFactory connectionFactory, SqliteSyncStore store, SyncService syncService)
    {
        DataDirectory = dataDirectory;
        ConnectionFactory = connectionFactory;
        Store = store;
        SyncService = syncService;
    }

    public string DataDirectory { get; }
    public SqliteConnectionFactory ConnectionFactory { get; }
    public SqliteSyncStore Store { get; }
    public SyncService SyncService { get; }

    public static Bootstrap Create(string? dataDirectoryOverride = null)
    {
        var dataDirectory = WindowsPaths.GetDataDirectory(dataDirectoryOverride);
        var connectionFactory = new SqliteConnectionFactory(dataDirectory);
        var store = new SqliteSyncStore(connectionFactory);
        IReadOnlyDictionary<JudgeId, IJudgeAdapter> adapters = new Dictionary<JudgeId, IJudgeAdapter>();
        var syncService = new SyncService(adapters, store, new SystemClock(), static () => "windows-cli-v1");
        return new Bootstrap(dataDirectory, connectionFactory, store, syncService);
    }

    private sealed class SystemClock : IClock
    {
        public DateTimeOffset UtcNow => DateTimeOffset.UtcNow;
    }
}
