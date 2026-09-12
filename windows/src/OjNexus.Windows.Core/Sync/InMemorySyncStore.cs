using OjNexus.Windows.Core.Contracts;
using OjNexus.Windows.Core.Domain;

namespace OjNexus.Windows.Core.Sync;

public sealed class InMemorySyncStore : ISyncStore
{
    private readonly object _gate = new();
    private readonly Dictionary<JudgeId, JudgeAccount> _accounts = [];
    private readonly Dictionary<long, StoredOperation> _operations = [];
    private readonly Dictionary<string, CodeforcesProfilePayload> _codeforcesProfiles = new(StringComparer.OrdinalIgnoreCase);
    private readonly Dictionary<string, IReadOnlyList<CodeforcesRating>> _codeforcesRatings = new(StringComparer.OrdinalIgnoreCase);
    private readonly Dictionary<string, IReadOnlyList<CodeforcesSubmission>> _codeforcesSubmissions = new(StringComparer.OrdinalIgnoreCase);
    private readonly Dictionary<string, IReadOnlyList<AtCoderSubmission>> _atcoderSubmissions = new(StringComparer.OrdinalIgnoreCase);
    private long _nextOperationId = 1;

    public Task<IReadOnlyList<JudgeAccount>> GetAccountsAsync(CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        lock (_gate)
        {
            return Task.FromResult<IReadOnlyList<JudgeAccount>>(_accounts.Values.OrderBy(account => account.Judge).ToArray());
        }
    }

    public Task<CodeforcesPayloadSnapshot> GetCodeforcesPayloadAsync(
        string handle,
        CancellationToken cancellationToken)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(handle);
        cancellationToken.ThrowIfCancellationRequested();
        lock (_gate)
        {
            var normalizedHandle = handle.Trim();
            return Task.FromResult(new CodeforcesPayloadSnapshot(
                _codeforcesProfiles.GetValueOrDefault(normalizedHandle),
                _codeforcesRatings.GetValueOrDefault(normalizedHandle, Array.Empty<CodeforcesRating>()),
                _codeforcesSubmissions.GetValueOrDefault(normalizedHandle, Array.Empty<CodeforcesSubmission>())));
        }
    }

    public Task<AtCoderPayloadSnapshot> GetAtCoderPayloadAsync(
        string handle,
        CancellationToken cancellationToken)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(handle);
        cancellationToken.ThrowIfCancellationRequested();
        lock (_gate)
        {
            return Task.FromResult(new AtCoderPayloadSnapshot(
                _atcoderSubmissions.GetValueOrDefault(handle.Trim(), Array.Empty<AtCoderSubmission>())));
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
            var normalizedOutcome = ModuleFailureType.Normalize(outcome);
            operation.Modules.RemoveAll(existing => StringComparer.Ordinal.Equals(existing.Stage, outcome.Stage));
            operation.Modules.Add(normalizedOutcome);
            switch (normalizedOutcome.Payload)
            {
                case CodeforcesProfilePayload profile:
                    _codeforcesProfiles[profile.Handle] = profile;
                    break;
                case CodeforcesRatingsPayload ratings:
                    _codeforcesRatings[ratings.Handle] = ratings.Items.ToArray();
                    break;
                case CodeforcesSubmissionsPayload submissions:
                    _codeforcesSubmissions[submissions.Handle] = submissions.Items.ToArray();
                    break;
                case AtCoderSubmissionsPayload atcoderSubmissions:
                    _atcoderSubmissions[atcoderSubmissions.Handle] = atcoderSubmissions.Items.ToArray();
                    break;
            }
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

        public SyncOperation ToSyncOperation() =>
            new(Id, Account, DataGeneration, StartedAt, FinishedAt, Status, Modules);
    }
}
