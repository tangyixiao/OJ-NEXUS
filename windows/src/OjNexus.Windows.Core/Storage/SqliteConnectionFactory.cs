using Microsoft.Data.Sqlite;

namespace OjNexus.Windows.Core.Storage;

public sealed class SqliteConnectionFactory
{
    private const string DatabaseFileName = "ojnexus.db";
    private readonly string _connectionString;
    private readonly OperationOwner? _owner;
    private readonly Action? _beforeCloseForTests;

    // TRANSITIONAL LEGACY SEAM: Task 2 removes this public side-effecting factory constructor.
    public SqliteConnectionFactory(string? rootOverride = null)
    {
        var dataDirectory = WindowsPaths.GetDataDirectory(rootOverride);
        Directory.CreateDirectory(dataDirectory);
        DatabasePath = Path.Combine(dataDirectory, DatabaseFileName);
        _connectionString = new SqliteConnectionStringBuilder
        {
            DataSource = DatabasePath,
            ForeignKeys = true,
        }.ToString();
        SchemaMigrator.Migrate(_connectionString);
    }

    // Transitional legacy path: removed when CLI/Desktop callers migrate in Task 2.
    internal SqliteConnectionFactory(string databasePath, OperationOwner owner, Action? beforeCloseForTests = null)
    {
        DatabasePath = databasePath;
        _owner = owner;
        _beforeCloseForTests = beforeCloseForTests;
        _connectionString = new SqliteConnectionStringBuilder
        {
            DataSource = databasePath,
            Mode = SqliteOpenMode.ReadWrite,
            ForeignKeys = true,
            Pooling = false,
            DefaultTimeout = 2,
        }.ToString();
    }

    public string DatabasePath { get; }

    // TRANSITIONAL LEGACY SEAM: guarded sessions do not expose this method or its raw connection.
    public SqliteConnection OpenConnection()
    {
        var connection = new SqliteConnection(_connectionString);
        connection.Open();
        return connection;
    }

    internal void ValidatePermit(OperationPermit permit)
    {
        if (_owner is null) throw new StorageException(new(StorageFailureCode.SessionClosed, "connection"));
        _owner.ValidatePermit(permit);
    }

    internal OperationCallLease EnterStoreCall(OperationPermit permit)
    {
        ValidatePermit(permit);
        return _owner!.EnterStoreCall(permit);
    }

    internal SqliteConnectionLease OpenLease(OperationPermit permit)
    {
        ValidatePermit(permit);
        var reservation = _owner!.ReserveNativeLease(permit);
        SqliteConnection? connection = null;
        try
        {
            connection = new SqliteConnection(_connectionString);
            connection.Open();
            return new SqliteConnectionLease(connection, reservation, _beforeCloseForTests);
        }
        catch
        {
            try
            {
                if (connection is not null)
                {
                    connection.Close();
                    connection.Dispose();
                }
                reservation.Dispose();
            }
            catch
            {
                // Keep the reservation if native closure cannot be confirmed.
            }
            throw;
        }
    }

    internal SqliteConnectionLease OpenLegacyLease() => new(OpenConnection(), null, null);
}
