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
    Problems,
}

public sealed class ConnectorRow : INotifyPropertyChanged
{
    private string _handle = string.Empty;
    private string _status = "NOT SYNCED";
    private string _lastSync = "NONE";
    private bool _isConfigured;
    private bool _isEnabled = true;
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

    public bool IsEnabled
    {
        get => _isEnabled;
        set
        {
            if (SetField(ref _isEnabled, value))
            {
                OnPropertyChanged(nameof(ToggleLabel));
            }
        }
    }

    public string ToggleLabel => IsEnabled ? "DISABLE" : "ENABLE";

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

public sealed record ProblemRow(
    string Judge,
    string ExternalId,
    string Title,
    string Url,
    string Status,
    string LastSeenAt)
{
    public bool HasUrl => !string.IsNullOrWhiteSpace(Url);
    public string StatusLabel => Status;
}
public sealed class DesktopViewModel : INotifyPropertyChanged, IAsyncDisposable
{
    private static readonly (JudgeId Judge, string Capability)[] SupportedJudges =
    [
        (JudgeId.Codeforces, "PUBLIC API / PROFILE / RATING / SUBMISSIONS"),
        (JudgeId.AtCoder, "COMMUNITY API / SUBMISSIONS"),
        (JudgeId.Luogu, "PUBLIC SITE / PROFILE / CONTESTS / PROBLEMSET"),
    ];

    private readonly IStorageOperationSource _storage;
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
    private bool _isBatchSyncing;
    private bool _batchCancelRequested;
    private string _selectedHistoryJudge = "ALL";
    private string _selectedProblemJudge = "ALL";
    private string _selectedProblemStatus = "ALL";

    public DesktopViewModel(IStorageOperationSource storage, SyncService syncService, string dataDirectory)
    {
        ArgumentNullException.ThrowIfNull(storage);
        ArgumentNullException.ThrowIfNull(syncService);
        ArgumentException.ThrowIfNullOrWhiteSpace(dataDirectory);
        _storage = storage;
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

    public ObservableCollection<ProblemRow> Problems { get; } = [];

    public bool HasNoHistory => History.Count == 0;

    public bool HasNoProblems => Problems.Count == 0;

    public IReadOnlyList<string> HistoryJudgeFilters { get; } = ["ALL", "CODEFORCES", "ATCODER", "LUOGU"];

    public IReadOnlyList<string> ProblemJudgeFilters { get; } = ["ALL", "CODEFORCES", "ATCODER"];

    public IReadOnlyList<string> ProblemStatusFilters { get; } = ["ALL", "SOLVED", "ATTEMPTED"];

    public string SelectedProblemJudge
    {
        get => _selectedProblemJudge;
        private set => SetField(ref _selectedProblemJudge, value);
    }

    public string SelectedProblemStatus
    {
        get => _selectedProblemStatus;
        private set => SetField(ref _selectedProblemStatus, value);
    }

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
                OnPropertyChanged(nameof(IsProblemsVisible));
            }
        }
    }

    public string PageTitle => CurrentPage switch
    {
        DesktopPage.Connectors => "CONNECTORS",
        DesktopPage.History => "SYNC HISTORY",
        DesktopPage.Problems => "PROBLEMS",
        _ => "DASHBOARD",
    };

    public bool IsDashboardVisible => CurrentPage == DesktopPage.Dashboard;

    public bool IsConnectorsVisible => CurrentPage == DesktopPage.Connectors;

    public bool IsHistoryVisible => CurrentPage == DesktopPage.History;

    public bool IsProblemsVisible => CurrentPage == DesktopPage.Problems;

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

    public bool CanSyncAll => !_isBusy && !_isBatchSyncing && Connectors.Any(row => row.IsConfigured && row.IsEnabled);

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

    public async Task SelectProblemJudgeAsync(string judgeFilter, CancellationToken cancellationToken)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(judgeFilter);
        var normalizedFilter = judgeFilter.Trim().ToUpperInvariant();
        if (normalizedFilter != "ALL" && !JudgeIdParser.TryParse(normalizedFilter, out _))
        {
            StatusText = "ERROR";
            LastError = "UNKNOWN JUDGE FILTER";
            return;
        }

        SelectedProblemJudge = normalizedFilter;
        await RefreshAsync(cancellationToken);
    }

    public async Task SelectProblemStatusAsync(string statusFilter, CancellationToken cancellationToken)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(statusFilter);
        var normalizedFilter = statusFilter.Trim().ToUpperInvariant();
        if (normalizedFilter != "ALL" && !ProblemStatusParser.TryParse(normalizedFilter, out _))
        {
            StatusText = "ERROR";
            LastError = "UNKNOWN STATUS FILTER";
            return;
        }

        SelectedProblemStatus = normalizedFilter;
        await RefreshAsync(cancellationToken);
    }

    public async Task RefreshAsync(CancellationToken cancellationToken)
    {
        await _storage.RunAsync((store, token) => RefreshCoreAsync(store, token), cancellationToken);
    }

    private async Task RefreshCoreAsync(ISyncStore store, CancellationToken cancellationToken)
    {
        IsBusy = true;
        StatusText = "LOADING";
        LastError = string.Empty;
        try
        {
            var accounts = await store.GetAccountsAsync(cancellationToken);
            var operations = await store.GetRecentOperationsAsync(null, 20, cancellationToken);
            ProjectAccounts(accounts, operations);
            var historyOperations = SelectedHistoryJudge == "ALL"
                ? operations
                : JudgeIdParser.TryParse(SelectedHistoryJudge, out var historyJudge)
                    ? await store.GetRecentOperationsAsync(historyJudge, 5, cancellationToken)
                    : Array.Empty<SyncOperation>();
            ProjectHistory(historyOperations);
            JudgeId? problemJudge = SelectedProblemJudge != "ALL" && JudgeIdParser.TryParse(SelectedProblemJudge, out var problemJudgeId)
                ? problemJudgeId
                : null;
            ProblemStatus? problemStatus = ProblemStatusParser.TryParse(SelectedProblemStatus, out var parsedStatus)
                ? parsedStatus
                : null;
            var problems = await store.GetProblemsAsync(problemJudge, problemStatus, 100, cancellationToken);
            ProjectProblems(problems);
            StatusText = "READY";
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            StatusText = "CANCELLED";
            throw;
        }
        catch (StorageException exception)
        {
            StatusText = "ERROR";
            LastError = exception.Failure.WireCode;
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
        return await _storage.RunAsync((store, token) => SaveConnectorCoreAsync(row, store, token), cancellationToken);
    }

    private async Task<bool> SaveConnectorCoreAsync(ConnectorRow row, ISyncStore store, CancellationToken cancellationToken)
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
            var account = JudgeAccount.Create(row.Judge, handle) with
            {
                Enabled = row.IsConfigured && !row.IsEnabled ? false : true,
            };
            await store.UpsertAccountAsync(account, cancellationToken);
            row.Handle = account.Handle;
            row.IsConfigured = true;
            row.IsEnabled = account.Enabled;
            row.Status = "READY";
            LastError = string.Empty;
            StatusText = "READY";
            OnPropertyChanged(nameof(CanSyncAll));
            return true;
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            row.Status = "CANCELLED";
            StatusText = "CANCELLED";
            return false;
        }
        catch (StorageException exception)
        {
            row.Status = "ERROR";
            StatusText = "ERROR";
            LastError = exception.Failure.WireCode;
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

    public async Task<bool> SyncAllAsync(CancellationToken cancellationToken)
    {
        return await _storage.RunAsync((store, token) => SyncAllCoreAsync(store, token), cancellationToken);
    }

    private async Task<bool> SyncAllCoreAsync(ISyncStore store, CancellationToken cancellationToken)
    {
        if (_isBusy || _isBatchSyncing || HasActiveSyncs())
        {
            StatusText = "ERROR";
            LastError = "SYNC IN PROGRESS";
            return false;
        }

        var candidates = Connectors
            .Where(row => row.IsConfigured && row.IsEnabled)
            .ToArray();
        if (candidates.Length == 0)
        {
            StatusText = "ERROR";
            LastError = "NO ENABLED ACCOUNTS";
            return false;
        }

        _isBatchSyncing = true;
        _batchCancelRequested = false;
        OnPropertyChanged(nameof(CanSyncAll));
        StatusText = "SYNCING";
        LastError = string.Empty;
        var allSucceeded = true;
        try
        {
            foreach (var row in candidates)
            {
                if (cancellationToken.IsCancellationRequested || BatchCancelRequested())
                {
                    StatusText = "CANCELLED";
                    LastError = "CANCELLED";
                    return false;
                }

                if (!await SyncConnectorCoreAsync(row, store, cancellationToken))
                {
                    allSucceeded = false;
                }
            }

            if (cancellationToken.IsCancellationRequested || BatchCancelRequested())
            {
                StatusText = "CANCELLED";
                LastError = "CANCELLED";
                return false;
            }

            StatusText = allSucceeded ? "READY" : "ERROR";
            LastError = allSucceeded ? string.Empty : "SYNC PARTIAL";
            return allSucceeded;
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            StatusText = "CANCELLED";
            LastError = "CANCELLED";
            return false;
        }
        finally
        {
            _isBatchSyncing = false;
            _batchCancelRequested = false;
            OnPropertyChanged(nameof(CanSyncAll));
        }
    }

    public async Task<bool> SetConnectorEnabledAsync(
        ConnectorRow row,
        bool enabled,
        CancellationToken cancellationToken)
    {
        return await _storage.RunAsync((store, token) => SetConnectorEnabledCoreAsync(row, enabled, store, token), cancellationToken);
    }

    private async Task<bool> SetConnectorEnabledCoreAsync(
        ConnectorRow row,
        bool enabled,
        ISyncStore store,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(row);
        if (row.IsSyncing)
        {
            StatusText = "ERROR";
            LastError = "SYNC IN PROGRESS";
            return false;
        }

        try
        {
            var accounts = await store.GetAccountsAsync(cancellationToken);
            var account = accounts.FirstOrDefault(candidate =>
                candidate.Judge == row.Judge
                && JudgeIdentity.HandlesMatch(row.Judge, candidate.Handle, row.Handle));
            if (account is null)
            {
                StatusText = "ERROR";
                LastError = "ACCOUNT NOT CONFIGURED";
                return false;
            }

            await store.UpsertAccountAsync(account with { Enabled = enabled }, cancellationToken);
            row.IsConfigured = true;
            row.IsEnabled = enabled;
            StatusText = "READY";
            LastError = string.Empty;
            OnPropertyChanged(nameof(CanSyncAll));
            return true;
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            StatusText = "CANCELLED";
            return false;
        }
        catch (StorageException exception)
        {
            StatusText = "ERROR";
            LastError = exception.Failure.WireCode;
            return false;
        }
        catch (Exception)
        {
            StatusText = "ERROR";
            LastError = "ACCOUNT UPDATE FAILED";
            return false;
        }
    }

    public async Task<bool> SyncConnectorAsync(ConnectorRow row, CancellationToken cancellationToken)
    {
        return await _storage.RunAsync((store, token) => SyncConnectorCoreAsync(row, store, token), cancellationToken);
    }

    private async Task<bool> SyncConnectorCoreAsync(ConnectorRow row, ISyncStore store, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(row);
        if (row.IsConfigured && !row.IsEnabled)
        {
            StatusText = "ERROR";
            LastError = "ACCOUNT DISABLED";
            return false;
        }

        lock (_syncCancellationLock)
        {
            if (_syncCancellations.ContainsKey(row))
            {
                return false;
            }
        }

        if (!await SaveConnectorCoreAsync(row, store, cancellationToken))
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
        string? storageFailureCode = null;
        try
        {
            var report = await _syncService.RunAsync(JudgeAccount.Create(row.Judge, row.Handle), false, store, linkedCancellation.Token);
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
        catch (StorageException exception)
        {
            storageFailureCode = exception.Failure.WireCode;
            row.Status = "ERROR";
            LastError = storageFailureCode;
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

            await RefreshCoreAsync(store, CancellationToken.None);
            if (storageFailureCode is not null)
            {
                StatusText = "ERROR";
                LastError = storageFailureCode;
            }
        }
    }

    public async Task<bool> RetryHistoryAsync(HistoryRow row, CancellationToken cancellationToken)
    {
        return await _storage.RunAsync((store, token) => RetryHistoryCoreAsync(row, store, token), cancellationToken);
    }

    private async Task<bool> RetryHistoryCoreAsync(HistoryRow row, ISyncStore store, CancellationToken cancellationToken)
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

        if (!JudgeIdentity.HandlesMatch(judge, connector.Handle, row.Handle))
        {
            StatusText = "ERROR";
            LastError = "ACCOUNT HANDLE CHANGED";
            return false;
        }

        connector.IsConfigured = true;
        return await SyncConnectorCoreAsync(connector, store, cancellationToken);
    }

    public void CancelSync(ConnectorRow row)
    {
        ArgumentNullException.ThrowIfNull(row);
        if (_isBatchSyncing)
        {
            _batchCancelRequested = true;
        }
        CancellationTokenSource? cancellation;
        lock (_syncCancellationLock)
        {
            _syncCancellations.TryGetValue(row, out cancellation);
        }
        cancellation?.Cancel();
    }

    public void CancelSync()
    {
        if (_isBatchSyncing)
        {
            _batchCancelRequested = true;
        }
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

    private bool BatchCancelRequested()
    {
        return _batchCancelRequested;
    }

    private bool HasActiveSyncs()
    {
        lock (_syncCancellationLock)
        {
            return _syncCancellations.Count != 0;
        }
    }

    public async ValueTask DisposeAsync()
    {
        CancelSync();
        await _storage.DisposeAsync();
    }

    public void ReportStorageFailure(string wireCode)
    {
        StatusText = "ERROR";
        LastError = wireCode;
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
            row.IsEnabled = account?.Enabled ?? true;
            var latest = operations.FirstOrDefault(operation =>
                operation.Account.Judge == row.Judge
                && JudgeIdentity.HandlesMatch(row.Judge, operation.Account.Handle, row.Handle));
            row.Status = latest?.Status.ToString().ToUpperInvariant() ?? "NOT SYNCED";
            row.LastSync = latest?.FinishedAt?.ToString("yyyy-MM-dd HH:mm:ss 'UTC'") ?? "NONE";
        }

        var latestOperation = operations.FirstOrDefault();
        LatestSignal = latestOperation is null
            ? "NO SYNC RECORDED"
            : $"{latestOperation.Account.Judge.ToString().ToUpperInvariant()} / {latestOperation.Status.ToString().ToUpperInvariant()}";
        LastSync = latestOperation?.FinishedAt?.ToString("yyyy-MM-dd HH:mm:ss 'UTC'") ?? "NONE";
        LastError = latestOperation is null ? string.Empty : FormatOperationError(latestOperation);
        OnPropertyChanged(nameof(CanSyncAll));
    }

    private static string FormatOperationError(SyncOperation operation)
    {
        if (operation.Error is not null)
        {
            return operation.Error.Value.ToString().ToUpperInvariant();
        }

        var failedModule = operation.Modules.FirstOrDefault(module =>
            module.Status is SyncOperationStatus.Error or SyncOperationStatus.Offline or SyncOperationStatus.Cancelled);
        return failedModule?.FailureType?.ToUpperInvariant() ?? string.Empty;
    }

    private void ProjectHistory(IReadOnlyList<SyncOperation> operations)
    {
        History.Clear();
        OnPropertyChanged(nameof(HasNoHistory));
        foreach (var operation in operations.Take(5))
        {
            var moduleSummary = operation.Modules.Count == 0
                ? "NO MODULE RECEIPTS"
                : string.Join("  ·  ", operation.Modules.Select(FormatModuleSummary));
            if (operation.Error is not null)
            {
                moduleSummary = $"{moduleSummary}  ·  ERROR:{operation.Error.Value.ToString().ToUpperInvariant()}";
            }

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

    private static string FormatModuleSummary(SyncModuleOutcome module)
    {
        var summary = $"{module.Stage}:{module.Status.ToString().ToUpperInvariant()}";
        return string.IsNullOrWhiteSpace(module.FailureType)
            ? summary
            : $"{summary} ({module.FailureType.ToUpperInvariant()})";
    }

    private void ProjectProblems(IReadOnlyList<ProblemEntry> problems)
    {
        Problems.Clear();
        OnPropertyChanged(nameof(HasNoProblems));
        foreach (var problem in problems)
        {
            Problems.Add(new ProblemRow(
                problem.Judge.ToString().ToUpperInvariant(),
                problem.ExternalId,
                problem.Title,
                problem.Url,
                problem.Solved ? "SOLVED" : "ATTEMPTED",
                problem.LastSeenAt.ToString("yyyy-MM-dd HH:mm:ss 'UTC'")));
        }
        OnPropertyChanged(nameof(HasNoProblems));
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
    public static DesktopViewModel Create(IStorageOperationSource storage, string dataDirectory)
    {
        ArgumentNullException.ThrowIfNull(storage);
        var clock = new SystemClock();
        var adapters = new Dictionary<JudgeId, IJudgeAdapter>
        {
            [JudgeId.Codeforces] = new CodeforcesAdapter(PublicHttpClientFactory.Create),
            [JudgeId.AtCoder] = new AtCoderAdapter(PublicHttpClientFactory.Create),
            [JudgeId.Luogu] = new LuoguAdapter(PublicHttpClientFactory.Create),
        };
        var syncService = new SyncService(adapters, clock, static () => "windows-desktop-v1");
        return new DesktopViewModel(storage, syncService, dataDirectory);
    }

    private sealed class SystemClock : IClock
    {
        public DateTimeOffset UtcNow => DateTimeOffset.UtcNow;
    }
}
