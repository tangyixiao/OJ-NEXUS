using System.Collections.Concurrent;
using OjNexus.Windows.Core.Contracts;
using OjNexus.Windows.Core.Domain;

namespace OjNexus.Windows.Core.Sync;

public sealed class SyncService
{
    private readonly IReadOnlyDictionary<JudgeId, IJudgeAdapter> _adapters;
    private readonly ISyncStore _store;
    private readonly IClock _clock;
    private readonly Func<string> _dataGeneration;
    private readonly ConcurrentDictionary<string, SemaphoreSlim> _accountGates = new(StringComparer.Ordinal);

    public SyncService(
        IReadOnlyDictionary<JudgeId, IJudgeAdapter> adapters,
        ISyncStore store,
        IClock clock,
        Func<string> dataGeneration)
    {
        ArgumentNullException.ThrowIfNull(adapters);
        ArgumentNullException.ThrowIfNull(store);
        ArgumentNullException.ThrowIfNull(clock);
        ArgumentNullException.ThrowIfNull(dataGeneration);
        _adapters = adapters;
        _store = store;
        _clock = clock;
        _dataGeneration = dataGeneration;
    }

    public async Task<SyncReport> RunAsync(
        JudgeAccount account,
        bool force,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(account);

        var gate = _accountGates.GetOrAdd(GetAccountKey(account), static _ => new SemaphoreSlim(1, 1));
        await gate.WaitAsync(cancellationToken);
        try
        {
            return await RunExclusiveAsync(account, force, cancellationToken);
        }
        finally
        {
            gate.Release();
        }
    }

    private async Task<SyncReport> RunExclusiveAsync(
        JudgeAccount account,
        bool force,
        CancellationToken cancellationToken)
    {
        var startedAt = _clock.UtcNow;
        var dataGeneration = _dataGeneration();
        var operationId = await _store.OpenOperationAsync(account, dataGeneration, startedAt, CancellationToken.None);
        var modules = new List<SyncModuleOutcome>();
        var status = SyncOperationStatus.Running;
        SyncError? error = null;

        try
        {
            if (!account.Enabled && !force)
            {
                status = SyncOperationStatus.Error;
                error = SyncError.InvalidConfiguration;
            }
            else if (!_adapters.TryGetValue(account.Judge, out var adapter) || adapter is null)
            {
                status = SyncOperationStatus.Error;
                error = SyncError.UnsupportedJudge;
            }
            else
            {
                cancellationToken.ThrowIfCancellationRequested();
                var outcomes = await adapter.SyncAsync(account, cancellationToken);
                foreach (var outcome in outcomes)
                {
                    cancellationToken.ThrowIfCancellationRequested();
                    var storedOutcome = ToStoredOutcome(outcome);
                    await _store.AppendModuleAsync(operationId, storedOutcome, _clock.UtcNow, CancellationToken.None);
                    modules.Add(storedOutcome);
                }

                status = modules.All(module => module.Status == SyncOperationStatus.Success)
                    ? SyncOperationStatus.Success
                    : SyncOperationStatus.Partial;
            }
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            status = SyncOperationStatus.Cancelled;
            error = SyncError.Cancelled;
        }
        catch (Exception)
        {
            status = SyncOperationStatus.Error;
            error = SyncError.Network;
        }

        var finishedAt = _clock.UtcNow;
        await _store.CloseOperationAsync(operationId, status, error, finishedAt, CancellationToken.None);
        var operation = new SyncOperation(operationId, account, dataGeneration, startedAt, finishedAt, status, modules);
        return new SyncReport(operation, status, error);
    }

    private static string GetAccountKey(JudgeAccount account) =>
        $"{account.Judge}:{account.Handle.Trim().ToUpperInvariant()}";

    private static SyncModuleOutcome ToStoredOutcome(SyncModuleOutcome outcome)
    {
        ArgumentNullException.ThrowIfNull(outcome);

        var failureType = outcome.Status == SyncOperationStatus.Success
            ? null
            : ToFailureCategory(outcome.FailureType);
        return outcome with { FailureType = failureType };
    }

    private static string ToFailureCategory(string? failureType) =>
        Enum.TryParse<SyncError>(failureType, ignoreCase: false, out var category)
            ? category.ToString()
            : SyncError.Api.ToString();
}
