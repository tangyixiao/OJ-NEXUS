namespace OjNexus.Windows.Core.Domain;

public enum JudgeId
{
    Codeforces,
    AtCoder,
    Luogu,
}

public static class JudgeIdParser
{
    public static bool TryParse(string value, out JudgeId judge)
    {
        if (string.Equals(value, nameof(JudgeId.Codeforces), StringComparison.OrdinalIgnoreCase))
        {
            judge = JudgeId.Codeforces;
            return true;
        }

        if (string.Equals(value, nameof(JudgeId.AtCoder), StringComparison.OrdinalIgnoreCase))
        {
            judge = JudgeId.AtCoder;
            return true;
        }

        if (string.Equals(value, nameof(JudgeId.Luogu), StringComparison.OrdinalIgnoreCase))
        {
            judge = JudgeId.Luogu;
            return true;
        }

        judge = default;
        return false;
    }
}
