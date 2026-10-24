using System.Reflection;
using OjNexus.Windows.Core.Contracts;

namespace OjNexus.Windows.Core.Storage;

public sealed class StorageOperationScope(ISyncStore store) : IStorageOperationSource
{
    private readonly OperationOwner _owner = new();
    public StorageOperationContext BeginOperation(CancellationToken cancellationToken = default) => _owner.Begin(store, cancellationToken);
    public async Task RunAsync(Func<ISyncStore, CancellationToken, Task> work, CancellationToken cancellationToken)
    { await using var operation = BeginOperation(cancellationToken); await work(operation.Store, operation.CancellationToken).ConfigureAwait(false); }
    public async Task<T> RunAsync<T>(Func<ISyncStore, CancellationToken, Task<T>> work, CancellationToken cancellationToken)
    { await using var operation = BeginOperation(cancellationToken); return await work(operation.Store, operation.CancellationToken).ConfigureAwait(false); }
    public ValueTask DisposeAsync() => _owner.DisposeAsync();
}

public sealed class StorageOperationContext : IAsyncDisposable, IDisposable
{
    private readonly OperationOwner _owner;
    private readonly CancellationTokenSource _linked;
    private int _disposed;
    internal StorageOperationContext(OperationOwner owner, OperationPermit permit, ISyncStore store, CancellationTokenSource linked)
    { _owner = owner; _permit = permit; Store = store; _linked = linked; }
    private readonly OperationPermit _permit;
    public ISyncStore Store { get; }
    public CancellationToken CancellationToken => _linked.Token;
    public void Dispose() { if (Interlocked.Exchange(ref _disposed, 1) == 0) { _permit.Close(); _owner.EndOperation(); _linked.Dispose(); } }
    public ValueTask DisposeAsync() { Dispose(); return ValueTask.CompletedTask; }
}

internal sealed class OperationOwner
{
    private readonly object _gate = new();
    private readonly CancellationTokenSource _shutdown = new();
    private TaskCompletionSource _drained = NewSignal();
    private int _active;
    private bool _draining;
    private bool _closed;

    public StorageOperationContext Begin(ISyncStore store, CancellationToken caller)
    {
        lock (_gate)
        {
            if (_draining || _closed) throw new StorageException(new(StorageFailureCode.SessionClosed, "operation"));
            _active++;
        }
        var linked = CancellationTokenSource.CreateLinkedTokenSource(caller, _shutdown.Token);
        var permit = new OperationPermit();
        return new(this, permit, GuardedStore.Create(store, this, permit, linked.Token), linked);
    }

    public void EnsureOpen(CancellationToken token)
    {
        token.ThrowIfCancellationRequested();
        lock (_gate) if (_draining || _closed) throw new StorageException(new(StorageFailureCode.SessionClosed, "operation"));
    }

    public void ReserveCall(OperationPermit permit, CancellationToken token)
    {
        token.ThrowIfCancellationRequested();
        lock (_gate)
        {
            if (_draining || _closed || permit.IsClosed) throw new StorageException(new(StorageFailureCode.SessionClosed, "operation"));
            _active++;
        }
    }

    public void EndOperation()
    {
        lock (_gate) { if (_active > 0) _active--; if (_draining && _active == 0) _drained.TrySetResult(); }
    }

    public async ValueTask DisposeAsync()
    {
        lock (_gate)
        {
            if (_closed) return;
            _draining = true;
            if (_active == 0) _drained.TrySetResult();
        }
        _shutdown.Cancel();
        await _drained.Task.ConfigureAwait(false);
        lock (_gate) _closed = true;
        _shutdown.Dispose();
    }

    private static TaskCompletionSource NewSignal() => new(TaskCreationOptions.RunContinuationsAsynchronously);
}

internal sealed class OperationPermit
{
    private int _closed;
    public bool IsClosed => Volatile.Read(ref _closed) != 0;
    public void Close() => Interlocked.Exchange(ref _closed, 1);
}

internal class GuardedStore : DispatchProxy
{
    private ISyncStore _inner = null!;
    private OperationOwner _owner = null!;
    private OperationPermit _permit = null!;
    private CancellationToken _token;
    internal static ISyncStore Create(ISyncStore inner, OperationOwner owner, OperationPermit permit, CancellationToken token)
    { var proxy = (GuardedStore)(object)Create<ISyncStore, GuardedStore>(); proxy._inner = inner; proxy._owner = owner; proxy._permit = permit; proxy._token = token; return (ISyncStore)(object)proxy; }
    protected override object? Invoke(MethodInfo? targetMethod, object?[]? args)
    {
        _owner.ReserveCall(_permit, _token);
        Task pending;
        try { pending = (Task)targetMethod!.Invoke(_inner, args)!; }
        catch { _owner.EndOperation(); throw; }
        return targetMethod.ReturnType == typeof(Task)
            ? Await(pending)
            : typeof(GuardedStore).GetMethod(nameof(AwaitGeneric), BindingFlags.NonPublic | BindingFlags.Instance)!
                .MakeGenericMethod(targetMethod.ReturnType.GenericTypeArguments[0]).Invoke(this, [pending]);
    }
    private async Task Await(Task task) { try { await task.ConfigureAwait(false); } finally { _owner.EndOperation(); } }
    private async Task<T> AwaitGeneric<T>(Task<T> task) { try { return await task.ConfigureAwait(false); } finally { _owner.EndOperation(); } }
}
