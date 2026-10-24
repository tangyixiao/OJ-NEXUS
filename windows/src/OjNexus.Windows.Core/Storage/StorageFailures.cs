namespace OjNexus.Windows.Core.Storage;

public enum StorageFailureCode
{
    Busy, IntegrityFailure, UnsupportedSchema, RecoveryBlocked, RecoverySourceMissing,
    PermissionDenied, UnsupportedLocation, MigrationFailed, IoFailure, StorageFull, SessionClosed
}

public sealed record StorageFailure(StorageFailureCode Code, string Stage)
{
    public string WireCode => Code switch
    {
        StorageFailureCode.Busy => "STORAGE_BUSY",
        StorageFailureCode.IntegrityFailure => "INTEGRITY_FAILURE",
        StorageFailureCode.UnsupportedSchema => "UNSUPPORTED_SCHEMA",
        StorageFailureCode.RecoveryBlocked => "RECOVERY_BLOCKED",
        StorageFailureCode.RecoverySourceMissing => "RECOVERY_SOURCE_MISSING",
        StorageFailureCode.PermissionDenied => "PERMISSION_DENIED",
        StorageFailureCode.UnsupportedLocation => "UNSUPPORTED_LOCATION",
        StorageFailureCode.MigrationFailed => "MIGRATION_FAILED",
        StorageFailureCode.StorageFull => "STORAGE_FULL",
        StorageFailureCode.SessionClosed => "SESSION_CLOSED",
        _ => "STORAGE_IO_FAILURE"
    };
}

public sealed class StorageException(StorageFailure failure, Exception? inner = null) : Exception(failure.WireCode, inner)
{
    public StorageFailure Failure { get; } = failure;
}

internal static class StorageExceptionClassifier
{
    public static StorageException Classify(Exception error, string stage)
    {
        if (error is StorageException storage) return storage;
        if (error is Microsoft.Data.Sqlite.SqliteException sqlite)
        {
            var code = sqlite.SqliteErrorCode switch
            {
                5 or 6 => StorageFailureCode.Busy,
                13 => StorageFailureCode.StorageFull,
                11 or 26 => StorageFailureCode.IntegrityFailure,
                _ => StorageFailureCode.IoFailure
            };
            return new(new StorageFailure(code, stage), error);
        }
        if (error is UnauthorizedAccessException) return new(new(StorageFailureCode.PermissionDenied, stage), error);
        if (error is IOException io)
        {
            var native = io.HResult & 0xffff;
            return new(new(native is 32 or 33 ? StorageFailureCode.Busy : native is 112 or 39 ? StorageFailureCode.StorageFull : StorageFailureCode.IoFailure, stage), error);
        }
        if (error is InvalidDataException) return new(new(StorageFailureCode.IntegrityFailure, stage), error);
        return new(new(StorageFailureCode.IoFailure, stage), error);
    }
}
