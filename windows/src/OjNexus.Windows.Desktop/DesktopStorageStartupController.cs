using OjNexus.Windows.Core.Storage;

namespace OjNexus.Windows.Desktop;

public enum DesktopStorageStartupState { Opening, Blocked, Ready, Closing, Closed }

public sealed record DesktopStorageStartupResult(DesktopStorageStartupState State, WindowsStorageSession? Session, string? FailureCode);

public sealed class DesktopStorageStartupController
{
    private readonly SemaphoreSlim _serial = new(1, 1);
    private readonly CancellationTokenSource _lifetime = new();
    private readonly object _gate = new();
    private WindowsStorageSession? _session;
    private Task? _closingTask;
    private DesktopStorageStartupState _state = DesktopStorageStartupState.Opening;
    private readonly string? _rootOverride;
    private readonly Func<string?, CancellationToken, Task<StorageOpenResult>> _open;

    public DesktopStorageStartupController(string? rootOverride = null)
        : this(rootOverride, static (root, token) => WindowsStorage.OpenAsync(root, null, token)) { }

    internal DesktopStorageStartupController(
        string? rootOverride,
        Func<string?, CancellationToken, Task<StorageOpenResult>> open)
    {
        _rootOverride = rootOverride;
        _open = open ?? throw new ArgumentNullException(nameof(open));
    }

    public DesktopStorageStartupState State { get { lock (_gate) return _state; } }

    public async Task<DesktopStorageStartupResult> OpenOrRetryAsync()
    {
        await _serial.WaitAsync();
        try
        {
            lock (_gate)
            {
                if (_state is DesktopStorageStartupState.Closing or DesktopStorageStartupState.Closed)
                    return new(_state, null, null);
                if (_session is not null)
                    return new(DesktopStorageStartupState.Ready, _session, null);
                _state = DesktopStorageStartupState.Opening;
            }

            try
            {
                var opened = await _open(_rootOverride, _lifetime.Token);
                if (opened.Session is null)
                {
                    var failureCode = opened.Failure?.WireCode ?? "STORAGE_IO_FAILURE";
                    lock (_gate)
                    {
                        if (_state is not (DesktopStorageStartupState.Closing or DesktopStorageStartupState.Closed))
                            _state = DesktopStorageStartupState.Blocked;
                    }
                    return new(State, null, failureCode);
                }

                bool closing;
                DesktopStorageStartupState currentState;
                lock (_gate)
                {
                    closing = _state is DesktopStorageStartupState.Closing or DesktopStorageStartupState.Closed;
                    if (!closing)
                    {
                        _session = opened.Session;
                        _state = DesktopStorageStartupState.Ready;
                    }
                    currentState = _state;
                }
                if (closing)
                {
                    await opened.Session.DisposeAsync();
                    return new(currentState, null, null);
                }
                return new(DesktopStorageStartupState.Ready, opened.Session, null);
            }
            catch (OperationCanceledException) when (_lifetime.IsCancellationRequested)
            {
                lock (_gate) return new(_state, null, null);
            }
            catch (Exception)
            {
                lock (_gate)
                {
                    if (_state is not (DesktopStorageStartupState.Closing or DesktopStorageStartupState.Closed))
                        _state = DesktopStorageStartupState.Blocked;
                    return new(_state, null, "STORAGE_IO_FAILURE");
                }
            }
        }
        finally
        {
            _serial.Release();
        }
    }

    public Task CloseAsync()
    {
        lock (_gate)
        {
            if (_closingTask is not null) return _closingTask;
            _state = DesktopStorageStartupState.Closing;
            _lifetime.Cancel();
            _closingTask = CloseCoreAsync();
            return _closingTask;
        }
    }

    private async Task CloseCoreAsync()
    {
        await _serial.WaitAsync();
        try
        {
            WindowsStorageSession? session;
            lock (_gate)
            {
                session = _session;
                _session = null;
            }
            if (session is not null) await session.DisposeAsync();
            lock (_gate) _state = DesktopStorageStartupState.Closed;
        }
        finally
        {
            _serial.Release();
            _lifetime.Dispose();
            _serial.Dispose();
        }
    }

}
