using OjNexus.Windows.Core.Domain;

namespace OjNexus.Windows.Core.Contracts;

public interface ISyncStore
{
    Task<IReadOnlyList<JudgeAccount>> GetAccountsAsync(CancellationToken cancellationToken);

    Task UpsertAccountAsync(JudgeAccount account, CancellationToken cancellationToken);

    Task<IReadOnlyList<SyncOperation>> GetRecentOperationsAsync(
        JudgeId? judge,
        int limit,
        CancellationToken cancellationToken);

    Task<CodeforcesPayloadSnapshot> GetCodeforcesPayloadAsync(
        string handle,
        CancellationToken cancellationToken);

    Task<AtCoderPayloadSnapshot> GetAtCoderPayloadAsync(
        string handle,
        CancellationToken cancellationToken);

    Task<long> OpenOperationAsync(
        JudgeAccount account,
        string dataGeneration,
        DateTimeOffset startedAt,
        CancellationToken cancellationToken);

    Task AppendModuleAsync(
        long operationId,
        SyncModuleOutcome outcome,
        DateTimeOffset completedAt,
        CancellationToken cancellationToken);

    Task CloseOperationAsync(
        long operationId,
        SyncOperationStatus status,
        SyncError? error,
        DateTimeOffset finishedAt,
        CancellationToken cancellationToken);
}
