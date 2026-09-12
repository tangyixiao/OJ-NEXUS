using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Net.Http;
using System.Runtime.CompilerServices;
using OjNexus.Windows.Core.Contracts;
using OjNexus.Windows.Core.Domain;
using OjNexus.Windows.Core.Network;
using OjNexus.Windows.Core.Storage;
using OjNexus.Windows.Core.Sync;

namespace OjNexus.Windows.Desktop.ViewModels;

public enum DesktopPage
{
    Dashboard,
    Connectors,
    History,
}

public sealed class ConnectorRow : INotifyPropertyChanged
{
    private string _handle = string.Empty;
    private string _status = "NOT SYNCED";
    private string _lastSync = "NONE";
    private bool _isConfigured;
    private bool _isSyncing;

    public ConnectorRow(JudgeId judge, string capability)
    {
        Judge = judge;
        Capability = capability;
    }

    public event PropertyChangedEventHandler? PropertyChanged;

    public JudgeId Judge { get; }

    public string JudgeLabel => Judge.ToString().ToUpperInvariant();

    public string Capability { get; }

    public string Handle
    {
        get => _handle;
        set => SetField(ref _handle, value ?? string.Empty);
    }

    public string Status
    {
        get => _status;
        set => SetField(ref _status, value);
    }

    public string LastSync
    {
        get => _lastSync;
        set => SetField(ref _lastSync, value);
    }

    public bool IsConfigured
    {
        get => _isConfigured;
        set
        {
            if (SetField(ref _isConfigured, value))
            {
                OnPropertyChanged(nameof(ActionLabel));
            }
        }
    }

    public bool IsSyncing
    {
        get => _isSyncing;
        set
        {
            if (SetField(ref _isSyncing, value))
            {
                OnPropertyChanged(nameof(ActionLabel));
            }
        }
    }

    public string ActionLabel => IsSyncing ? "CANCEL" : IsConfigured ? "SYNC" : "CONNECT";

    private bool SetField<T>(ref T field, T value, [CallerMemberName] string? propertyName = null)
    {
        if (EqualityComparer<T>.Default.Equals(field, value))
        {
            return false;
        }

        field = value;
        OnPropertyChanged(propertyName);
        return true;
    }

    private void OnPropertyChanged(string? propertyName) => PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
}

public sealed record HistoryRow(
    long Id,
    string Judge,
    string Handle,
    string Status,
    string StartedAt,
    string ModuleSummary)
{
    public bool CanRetry => Status is "PARTIAL" or "ERROR" or "CANCELLED" or "OFFLINE";
}

public sealed class DesktopViewModel : INotifyPropertyChanged, IDisposable
{
    private static readonly (JudgeId Judge, string Capability)[] SupportedJudges =
    [
        (JudgeId.Codeforces, "PUBLIC API / PROFILE / RATING / SUBMISSIONS"),
        (JudgeId.AtCoder, "COMMUNITY API / SUBMISSIONS"),
        (JudgeId.Luogu, "PUBLIC SITE / PROFILE / CONTESTS / PROBLEMSET"),
    ];

    private readonly ISyncStore _store;
    private readonly SyncService _syncService;
    private readonly object _syncCancellationLock = new();
    private readonly Dictionary<ConnectorRow, CancellationTokenSource> _syncCancellations = [];
    private DesktopPage _currentPage = DesktopPage.Dashboard;
    private int _accountCount;
    private int _connectedJudgeCount;
    private string _latestSignal = "NO SYNC RECORDED";
    private string _lastSync = "NONE";
    private string _statusText = "READY";
    private string _lastError = string.Empty;
    private bool _isBusy;
    private string _selectedHistoryJudge = "ALL";

    public DesktopViewModel(ISyncStore store, SyncService syncService, string dataDirectory)
    {
        ArgumentNullException.ThrowIfNull(store);
        ArgumentNullException.ThrowIfNull(syncService);
        ArgumentException.ThrowIfNullOrWhiteSpace(dataDirectory);
        _store = store;
        _syncService = syncService;
        DataDirectory = dataDirectory;
        foreach (var (judge, capability) in SupportedJudges)
        {
            Connectors.Add(new ConnectorRow(judge, capability));
        }
    }

    public event PropertyChangedEventHandler? PropertyChanged;

    public string DataDirectory { get; }

    public ObservableCollection<ConnectorRow> Connectors { get; } = [];

    public ObservableCollection<HistoryRow> History { get; } = [];

    public bool HasNoHistory => History.Count == 0;

    public IReadOnlyList<string> HistoryJudgeFilters { get; } = ["ALL", "CODEFORCES", "ATCODER", "LUOGU"];

    public string SelectedHistoryJudge
    {
        get => _selectedHistoryJudge;
        private set => SetField(ref _selectedHistoryJudge, value);
    }

    public DesktopPage CurrentPage
    {
        get => _currentPage;
        private set
        {
            if (SetField(ref _currentPage, value))
            {
                OnPropertyChanged(nameof(PageTitle));
                OnPropertyChanged(nameof(IsDashboardVisible));
                OnPropertyChanged(nameof(IsConnectorsVisible));
                OnPropertyChanged(nameof(IsHistoryVisible));
            }
        }
    }

    public string PageTitle => CurrentPage switch
    {
        DesktopPage.Connectors => "CONNECTORS",
        DesktopPage.History => "SYNC HISTORY",
        _ => "DASHBOARD",
    };

    public bool IsDashboardVisible => CurrentPage == DesktopPage.Dashboard;

    public bool IsConnectorsVisible => CurrentPage == DesktopPage.Connectors;

    public bool IsHistoryVisible => CurrentPage == DesktopPage.History;

    public int AccountCount
    {
        get => _accountCount;
        private set => SetField(ref _accountCount, value);
    }

    public int ConnectedJudgeCount
    {
        get => _connectedJudgeCount;
        private set => SetField(ref _connectedJudgeCount, value);
    }

    public string LatestSignal
    {
        get => _latestSignal;
        private set => SetField(ref _latestSignal, value);
    }

    public string LastSync
    {
        get => _lastSync;
        private set => SetField(ref _lastSync, value);
    }

    public string StatusText
    {
        get => _statusText;
        private set => SetField(ref _statusText, value);
    }

    public string LastError
    {
        get => _lastError;
        private set => SetField(ref _lastError, value);
    }

    public bool IsBusy
    {
        get => _isBusy;
        private set => SetField(ref _isBusy, value);
    }

    public void Navigate(DesktopPage page) => CurrentPage = page;

    public async Task SelectHistoryJudgeAsync(string judgeFilter, CancellationToken cancellationToken)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(judgeFilter);
        var normalizedFilter = judgeFilter.Trim().ToUpperInvariant();
        if (normalizedFilter != "ALL" && !JudgeIdParser.TryParse(normalizedFilter, out _))
        {
            StatusText = "ERROR";
            LastError = "UNKNOWN JUDGE FILTER";
            return;
        }

        SelectedHistoryJudge = normalizedFilter;
        await RefreshAsync(cancellationToken);
    }

    public async Task RefreshAsync(CancellationToken cancellationToken)
    {
        IsBusy = true;
        StatusText = "LOADING";
        LastError = string.Empty;
        try
        {
            var accounts = await _store.GetAccountsAsync(cancellationToken);
            var operations = await _store.GetRecentOperationsAsync(null, 20, cancellationToken);
            ProjectAccounts(accounts, operations);
            var historyOperations = SelectedHistoryJudge == "ALL"
                ? operations
                : JudgeIdParser.TryParse(SelectedHistoryJudge, out var historyJudge)
                    ? await _store.GetRecentOperationsAsync(historyJudge, 5, cancellationToken)
                    : Array.Empty<SyncOperation>();
            ProjectHistory(historyOperations);
            StatusText = "READY";
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            StatusText = "CANCELLED";
            throw;
        }
        catch (Exception)
        {
            StatusText = "ERROR";
            LastError = "LOCAL STATE UNAVAILABLE";
        }
        finally
        {
            IsBusy = false;
        }
    }

    public async Task<bool> SaveConnectorAsync(ConnectorRow row, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(row);
        var handle = row.Handle.Trim();
        if (handle.Length == 0)
        {
            row.Status = "INVALID";
            StatusText = "ERROR";
            LastError = "HANDLE REQUIRED";
            return false;
        }

        try
        {
            var account = JudgeAccount.Create(row.Judge, handle);
            await _store.UpsertAccountAsync(account, cancellationToken);
            row.Handle = account.Handle;
            row.IsConfigured = true;
            row.Status = "READY";
            LastError = string.Empty;
            StatusText = "READY";
            return true;
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            row.Status = "CANCELLED";
            StatusText = "CANCELLED";
            return false;
        }
        catch (Exception)
        {
            row.Status = "ERROR";
            StatusText = "ERROR";
            LastError = "ACCOUNT SAVE FAILED";
            return false;
        }
    }

    public async Task<bool> SyncConnectorAsync(ConnectorRow row, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(row);
        lock (_syncCancellationLock)
        {
            if (_syncCancellations.ContainsKey(row))
            {
                return false;
            }
        }

        if (!await SaveConnectorAsync(row, cancellationToken))
        {
            return false;
        }

        using var linkedCancellation = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        lock (_syncCancellationLock)
        {
            if (_syncCancellations.ContainsKey(row))
            {
                return false;
            }

            _syncCancellations[row] = linkedCancellation;
        }

        row.IsSyncing = true;
        row.Status = "SYNCING";
        StatusText = "SYNCING";
        LastError = string.Empty;
        try
        {
            var report = await _syncService.RunAsync(JudgeAccount.Create(row.Judge, row.Handle), false, linkedCancellation.Token);
            row.Status = report.Status.ToString().ToUpperInvariant();
            if (report.Error is not null)
            {
                LastError = report.Error.Value.ToString().ToUpperInvariant();
            }

            return report.Status == SyncOperationStatus.Success;
        }
        catch (OperationCanceledException) when (linkedCancellation.IsCancellationRequested)
        {
            row.Status = "CANCELLED";
            LastError = "CANCELLED";
            return false;
        }
        catch (Exception)
        {
            row.Status = "ERROR";
            LastError = "SYNC FAILED";
            return false;
        }
        finally
        {
            row.IsSyncing = false;
            lock (_syncCancellationLock)
            {
                if (_syncCancellations.TryGetValue(row, out var activeCancellation)
                    && ReferenceEquals(activeCancellation, linkedCancellation))
                {
                    _syncCancellations.Remove(row);
                }
            }

            await RefreshAsync(CancellationToken.None);
        }
    }

    public async Task<bool> RetryHistoryAsync(HistoryRow row, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(row);
        if (!row.CanRetry || !JudgeIdParser.TryParse(row.Judge, out var judge))
        {
            return false;
        }

        var connector = Connectors.FirstOrDefault(candidate => candidate.Judge == judge);
        if (connector is null)
        {
            StatusText = "ERROR";
            LastError = "JUDGE UNAVAILABLE";
            return false;
        }

        connector.Handle = row.Handle;
        connector.IsConfigured = true;
        return await SyncConnectorAsync(connector, cancellationToken);
    }

    public void CancelSync(ConnectorRow row)
    {
        ArgumentNullException.ThrowIfNull(row);
        lock (_syncCancellationLock)
        {
            if (_syncCancellations.TryGetValue(row, out var cancellation))
            {
                cancellation.Cancel();
            }
        }
    }

    public void CancelSync()
    {
        CancellationTokenSource[] activeCancellations;
        lock (_syncCancellationLock)
        {
            activeCancellations = [.. _syncCancellations.Values];
        }

        foreach (var cancellation in activeCancellations)
        {
            cancellation.Cancel();
        }
    }

    public void Dispose()
    {
        CancelSync();
    }

    private void ProjectAccounts(IReadOnlyList<JudgeAccount> accounts, IReadOnlyList<SyncOperation> operations)
    {
        var accountByJudge = accounts.ToDictionary(account => account.Judge);
        AccountCount = accounts.Count;
        ConnectedJudgeCount = accounts.Count(account => account.Enabled);
        foreach (var row in Connectors)
        {
            accountByJudge.TryGetValue(row.Judge, out var account);
            row.Handle = account?.Handle ?? string.Empty;
            row.IsConfigured = account is not null;
            var latest = operations.FirstOrDefault(operation => operation.Account.Judge == row.Judge);
            row.Status = latest?.Status.ToString().ToUpperInvariant() ?? "NOT SYNCED";
            row.LastSync = latest?.FinishedAt?.ToString("yyyy-MM-dd HH:mm:ss 'UTC'") ?? "NONE";
        }

        var latestOperation = operations.FirstOrDefault();
        LatestSignal = latestOperation is null
            ? "NO SYNC RECORDED"
            : $"{latestOperation.Account.Judge.ToString().ToUpperInvariant()} / {latestOperation.Status.ToString().ToUpperInvariant()}";
        LastSync = latestOperation?.FinishedAt?.ToString("yyyy-MM-dd HH:mm:ss 'UTC'") ?? "NONE";
    }

    private void ProjectHistory(IReadOnlyList<SyncOperation> operations)
    {
        History.Clear();
        OnPropertyChanged(nameof(HasNoHistory));
        foreach (var operation in operations.Take(5))
        {
            var moduleSummary = operation.Modules.Count == 0
                ? "NO MODULE RECEIPTS"
                : string.Join("  ·  ", operation.Modules.Select(module => $"{module.Stage}:{module.Status.ToString().ToUpperInvariant()}"));
            History.Add(new HistoryRow(
                operation.Id,
                operation.Account.Judge.ToString().ToUpperInvariant(),
                operation.Account.Handle,
                operation.Status.ToString().ToUpperInvariant(),
                operation.StartedAt.ToString("yyyy-MM-dd HH:mm:ss 'UTC'"),
                moduleSummary));
        }
        OnPropertyChanged(nameof(HasNoHistory));
    }

    private bool SetField<T>(ref T field, T value, [CallerMemberName] string? propertyName = null)
    {
        if (EqualityComparer<T>.Default.Equals(field, value))
        {
            return false;
        }

        field = value;
        OnPropertyChanged(propertyName);
        return true;
    }

    private void OnPropertyChanged(string? propertyName) => PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
}

public static class DesktopRuntime
{
    public static DesktopViewModel Create()
    {
        var dataDirectory = WindowsPaths.GetDataDirectory();
        var connectionFactory = new SqliteConnectionFactory(dataDirectory);
        var store = new SqliteSyncStore(connectionFactory);
        var adapters = new Dictionary<JudgeId, IJudgeAdapter>
        {
            [JudgeId.Codeforces] = new CodeforcesAdapter(static () => new HttpClient()),
            [JudgeId.AtCoder] = new AtCoderAdapter(static () => new HttpClient()),
            [JudgeId.Luogu] = new LuoguAdapter(static () => new HttpClient()),
        };
        var syncService = new SyncService(adapters, store, new SystemClock(), static () => "windows-desktop-v1");
        return new DesktopViewModel(store, syncService, dataDirectory);
    }

    private sealed class SystemClock : IClock
    {
        public DateTimeOffset UtcNow => DateTimeOffset.UtcNow;
    }
}
