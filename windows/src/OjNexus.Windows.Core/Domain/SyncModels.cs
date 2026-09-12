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

public record SyncModuleOutcome
{
    public SyncModuleOutcome(
        string stage,
        SyncOperationStatus status,
        int attemptedCount,
        int importedCount,
        int updatedCount,
        string? failureType)
        : this(stage, status, attemptedCount, importedCount, updatedCount, failureType, null)
    {
    }

    public SyncModuleOutcome(
        string stage,
        SyncOperationStatus status,
        int attemptedCount,
        int importedCount,
        int updatedCount,
        string? failureType,
        SyncModulePayload? payload)
    {
        Stage = stage;
        Status = status;
        AttemptedCount = attemptedCount;
        ImportedCount = importedCount;
        UpdatedCount = updatedCount;
        FailureType = failureType;
        Payload = payload;
    }

    public string Stage { get; init; }

    public SyncOperationStatus Status { get; init; }

    public int AttemptedCount { get; init; }

    public int ImportedCount { get; init; }

    public int UpdatedCount { get; init; }

    public string? FailureType { get; init; }

    public SyncModulePayload? Payload { get; init; }
}

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
        : this(Id, Account, DataGeneration, StartedAt, FinishedAt, Status, Modules, null)
    {
    }

    public SyncOperation(
        long Id,
        JudgeAccount Account,
        string DataGeneration,
        DateTimeOffset StartedAt,
        DateTimeOffset? FinishedAt,
        SyncOperationStatus Status,
        IReadOnlyList<SyncModuleOutcome> Modules,
        SyncError? Error)
    {
        ArgumentNullException.ThrowIfNull(Modules);
        this.Id = Id;
        this.Account = Account;
        this.DataGeneration = DataGeneration;
        this.StartedAt = StartedAt;
        this.FinishedAt = FinishedAt;
        this.Status = Status;
        this.Modules = Array.AsReadOnly(Modules.ToArray());
        this.Error = Error;
    }

    public long Id { get; init; }

    public JudgeAccount Account { get; init; }

    public string DataGeneration { get; init; }

    public DateTimeOffset StartedAt { get; init; }

    public DateTimeOffset? FinishedAt { get; init; }

    public SyncOperationStatus Status { get; init; }

    public IReadOnlyList<SyncModuleOutcome> Modules { get; init; }

    public SyncError? Error { get; init; }

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
