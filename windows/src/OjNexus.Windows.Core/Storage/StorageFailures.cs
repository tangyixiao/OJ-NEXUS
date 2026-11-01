namespace OjNexus.Windows.Core.Storage;

using System.ComponentModel;

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

    public static StorageException ForPersistence(Exception exception, string stage) =>
        StorageExceptionClassifier.Classify(exception, stage);
}

internal static class StorageExceptionClassifier
{
    public static StorageException Classify(Exception error, string stage)
    {
        if (TryClassifyProviderFailure(error, stage, out var classified))
        {
            return classified;
        }
        if (error is InvalidDataException)
        {
            return new(new(StorageFailureCode.IntegrityFailure, stage), error);
        }
        return new(new(StorageFailureCode.IoFailure, stage), error);
    }

    public static bool TryClassifyProviderFailure(Exception error, string stage, out StorageException classified)
    {
        if (error is StorageException storage)
        {
            classified = storage;
            return true;
        }
        if (error is Microsoft.Data.Sqlite.SqliteException sqlite)
        {
            var code = sqlite.SqliteExtendedErrorCode switch
            {
                264 or 776 => StorageFailureCode.RecoveryBlocked,
                520 => StorageFailureCode.Busy,
                _ => sqlite.SqliteErrorCode switch
                {
                    5 or 6 => StorageFailureCode.Busy,
                    13 => StorageFailureCode.StorageFull,
                    11 or 26 => StorageFailureCode.IntegrityFailure,
                    3 or 8 => StorageFailureCode.PermissionDenied,
                    _ => StorageFailureCode.IoFailure
                }
            };
            classified = new(new(code, stage), error);
            return sqlite.SqliteErrorCode is 3 or 5 or 6 or 8 or 11 or 13 or 26
                || sqlite.SqliteExtendedErrorCode is 264 or 520 or 776;
        }
        if (error is UnauthorizedAccessException)
        {
            classified = new(new(StorageFailureCode.PermissionDenied, stage), error);
            return true;
        }
        if (error is Win32Exception win32)
        {
            classified = FromWindowsCode(win32.NativeErrorCode, stage, error);
            return true;
        }
        if (error is IOException io)
        {
            classified = FromWindowsCode(io.HResult & 0xffff, stage, error);
            return true;
        }
        classified = null!;
        return false;
    }

    private static StorageException FromWindowsCode(int code, string stage, Exception error)
    {
        var failureCode = code switch
        {
            32 or 33 => StorageFailureCode.Busy,
            5 => StorageFailureCode.PermissionDenied,
            112 or 39 => StorageFailureCode.StorageFull,
            _ => StorageFailureCode.IoFailure
        };
        return new(new(failureCode, stage), error);
    }
}
