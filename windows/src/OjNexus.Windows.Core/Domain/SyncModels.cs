namespace OjNexus.Windows.Core.Domain;

public enum SyncOperationStatus
{
    Running,
    Success,
    Partial,
    Error,
    Cancelled,
    Offline,
}

public record SyncModuleOutcome(
    string Stage,
    SyncOperationStatus Status,
    int AttemptedCount,
    int ImportedCount,
    int UpdatedCount,
    string? FailureType);

public record SyncOperation
{
    public SyncOperation(
        long Id,
        JudgeAccount Account,
        string DataGeneration,
        DateTimeOffset StartedAt,
        DateTimeOffset? FinishedAt,
        SyncOperationStatus Status,
        IReadOnlyList<SyncModuleOutcome> Modules)
    {
        ArgumentNullException.ThrowIfNull(Modules);
        this.Id = Id;
        this.Account = Account;
        this.DataGeneration = DataGeneration;
        this.StartedAt = StartedAt;
        this.FinishedAt = FinishedAt;
        this.Status = Status;
        this.Modules = Array.AsReadOnly(Modules.ToArray());
    }

    public long Id { get; init; }

    public JudgeAccount Account { get; init; }

    public string DataGeneration { get; init; }

    public DateTimeOffset StartedAt { get; init; }

    public DateTimeOffset? FinishedAt { get; init; }

    public SyncOperationStatus Status { get; init; }

    public IReadOnlyList<SyncModuleOutcome> Modules { get; init; }

    public void Deconstruct(
        out long Id,
        out JudgeAccount Account,
        out string DataGeneration,
        out DateTimeOffset StartedAt,
        out DateTimeOffset? FinishedAt,
        out SyncOperationStatus Status,
        out IReadOnlyList<SyncModuleOutcome> Modules)
    {
        Id = this.Id;
        Account = this.Account;
        DataGeneration = this.DataGeneration;
        StartedAt = this.StartedAt;
        FinishedAt = this.FinishedAt;
        Status = this.Status;
        Modules = this.Modules;
    }
}

public record SyncReport(SyncOperation Operation, SyncOperationStatus Status, SyncError? Error);
