using System.Collections.ObjectModel;
using System.ComponentModel;
using System.IO;
using System.Net.Http;
using System.Runtime.CompilerServices;
using OjNexus.Windows.Core.Contracts;
using OjNexus.Windows.Core.Domain;
using OjNexus.Windows.Core.Network;
using OjNexus.Windows.Core.Portable;
using OjNexus.Windows.Core.Storage;
using OjNexus.Windows.Core.Sync;

namespace OjNexus.Windows.Desktop.ViewModels;

public enum DesktopPage
{
    Dashboard,
    Connectors,
    History,
    Problems,
    Portable,
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
    string LastSeenAt,
    string DifficultyLabel,
    int AttemptCount)
{
    public bool HasUrl => !string.IsNullOrWhiteSpace(Url);
    public string StatusLabel => Status;
}

/// <summary>Full single-problem detail for the PROBLEMS detail sub-state.</summary>
public sealed record ProblemDetailRow(
    string Judge,
    string ExternalId,
    string Title,
    string Url,
    string Status,
    string Difficulty,
    int AttemptCount,
    bool Favorite,
    bool InReview,
    string Tags,
    string FirstSeenAt,
    string LastSeenAt,
    string UpdatedAt)
{
    public bool HasUrl => !string.IsNullOrWhiteSpace(Url);
}
public sealed class DesktopViewModel : INotifyPropertyChanged, IAsyncDisposable
{
    private readonly record struct SyncActionResult(bool Succeeded, string? StorageFailureCode = null);

    private static readonly (JudgeId Judge, string Capability)[] SupportedJudges =
    [
        (JudgeId.Codeforces, "PUBLIC API / PROFILE / RATING / SUBMISSIONS"),
        (JudgeId.AtCoder, "COMMUNITY API / SUBMISSIONS"),
        (JudgeId.Luogu, "PUBLIC SITE / PROFILE / CONTESTS / PROBLEMSET"),
    ];

    private readonly IStorageOperationSource _storage;
    private readonly SyncService _syncService;
    private readonly Func<StorageOperationContext>? _beginPortableOperation;
    private readonly Func<StorageOperationContext, PortableRepository>? _createPortableRepository;
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
    private string _problemSearchText = string.Empty;
    private string _problemSort = "UPDATED";
    private string _problemTag = string.Empty;
    private bool _problemFavoriteOnly;
    private string _problemDifficultyMin = string.Empty;
    private string _problemDifficultyMax = string.Empty;
    private int _problemVisibleMatches;
    private int _libraryTotal;
    private int _librarySolved;
    private int _libraryReview;
    private int _libraryFavorites;
    private bool _problemFiltersLoaded;
    private ProblemDetailRow? _selectedProblemDetail;
    private byte[]? _pendingPortableBytes;
    private string _portableIdentity = "NONE";
    private string _portableSummary = "NO PACKAGE LOADED";
    private string _portableStatus = string.Empty;

    public DesktopViewModel(IStorageOperationSource storage, SyncService syncService, string dataDirectory,
        Func<StorageOperationContext>? beginPortableOperation = null,
        Func<StorageOperationContext, PortableRepository>? createPortableRepository = null)
    {
        ArgumentNullException.ThrowIfNull(storage);
        ArgumentNullException.ThrowIfNull(syncService);
        ArgumentException.ThrowIfNullOrWhiteSpace(dataDirectory);
        _storage = storage;
        _syncService = syncService;
        _beginPortableOperation = beginPortableOperation;
        _createPortableRepository = createPortableRepository;
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

    public IReadOnlyList<string> ProblemJudgeFilters { get; } = ["ALL", "CODEFORCES", "ATCODER", "LUOGU"];

    public IReadOnlyList<string> ProblemStatusFilters { get; } = ["ALL", "UNSOLVED", "ATTEMPTED", "SOLVED", "REVIEW"];

    public IReadOnlyList<string> ProblemSortOptions { get; } = ["UPDATED", "DIFFICULTY", "TITLE", "ATTEMPTS"];

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

    public string ProblemSearchText
    {
        get => _problemSearchText;
        private set
        {
            if (SetField(ref _problemSearchText, value))
            {
                OnProblemFilterShapeChanged();
            }
        }
    }

    public string ProblemSort
    {
        get => _problemSort;
        private set => SetField(ref _problemSort, value);
    }

    public string ProblemTag
    {
        get => _problemTag;
        private set
        {
            if (SetField(ref _problemTag, value))
            {
                OnProblemFilterShapeChanged();
            }
        }
    }

    public bool ProblemFavoriteOnly
    {
        get => _problemFavoriteOnly;
        private set
        {
            if (SetField(ref _problemFavoriteOnly, value))
            {
                OnProblemFilterShapeChanged();
            }
        }
    }

    public string ProblemDifficultyMin
    {
        get => _problemDifficultyMin;
        private set
        {
            if (SetField(ref _problemDifficultyMin, value))
            {
                OnProblemFilterShapeChanged();
            }
        }
    }

    public string ProblemDifficultyMax
    {
        get => _problemDifficultyMax;
        private set
        {
            if (SetField(ref _problemDifficultyMax, value))
            {
                OnProblemFilterShapeChanged();
            }
        }
    }

    public ProblemDetailRow? SelectedProblemDetail
    {
        get => _selectedProblemDetail;
        private set
        {
            if (SetField(ref _selectedProblemDetail, value))
            {
                OnPropertyChanged(nameof(IsProblemDetailVisible));
                OnPropertyChanged(nameof(ShowProblemList));
                OnProblemFilterShapeChanged();
            }
        }
    }

    public bool IsProblemDetailVisible => SelectedProblemDetail is not null;

    public bool ShowProblemList => !IsProblemDetailVisible;

    public bool ShowNoProblemsEmptyState => IsProblemDetailVisible is false && HasNoProblems && !HasActiveProblemFilters;

    public bool ShowFilteredEmptyState => IsProblemDetailVisible is false && HasNoProblems && HasActiveProblemFilters;

    /// <summary>True when any ledger filter is non-default (drives the clear-filters empty state).</summary>
    public bool HasActiveProblemFilters =>
        ProblemSearchText.Length > 0
        || SelectedProblemJudge != "ALL"
        || SelectedProblemStatus != "ALL"
        || ProblemFavoriteOnly
        || ProblemTag.Length > 0
        || ProblemDifficultyMin.Length > 0
        || ProblemDifficultyMax.Length > 0;

    private void OnProblemFilterShapeChanged()
    {
        OnPropertyChanged(nameof(HasActiveProblemFilters));
        OnPropertyChanged(nameof(ShowNoProblemsEmptyState));
        OnPropertyChanged(nameof(ShowFilteredEmptyState));
    }

    public bool HasMoreProblems => Problems.Count < _problemVisibleMatches;

    public string ProblemMatchSummary =>
        $"MATCHES {_problemVisibleMatches} / LIBRARY {_libraryTotal} (SOLVED {_librarySolved} / REVIEW {_libraryReview} / FAVORITES {_libraryFavorites})";

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
                OnPropertyChanged(nameof(IsPortableVisible));
            }
        }
    }

    public string PageTitle => CurrentPage switch
    {
        DesktopPage.Connectors => "CONNECTORS",
        DesktopPage.History => "SYNC HISTORY",
        DesktopPage.Problems => "PROBLEMS",
        DesktopPage.Portable => "PORTABLE WORKSPACE",
        _ => "DASHBOARD",
    };

    public bool IsDashboardVisible => CurrentPage == DesktopPage.Dashboard;

    public bool IsConnectorsVisible => CurrentPage == DesktopPage.Connectors;

    public bool IsHistoryVisible => CurrentPage == DesktopPage.History;

    public bool IsProblemsVisible => CurrentPage == DesktopPage.Problems;

    public bool IsPortableVisible => CurrentPage == DesktopPage.Portable;

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
        await CommitProblemFiltersAsync(cancellationToken);
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
        await CommitProblemFiltersAsync(cancellationToken);
    }

    public async Task SelectProblemSortAsync(string sort, CancellationToken cancellationToken)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(sort);
        var normalizedSort = sort.Trim().ToUpperInvariant();
        if (!ProblemSortParser.TryParse(normalizedSort, out _))
        {
            StatusText = "ERROR";
            LastError = "UNKNOWN PROBLEM SORT";
            return;
        }

        ProblemSort = normalizedSort;
        await CommitProblemFiltersAsync(cancellationToken);
    }

    public async Task SetProblemSearchAsync(string search, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(search);
        ProblemSearchText = search.Trim();
        await CommitProblemFiltersAsync(cancellationToken);
    }

    public async Task SetProblemTagAsync(string tag, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(tag);
        ProblemTag = tag.Trim();
        await CommitProblemFiltersAsync(cancellationToken);
    }

    public async Task SetProblemFavoriteOnlyAsync(bool favoriteOnly, CancellationToken cancellationToken)
    {
        ProblemFavoriteOnly = favoriteOnly;
        await CommitProblemFiltersAsync(cancellationToken);
    }

    public async Task SetProblemDifficultyBoundsAsync(string min, string max, CancellationToken cancellationToken)
    {
        var normalizedMin = (min ?? string.Empty).Trim();
        var normalizedMax = (max ?? string.Empty).Trim();
        if (normalizedMin.Length > 0 && !int.TryParse(normalizedMin, System.Globalization.NumberStyles.Integer, System.Globalization.CultureInfo.InvariantCulture, out _))
        {
            StatusText = "ERROR";
            LastError = "DIFFICULTY BOUNDS MUST BE INTEGERS";
            return;
        }

        if (normalizedMax.Length > 0 && !int.TryParse(normalizedMax, System.Globalization.NumberStyles.Integer, System.Globalization.CultureInfo.InvariantCulture, out _))
        {
            StatusText = "ERROR";
            LastError = "DIFFICULTY BOUNDS MUST BE INTEGERS";
            return;
        }

        ProblemDifficultyMin = normalizedMin;
        ProblemDifficultyMax = normalizedMax;
        await CommitProblemFiltersAsync(cancellationToken);
    }

    public async Task ClearProblemFiltersAsync(CancellationToken cancellationToken)
    {
        SelectedProblemJudge = "ALL";
        SelectedProblemStatus = "ALL";
        ProblemSearchText = string.Empty;
        ProblemTag = string.Empty;
        ProblemFavoriteOnly = false;
        ProblemDifficultyMin = string.Empty;
        ProblemDifficultyMax = string.Empty;
        ProblemSort = "UPDATED";
        await CommitProblemFiltersAsync(cancellationToken);
    }

    public async Task LoadMoreProblemsAsync(CancellationToken cancellationToken)
    {
        if (!HasMoreProblems || IsBusy)
        {
            return;
        }

        await RefreshProblemsAsync(resetPaging: false, cancellationToken);
    }

    public async Task OpenProblemDetailAsync(ProblemRow row, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(row);
        try
        {
            if (!JudgeIdParser.TryParse(row.Judge, out var judge))
            {
                StatusText = "ERROR";
                LastError = "UNKNOWN JUDGE FILTER";
                return;
            }

            var detail = await _storage.RunAsync(
                (store, token) => store.GetProblemDetailAsync(judge, row.ExternalId, token),
                cancellationToken);
            SelectedProblemDetail = detail is null ? null : ProjectProblemDetail(detail);
        }
        catch (StorageException exception)
        {
            StatusText = "ERROR";
            LastError = exception.Failure.WireCode;
        }
    }

    public void CloseProblemDetail()
    {
        SelectedProblemDetail = null;
    }

    private async Task CommitProblemFiltersAsync(CancellationToken cancellationToken)
    {
        await RefreshProblemsAsync(resetPaging: true, cancellationToken);
        try
        {
            await SaveProblemFiltersAsync(cancellationToken);
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            throw;
        }
        catch (StorageException)
        {
            // Filter persistence is best-effort: the UI keeps the in-memory state
            // and the next committed change retries the write.
        }
    }

    private static ProblemDetailRow ProjectProblemDetail(ProblemEntry entry) => new(
        entry.Judge.ToString().ToUpperInvariant(),
        entry.ExternalId,
        entry.Title,
        entry.Url,
        ProblemStatusParser.ToUpper(entry.Status),
        entry.Difficulty?.ToString(System.Globalization.CultureInfo.InvariantCulture) ?? "UNKNOWN",
        entry.AttemptCount,
        entry.Favorite,
        entry.InReview,
        entry.Tags.Count == 0 ? "NONE" : string.Join(", ", entry.Tags),
        FormatProblemTimestamp(entry.FirstSeenAt),
        FormatProblemTimestamp(entry.LastSeenAt),
        FormatProblemTimestamp(entry.UpdatedAt));

    private static string FormatProblemTimestamp(DateTimeOffset value) =>
        value.ToUniversalTime().ToString("yyyy-MM-dd HH:mm:ss 'UTC'", System.Globalization.CultureInfo.InvariantCulture);

    /// <summary>
    /// Loads one OFFSET page of the ledger. Persisted filters load once before the
    /// first page so filters survive restart; every committed filter change resets
    /// to page one and persists (restart-persistence acceptance).
    /// </summary>
    public async Task RefreshProblemsAsync(bool resetPaging, CancellationToken cancellationToken)
    {
        if (!_problemFiltersLoaded)
        {
            _problemFiltersLoaded = true;
            await LoadProblemFiltersAsync(cancellationToken);
        }

        try
        {
            var offset = resetPaging ? 0 : Problems.Count;
            var page = await _storage.RunAsync(
                (store, token) => store.QueryProblemsAsync(BuildProblemQuery(), offset, ProblemPageSize, token),
                cancellationToken);
            if (resetPaging)
            {
                Problems.Clear();
            }

            foreach (var entry in page.Rows)
            {
                Problems.Add(ProjectProblemEntry(entry));
            }

            _problemVisibleMatches = page.Summary.Visible;
            _libraryTotal = page.Summary.Total;
            _librarySolved = page.Summary.Solved;
            _libraryReview = page.Summary.Review;
            _libraryFavorites = page.Summary.Favorites;
            OnPropertyChanged(nameof(HasMoreProblems));
            OnPropertyChanged(nameof(ProblemMatchSummary));
            OnPropertyChanged(nameof(HasNoProblems));
            OnPropertyChanged(nameof(ShowNoProblemsEmptyState));
            OnPropertyChanged(nameof(ShowFilteredEmptyState));
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
    }

    private const string ProblemFiltersSettingKey = "problems.filters.v1";
    private const int ProblemPageSize = 60;

    private sealed record ProblemFilterState(
        string Search, string Judge, string Status, string Sort, string Tag,
        bool FavoriteOnly, string DifficultyMin, string DifficultyMax);

    private ProblemLedgerQuery BuildProblemQuery()
    {
        var status = ProblemStatusParser.TryParse(SelectedProblemStatus, out var parsedStatus) ? (ProblemStatus?)parsedStatus : null;
        var judge = JudgeIdParser.TryParse(SelectedProblemJudge, out var parsedJudge) ? (JudgeId?)parsedJudge : null;
        var sort = ProblemSortParser.TryParse(ProblemSort, out var parsedSort) ? parsedSort : global::OjNexus.Windows.Core.Domain.ProblemSort.Updated;
        int? min = int.TryParse(ProblemDifficultyMin, System.Globalization.NumberStyles.Integer, System.Globalization.CultureInfo.InvariantCulture, out var minValue) ? minValue : null;
        int? max = int.TryParse(ProblemDifficultyMax, System.Globalization.NumberStyles.Integer, System.Globalization.CultureInfo.InvariantCulture, out var maxValue) ? maxValue : null;
        return new ProblemLedgerQuery(
            ProblemSearchText.Length == 0 ? null : ProblemSearchText,
            status,
            judge,
            ProblemFavoriteOnly,
            ProblemTag.Length == 0 ? null : ProblemTag,
            min,
            max,
            sort);
    }

    private async Task SaveProblemFiltersAsync(CancellationToken cancellationToken)
    {
        var state = new ProblemFilterState(
            ProblemSearchText, SelectedProblemJudge, SelectedProblemStatus, ProblemSort, ProblemTag,
            ProblemFavoriteOnly, ProblemDifficultyMin, ProblemDifficultyMax);
        await _storage.RunAsync(
            (store, token) => store.SetSettingAsync(ProblemFiltersSettingKey, System.Text.Json.JsonSerializer.Serialize(state), token),
            cancellationToken);
    }

    private async Task LoadProblemFiltersAsync(CancellationToken cancellationToken)
    {
        var json = await _storage.RunAsync(
            (store, token) => store.GetSettingAsync(ProblemFiltersSettingKey, token),
            cancellationToken);
        if (string.IsNullOrWhiteSpace(json))
        {
            return;
        }

        var state = System.Text.Json.JsonSerializer.Deserialize<ProblemFilterState>(json);
        if (state is null)
        {
            return;
        }

        SelectedProblemJudge = state.Judge;
        SelectedProblemStatus = state.Status;
        ProblemSearchText = state.Search ?? string.Empty;
        ProblemTag = state.Tag ?? string.Empty;
        ProblemFavoriteOnly = state.FavoriteOnly;
        ProblemDifficultyMin = state.DifficultyMin ?? string.Empty;
        ProblemDifficultyMax = state.DifficultyMax ?? string.Empty;
        ProblemSort = string.IsNullOrEmpty(state.Sort) ? "UPDATED" : state.Sort;
    }

    public async Task RefreshAsync(CancellationToken cancellationToken)
    {
        await _storage.RunAsync((store, token) => RefreshCoreAsync(store, token), cancellationToken);
    }

    private async Task<string?> RefreshCoreAsync(ISyncStore store, CancellationToken cancellationToken)
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
            await RefreshProblemsAsync(resetPaging: true, cancellationToken);
            StatusText = "READY";
            return null;
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
            return exception.Failure.WireCode;
        }
        catch (Exception)
        {
            StatusText = "ERROR";
            LastError = "LOCAL STATE UNAVAILABLE";
            return null;
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
        string? batchStorageFailureCode = null;
        try
        {
            foreach (var row in candidates)
            {
                if (cancellationToken.IsCancellationRequested || BatchCancelRequested())
                {
                    StatusText = "CANCELLED";
                    LastError = batchStorageFailureCode ?? "CANCELLED";
                    return false;
                }

                var result = await SyncConnectorCoreAsync(row, store, cancellationToken);
                if (!result.Succeeded)
                {
                    allSucceeded = false;
                }
                batchStorageFailureCode ??= result.StorageFailureCode;
            }

            if (cancellationToken.IsCancellationRequested || BatchCancelRequested())
            {
                StatusText = "CANCELLED";
                LastError = batchStorageFailureCode ?? "CANCELLED";
                return false;
            }

            StatusText = allSucceeded ? "READY" : "ERROR";
            LastError = batchStorageFailureCode ?? (allSucceeded ? string.Empty : "SYNC PARTIAL");
            if (batchStorageFailureCode is not null)
            {
                StatusText = "ERROR";
            }
            return allSucceeded;
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            StatusText = "CANCELLED";
            LastError = batchStorageFailureCode ?? "CANCELLED";
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
        var result = await _storage.RunAsync((store, token) => SyncConnectorCoreAsync(row, store, token), cancellationToken);
        return result.Succeeded;
    }

    private async Task<SyncActionResult> SyncConnectorCoreAsync(ConnectorRow row, ISyncStore store, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(row);
        if (row.IsConfigured && !row.IsEnabled)
        {
            StatusText = "ERROR";
            LastError = "ACCOUNT DISABLED";
            return new(false);
        }

        lock (_syncCancellationLock)
        {
            if (_syncCancellations.ContainsKey(row))
            {
                return new(false);
            }
        }

        if (!await SaveConnectorCoreAsync(row, store, cancellationToken))
        {
            return new(false, GetStorageWireCode(LastError));
        }

        using var linkedCancellation = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        lock (_syncCancellationLock)
        {
            if (_syncCancellations.ContainsKey(row))
            {
                return new(false);
            }

            _syncCancellations[row] = linkedCancellation;
        }

        row.IsSyncing = true;
        row.Status = "SYNCING";
        StatusText = "SYNCING";
        LastError = string.Empty;
        string? storageFailureCode = null;
        var succeeded = false;
        try
        {
            var report = await _syncService.RunAsync(JudgeAccount.Create(row.Judge, row.Handle), false, store, linkedCancellation.Token);
            row.Status = report.Status.ToString().ToUpperInvariant();
            if (report.Error is not null)
            {
                LastError = report.Error.Value.ToString().ToUpperInvariant();
            }

            succeeded = report.Status == SyncOperationStatus.Success;
        }
        catch (OperationCanceledException) when (linkedCancellation.IsCancellationRequested)
        {
            row.Status = "CANCELLED";
            LastError = "CANCELLED";
            succeeded = false;
        }
        catch (StorageException exception)
        {
            storageFailureCode = exception.Failure.WireCode;
            row.Status = "ERROR";
            LastError = storageFailureCode;
            succeeded = false;
        }
        catch (Exception)
        {
            row.Status = "ERROR";
            LastError = "SYNC FAILED";
            succeeded = false;
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

            var refreshStorageFailureCode = await RefreshCoreAsync(store, CancellationToken.None);
            storageFailureCode ??= refreshStorageFailureCode;
            if (storageFailureCode is not null)
            {
                row.Status = "ERROR";
                succeeded = false;
                StatusText = "ERROR";
                LastError = storageFailureCode;
            }
        }

        return new(succeeded, storageFailureCode);
    }

    private static string? GetStorageWireCode(string value)
    {
        foreach (var code in Enum.GetValues<StorageFailureCode>())
        {
            var wireCode = new StorageFailure(code, "desktop").WireCode;
            if (string.Equals(value, wireCode, StringComparison.Ordinal)) return wireCode;
        }

        return null;
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
        return (await SyncConnectorCoreAsync(connector, store, cancellationToken)).Succeeded;
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

    private static ProblemRow ProjectProblemEntry(ProblemEntry problem) => new(
        problem.Judge.ToString().ToUpperInvariant(),
        problem.ExternalId,
        problem.Title,
        problem.Url,
        ProblemStatusParser.ToUpper(problem.Status),
        FormatProblemTimestamp(problem.LastSeenAt),
        problem.Difficulty?.ToString(System.Globalization.CultureInfo.InvariantCulture) ?? "UNKNOWN",
        problem.AttemptCount);

    private void ProjectProblems(IReadOnlyList<ProblemEntry> problems)
    {
        Problems.Clear();
        foreach (var problem in problems)
        {
            Problems.Add(ProjectProblemEntry(problem));
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

    // --- portable workspace (phase 133) ---

    /// <summary>Test stub for the import file picker; null uses the real OpenFileDialog.</summary>
    internal Func<string?>? PortableOpenFilePicker { get; set; }

    /// <summary>Test stub for the export save picker; null uses the real SaveFileDialog.</summary>
    internal Func<string?>? PortableSaveFilePicker { get; set; }

    public string PortableIdentity
    {
        get => _portableIdentity;
        private set => SetField(ref _portableIdentity, value);
    }

    public string PortableSummary
    {
        get => _portableSummary;
        private set => SetField(ref _portableSummary, value);
    }

    public string PortableStatus
    {
        get => _portableStatus;
        private set => SetField(ref _portableStatus, value);
    }

    public bool HasPendingPortableImport => _pendingPortableBytes is not null;

    private bool PortableStorageAvailable => _beginPortableOperation is not null && _createPortableRepository is not null;

    /// <summary>
    /// Opens the import file picker (UI thread) and loads a read-only preview.
    /// A cancelled picker is a clean no-op; nothing is written at this stage.
    /// </summary>
    public async Task ImportPortableAsync(string? pickedPath, CancellationToken cancellationToken)
    {
        if (pickedPath is null)
        {
            PortableStatus = "IMPORT CANCELLED";
            return;
        }

        if (!PortableStorageAvailable)
        {
            PortableStatus = "PORTABLE STORAGE UNAVAILABLE";
            return;
        }

        PortableStatus = "READING";
        try
        {
            var bytes = await File.ReadAllBytesAsync(pickedPath, cancellationToken);
            var preview = await RunPortableAsync((repository, token) => repository.PreviewAsync(bytes, token), cancellationToken);
            _pendingPortableBytes = bytes;
            PortableIdentity = preview.Identity;
            PortableSummary = BuildPortableSummary(preview);
            PortableStatus = preview.AcceptAll
                ? "PREVIEW READY — CONFIRM TO APPLY"
                : "PREVIEW READY — PACKAGE HAS REJECTS";
        }
        catch (PortableContractException exception)
        {
            DiscardPendingPortableImport();
            PortableStatus = $"REJECTED {exception.Reject.Wire}: {exception.Reject.Detail}";
        }
        catch (Exception exception)
        {
            DiscardPendingPortableImport();
            PortableStatus = $"IMPORT FAILED: {ClassifyPortableError(exception)}";
        }
    }

    /// <summary>Applies the pending preview inside one transaction. Cancel-safe: nothing is written until commit.</summary>
    public async Task ConfirmPortableImportAsync(CancellationToken cancellationToken)
    {
        if (_pendingPortableBytes is null)
        {
            PortableStatus = "NO PACKAGE PENDING";
            return;
        }

        PortableStatus = "APPLYING";
        try
        {
            var pending = _pendingPortableBytes;
            var result = await RunPortableAsync((repository, token) => repository.ApplyAsync(pending, token), cancellationToken);
            DiscardPendingPortableImport();
            PortableStatus = result.Identical
                ? "ARCHIVE ALREADY MATCHES PACKAGE. NO CHANGES APPLIED."
                : $"APPLIED {result.RowsUpserted} UPSERTED / {result.RowsDeleted} DELETED";
        }
        catch (PortableContractException exception)
        {
            DiscardPendingPortableImport();
            PortableStatus = $"REJECTED {exception.Reject.Wire}: {exception.Reject.Detail}";
        }
        catch (OperationCanceledException)
        {
            DiscardPendingPortableImport();
            PortableStatus = "APPLY CANCELLED — NOTHING WRITTEN";
        }
        catch (Exception exception)
        {
            PortableStatus = $"APPLY FAILED: {ClassifyPortableError(exception)}";
        }
    }

    /// <summary>Discards a pending preview. Pre-apply only: nothing was ever written to discard.</summary>
    public void CancelPortableImport()
    {
        if (_pendingPortableBytes is null)
        {
            PortableStatus = "NO PACKAGE PENDING";
            return;
        }

        DiscardPendingPortableImport();
        PortableStatus = "IMPORT CANCELLED — NOTHING WRITTEN";
    }

    /// <summary>Rebuilds the canonical package from the archive and writes it through the save picker.</summary>
    public async Task ExportPortableAsync(CancellationToken cancellationToken)
    {
        if (!PortableStorageAvailable)
        {
            PortableStatus = "PORTABLE STORAGE UNAVAILABLE";
            return;
        }

        var savePath = PortableSaveFilePicker?.Invoke();
        if (savePath is null)
        {
            PortableStatus = "EXPORT CANCELLED";
            return;
        }

        PortableStatus = "WRITING";
        try
        {
            var bytes = await RunPortableAsync((repository, token) => repository.ExportAsync(token), cancellationToken);
            await File.WriteAllBytesAsync(savePath, bytes, cancellationToken);
            PortableStatus = $"EXPORT WRITTEN ({bytes.Length} BYTES)";
        }
        catch (InvalidOperationException)
        {
            PortableStatus = "ARCHIVE EMPTY — NOTHING TO EXPORT";
        }
        catch (Exception exception)
        {
            PortableStatus = $"EXPORT FAILED: {ClassifyPortableError(exception)}";
        }
    }

    /// <summary>UI-thread picker resolution: stub first, then the real dialog. Null = cancelled.</summary>
    public string? PickPortableImportFile()
    {
        if (PortableOpenFilePicker is { } stub)
        {
            return stub();
        }

        var dialog = new Microsoft.Win32.OpenFileDialog
        {
            Filter = "Portable workspace (*.json)|*.json|All files (*.*)|*.*",
            Title = "IMPORT PORTABLE WORKSPACE",
        };
        return dialog.ShowDialog() == true ? dialog.FileName : null;
    }

    public string? PickPortableExportFile()
    {
        if (PortableSaveFilePicker is { } stub)
        {
            return stub();
        }

        var dialog = new Microsoft.Win32.SaveFileDialog
        {
            Filter = "Portable workspace (*.json)|*.json",
            FileName = "oj-nexus-portable-workspace.json",
            Title = "EXPORT PORTABLE WORKSPACE",
        };
        return dialog.ShowDialog() == true ? dialog.FileName : null;
    }

    private async Task<T> RunPortableAsync<T>(Func<PortableRepository, CancellationToken, Task<T>> work, CancellationToken cancellationToken)
    {
        var begin = _beginPortableOperation ?? throw new InvalidOperationException("portable storage unavailable");
        var create = _createPortableRepository ?? throw new InvalidOperationException("portable storage unavailable");
        await using var operation = begin();
        var repository = create(operation);
        return await work(repository, operation.CancellationToken).ConfigureAwait(false);
    }

    private void DiscardPendingPortableImport()
    {
        _pendingPortableBytes = null;
        PortableIdentity = "NONE";
        PortableSummary = "NO PACKAGE LOADED";
        OnPropertyChanged(nameof(HasPendingPortableImport));
    }

    private static string BuildPortableSummary(PortablePreview preview)
    {
        var lines = new List<string>
        {
            "COUNTS " + string.Join(" / ", PortableSpec.Collections.Select(c => $"{c.ToUpperInvariant()} {preview.Counts.GetValueOrDefault(c)}")),
        };
        lines.Add(preview.Sources.Count == 0
            ? "SOURCES NONE"
            : "SOURCES " + string.Join(" / ", preview.Sources.Select(s => $"{s.SourceScopeId} {s.RecordCount}")));
        lines.Add($"CAPABILITY EDITABLE {preview.EditableCount} / STORED-ONLY {preview.StoredOnlyCount} / REJECTED {preview.RejectedCount}");
        lines.Add("STORED-ONLY RECORDS KEEP SAVED ARCHIVE STATE");
        if (preview.Conflicts.Count > 0)
        {
            lines.Add("CONFLICTS " + string.Join(" / ", preview.Conflicts.Select(c => $"{c.Collection} {c.StableId} {c.Kind}")));
        }

        return string.Join(Environment.NewLine, lines);
    }

    private static string ClassifyPortableError(Exception exception) => exception switch
    {
        PortableContractException contract => contract.Reject.Wire,
        StorageException storage => storage.Failure.WireCode,
        _ => "INTERNAL FAILURE",
    };

    private void OnPropertyChanged(string? propertyName) => PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
}

public static class DesktopRuntime
{
    public static DesktopViewModel Create(IStorageOperationSource storage, string dataDirectory,
        Func<StorageOperationContext>? beginPortableOperation = null,
        Func<StorageOperationContext, PortableRepository>? createPortableRepository = null)
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
        return new DesktopViewModel(storage, syncService, dataDirectory, beginPortableOperation, createPortableRepository);
    }

    private sealed class SystemClock : IClock
    {
        public DateTimeOffset UtcNow => DateTimeOffset.UtcNow;
    }
}
