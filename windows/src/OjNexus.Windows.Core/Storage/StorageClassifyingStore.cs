using System.ComponentModel;
using Microsoft.Data.Sqlite;
using OjNexus.Windows.Core.Contracts;
using OjNexus.Windows.Core.Domain;

namespace OjNexus.Windows.Core.Storage;

internal sealed class StorageClassifyingStore(ISyncStore inner) : ISyncStore
{
    private static bool IsStorageFailure(Exception exception) => exception is StorageException or SqliteException
        or IOException or UnauthorizedAccessException or Win32Exception;

    private static async Task<T> Run<T>(Func<Task<T>> action, string stage)
    {
        try { return await action().ConfigureAwait(false); }
        catch (Exception exception) when (IsStorageFailure(exception))
        {
            if (exception is StorageException) throw;
            throw StorageException.ForPersistence(exception, stage);
        }
    }

    private static async Task Run(Func<Task> action, string stage)
    {
        try { await action().ConfigureAwait(false); }
        catch (Exception exception) when (IsStorageFailure(exception))
        {
            if (exception is StorageException) throw;
            throw StorageException.ForPersistence(exception, stage);
        }
    }

    public Task<IReadOnlyList<JudgeAccount>> GetAccountsAsync(CancellationToken cancellationToken) => Run(() => inner.GetAccountsAsync(cancellationToken), "read-accounts");
    public Task UpsertAccountAsync(JudgeAccount account, CancellationToken cancellationToken) => Run(() => inner.UpsertAccountAsync(account, cancellationToken), "save-account");
    public Task<IReadOnlyList<SyncOperation>> GetRecentOperationsAsync(JudgeId? judge, int limit, CancellationToken cancellationToken) => Run(() => inner.GetRecentOperationsAsync(judge, limit, cancellationToken), "read-history");
    public Task<IReadOnlyList<ProblemEntry>> GetProblemsAsync(JudgeId? judge, ProblemStatus? status, int limit, CancellationToken cancellationToken) => Run(() => inner.GetProblemsAsync(judge, status, limit, cancellationToken), "read-problems");
    public Task<IReadOnlyList<ReviewEntry>> GetDueReviewsAsync(JudgeId? judge, int limit, CancellationToken cancellationToken) => Run(() => inner.GetDueReviewsAsync(judge, limit, cancellationToken), "read-reviews");
    public Task<ReviewEntry> MarkReviewAsync(JudgeId judge, string externalId, ReviewResult result, DateTimeOffset now, CancellationToken cancellationToken) => Run(() => inner.MarkReviewAsync(judge, externalId, result, now, cancellationToken), "mark-review");
    public Task<CodeforcesPayloadSnapshot> GetCodeforcesPayloadAsync(string handle, CancellationToken cancellationToken) => Run(() => inner.GetCodeforcesPayloadAsync(handle, cancellationToken), "read-codeforces");
    public Task<AtCoderPayloadSnapshot> GetAtCoderPayloadAsync(string handle, CancellationToken cancellationToken) => Run(() => inner.GetAtCoderPayloadAsync(handle, cancellationToken), "read-atcoder");
    public Task<LuoguPayloadSnapshot> GetLuoguPayloadAsync(string handle, CancellationToken cancellationToken) => Run(() => inner.GetLuoguPayloadAsync(handle, cancellationToken), "read-luogu");
    public Task<long> OpenOperationAsync(JudgeAccount account, string dataGeneration, DateTimeOffset startedAt, CancellationToken cancellationToken) => Run(() => inner.OpenOperationAsync(account, dataGeneration, startedAt, cancellationToken), "open-operation");
    public Task AppendModuleAsync(long operationId, SyncModuleOutcome outcome, DateTimeOffset completedAt, CancellationToken cancellationToken) => Run(() => inner.AppendModuleAsync(operationId, outcome, completedAt, cancellationToken), "append-module");
    public Task CloseOperationAsync(long operationId, SyncOperationStatus status, SyncError? error, DateTimeOffset finishedAt, CancellationToken cancellationToken) => Run(() => inner.CloseOperationAsync(operationId, status, error, finishedAt, cancellationToken), "close-operation");
}
