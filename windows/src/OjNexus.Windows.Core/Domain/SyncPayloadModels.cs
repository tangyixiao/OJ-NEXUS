namespace OjNexus.Windows.Core.Domain;

public abstract record SyncModulePayload;

public sealed record CodeforcesProfilePayload(
    string Handle,
    int? Rating,
    string? Rank,
    int? MaxRating,
    string? MaxRank) : SyncModulePayload;

public sealed record CodeforcesRating(
    int ContestId,
    string ContestName,
    int Rank,
    long RatingUpdateTimeSeconds,
    int OldRating,
    int NewRating);

public sealed record CodeforcesRatingsPayload(
    string Handle,
    IReadOnlyList<CodeforcesRating> Items) : SyncModulePayload;

public sealed record CodeforcesSubmission(
    long Id,
    int? ContestId,
    string? ProblemIndex,
    string? ProblemName,
    string? Verdict,
    string ProgrammingLanguage,
    int PassedTestCount,
    int TimeConsumedMillis,
    long MemoryConsumedBytes,
    long CreationTimeSeconds);

public sealed record CodeforcesSubmissionsPayload(
    string Handle,
    IReadOnlyList<CodeforcesSubmission> Items) : SyncModulePayload;

public sealed record CodeforcesPayloadSnapshot(
    CodeforcesProfilePayload? Profile,
    IReadOnlyList<CodeforcesRating> Ratings,
    IReadOnlyList<CodeforcesSubmission> Submissions);

public sealed record AtCoderSubmission(
    long Id,
    long EpochSecond,
    string ProblemId,
    string ContestId,
    string Language,
    double Point,
    int SourceLength,
    string Result,
    long ExecutionTimeMillis);

public sealed record AtCoderSubmissionsPayload(
    string Handle,
    IReadOnlyList<AtCoderSubmission> Items) : SyncModulePayload;

public sealed record AtCoderPayloadSnapshot(
    IReadOnlyList<AtCoderSubmission> Submissions);

public sealed record LuoguProfilePayload(
    string Handle,
    long UserId,
    string DisplayName,
    int? Rating) : SyncModulePayload;

public sealed record LuoguCollectionPayload(
    string Handle,
    string Collection,
    int Count) : SyncModulePayload;

public sealed record LuoguPayloadSnapshot(
    LuoguProfilePayload? Profile,
    int SubmissionsCount,
    int ContestsCount,
    int ProblemsCount);
