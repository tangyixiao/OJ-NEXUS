using System.Text;
using OjNexus.Windows.Core.Contracts;
using OjNexus.Windows.Core.Domain;

namespace OjNexus.Windows.Core.Sync;

public sealed class InMemorySyncStore : ISyncStore
{
    private readonly object _gate = new();
    private readonly Dictionary<JudgeId, JudgeAccount> _accounts = [];
    private readonly Dictionary<long, StoredOperation> _operations = [];
    private readonly Dictionary<string, CodeforcesProfilePayload> _codeforcesProfiles = new(StringComparer.OrdinalIgnoreCase);
    private readonly Dictionary<string, IReadOnlyList<CodeforcesRating>> _codeforcesRatings = new(StringComparer.OrdinalIgnoreCase);
    private readonly Dictionary<string, IReadOnlyList<CodeforcesSubmission>> _codeforcesSubmissions = new(StringComparer.OrdinalIgnoreCase);
    private readonly Dictionary<string, IReadOnlyList<AtCoderSubmission>> _atcoderSubmissions = new(StringComparer.OrdinalIgnoreCase);
    private readonly Dictionary<string, LuoguPayloadState> _luoguPayloads = new(StringComparer.OrdinalIgnoreCase);
    private readonly Dictionary<string, ProblemEntry> _problems = new(StringComparer.Ordinal);
    private readonly Dictionary<string, List<string>> _problemTags = new(StringComparer.Ordinal);
    private readonly Dictionary<string, StoredReview> _reviews = new(StringComparer.Ordinal);
    private readonly Dictionary<string, string> _settings = new(StringComparer.Ordinal);
    private long _nextOperationId = 1;

    public Task<IReadOnlyList<JudgeAccount>> GetAccountsAsync(CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        lock (_gate)
        {
            return Task.FromResult<IReadOnlyList<JudgeAccount>>(_accounts.Values.OrderBy(account => account.Judge).ToArray());
        }
    }

    public Task<CodeforcesPayloadSnapshot> GetCodeforcesPayloadAsync(
        string handle,
        CancellationToken cancellationToken)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(handle);
        cancellationToken.ThrowIfCancellationRequested();
        lock (_gate)
        {
            var normalizedHandle = handle.Trim();
            return Task.FromResult(new CodeforcesPayloadSnapshot(
                _codeforcesProfiles.GetValueOrDefault(normalizedHandle),
                _codeforcesRatings.GetValueOrDefault(normalizedHandle, Array.Empty<CodeforcesRating>()),
                _codeforcesSubmissions.GetValueOrDefault(normalizedHandle, Array.Empty<CodeforcesSubmission>())));
        }
    }

    public Task<AtCoderPayloadSnapshot> GetAtCoderPayloadAsync(
        string handle,
        CancellationToken cancellationToken)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(handle);
        cancellationToken.ThrowIfCancellationRequested();
        lock (_gate)
        {
            return Task.FromResult(new AtCoderPayloadSnapshot(
                _atcoderSubmissions.GetValueOrDefault(handle.Trim(), Array.Empty<AtCoderSubmission>())));
        }
    }

    public Task<LuoguPayloadSnapshot> GetLuoguPayloadAsync(
        string handle,
        CancellationToken cancellationToken)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(handle);
        cancellationToken.ThrowIfCancellationRequested();
        lock (_gate)
        {
            var normalizedHandle = handle.Trim();
            var state = _luoguPayloads.GetValueOrDefault(normalizedHandle);
            return Task.FromResult(state?.ToSnapshot() ?? new LuoguPayloadSnapshot(null, 0, 0, 0));
        }
    }

    public Task UpsertAccountAsync(JudgeAccount account, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(account);
        account = JudgeAccount.Create(account.Judge, account.Handle) with { Enabled = account.Enabled };
        cancellationToken.ThrowIfCancellationRequested();
        lock (_gate)
        {
            _accounts[account.Judge] = account;
        }

        return Task.CompletedTask;
    }

    public Task<IReadOnlyList<SyncOperation>> GetRecentOperationsAsync(
        JudgeId? judge,
        int limit,
        CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        if (limit <= 0)
        {
            return Task.FromResult<IReadOnlyList<SyncOperation>>(Array.Empty<SyncOperation>());
        }

        lock (_gate)
        {
            var operations = _operations.Values
                .Where(operation => judge is null || operation.Account.Judge == judge)
                .OrderByDescending(operation => operation.StartedAt)
                .ThenByDescending(operation => operation.Id)
                .Take(limit)
                .Select(operation => operation.ToSyncOperation())
                .ToArray();
            return Task.FromResult<IReadOnlyList<SyncOperation>>(operations);
        }
    }

    public Task<ProblemLedgerPage> QueryProblemsAsync(
        ProblemLedgerQuery query,
        int offset,
        int limit,
        CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        lock (_gate)
        {
            var all = _problems.Values
                .Select(EffectiveEntry)
                .Where(row => MatchesQuery(row, query))
                .OrderBy(row => row, CreateLedgerComparer(query.Sort))
                .ToArray();
            var bounded = limit <= 0 || offset < 0
                ? Array.Empty<ProblemEntry>()
                : all.Skip(Math.Max(offset, 0)).Take(limit).ToArray();
            var summary = SummarizeUnfiltered() with { Visible = all.Length };
            return Task.FromResult(new ProblemLedgerPage(bounded, Math.Max(offset, 0), limit, summary));
        }
    }

    public Task<ProblemEntry?> GetProblemDetailAsync(
        JudgeId judge,
        string externalId,
        CancellationToken cancellationToken)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(externalId);
        cancellationToken.ThrowIfCancellationRequested();
        lock (_gate)
        {
            var key = $"{judge}:{externalId}";
            var row = _problems.GetValueOrDefault(key);
            return Task.FromResult(row is null ? null : EffectiveEntry(row));
        }
    }

    public Task<string?> GetSettingAsync(string key, CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        lock (_gate)
        {
            return Task.FromResult(_settings.GetValueOrDefault(key));
        }
    }

    public Task SetSettingAsync(string key, string value, CancellationToken cancellationToken)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        cancellationToken.ThrowIfCancellationRequested();
        lock (_gate)
        {
            _settings[key] = value;
        }

        return Task.CompletedTask;
    }

    /// <summary>
    /// Test/bootstrap surface: inserts a contract-shaped ledger row directly,
    /// bypassing payload derivation. Used by VM tests that need difficulty,
    /// favorite, attempt counts, review state or tags the payloads cannot carry.
    /// </summary>
    public void SeedProblem(ProblemEntry entry, IReadOnlyList<string>? tags = null)
    {
        ArgumentNullException.ThrowIfNull(entry);
        lock (_gate)
        {
            _problems[$"{entry.Judge}:{entry.ExternalId}"] = entry;
            _problemTags[$"{entry.Judge}:{entry.ExternalId}"] = tags is null
                ? []
                : [.. tags];
        }
    }

    /// <summary>
    /// in_review is derived at read time, mirroring the SQL EXISTS on the
    /// reviews table: a stored seed flag ORs with any review rows for the key.
    /// </summary>
    private ProblemEntry EffectiveEntry(ProblemEntry row) =>
        _reviews.ContainsKey($"{row.Judge}:{row.ExternalId}")
            ? row with { InReview = true }
            : row;

    private bool MatchesQuery(ProblemEntry row, ProblemLedgerQuery query)
    {
        if (query.Judge is { } judge && row.Judge != judge)
        {
            return false;
        }

        if (query.Status is { } status && row.Status != status)
        {
            return false;
        }

        if (query.FavoriteOnly && !row.Favorite)
        {
            return false;
        }

        if (query.DifficultyMin is not null || query.DifficultyMax is not null)
        {
            if (row.Difficulty is null)
            {
                return false;
            }

            if (query.DifficultyMin is { } min && row.Difficulty < min)
            {
                return false;
            }

            if (query.DifficultyMax is { } max && row.Difficulty > max)
            {
                return false;
            }
        }

        if (!string.IsNullOrEmpty(query.Tag)
            && !_problemTags.GetValueOrDefault($"{row.Judge}:{row.ExternalId}", []).Contains(query.Tag, StringComparer.Ordinal))
        {
            return false;
        }

        var search = ProblemSearch.Normalize(query.Search);
        if (search is not null
            && !row.Title.ToLowerInvariant().Contains(search, StringComparison.Ordinal)
            && !row.ExternalId.ToLowerInvariant().Contains(search, StringComparison.Ordinal))
        {
            return false;
        }

        return true;
    }

    private ProblemLedgerSummary SummarizeUnfiltered()
    {
        var rows = _problems.Values.ToArray();
        return new ProblemLedgerSummary(
            Visible: 0,
            Total: rows.Length,
            Solved: rows.Count(row => row.Solved),
            Review: rows.Count(row => EffectiveEntry(row).InReview),
            Favorites: rows.Count(row => row.Favorite));
    }

    /// <summary>
    /// The contract sort + stable code-point tie-break as an in-memory comparer.
    /// UTF-8 byte order is code-point order; StringComparer.Ordinal would be
    /// UTF-16 code-unit order, which disagrees on astral vs high-BMP titles
    /// (vector 007 pins exactly that case).
    /// </summary>
    private static Comparer<ProblemEntry> CreateLedgerComparer(ProblemSort sort)
    {
        return Comparer<ProblemEntry>.Create((left, right) =>
        {
            int primary = sort switch
            {
                ProblemSort.Updated => right.UpdatedAt.CompareTo(left.UpdatedAt),
                ProblemSort.Difficulty => (left.Difficulty is null, right.Difficulty is null) switch
                {
                    (true, true) => 0,
                    (true, false) => 1,
                    (false, true) => -1,
                    _ => left.Difficulty.GetValueOrDefault().CompareTo(right.Difficulty.GetValueOrDefault()),
                },
                ProblemSort.Title => CompareUtf8CodePoint(left.Title.ToLowerInvariant(), right.Title.ToLowerInvariant()),
                ProblemSort.Attempts => right.AttemptCount.CompareTo(left.AttemptCount),
                _ => 0,
            };
            if (primary != 0)
            {
                return primary;
            }

            var judge = CompareUtf8CodePoint(left.Judge.ToString().ToLowerInvariant(), right.Judge.ToString().ToLowerInvariant());
            if (judge != 0)
            {
                return judge;
            }

            return CompareUtf8CodePoint(left.ExternalId, right.ExternalId);
        });
    }

    private static int CompareUtf8CodePoint(string left, string right)
    {
        var leftBytes = Encoding.UTF8.GetBytes(left);
        var rightBytes = Encoding.UTF8.GetBytes(right);
        var length = Math.Min(leftBytes.Length, rightBytes.Length);
        for (var index = 0; index < length; index++)
        {
            if (leftBytes[index] != rightBytes[index])
            {
                return leftBytes[index].CompareTo(rightBytes[index]);
            }
        }

        return leftBytes.Length.CompareTo(rightBytes.Length);
    }

    public Task<IReadOnlyList<ReviewEntry>> GetDueReviewsAsync(
        JudgeId? judge,
        int limit,
        CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        if (limit <= 0)
        {
            return Task.FromResult<IReadOnlyList<ReviewEntry>>(Array.Empty<ReviewEntry>());
        }

        var today = ReviewIntervalLadder.DayIndexFor(DateTimeOffset.Now);
        lock (_gate)
        {
            var rows = _reviews.Values
                .Where(row => string.CompareOrdinal(row.DueDayIndex, today) <= 0)
                .Where(row => judge is null || row.Judge == judge)
                .OrderBy(row => row.DueDayIndex, StringComparer.Ordinal)
                .ThenBy(row => row.Judge)
                .ThenBy(row => row.ExternalId, StringComparer.Ordinal)
                .Take(limit)
                .Select(row =>
                {
                    _problems.TryGetValue($"{row.Judge}:{row.ExternalId}", out var problem);
                    return new ReviewEntry(
                        row.Judge,
                        row.ExternalId,
                        problem?.Title,
                        problem?.Url,
                        row.Stage,
                        row.DueAt,
                        row.DueDayIndex,
                        row.LastResult,
                        row.LastReviewedAt);
                })
                .ToArray();
            return Task.FromResult<IReadOnlyList<ReviewEntry>>(rows);
        }
    }

    public Task<ReviewEntry> MarkReviewAsync(
        JudgeId judge,
        string externalId,
        ReviewResult result,
        DateTimeOffset now,
        CancellationToken cancellationToken)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(externalId);
        cancellationToken.ThrowIfCancellationRequested();
        var trimmedId = externalId.Trim();
        lock (_gate)
        {
            var key = $"{judge}:{trimmedId}";
            var existing = _reviews.GetValueOrDefault(key);
            var stage = existing?.Stage ?? 0;
            var (newStage, dueAt, dueDayIndex) = ReviewIntervalLadder.ScheduleNext(result, stage, now);
            var stored = new StoredReview(judge, trimmedId, newStage, dueAt, dueDayIndex, result, now, now);
            _reviews[key] = stored;
            _problems.TryGetValue(key, out var problem);
            return Task.FromResult(new ReviewEntry(
                judge,
                trimmedId,
                problem?.Title,
                problem?.Url,
                newStage,
                dueAt,
                dueDayIndex,
                result,
                now));
        }
    }

    public Task<long> OpenOperationAsync(
        JudgeAccount account,
        string dataGeneration,
        DateTimeOffset startedAt,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(account);
        ArgumentNullException.ThrowIfNull(dataGeneration);
        account = JudgeAccount.Create(account.Judge, account.Handle) with { Enabled = account.Enabled };
        cancellationToken.ThrowIfCancellationRequested();
        lock (_gate)
        {
            _accounts[account.Judge] = account;
            var id = _nextOperationId++;
            _operations.Add(id, new StoredOperation(id, account, dataGeneration, startedAt));
            return Task.FromResult(id);
        }
    }

    public Task AppendModuleAsync(
        long operationId,
        SyncModuleOutcome outcome,
        DateTimeOffset completedAt,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(outcome);
        cancellationToken.ThrowIfCancellationRequested();
        lock (_gate)
        {
            var operation = GetOperation(operationId);
            var normalizedOutcome = ModuleFailureType.Normalize(outcome);
            operation.Modules.RemoveAll(existing => StringComparer.Ordinal.Equals(existing.Stage, outcome.Stage));
            operation.Modules.Add(normalizedOutcome);
            switch (normalizedOutcome.Payload)
            {
                case CodeforcesProfilePayload profile:
                    _codeforcesProfiles[profile.Handle] = profile;
                    break;
                case CodeforcesRatingsPayload ratings:
                    _codeforcesRatings[ratings.Handle] = ratings.Items.ToArray();
                    break;
                case CodeforcesSubmissionsPayload submissions:
                    _codeforcesSubmissions[submissions.Handle] = submissions.Items.ToArray();
                    DeriveProblems(JudgeId.Codeforces, submissions.Items.Select(item => (
                        ExternalId: ProblemUrls.CodeforcesExternalId(item.ContestId, item.ProblemIndex),
                        Title: string.IsNullOrWhiteSpace(item.ProblemName) ? null : item.ProblemName.Trim(),
                        Solved: IsAcceptedVerdict(item.Verdict))), completedAt);
                    break;
                case AtCoderSubmissionsPayload atcoderSubmissions:
                    _atcoderSubmissions[atcoderSubmissions.Handle] = atcoderSubmissions.Items.ToArray();
                    DeriveProblems(JudgeId.AtCoder, atcoderSubmissions.Items.Select(item => (
                        ExternalId: string.IsNullOrWhiteSpace(item.ContestId) || string.IsNullOrWhiteSpace(item.ProblemId)
                            ? null
                            : $"{item.ContestId.Trim()}+{item.ProblemId.Trim()}",
                        Title: string.IsNullOrWhiteSpace(item.ProblemId) ? null : item.ProblemId.Trim(),
                        Solved: IsAcceptedVerdict(item.Result))), completedAt);
                    break;
                case LuoguProfilePayload luoguProfile:
                    GetLuoguState(luoguProfile.Handle).Profile = luoguProfile;
                    break;
                case LuoguCollectionPayload luoguCollection:
                    var luoguState = GetLuoguState(luoguCollection.Handle);
                    switch (luoguCollection.Collection)
                    {
                        case "SUBMISSIONS": luoguState.SubmissionsCount = luoguCollection.Count; break;
                        case "CONTESTS": luoguState.ContestsCount = luoguCollection.Count; break;
                        case "PROBLEMSET": luoguState.ProblemsCount = luoguCollection.Count; break;
                    }
                    break;
            }
        }

        return Task.CompletedTask;
    }

    public Task CloseOperationAsync(
        long operationId,
        SyncOperationStatus status,
        SyncError? error,
        DateTimeOffset finishedAt,
        CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        lock (_gate)
        {
            var operation = GetOperation(operationId);
            operation.Status = status;
            operation.FinishedAt = finishedAt;
            operation.Error = error;
        }

        return Task.CompletedTask;
    }

    private StoredOperation GetOperation(long operationId) =>
        _operations.TryGetValue(operationId, out var operation)
            ? operation
            : throw new KeyNotFoundException($"No operation exists with ID {operationId}.");

    private static bool IsAcceptedVerdict(string? verdict) =>
        verdict is not null
        && (verdict.Equals("OK", StringComparison.OrdinalIgnoreCase)
            || verdict.Equals("AC", StringComparison.OrdinalIgnoreCase)
            || verdict.StartsWith("Accepted", StringComparison.OrdinalIgnoreCase));

    /// <summary>Mirrors SqliteSyncStore problem derivation: solved is sticky, title keeps its first value,
    /// attempt_count recomputes exactly from the current per-handle payload (one account per judge).</summary>
    private void DeriveProblems(
        JudgeId judge,
        IEnumerable<(string? ExternalId, string? Title, bool Solved)> submissions,
        DateTimeOffset seenAt)
    {
        var seen = new Dictionary<string, (string Title, bool Solved, int Attempts)>(StringComparer.Ordinal);
        foreach (var (externalId, title, solved) in submissions)
        {
            if (externalId is null || ProblemUrls.For(judge, externalId) is not { } url)
            {
                continue;
            }

            if (seen.TryGetValue(externalId, out var existing))
            {
                seen[externalId] = (existing.Title, existing.Solved || solved, existing.Attempts + 1);
            }
            else
            {
                seen[externalId] = (title ?? externalId, solved, 1);
            }
        }

        foreach (var (externalId, info) in seen)
        {
            var key = $"{judge}:{externalId}";
            if (_problems.TryGetValue(key, out var row))
            {
                _problems[key] = row with
                {
                    Solved = row.Solved || info.Solved,
                    LastSeenAt = seenAt,
                    UpdatedAt = seenAt,
                    AttemptCount = info.Attempts,
                };
            }
            else
            {
                _problems[key] = new ProblemEntry(
                    judge,
                    externalId,
                    info.Title,
                    ProblemUrls.For(judge, externalId) ?? string.Empty,
                    info.Solved,
                    seenAt,
                    seenAt,
                    seenAt,
                    Difficulty: null,
                    Favorite: false,
                    AttemptCount: info.Attempts,
                    InReview: false,
                    Tags: Array.Empty<string>());
            }
        }
    }

    private LuoguPayloadState GetLuoguState(string handle)
    {
        var normalizedHandle = handle.Trim();
        if (!_luoguPayloads.TryGetValue(normalizedHandle, out var state))
        {
            state = new LuoguPayloadState();
            _luoguPayloads[normalizedHandle] = state;
        }

        return state;
    }

    private sealed class LuoguPayloadState
    {
        public LuoguProfilePayload? Profile { get; set; }
        public int SubmissionsCount { get; set; }
        public int ContestsCount { get; set; }
        public int ProblemsCount { get; set; }

        public LuoguPayloadSnapshot ToSnapshot() => new(Profile, SubmissionsCount, ContestsCount, ProblemsCount);
    }

    private sealed record StoredReview(
        JudgeId Judge,
        string ExternalId,
        int Stage,
        DateTimeOffset DueAt,
        string DueDayIndex,
        ReviewResult? LastResult,
        DateTimeOffset LastReviewedAt,
        DateTimeOffset CreatedAt);

    private sealed class StoredOperation(long id, JudgeAccount account, string dataGeneration, DateTimeOffset startedAt)
    {
        public long Id { get; } = id;

        public JudgeAccount Account { get; } = account;

        public string DataGeneration { get; } = dataGeneration;

        public DateTimeOffset StartedAt { get; } = startedAt;

        public DateTimeOffset? FinishedAt { get; set; }

        public SyncOperationStatus Status { get; set; } = SyncOperationStatus.Running;

        public SyncError? Error { get; set; }

        public List<SyncModuleOutcome> Modules { get; } = [];

        public SyncOperation ToSyncOperation() =>
            new(Id, Account, DataGeneration, StartedAt, FinishedAt, Status, Modules, Error);
    }
}
