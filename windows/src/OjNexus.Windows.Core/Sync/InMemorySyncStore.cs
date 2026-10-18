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
    private readonly Dictionary<string, StoredReview> _reviews = new(StringComparer.Ordinal);
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

    public Task<IReadOnlyList<ProblemEntry>> GetProblemsAsync(
        JudgeId? judge,
        ProblemStatus? status,
        int limit,
        CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        if (limit <= 0)
        {
            return Task.FromResult<IReadOnlyList<ProblemEntry>>(Array.Empty<ProblemEntry>());
        }

        lock (_gate)
        {
            var rows = _problems.Values
                .Where(row => judge is null || row.Judge == judge)
                .Where(row => status is null || row.Solved == (status == ProblemStatus.Solved))
                .OrderByDescending(row => row.LastSeenAt)
                .ThenBy(row => row.Judge)
                .ThenBy(row => row.ExternalId, StringComparer.Ordinal)
                .Take(limit)
                .ToArray();
            return Task.FromResult<IReadOnlyList<ProblemEntry>>(rows);
        }
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

    /// <summary>Mirrors SqliteSyncStore problem derivation: solved is sticky, title keeps its first value.</summary>
    private void DeriveProblems(
        JudgeId judge,
        IEnumerable<(string? ExternalId, string? Title, bool Solved)> submissions,
        DateTimeOffset seenAt)
    {
        foreach (var (externalId, title, solved) in submissions)
        {
            if (externalId is null || ProblemUrls.For(judge, externalId) is not { } url)
            {
                continue;
            }

            var key = $"{judge}:{externalId}";
            if (_problems.TryGetValue(key, out var existing))
            {
                _problems[key] = existing with { Solved = existing.Solved || solved, LastSeenAt = seenAt };
            }
            else
            {
                _problems[key] = new ProblemEntry(
                    judge,
                    externalId,
                    title ?? externalId,
                    url,
                    solved,
                    seenAt,
                    seenAt);
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
