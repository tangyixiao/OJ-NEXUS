using OjNexus.Windows.Core.Contracts;
using OjNexus.Windows.Core.Domain;
using OjNexus.Windows.Core.Sync;

namespace OjNexus.Windows.Core.Storage;

public sealed class StorageOperationScope(ISyncStore store) : IStorageOperationSource
{
    private readonly OperationOwner _owner = new();

    public StorageOperationContext BeginOperation(CancellationToken cancellationToken = default) =>
        _owner.Begin(permit => new ScopedMemoryStore(store, _owner, permit), cancellationToken);

    public async Task RunAsync(Func<ISyncStore, CancellationToken, Task> work, CancellationToken cancellationToken)
    {
        await using var operation = BeginOperation(cancellationToken);
        await work(operation.Store, operation.CancellationToken).ConfigureAwait(false);
    }

    public async Task<T> RunAsync<T>(Func<ISyncStore, CancellationToken, Task<T>> work, CancellationToken cancellationToken)
    {
        await using var operation = BeginOperation(cancellationToken);
        return await work(operation.Store, operation.CancellationToken).ConfigureAwait(false);
    }

    public ValueTask DisposeAsync() => _owner.DisposeAsync();
}

public sealed class StorageOperationContext : IAsyncDisposable, IDisposable
{
    private readonly OperationOwner _owner;
    private readonly OperationPermit _permit;
    private readonly CancellationTokenSource _linked;
    private int _disposed;

    internal StorageOperationContext(OperationOwner owner, OperationPermit permit, ISyncStore store, CancellationTokenSource linked)
    {
        _owner = owner;
        _permit = permit;
        Store = store;
        _linked = linked;
    }

    public ISyncStore Store { get; }
    internal OperationPermit Permit => _permit;
    public CancellationToken CancellationToken => _linked.Token;

    public void Dispose()
    {
        if (Interlocked.Exchange(ref _disposed, 1) != 0) return;
        _owner.ClosePermit(_permit);
        _linked.Dispose();
    }

    public ValueTask DisposeAsync()
    {
        Dispose();
        return ValueTask.CompletedTask;
    }
}

internal sealed class ScopedMemoryStore(ISyncStore inner, OperationOwner owner, OperationPermit permit) : ISyncStore
{
    private async Task<T> Run<T>(Func<Task<T>> action)
    {
        using var call = owner.EnterStoreCall(permit);
        return await action().ConfigureAwait(false);
    }

    private async Task Run(Func<Task> action)
    {
        using var call = owner.EnterStoreCall(permit);
        await action().ConfigureAwait(false);
    }

    public Task<IReadOnlyList<JudgeAccount>> GetAccountsAsync(CancellationToken cancellationToken) => Run(() => inner.GetAccountsAsync(cancellationToken));
    public Task UpsertAccountAsync(JudgeAccount account, CancellationToken cancellationToken) => Run(() => inner.UpsertAccountAsync(account, cancellationToken));
    public Task<IReadOnlyList<SyncOperation>> GetRecentOperationsAsync(JudgeId? judge, int limit, CancellationToken cancellationToken) => Run(() => inner.GetRecentOperationsAsync(judge, limit, cancellationToken));
    public Task<IReadOnlyList<ProblemEntry>> GetProblemsAsync(JudgeId? judge, ProblemStatus? status, int limit, CancellationToken cancellationToken) => Run(() => inner.GetProblemsAsync(judge, status, limit, cancellationToken));
    public Task<IReadOnlyList<ReviewEntry>> GetDueReviewsAsync(JudgeId? judge, int limit, CancellationToken cancellationToken) => Run(() => inner.GetDueReviewsAsync(judge, limit, cancellationToken));
    public Task<ReviewEntry> MarkReviewAsync(JudgeId judge, string externalId, ReviewResult result, DateTimeOffset now, CancellationToken cancellationToken) => Run(() => inner.MarkReviewAsync(judge, externalId, result, now, cancellationToken));
    public Task<CodeforcesPayloadSnapshot> GetCodeforcesPayloadAsync(string handle, CancellationToken cancellationToken) => Run(() => inner.GetCodeforcesPayloadAsync(handle, cancellationToken));
    public Task<AtCoderPayloadSnapshot> GetAtCoderPayloadAsync(string handle, CancellationToken cancellationToken) => Run(() => inner.GetAtCoderPayloadAsync(handle, cancellationToken));
    public Task<LuoguPayloadSnapshot> GetLuoguPayloadAsync(string handle, CancellationToken cancellationToken) => Run(() => inner.GetLuoguPayloadAsync(handle, cancellationToken));
    public Task<long> OpenOperationAsync(JudgeAccount account, string dataGeneration, DateTimeOffset startedAt, CancellationToken cancellationToken) => Run(() => inner.OpenOperationAsync(account, dataGeneration, startedAt, cancellationToken));
    public Task AppendModuleAsync(long operationId, SyncModuleOutcome outcome, DateTimeOffset completedAt, CancellationToken cancellationToken) => Run(() => inner.AppendModuleAsync(operationId, outcome, completedAt, cancellationToken));
    public Task CloseOperationAsync(long operationId, SyncOperationStatus status, SyncError? error, DateTimeOffset finishedAt, CancellationToken cancellationToken) => Run(() => inner.CloseOperationAsync(operationId, status, error, finishedAt, cancellationToken));
}
