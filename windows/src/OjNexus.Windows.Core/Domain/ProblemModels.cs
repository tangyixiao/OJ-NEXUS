using System.Globalization;

namespace OjNexus.Windows.Core.Domain;

/// <summary>Local problem-library status derived from persisted submissions only.</summary>
public enum ProblemStatus
{
    Attempted,
    Solved,
}

public static class ProblemStatusParser
{
    public static bool TryParse(string value, out ProblemStatus status)
    {
        if (string.Equals(value, nameof(ProblemStatus.Solved), StringComparison.OrdinalIgnoreCase))
        {
            status = ProblemStatus.Solved;
            return true;
        }

        if (string.Equals(value, nameof(ProblemStatus.Attempted), StringComparison.OrdinalIgnoreCase))
        {
            status = ProblemStatus.Attempted;
            return true;
        }

        status = default;
        return false;
    }

    public static string ToUpper(ProblemStatus status) => status.ToString().ToUpperInvariant();
}

/// <summary>
/// One read-only row of the local problem library. Rows are DERIVED from persisted public
/// submissions at sync time; the ledger never scrapes a problem page.
/// </summary>
public sealed record ProblemEntry(
    JudgeId Judge,
    string ExternalId,
    string Title,
    string Url,
    bool Solved,
    DateTimeOffset FirstSeenAt,
    DateTimeOffset LastSeenAt);

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
