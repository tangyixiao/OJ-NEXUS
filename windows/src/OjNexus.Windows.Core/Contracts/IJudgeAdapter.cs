using OjNexus.Windows.Core.Domain;

namespace OjNexus.Windows.Core.Contracts;

public interface IJudgeAdapter
{
    JudgeId Judge { get; }

    Task<IReadOnlyList<SyncModuleOutcome>> SyncAsync(
        JudgeAccount account,
        CancellationToken cancellationToken);
}
