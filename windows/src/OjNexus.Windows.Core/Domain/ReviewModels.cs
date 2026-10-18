using System.Globalization;

namespace OjNexus.Windows.Core.Domain;

public enum ReviewResult
{
    Pass,
    Hard,
    Fail,
    Skip,
}

public static class ReviewResultParser
{
    public static bool TryParse(string? value, out ReviewResult result)
    {
        if (Enum.TryParse<ReviewResult>(value?.Trim(), ignoreCase: true, out var parsed)
            && Enum.IsDefined(parsed))
        {
            result = parsed;
            return true;
        }

        result = default;
        return false;
    }

    public static string ToWire(ReviewResult result) => result.ToString().ToUpperInvariant();
}

/// <summary>
/// One due review projected from the shared ledger. Problem title/url are joined from the
/// derived problem library when a row exists; otherwise the identity alone is shown.
/// </summary>
public sealed record ReviewEntry(
    JudgeId Judge,
    string ExternalId,
    string? Title,
    string? Url,
    int Stage,
    DateTimeOffset DueAt,
    string DueDayIndex,
    ReviewResult? LastResult,
    DateTimeOffset? LastReviewedAt);

/// <summary>
/// Windows mirror of the Android review ladder: intervals [1,3,7,21,45,90] days; PASS advances
/// the stage (capped at the last index) with the full interval, HARD stays at the stage with half
/// the interval (min 1 day), FAIL drops the stage (floored at 0) and re-tests the next day, SKIP
/// repeats the stage with the full interval. Due-day uses the machine-local calendar day.
/// </summary>
public static class ReviewIntervalLadder
{
    public static readonly IReadOnlyList<int> IntervalDays = new[] { 1, 3, 7, 21, 45, 90 };

    public const int MaxStageIndex = 5;

    public static int FullIntervalDays(int stage)
    {
        var index = Math.Clamp(stage, 0, MaxStageIndex);
        return IntervalDays[index];
    }

    public static int NextStage(ReviewResult result, int stage) => result switch
    {
        ReviewResult.Pass => Math.Min(stage + 1, MaxStageIndex),
        ReviewResult.Hard => Math.Clamp(stage, 0, MaxStageIndex),
        ReviewResult.Fail => Math.Max(stage - 1, 0),
        ReviewResult.Skip => Math.Clamp(stage, 0, MaxStageIndex),
        _ => Math.Clamp(stage, 0, MaxStageIndex),
    };

    public static int IntervalDaysForResult(ReviewResult result, int stage) => result switch
    {
        ReviewResult.Pass => FullIntervalDays(stage + 1),
        ReviewResult.Hard => Math.Max(FullIntervalDays(stage) / 2, 1),
        ReviewResult.Fail => 1,
        ReviewResult.Skip => FullIntervalDays(stage),
        _ => FullIntervalDays(stage),
    };

    public static (int Stage, DateTimeOffset DueAt, string DueDayIndex) ScheduleNext(
        ReviewResult result,
        int stage,
        DateTimeOffset now)
    {
        var newStage = NextStage(result, stage);
        var interval = IntervalDaysForResult(result, stage);
        var dueAt = now.AddDays(interval);
        var dueDayIndex = dueAt.ToLocalTime().ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
        return (newStage, dueAt, dueDayIndex);
    }

    public static string DayIndexFor(DateTimeOffset now) =>
        now.ToLocalTime().ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
}
