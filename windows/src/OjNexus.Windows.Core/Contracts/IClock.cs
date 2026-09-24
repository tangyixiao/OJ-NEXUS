namespace OjNexus.Windows.Core.Contracts;

public interface IClock
{
    DateTimeOffset UtcNow { get; }
}
