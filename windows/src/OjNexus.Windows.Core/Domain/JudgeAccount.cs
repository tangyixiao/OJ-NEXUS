namespace OjNexus.Windows.Core.Domain;

public record JudgeAccount(JudgeId Judge, string Handle, bool Enabled = true)
{
    public static JudgeAccount Create(JudgeId judge, string handle)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(handle);
        return new JudgeAccount(judge, handle.Trim());
    }
}
