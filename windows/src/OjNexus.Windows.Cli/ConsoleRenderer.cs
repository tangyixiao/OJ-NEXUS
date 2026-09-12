using System.Text.Json;
using OjNexus.Windows.Core.Domain;
using OjNexus.Windows.Core.Sync;

namespace OjNexus.Windows.Cli;

public sealed record CliStatus(int AccountCount, SyncOperation? LastOperation);

public sealed record CliConfig(string DataDirectory, string DatabasePath, IReadOnlyList<JudgeAccount> Accounts);

public static class ConsoleRenderer
{
    public static string RenderHuman(CliStatus status) => string.Join(Environment.NewLine, "STATUS: READY", $"ACCOUNTS: {status.AccountCount}", $"LAST SYNC: {status.LastOperation?.Status.ToString().ToUpperInvariant() ?? "NONE"}");

    public static string RenderJson(CliStatus status) => JsonSerializer.Serialize(new { status = "ready", accountCount = status.AccountCount, lastSync = status.LastOperation is null ? null : ProjectOperation(status.LastOperation) });

    public static string RenderHuman(IReadOnlyList<SyncOperation> operations) => operations.Count == 0
        ? "HISTORY: EMPTY"
        : string.Join(Environment.NewLine, operations.Select(operation => $"OPERATION {operation.Id}: {operation.Account.Judge.ToString().ToUpperInvariant()} {operation.Status.ToString().ToUpperInvariant()}"));

    public static string RenderJson(IReadOnlyList<SyncOperation> operations) => JsonSerializer.Serialize(new { operations = operations.Select(ProjectOperation) });

    public static string RenderHuman(CodeforcesPayloadSnapshot payload) => string.Join(Environment.NewLine, "DATA: CODEFORCES", $"PROFILE: {(payload.Profile is null ? "NONE" : "PRESENT")}", $"RATINGS: {payload.Ratings.Count}", $"SUBMISSIONS: {payload.Submissions.Count}");

    public static string RenderJson(CodeforcesPayloadSnapshot payload) => JsonSerializer.Serialize(new
    {
        judge = "Codeforces",
        profile = payload.Profile is null ? null : new
        {
            handle = payload.Profile.Handle,
            rating = payload.Profile.Rating,
            rank = payload.Profile.Rank,
            maxRating = payload.Profile.MaxRating,
            maxRank = payload.Profile.MaxRank,
        },
        ratings = payload.Ratings.Select(rating => new
        {
            contestId = rating.ContestId,
            contestName = rating.ContestName,
            rank = rating.Rank,
            ratingUpdateTimeSeconds = rating.RatingUpdateTimeSeconds,
            oldRating = rating.OldRating,
            newRating = rating.NewRating,
        }),
        submissions = payload.Submissions.Select(submission => new
        {
            id = submission.Id,
            contestId = submission.ContestId,
            problemIndex = submission.ProblemIndex,
            problemName = submission.ProblemName,
            verdict = submission.Verdict,
            programmingLanguage = submission.ProgrammingLanguage,
            passedTestCount = submission.PassedTestCount,
            timeConsumedMillis = submission.TimeConsumedMillis,
            memoryConsumedBytes = submission.MemoryConsumedBytes,
            creationTimeSeconds = submission.CreationTimeSeconds,
        }),
    });

    public static string RenderHuman(AtCoderPayloadSnapshot payload) => string.Join(Environment.NewLine, "DATA: ATCODER", $"SUBMISSIONS: {payload.Submissions.Count}");

    public static string RenderJson(AtCoderPayloadSnapshot payload) => JsonSerializer.Serialize(new
    {
        judge = "AtCoder",
        submissions = payload.Submissions.Select(submission => new
        {
            id = submission.Id,
            epochSecond = submission.EpochSecond,
            problemId = submission.ProblemId,
            contestId = submission.ContestId,
            language = submission.Language,
            point = submission.Point,
            sourceLength = submission.SourceLength,
            result = submission.Result,
            executionTimeMillis = submission.ExecutionTimeMillis,
        }),
    });

    public static string RenderHuman(LuoguPayloadSnapshot payload) => string.Join(
        Environment.NewLine,
        "DATA: LUOGU",
        $"PROFILE: {(payload.Profile is null ? "NONE" : "PRESENT")}",
        $"SUBMISSIONS: {payload.SubmissionsCount}",
        $"CONTESTS: {payload.ContestsCount}",
        $"PROBLEMS: {payload.ProblemsCount}");

    public static string RenderJson(LuoguPayloadSnapshot payload) => JsonSerializer.Serialize(new
    {
        judge = "Luogu",
        profile = payload.Profile is null ? null : new
        {
            handle = payload.Profile.Handle,
            userId = payload.Profile.UserId,
            displayName = payload.Profile.DisplayName,
            rating = payload.Profile.Rating,
        },
        submissions = payload.SubmissionsCount,
        contests = payload.ContestsCount,
        problems = payload.ProblemsCount,
    });

    public static string RenderHuman(CliConfig config) => string.Join(Environment.NewLine, "CONFIG: REDACTED", $"DATA DIRECTORY: {config.DataDirectory}", $"DATABASE: {config.DatabasePath}", $"ACCOUNTS: {config.Accounts.Count}");

    public static string RenderJson(CliConfig config) => JsonSerializer.Serialize(new
    {
        dataDirectory = config.DataDirectory,
        databasePath = config.DatabasePath,
        accounts = config.Accounts.Select(account => new { judge = account.Judge.ToString(), handle = account.Handle, enabled = account.Enabled }),
    });

    public static string RenderHuman(SyncReport report) => string.Join(Environment.NewLine, $"SYNC: {report.Status.ToString().ToUpperInvariant()}", $"JUDGE: {report.Operation.Account.Judge.ToString().ToUpperInvariant()}", $"MODULES: {report.Operation.Modules.Count}", $"ERROR: {report.Error?.ToString().ToUpperInvariant() ?? "NONE"}");

    public static string RenderJson(SyncReport report) => SyncReportProjector.ToJson(report);

    private static object ProjectOperation(SyncOperation operation) => new
    {
        id = operation.Id,
        judge = operation.Account.Judge.ToString(),
        handle = operation.Account.Handle,
        status = operation.Status.ToString(),
        startedAt = operation.StartedAt,
        finishedAt = operation.FinishedAt,
        moduleCount = operation.Modules.Count,
    };
}
