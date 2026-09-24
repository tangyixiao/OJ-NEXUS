namespace OjNexus.Windows.Core.Domain;

public record JudgeAccount(JudgeId Judge, string Handle, bool Enabled = true)
{
    public static JudgeAccount Create(JudgeId judge, string handle)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(handle);
        var normalized = handle.Trim();
        if (!IsValidHandle(judge, normalized))
        {
            throw new ArgumentException("Handle is outside the judge identity boundary.", nameof(handle));
        }

        return new JudgeAccount(judge, normalized);
    }

    private static bool IsValidHandle(JudgeId judge, string handle) => judge switch
    {
        JudgeId.Codeforces => IsSafeCodeforcesHandle(handle),
        JudgeId.AtCoder => IsSafeAtCoderHandle(handle),
        JudgeId.Luogu => IsSafeLuoguUid(handle),
        _ => false,
    };

    private static bool IsSafeCodeforcesHandle(string handle) =>
        handle.All(character => IsAsciiAlphaNumeric(character) || character is '-' or '.' or '_');

    private static bool IsSafeAtCoderHandle(string handle) =>
        handle.All(character => IsAsciiAlphaNumeric(character) || character is '-' or '_');

    private static bool IsAsciiAlphaNumeric(char character) =>
        character is >= 'a' and <= 'z'
            or >= 'A' and <= 'Z'
            or >= '0' and <= '9';

    private static bool IsSafeLuoguUid(string handle)
    {
        var uid = handle.StartsWith("uid:", StringComparison.OrdinalIgnoreCase) ? handle[4..] : handle;
        return uid.Length > 0 && uid.All(char.IsDigit);
    }
}

public static class JudgeIdentity
{
    public static bool HandlesMatch(JudgeId judge, string left, string right)
    {
        var normalizedLeft = left.Trim();
        var normalizedRight = right.Trim();
        return judge switch
        {
            JudgeId.Codeforces => string.Equals(normalizedLeft, normalizedRight, StringComparison.OrdinalIgnoreCase),
            JudgeId.AtCoder => string.Equals(normalizedLeft, normalizedRight, StringComparison.Ordinal),
            JudgeId.Luogu => string.Equals(
                NormalizeLuogu(normalizedLeft), NormalizeLuogu(normalizedRight), StringComparison.OrdinalIgnoreCase),
            _ => false,
        };
    }

    private static string NormalizeLuogu(string value) =>
        value.StartsWith("uid:", StringComparison.OrdinalIgnoreCase) ? value[4..] : value;
}
