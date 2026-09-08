using OjNexus.Windows.Core.Contracts;
using OjNexus.Windows.Core.Domain;

namespace OjNexus.Windows.Core.Sync;

public sealed class InMemorySyncStore : ISyncStore
{
    private readonly object _gate = new();
    private readonly Dictionary<JudgeId, JudgeAccount> _accounts = [];
    private readonly Dictionary<long, StoredOperation> _operations = [];
    private long _nextOperationId = 1;

    public Task<IReadOnlyList<JudgeAccount>> GetAccountsAsync(CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        lock (_gate)
        {
            return Task.FromResult<IReadOnlyList<JudgeAccount>>(_accounts.Values.OrderBy(account => account.Judge).ToArray());
        }
    }

    public Task UpsertAccountAsync(JudgeAccount account, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(account);
        cancellationToken.ThrowIfCancellationRequested();
        lock (_gate)
        {
            _accounts[account.Judge] = account;
        }

        return Task.CompletedTask;
    }

    public Task<IReadOnlyList<SyncOperation>> GetRecentOperationsAsync(
        JudgeId? judge,
        int limit,
        CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        if (limit <= 0)
        {
            return Task.FromResult<IReadOnlyList<SyncOperation>>(Array.Empty<SyncOperation>());
        }

        lock (_gate)
        {
            var operations = _operations.Values
                .Where(operation => judge is null || operation.Account.Judge == judge)
                .OrderByDescending(operation => operation.StartedAt)
                .ThenByDescending(operation => operation.Id)
                .Take(limit)
                .Select(operation => operation.ToSyncOperation())
                .ToArray();
            return Task.FromResult<IReadOnlyList<SyncOperation>>(operations);
        }
    }

    public Task<long> OpenOperationAsync(
        JudgeAccount account,
        string dataGeneration,
        DateTimeOffset startedAt,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(account);
        ArgumentNullException.ThrowIfNull(dataGeneration);
        cancellationToken.ThrowIfCancellationRequested();
        lock (_gate)
        {
            _accounts[account.Judge] = account;
            var id = _nextOperationId++;
            _operations.Add(id, new StoredOperation(id, account, dataGeneration, startedAt));
            return Task.FromResult(id);
        }
    }

    public Task AppendModuleAsync(
        long operationId,
        SyncModuleOutcome outcome,
        DateTimeOffset completedAt,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(outcome);
        cancellationToken.ThrowIfCancellationRequested();
        lock (_gate)
        {
            var operation = GetOperation(operationId);
            if (!operation.ModuleStages.Add(outcome.Stage))
            {
                throw new InvalidOperationException("An operation can contain each module stage only once.");
            }

            operation.Modules.Add(outcome);
        }

        return Task.CompletedTask;
    }

    public Task CloseOperationAsync(
        long operationId,
        SyncOperationStatus status,
        SyncError? error,
        DateTimeOffset finishedAt,
        CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        lock (_gate)
        {
            var operation = GetOperation(operationId);
            operation.Status = status;
            operation.FinishedAt = finishedAt;
            operation.Error = error;
        }

        return Task.CompletedTask;
    }

    private StoredOperation GetOperation(long operationId) =>
        _operations.TryGetValue(operationId, out var operation)
            ? operation
            : throw new KeyNotFoundException($"No operation exists with ID {operationId}.");

    private sealed class StoredOperation(long id, JudgeAccount account, string dataGeneration, DateTimeOffset startedAt)
    {
        public long Id { get; } = id;

        public JudgeAccount Account { get; } = account;

        public string DataGeneration { get; } = dataGeneration;

        public DateTimeOffset StartedAt { get; } = startedAt;

        public DateTimeOffset? FinishedAt { get; set; }

        public SyncOperationStatus Status { get; set; } = SyncOperationStatus.Running;

        public SyncError? Error { get; set; }

        public List<SyncModuleOutcome> Modules { get; } = [];

        public HashSet<string> ModuleStages { get; } = new(StringComparer.Ordinal);

        public SyncOperation ToSyncOperation() =>
            new(Id, Account, DataGeneration, StartedAt, FinishedAt, Status, Modules);
    }
}
