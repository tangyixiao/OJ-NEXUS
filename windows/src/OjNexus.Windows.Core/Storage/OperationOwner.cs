using OjNexus.Windows.Core.Contracts;

namespace OjNexus.Windows.Core.Storage;

internal sealed class OperationPermit
{
    internal OperationPermit(OperationOwner owner) => Owner = owner;
    internal OperationOwner Owner { get; }
    internal bool IsActive { get; set; } = true;
}

internal sealed class OperationOwner(Func<ValueTask>? closeAction = null)
{
    private readonly object _gate = new();
    private readonly CancellationTokenSource _shutdown = new();
    private readonly Func<ValueTask>? _closeAction = closeAction;
    private Task? _shutdownTask;
    private TaskCompletionSource _drained = NewSignal();
    private int _operations;
    private int _storeCalls;
    private int _nativeLeases;
    private bool _draining;
    private bool _closed;

    internal StorageOperationContext Begin(Func<OperationPermit, ISyncStore> storeFactory, CancellationToken caller)
    {
        OperationPermit permit;
        lock (_gate)
        {
            if (_draining || _closed) throw Closed();
            _operations++;
            permit = new OperationPermit(this);
        }

        CancellationTokenSource linked;
        try
        {
            linked = CancellationTokenSource.CreateLinkedTokenSource(caller, _shutdown.Token);
            return new StorageOperationContext(this, permit, storeFactory(permit), linked);
        }
        catch
        {
            lock (_gate)
            {
                permit.IsActive = false;
                _operations--;
                SignalIfDrained();
            }
            throw;
        }
    }

    internal OperationCallLease EnterStoreCall(OperationPermit permit)
    {
        lock (_gate)
        {
            ValidatePermitUnderGate(permit);
            _storeCalls++;
            return new OperationCallLease(this, native: false);
        }
    }

    internal OperationCallLease ReserveNativeLease(OperationPermit permit)
    {
        lock (_gate)
        {
            ValidatePermitUnderGate(permit);
            _nativeLeases++;
            return new OperationCallLease(this, native: true);
        }
    }

    internal void ValidatePermit(OperationPermit permit)
    {
        lock (_gate) ValidatePermitUnderGate(permit);
    }

    internal void ClosePermit(OperationPermit permit)
    {
        lock (_gate)
        {
            ValidateOwnerUnderGate(permit);
            if (!permit.IsActive) return;
            permit.IsActive = false;
            _operations--;
            SignalIfDrained();
        }
    }

    internal ValueTask DisposeAsync()
    {
        Task task;
        TaskCompletionSource? start = null;
        lock (_gate)
        {
            if (_shutdownTask is not null) return new ValueTask(_shutdownTask);
            _draining = true;
            var completion = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
            _shutdownTask = task = completion.Task;
            start = completion;
            SignalIfDrained();
        }

        _ = RunShutdownAsync(start);
        return new ValueTask(task);
    }

    internal void ReleaseStoreCall(bool native)
    {
        lock (_gate)
        {
            if (native) _nativeLeases--;
            else _storeCalls--;
            SignalIfDrained();
        }
    }

    private async Task RunShutdownAsync(TaskCompletionSource completion)
    {
        Exception? callbackFailure = null;
        try { _shutdown.Cancel(); }
        catch (Exception ex) { callbackFailure = ex; }

        await _drained.Task.ConfigureAwait(false);
        try
        {
            if (_closeAction is not null) await _closeAction().ConfigureAwait(false);
            lock (_gate) _closed = true;
            _shutdown.Dispose();
            if (callbackFailure is null) completion.TrySetResult();
            else completion.TrySetException(callbackFailure);
        }
        catch (Exception closeFailure)
        {
            lock (_gate) _closed = true;
            completion.TrySetException(closeFailure);
        }
    }

    private void ValidatePermitUnderGate(OperationPermit permit)
    {
        ValidateOwnerUnderGate(permit);
        if (!permit.IsActive) throw Closed();
    }

    private void ValidateOwnerUnderGate(OperationPermit permit)
    {
        if (!ReferenceEquals(permit.Owner, this)) throw Closed();
    }

    private void SignalIfDrained()
    {
        if (_draining && _operations == 0 && _storeCalls == 0 && _nativeLeases == 0) _drained.TrySetResult();
    }

    private static StorageException Closed() => new(new StorageFailure(StorageFailureCode.SessionClosed, "operation"));
    private static TaskCompletionSource NewSignal() => new(TaskCreationOptions.RunContinuationsAsynchronously);
}

internal sealed class OperationCallLease(OperationOwner owner, bool native) : IDisposable
{
    private int _released;
    public void Dispose()
    {
        if (Interlocked.Exchange(ref _released, 1) == 0) owner.ReleaseStoreCall(native);
    }
}
