using System.Security;
using System.Text;
using Microsoft.Data.Sqlite;
using OjNexus.Windows.Core.Contracts;

namespace OjNexus.Windows.Core.Storage;

public sealed record StorageOpenResult(WindowsStorageSession? Session, StorageFailure? Failure)
{
    public bool IsReady => Session is not null;
    internal static StorageOpenResult Ready(WindowsStorageSession session) => new(session, null);
    internal static StorageOpenResult Blocked(StorageFailure failure) => new(null, failure);
}

public sealed class WindowsStorageSession : IStorageOperationSource
{
    private readonly OperationOwner _owner = new();
    private readonly FileStream _directoryLease;
    private readonly ISyncStore _store;
    internal WindowsStorageSession(StorageLocation location, FileStream lease)
    {
        DataDirectory = location.DataDirectory; DatabasePath = location.DatabasePath; _directoryLease = lease;
        _store = new SqliteSyncStore(new SqliteConnectionFactory(location.DatabasePath, guardedSession: true));
    }
    public string DataDirectory { get; }
    public string DatabasePath { get; }
    public StorageOperationContext BeginOperation(CancellationToken cancellationToken = default) => _owner.Begin(_store, cancellationToken);
    public async Task RunAsync(Func<ISyncStore, CancellationToken, Task> work, CancellationToken cancellationToken)
    { await using var operation = BeginOperation(cancellationToken); await work(operation.Store, operation.CancellationToken).ConfigureAwait(false); }
    public async Task<T> RunAsync<T>(Func<ISyncStore, CancellationToken, Task<T>> work, CancellationToken cancellationToken)
    { await using var operation = BeginOperation(cancellationToken); return await work(operation.Store, operation.CancellationToken).ConfigureAwait(false); }
    public async ValueTask DisposeAsync()
    {
        await _owner.DisposeAsync().ConfigureAwait(false);
        _directoryLease.Dispose();
    }
}

public static class WindowsStorage
{
    private static readonly byte[] MarkerBytes = Encoding.UTF8.GetBytes("v1\n");

    public static async Task<StorageOpenResult> OpenAsync(string? rootOverride, IClock? clock, CancellationToken cancellationToken)
    {
        try
        {
            cancellationToken.ThrowIfCancellationRequested();
            return await Task.Run(() => OpenCore(rootOverride, clock, cancellationToken), CancellationToken.None).ConfigureAwait(false);
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested) { throw; }
        catch (Exception ex) { return StorageOpenResult.Blocked(StorageExceptionClassifier.Classify(ex, "startup").Failure); }
    }

    private static StorageOpenResult OpenCore(string? root, IClock? clock, CancellationToken token)
    {
        var location = StorageLocation.Resolve(root);
        Directory.CreateDirectory(location.DataDirectory);
        location.ValidateControlledPaths();
        var shared = NativeFile.Acquire(location.LockPath, exclusive: false);
        try
        {
            var state = Inspect(location);
            if (state.Kind == StateKind.Ready && state.MarkerValid)
                return StorageOpenResult.Ready(new WindowsStorageSession(location, shared));
            shared.Dispose(); shared = null!;
            token.ThrowIfCancellationRequested();
            using (var exclusive = NativeFile.Acquire(location.LockPath, exclusive: true))
            {
                location.ValidateControlledPaths(exclusive);
                state = Inspect(location);
                if (state.Kind == StateKind.Fresh) Initialize(location, token);
                else if (state.Kind == StateKind.Ready)
                {
                    if (state.Version < 7) Migrate(location, token);
                    if (!state.MarkerValid) WriteMarker(location);
                }
                else throw state.Error!;
            }
            token.ThrowIfCancellationRequested();
            shared = NativeFile.Acquire(location.LockPath, exclusive: false);
            location.ValidateControlledPaths();
            state = Inspect(location);
            if (state.Kind != StateKind.Ready || !state.MarkerValid)
                throw state.Error ?? new StorageException(new(StorageFailureCode.RecoveryBlocked, "recheck"));
            return StorageOpenResult.Ready(new WindowsStorageSession(location, shared));
        }
        catch { shared?.Dispose(); throw; }
    }

    private static State Inspect(StorageLocation location)
    {
        location.ValidateControlledPaths();
        var markerExists = File.Exists(location.MarkerPath);
        var anyEvidence = markerExists || File.Exists(location.MarkerTempPath) || File.Exists(location.InitPath)
            || Directory.EnumerateFileSystemEntries(location.DataDirectory).Any(p =>
                Path.GetFileName(p).StartsWith("ojnexus.restore.", StringComparison.OrdinalIgnoreCase)
                || Path.GetFileName(p) is "ojnexus.db-wal" or "ojnexus.db-shm" or "ojnexus.db-journal");
        if (!File.Exists(location.DatabasePath))
            return anyEvidence ? State.Blocked(StorageFailureCode.RecoveryBlocked) : State.Fresh;
        if (!markerExists && anyEvidence) return State.Blocked(StorageFailureCode.RecoveryBlocked);
        try
        {
            var header = new byte[16];
            using (var file = new FileStream(location.DatabasePath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite))
            {
                if (file.Read(header, 0, header.Length) != header.Length
                    || !header.SequenceEqual(Encoding.ASCII.GetBytes("SQLite format 3\0")))
                    throw new InvalidDataException("Invalid SQLite header.");
            }
            using var connection = new SqliteConnection(new SqliteConnectionStringBuilder
            { DataSource = location.DatabasePath, Mode = SqliteOpenMode.ReadOnly, Pooling = false, ForeignKeys = true, DefaultTimeout = 2 }.ToString());
            connection.Open();
            using (var cmd = connection.CreateCommand())
            {
                cmd.CommandText = "PRAGMA application_id";
                _ = cmd.ExecuteScalar();
            }
            var version = SchemaMigrator.InspectExisting(connection);
            var markerValid = markerExists && File.ReadAllBytes(location.MarkerPath).SequenceEqual(MarkerBytes);
            if (markerExists && !markerValid) return State.Blocked(StorageFailureCode.RecoveryBlocked);
            return new(StateKind.Ready, version, markerValid, null);
        }
        catch (NotSupportedException ex) { return State.Blocked(StorageFailureCode.UnsupportedSchema, ex); }
        catch (Exception ex) { return State.Blocked(StorageExceptionClassifier.Classify(ex, "probe").Failure.Code, ex); }
    }

    private static void Initialize(StorageLocation location, CancellationToken token)
    {
        token.ThrowIfCancellationRequested();
        if (File.Exists(location.DatabasePath) || File.Exists(location.MarkerPath) || File.Exists(location.MarkerTempPath) || File.Exists(location.InitPath))
            throw new StorageException(new(StorageFailureCode.RecoveryBlocked, "initialize"));
        WriteMarker(location);
        token.ThrowIfCancellationRequested();
        var builder = new SqliteConnectionStringBuilder { DataSource = location.InitPath, Mode = SqliteOpenMode.ReadWriteCreate, Pooling = false, ForeignKeys = true };
        SchemaMigrator.Migrate(builder.ToString());
        token.ThrowIfCancellationRequested();
        using (var probe = new SqliteConnection(new SqliteConnectionStringBuilder { DataSource = location.InitPath, Mode = SqliteOpenMode.ReadOnly, Pooling = false }.ToString()))
        { probe.Open(); if (SchemaMigrator.InspectExisting(probe) != 7) throw new InvalidDataException(); }
        using (var stream = new FileStream(location.InitPath, FileMode.Open, FileAccess.ReadWrite, FileShare.Read)) stream.Flush(true);
        File.Move(location.InitPath, location.DatabasePath);
    }

    private static void Migrate(StorageLocation location, CancellationToken token)
    {
        token.ThrowIfCancellationRequested();
        var builder = new SqliteConnectionStringBuilder { DataSource = location.DatabasePath, Mode = SqliteOpenMode.ReadWrite, Pooling = false, ForeignKeys = true, DefaultTimeout = 2 };
        try { SchemaMigrator.Migrate(builder.ToString()); }
        catch (Exception ex) { throw new StorageException(new(StorageFailureCode.MigrationFailed, "migration"), ex); }
        token.ThrowIfCancellationRequested();
    }

    private static void WriteMarker(StorageLocation location)
    {
        using (var stream = new FileStream(location.MarkerTempPath, FileMode.CreateNew, FileAccess.Write, FileShare.None))
        { stream.Write(MarkerBytes); stream.Flush(true); }
        File.Move(location.MarkerTempPath, location.MarkerPath);
    }

    private enum StateKind { Fresh, Ready, Blocked }
    private sealed record State(StateKind Kind, int Version = 0, bool MarkerValid = false, Exception? Error = null)
    {
        public static State Fresh => new(StateKind.Fresh);
        public static State Blocked(StorageFailureCode code, Exception? inner = null) => new(StateKind.Blocked, Error: new StorageException(new(code, "inventory"), inner));
    }
}
