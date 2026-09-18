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
        JudgeAccount validatedAccount;
        try
        {
            validatedAccount = JudgeAccount.Create(account.Judge, account.Handle) with { Enabled = account.Enabled };
        }
        catch (ArgumentException)
        {
            return InvalidAccountReport(account);
        }

        var startedAt = _clock.UtcNow;
        var dataGeneration = _dataGeneration();
        var operationId = await _store.OpenOperationAsync(validatedAccount, dataGeneration, startedAt, CancellationToken.None);
        var modules = new List<SyncModuleOutcome>();
        var status = SyncOperationStatus.Running;
        SyncError? error = null;

        try
        {
            if (!validatedAccount.Enabled && !force)
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
                var outcomes = await adapter.SyncAsync(validatedAccount, cancellationToken);
                foreach (var outcome in outcomes)
                {
                    cancellationToken.ThrowIfCancellationRequested();
                    var storedOutcome = ToStoredOutcome(outcome);
                    await _store.AppendModuleAsync(operationId, storedOutcome, _clock.UtcNow, CancellationToken.None);
                    MoveReplacementToEnd(modules, storedOutcome);
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
        var operation = new SyncOperation(operationId, validatedAccount, dataGeneration, startedAt, finishedAt, status, modules, error);
        return new SyncReport(operation, status, error);
    }

    private SyncReport InvalidAccountReport(JudgeAccount account)
    {
        var timestamp = _clock.UtcNow;
        var operation = new SyncOperation(
            0,
            account,
            _dataGeneration(),
            timestamp,
            timestamp,
            SyncOperationStatus.Error,
            Array.Empty<SyncModuleOutcome>(),
            SyncError.InvalidConfiguration);
        return new SyncReport(operation, SyncOperationStatus.Error, SyncError.InvalidConfiguration);
    }

    private static string GetAccountKey(JudgeAccount account) =>
        $"{account.Judge}:{account.Handle.Trim().ToUpperInvariant()}";

    private static SyncModuleOutcome ToStoredOutcome(SyncModuleOutcome outcome) => ModuleFailureType.Normalize(outcome);

    private static void MoveReplacementToEnd(List<SyncModuleOutcome> modules, SyncModuleOutcome outcome)
    {
        modules.RemoveAll(existing => StringComparer.Ordinal.Equals(existing.Stage, outcome.Stage));
        modules.Add(outcome);
    }
}
