using OjNexus.Windows.Core.Contracts;
using OjNexus.Windows.Core.Domain;
using OjNexus.Windows.Core.Sync;

namespace OjNexus.Windows.Core.Tests;

internal sealed class FaultingSyncStore(ISyncStore inner, Func<string, Exception?> failure, Func<string, Task>? before = null) : ISyncStore
{
    private async Task<T> Run<T>(string operation, Func<Task<T>> action)
    {
        if (before is not null) await before(operation);
        if (failure(operation) is { } error) throw error;
        return await action();
    }
    private async Task Run(string operation, Func<Task> action)
    {
        if (before is not null) await before(operation);
        if (failure(operation) is { } error) throw error;
        await action();
    }

    public Task<IReadOnlyList<JudgeAccount>> GetAccountsAsync(CancellationToken cancellationToken) => Run(nameof(GetAccountsAsync), () => inner.GetAccountsAsync(cancellationToken));
    public Task UpsertAccountAsync(JudgeAccount account, CancellationToken cancellationToken) => Run(nameof(UpsertAccountAsync), () => inner.UpsertAccountAsync(account, cancellationToken));
    public Task<IReadOnlyList<SyncOperation>> GetRecentOperationsAsync(JudgeId? judge, int limit, CancellationToken cancellationToken) => Run(nameof(GetRecentOperationsAsync), () => inner.GetRecentOperationsAsync(judge, limit, cancellationToken));
    public Task<IReadOnlyList<ProblemEntry>> GetProblemsAsync(JudgeId? judge, ProblemStatus? status, int limit, CancellationToken cancellationToken) => Run(nameof(GetProblemsAsync), () => inner.GetProblemsAsync(judge, status, limit, cancellationToken));
    public Task<IReadOnlyList<ReviewEntry>> GetDueReviewsAsync(JudgeId? judge, int limit, CancellationToken cancellationToken) => Run(nameof(GetDueReviewsAsync), () => inner.GetDueReviewsAsync(judge, limit, cancellationToken));
    public Task<ReviewEntry> MarkReviewAsync(JudgeId judge, string externalId, ReviewResult result, DateTimeOffset now, CancellationToken cancellationToken) => Run(nameof(MarkReviewAsync), () => inner.MarkReviewAsync(judge, externalId, result, now, cancellationToken));
    public Task<CodeforcesPayloadSnapshot> GetCodeforcesPayloadAsync(string handle, CancellationToken cancellationToken) => Run(nameof(GetCodeforcesPayloadAsync), () => inner.GetCodeforcesPayloadAsync(handle, cancellationToken));
    public Task<AtCoderPayloadSnapshot> GetAtCoderPayloadAsync(string handle, CancellationToken cancellationToken) => Run(nameof(GetAtCoderPayloadAsync), () => inner.GetAtCoderPayloadAsync(handle, cancellationToken));
    public Task<LuoguPayloadSnapshot> GetLuoguPayloadAsync(string handle, CancellationToken cancellationToken) => Run(nameof(GetLuoguPayloadAsync), () => inner.GetLuoguPayloadAsync(handle, cancellationToken));
    public Task<long> OpenOperationAsync(JudgeAccount account, string dataGeneration, DateTimeOffset startedAt, CancellationToken cancellationToken) => Run(nameof(OpenOperationAsync), () => inner.OpenOperationAsync(account, dataGeneration, startedAt, cancellationToken));
    public Task AppendModuleAsync(long operationId, SyncModuleOutcome outcome, DateTimeOffset completedAt, CancellationToken cancellationToken) => Run(nameof(AppendModuleAsync), () => inner.AppendModuleAsync(operationId, outcome, completedAt, cancellationToken));
    public Task CloseOperationAsync(long operationId, SyncOperationStatus status, SyncError? error, DateTimeOffset finishedAt, CancellationToken cancellationToken) => Run(nameof(CloseOperationAsync), () => inner.CloseOperationAsync(operationId, status, error, finishedAt, cancellationToken));
}
