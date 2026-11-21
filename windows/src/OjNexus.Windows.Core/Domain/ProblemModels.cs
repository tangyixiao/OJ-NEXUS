using System.Globalization;

namespace OjNexus.Windows.Core.Domain;

/// <summary>
/// Derived problem status per the frozen phase-136 contract
/// (contracts/problems/v1): REVIEW overrides SOLVED, which overrides
/// ATTEMPTED; UNSOLVED is the empty case. Filters act on the derived value,
/// never on a stored flag.
/// </summary>
public enum ProblemStatus
{
    Unsolved,
    Attempted,
    Solved,
    Review,
}

public static class ProblemStatusParser
{
    public static bool TryParse(string value, out ProblemStatus status)
    {
        if (string.Equals(value, nameof(ProblemStatus.Unsolved), StringComparison.OrdinalIgnoreCase))
        {
            status = ProblemStatus.Unsolved;
            return true;
        }

        if (string.Equals(value, nameof(ProblemStatus.Attempted), StringComparison.OrdinalIgnoreCase))
        {
            status = ProblemStatus.Attempted;
            return true;
        }

        if (string.Equals(value, nameof(ProblemStatus.Solved), StringComparison.OrdinalIgnoreCase))
        {
            status = ProblemStatus.Solved;
            return true;
        }

        if (string.Equals(value, nameof(ProblemStatus.Review), StringComparison.OrdinalIgnoreCase))
        {
            status = ProblemStatus.Review;
            return true;
        }

        status = default;
        return false;
    }

    public static string ToUpper(ProblemStatus status) => status.ToString().ToUpperInvariant();
}

/// <summary>The four contract sort keys; every sort ends with the code-point tie-break.</summary>
public enum ProblemSort
{
    Updated,
    Difficulty,
    Title,
    Attempts,
}

public static class ProblemSortParser
{
    public static bool TryParse(string value, out ProblemSort sort)
    {
        if (string.Equals(value, nameof(ProblemSort.Updated), StringComparison.OrdinalIgnoreCase))
        {
            sort = ProblemSort.Updated;
            return true;
        }

        if (string.Equals(value, nameof(ProblemSort.Difficulty), StringComparison.OrdinalIgnoreCase))
        {
            sort = ProblemSort.Difficulty;
            return true;
        }

        if (string.Equals(value, nameof(ProblemSort.Title), StringComparison.OrdinalIgnoreCase))
        {
            sort = ProblemSort.Title;
            return true;
        }

        if (string.Equals(value, nameof(ProblemSort.Attempts), StringComparison.OrdinalIgnoreCase))
        {
            sort = ProblemSort.Attempts;
            return true;
        }

        sort = default;
        return false;
    }

    public static string ToUpper(ProblemSort sort) => sort.ToString().ToUpperInvariant();
}

/// <summary>
/// The frozen derived-status rule in one place. The SQL layer mirrors this
/// CASE textually (parity-tested by the 28 contract vectors); this helper is
/// the C# authority for display and for the in-memory store.
/// </summary>
public static class ProblemStatusRules
{
    public static ProblemStatus Derive(bool inReview, bool solved, int attemptCount) =>
        inReview ? ProblemStatus.Review
        : solved ? ProblemStatus.Solved
        : attemptCount > 0 ? ProblemStatus.Attempted
        : ProblemStatus.Unsolved;
}

/// <summary>
/// One read-only row of the local problem library, in the full phase-136
/// contract shape. Rows are DERIVED from persisted public submissions at sync
/// time; the ledger never scrapes a problem page. Difficulty and tags have no
/// Windows ingest source yet — they are contract-complete store columns that
/// default to null/empty (recorded phase-138 deviation) and are exercised by
/// the contract parity tests.
/// </summary>
public sealed record ProblemEntry(
    JudgeId Judge,
    string ExternalId,
    string Title,
    string Url,
    bool Solved,
    DateTimeOffset FirstSeenAt,
    DateTimeOffset LastSeenAt,
    DateTimeOffset UpdatedAt,
    int? Difficulty,
    bool Favorite,
    int AttemptCount,
    bool InReview,
    IReadOnlyList<string> Tags)
{
    /// <summary>
    /// Frozen cross-platform identity key: "judge/externalId" with the judge in
    /// lowercase wire form (the contract vectors' key format, e.g. "codeforces/1234A").
    /// </summary>
    public string Key => $"{Judge.ToString().ToLowerInvariant()}/{ExternalId}";

    public ProblemStatus Status => ProblemStatusRules.Derive(InReview, Solved, AttemptCount);
}

/// <summary>Filter/sort/search query over the problem ledger, contract-shaped.</summary>
public sealed record ProblemLedgerQuery(
    string? Search = null,
    ProblemStatus? Status = null,
    JudgeId? Judge = null,
    bool FavoriteOnly = false,
    string? Tag = null,
    int? DifficultyMin = null,
    int? DifficultyMax = null,
    ProblemSort Sort = ProblemSort.Updated);

/// <summary>
/// The contract's frozen search normalization: trim ONLY the ASCII whitespace
/// set (U+0020, U+0009-U+000D — NBSP and other Unicode whitespace are real
/// search terms, vector 026) then fold with the .NET invariant lowercase, the
/// equivalent of Kotlin lowercase()/Python str.lower() for every pinned vector.
/// An empty result matches everything (vector 027).
/// </summary>
public static class ProblemSearch
{
    private static readonly char[] AsciiWhitespace = [' ', '\t', '\n', '\r', '\v', '\f'];

    public static string? Normalize(string? search)
    {
        if (search is null)
        {
            return null;
        }

        var lowered = search.Trim(AsciiWhitespace).ToLowerInvariant();
        return lowered.Length == 0 ? null : lowered;
    }
}

/// <summary>Unfiltered totals plus the filtered match count; never derived from the visible page.</summary>
public sealed record ProblemLedgerSummary(int Visible, int Total, int Solved, int Review, int Favorites);

/// <summary>One OFFSET page over the deterministic contract order.</summary>
public sealed record ProblemLedgerPage(
    IReadOnlyList<ProblemEntry> Rows,
    int Offset,
    int Limit,
    ProblemLedgerSummary Summary);

/// <summary>Canonical public problem URLs per judge. Pure — no network.</summary>
public static class ProblemUrls
{
    public static string? For(JudgeId judge, string externalId)
    {
        if (string.IsNullOrWhiteSpace(externalId))
        {
            return null;
        }

        return judge switch
        {
            JudgeId.Codeforces => ForCodeforces(externalId),
            JudgeId.AtCoder => ForAtCoder(externalId),
            _ => null,
        };
    }

    /// <summary>
    /// Codeforces problem identity is the Android rule: externalId = "{contestId}{index}",
    /// where index is the trailing non-digit part. Returns null for malformed rows.
    /// </summary>
    public static string? CodeforcesExternalId(long? contestId, string? problemIndex)
    {
        if (contestId is null || contestId <= 0 || string.IsNullOrWhiteSpace(problemIndex))
        {
            return null;
        }

        return $"{contestId.Value.ToString(CultureInfo.InvariantCulture)}{problemIndex.Trim()}";
    }

    private static string? ForCodeforces(string externalId)
    {
        // externalId = digits + index (index may itself contain digits, e.g. "C1" → "2134C1").
        var digitsEnd = 0;
        while (digitsEnd < externalId.Length && char.IsDigit(externalId[digitsEnd]))
        {
            digitsEnd++;
        }

        if (digitsEnd == 0 || digitsEnd == externalId.Length)
        {
            return null;
        }

        var contestPart = externalId[..digitsEnd];
        var indexPart = externalId[digitsEnd..];
        return $"https://codeforces.com/contest/{contestPart}/problem/{Uri.EscapeDataString(indexPart)}";
    }

    private static string? ForAtCoder(string externalId)
    {
        // externalId = "{contestId}+{problemId}" (the '+' never appears in either part).
        var separator = externalId.IndexOf('+');
        if (separator <= 0 || separator == externalId.Length - 1)
        {
            return null;
        }

        var contestPart = externalId[..separator];
        var problemPart = externalId[(separator + 1)..];
        return $"https://atcoder.jp/contests/{Uri.EscapeDataString(contestPart)}/tasks/{Uri.EscapeDataString(problemPart)}";
    }
}
