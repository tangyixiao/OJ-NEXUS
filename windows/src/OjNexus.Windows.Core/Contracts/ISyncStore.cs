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

    /// <summary>
    /// Contract-shaped ledger query (phase-136 semantics): contract filters and
    /// sorts with the code-point tie-break, OFFSET pagination over the total
    /// deterministic order, and a summary whose aggregates never derive from
    /// the visible page.
    /// </summary>
    Task<ProblemLedgerPage> QueryProblemsAsync(
        ProblemLedgerQuery query,
        int offset,
        int limit,
        CancellationToken cancellationToken);

    Task<ProblemEntry?> GetProblemDetailAsync(
        JudgeId judge,
        string externalId,
        CancellationToken cancellationToken);

    Task<string?> GetSettingAsync(string key, CancellationToken cancellationToken);

    Task SetSettingAsync(string key, string value, CancellationToken cancellationToken);

    Task<IReadOnlyList<ReviewEntry>> GetDueReviewsAsync(
        JudgeId? judge,
        int limit,
        CancellationToken cancellationToken);

    Task<ReviewEntry> MarkReviewAsync(
        JudgeId judge,
        string externalId,
        ReviewResult result,
        DateTimeOffset now,
        CancellationToken cancellationToken);

    Task<CodeforcesPayloadSnapshot> GetCodeforcesPayloadAsync(
        string handle,
        CancellationToken cancellationToken);

    Task<AtCoderPayloadSnapshot> GetAtCoderPayloadAsync(
        string handle,
        CancellationToken cancellationToken);

    Task<LuoguPayloadSnapshot> GetLuoguPayloadAsync(
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
