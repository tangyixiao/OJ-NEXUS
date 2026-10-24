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
    private readonly OperationOwner _owner;
    private readonly SqliteConnectionFactory _factory;
    private readonly IClock? _clock;

    internal WindowsStorageSession(StorageLocation location, FileStream lifecycleLease, IClock? clock)
    {
        DataDirectory = location.DataDirectory;
        DatabasePath = location.DatabasePath;
        _clock = clock;
        _owner = new OperationOwner(() =>
        {
            lifecycleLease.Dispose();
            return ValueTask.CompletedTask;
        });
        _factory = new SqliteConnectionFactory(location.DatabasePath, _owner);
    }

    public string DataDirectory { get; }
    public string DatabasePath { get; }

    public StorageOperationContext BeginOperation(CancellationToken cancellationToken = default) =>
        _owner.Begin(permit => new SqliteSyncStore(_factory, permit, _clock), cancellationToken);

    public async Task RunAsync(Func<ISyncStore, CancellationToken, Task> work, CancellationToken cancellationToken)
    {
        await using var operation = BeginOperation(cancellationToken);
        await work(operation.Store, operation.CancellationToken).ConfigureAwait(false);
    }

    public async Task<T> RunAsync<T>(Func<ISyncStore, CancellationToken, Task<T>> work, CancellationToken cancellationToken)
    {
        await using var operation = BeginOperation(cancellationToken);
        return await work(operation.Store, operation.CancellationToken).ConfigureAwait(false);
    }

    public ValueTask DisposeAsync() => _owner.DisposeAsync();
}

internal enum StartupStage
{
    InitialProbeCompleted,
    ExclusiveProbeCompleted,
    MaintenanceCompleted,
    FinalProbeCompleted,
    MarkerCreated,
    InitializationStageCreated,
    InitializationStageValidated,
    DatabasePromoted
}

internal sealed record StartupTestHooks(Action<StartupStage>? OnStage = null, Action? BeforeConnectionClose = null);

public static class WindowsStorage
{
    private const int CurrentSchemaVersion = 7;
    private static readonly byte[] MarkerBytes = Encoding.UTF8.GetBytes("v1\n");

    public static Task<StorageOpenResult> OpenAsync(string? rootOverride, IClock? clock, CancellationToken cancellationToken) =>
        OpenCoreAsync(rootOverride, clock, cancellationToken, hooks: null);

    internal static Task<StorageOpenResult> OpenAsyncForTests(
        string? rootOverride, IClock? clock, CancellationToken cancellationToken, StartupTestHooks hooks) =>
        OpenCoreAsync(rootOverride, clock, cancellationToken, hooks);

    private static async Task<StorageOpenResult> OpenCoreAsync(
        string? rootOverride, IClock? clock, CancellationToken cancellationToken, StartupTestHooks? hooks)
    {
        try
        {
            cancellationToken.ThrowIfCancellationRequested();
            return await Task.Run(() => OpenCore(rootOverride, clock, cancellationToken, hooks), CancellationToken.None)
                .ConfigureAwait(false);
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            throw;
        }
        catch (Exception exception)
        {
            return StorageOpenResult.Blocked(StorageExceptionClassifier.Classify(exception, "startup").Failure);
        }
    }

    private static StorageOpenResult OpenCore(
        string? rootOverride, IClock? clock, CancellationToken cancellationToken, StartupTestHooks? hooks)
    {
        var location = StorageLocation.Resolve(rootOverride);
        Directory.CreateDirectory(location.DataDirectory);
        _ = location.Inventory();
        cancellationToken.ThrowIfCancellationRequested();

        FileStream? sharedLease = NativeFile.Acquire(location.LockPath, exclusive: false);
        try
        {
            var state = Inspect(location, sharedLease);
            InvokeStage(hooks, StartupStage.InitialProbeCompleted);
            cancellationToken.ThrowIfCancellationRequested();
            if (state.Kind == StateKind.Current && state.MarkerValid)
            {
                return StorageOpenResult.Ready(new WindowsStorageSession(location, sharedLease, clock));
            }
            if (state.Kind == StateKind.Blocked)
            {
                throw state.Error!;
            }

            sharedLease.Dispose();
            sharedLease = null;
            cancellationToken.ThrowIfCancellationRequested();

            using (var exclusiveLease = NativeFile.Acquire(location.LockPath, exclusive: true))
            {
                var inventory = location.Inventory(exclusiveLease);
                state = Inspect(location, exclusiveLease, inventory);
                InvokeStage(hooks, StartupStage.ExclusiveProbeCompleted);
                cancellationToken.ThrowIfCancellationRequested();

                if (state.Kind == StateKind.Fresh)
                {
                    Initialize(location, cancellationToken, hooks);
                }
                else if (state.Kind is StateKind.Current or StateKind.Legacy)
                {
                    if (state.Version < CurrentSchemaVersion)
                    {
                        Migrate(location, cancellationToken);
                    }
                    if (!state.MarkerValid)
                    {
                        WriteMarker(location);
                        InvokeStage(hooks, StartupStage.MarkerCreated);
                    }
                }
                else
                {
                    throw state.Error!;
                }

                InvokeStage(hooks, StartupStage.MaintenanceCompleted);
                cancellationToken.ThrowIfCancellationRequested();
            }

            cancellationToken.ThrowIfCancellationRequested();
            sharedLease = NativeFile.Acquire(location.LockPath, exclusive: false);
            var finalInventory = location.Inventory(sharedLease);
            var finalState = Inspect(location, sharedLease, finalInventory);
            InvokeStage(hooks, StartupStage.FinalProbeCompleted);
            cancellationToken.ThrowIfCancellationRequested();
            if (finalState.Kind != StateKind.Current || !finalState.MarkerValid)
            {
                throw finalState.Error ?? new StorageException(new(StorageFailureCode.RecoveryBlocked, "recheck"));
            }

            var ready = sharedLease;
            sharedLease = null;
            return StorageOpenResult.Ready(new WindowsStorageSession(location, ready, clock));
        }
        catch
        {
            sharedLease?.Dispose();
            throw;
        }
    }

    private static State Inspect(StorageLocation location, FileStream? heldLock = null, StorageInventory? inventory = null)
    {
        inventory ??= location.Inventory(heldLock);
        if (inventory.HasIncompleteEvidence)
        {
            return State.Blocked(StorageFailureCode.RecoveryBlocked);
        }
        if (!inventory.HasDatabase)
        {
            if (inventory.HasMarker || inventory.HasAnyMainCompanion)
            {
                return State.Blocked(StorageFailureCode.RecoverySourceMissing);
            }
            return State.Fresh;
        }
        if (inventory.HasMarker && !MarkerIsValid(location.MarkerPath))
        {
            return State.Blocked(StorageFailureCode.RecoveryBlocked);
        }

        try
        {
            VerifySqliteHeader(location.DatabasePath);
            using var connection = new SqliteConnection(new SqliteConnectionStringBuilder
            {
                DataSource = location.DatabasePath,
                Mode = SqliteOpenMode.ReadOnly,
                Pooling = false,
                ForeignKeys = true,
                DefaultTimeout = 2
            }.ToString());
            connection.Open();
            var version = SchemaMigrator.InspectExisting(connection);
            var markerValid = inventory.HasMarker;
            return new(version == CurrentSchemaVersion ? StateKind.Current : StateKind.Legacy, version, markerValid, null);
        }
        catch (NotSupportedException exception)
        {
            return State.Blocked(StorageFailureCode.UnsupportedSchema, exception);
        }
        catch (SchemaValidationException exception)
        {
            return State.Blocked(StorageFailureCode.IntegrityFailure, exception);
        }
        catch (Exception exception)
        {
            return State.Blocked(StorageExceptionClassifier.Classify(exception, "probe").Failure.Code, exception);
        }
    }

    private static bool MarkerIsValid(string markerPath)
    {
        using var stream = new FileStream(markerPath, FileMode.Open, FileAccess.Read, FileShare.Read);
        if (stream.Length != MarkerBytes.Length)
        {
            return false;
        }
        var contents = new byte[MarkerBytes.Length];
        return stream.Read(contents, 0, contents.Length) == contents.Length && contents.SequenceEqual(MarkerBytes);
    }

    private static void VerifySqliteHeader(string databasePath)
    {
        var expectedHeader = Encoding.ASCII.GetBytes("SQLite format 3\0");
        var header = new byte[expectedHeader.Length];
        using var stream = new FileStream(databasePath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite);
        if (stream.Read(header, 0, header.Length) != header.Length || !header.SequenceEqual(expectedHeader))
        {
            throw new InvalidDataException("Invalid SQLite header.");
        }
    }

    private static void Initialize(StorageLocation location, CancellationToken cancellationToken, StartupTestHooks? hooks)
    {
        cancellationToken.ThrowIfCancellationRequested();
        var inventory = location.Inventory();
        if (inventory.HasDatabase || inventory.HasMarker || inventory.HasMarkerTemp || inventory.HasInitDatabase
            || inventory.HasAnyMainCompanion || inventory.HasAnyInitCompanion || inventory.RestoreEntries.Count != 0)
        {
            throw new StorageException(new(StorageFailureCode.RecoveryBlocked, "initialize"));
        }

        WriteMarker(location);
        InvokeStage(hooks, StartupStage.MarkerCreated);
        cancellationToken.ThrowIfCancellationRequested();

        var connectionString = new SqliteConnectionStringBuilder
        {
            DataSource = location.InitPath,
            Mode = SqliteOpenMode.ReadWriteCreate,
            Pooling = false,
            ForeignKeys = true,
            DefaultTimeout = 2
        }.ToString();
        SchemaMigrator.Migrate(connectionString);
        InvokeStage(hooks, StartupStage.InitializationStageCreated);
        cancellationToken.ThrowIfCancellationRequested();

        using (var probe = new SqliteConnection(new SqliteConnectionStringBuilder
        {
            DataSource = location.InitPath,
            Mode = SqliteOpenMode.ReadOnly,
            Pooling = false,
            ForeignKeys = true,
            DefaultTimeout = 2
        }.ToString()))
        {
            probe.Open();
            if (SchemaMigrator.InspectExisting(probe) != CurrentSchemaVersion)
            {
                throw new InvalidDataException("Initialization stage did not validate as current schema.");
            }
        }

        using (var stream = new FileStream(location.InitPath, FileMode.Open, FileAccess.ReadWrite, FileShare.Read))
        {
            stream.Flush(true);
        }
        InvokeStage(hooks, StartupStage.InitializationStageValidated);
        cancellationToken.ThrowIfCancellationRequested();
        File.Move(location.InitPath, location.DatabasePath);
        InvokeStage(hooks, StartupStage.DatabasePromoted);
    }

    private static void Migrate(StorageLocation location, CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        var connectionString = new SqliteConnectionStringBuilder
        {
            DataSource = location.DatabasePath,
            Mode = SqliteOpenMode.ReadWrite,
            Pooling = false,
            ForeignKeys = true,
            DefaultTimeout = 2
        }.ToString();
        try
        {
            SchemaMigrator.Migrate(connectionString);
        }
        catch (Exception exception) when (exception is not StorageException)
        {
            if (StorageExceptionClassifier.TryClassifyProviderFailure(exception, "migration", out var classified))
            {
                throw classified;
            }
            throw new StorageException(new(StorageFailureCode.MigrationFailed, "migration"), exception);
        }
        cancellationToken.ThrowIfCancellationRequested();
    }

    private static void WriteMarker(StorageLocation location)
    {
        using (var stream = new FileStream(location.MarkerTempPath, FileMode.CreateNew, FileAccess.Write, FileShare.None))
        {
            stream.Write(MarkerBytes);
            stream.Flush(true);
        }
        File.Move(location.MarkerTempPath, location.MarkerPath);
    }

    private static void InvokeStage(StartupTestHooks? hooks, StartupStage stage) => hooks?.OnStage?.Invoke(stage);

    private enum StateKind { Fresh, Current, Legacy, Blocked }

    private sealed record State(StateKind Kind, int Version = 0, bool MarkerValid = false, Exception? Error = null)
    {
        public static State Fresh => new(StateKind.Fresh);
        public static State Blocked(StorageFailureCode code, Exception? inner = null) =>
            new(StateKind.Blocked, Error: new StorageException(new(code, "inventory"), inner));
    }
}
