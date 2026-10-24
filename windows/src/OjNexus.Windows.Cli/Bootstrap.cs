using OjNexus.Windows.Core.Contracts;
using OjNexus.Windows.Core.Domain;
using OjNexus.Windows.Core.Network;
using OjNexus.Windows.Core.Storage;
using OjNexus.Windows.Core.Sync;

namespace OjNexus.Windows.Cli;

public sealed class Bootstrap(WindowsStorageSession session, SyncService syncService) : IAsyncDisposable
{
    public WindowsStorageSession Session { get; } = session;
    public SyncService SyncService { get; } = syncService;
    public ValueTask DisposeAsync() => Session.DisposeAsync();

    public static async Task<Bootstrap> OpenAsync(
        string? dataDirectoryOverride = null,
        IReadOnlyDictionary<JudgeId, IJudgeAdapter>? adapters = null,
        CancellationToken cancellationToken = default)
    {
        var opened = await WindowsStorage.OpenAsync(dataDirectoryOverride, new SystemClock(), cancellationToken);
        if (opened.Session is null)
            throw new StorageException(opened.Failure ?? new StorageFailure(StorageFailureCode.IoFailure, "startup"));
        var clock = new SystemClock();
        var effectiveAdapters = adapters ?? new Dictionary<JudgeId, IJudgeAdapter>
        {
            [JudgeId.Codeforces] = new CodeforcesAdapter(PublicHttpClientFactory.Create),
            [JudgeId.AtCoder] = new AtCoderAdapter(PublicHttpClientFactory.Create),
            [JudgeId.Luogu] = new LuoguAdapter(PublicHttpClientFactory.Create),
        };
        return new Bootstrap(opened.Session, new SyncService(effectiveAdapters, clock, static () => "windows-cli-v1"));
    }

    private sealed class SystemClock : IClock
    {
        public DateTimeOffset UtcNow => DateTimeOffset.UtcNow;
    }
}
