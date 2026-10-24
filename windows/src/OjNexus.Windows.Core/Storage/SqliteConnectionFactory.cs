using Microsoft.Data.Sqlite;

namespace OjNexus.Windows.Core.Storage;

public sealed class SqliteConnectionFactory
{
    private readonly string _connectionString;
    private readonly OperationOwner _owner;
    private readonly Action? _beforeCloseForTests;

    internal SqliteConnectionFactory(string databasePath, OperationOwner owner, Action? beforeCloseForTests = null)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(databasePath);
        ArgumentNullException.ThrowIfNull(owner);
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

    internal string DatabasePath { get; }

    internal void ValidatePermit(OperationPermit permit) => _owner.ValidatePermit(permit);

    internal OperationCallLease EnterStoreCall(OperationPermit permit)
    {
        ValidatePermit(permit);
        return _owner.EnterStoreCall(permit);
    }

    internal SqliteConnectionLease OpenLease(OperationPermit permit)
    {
        ValidatePermit(permit);
        var reservation = _owner.ReserveNativeLease(permit);
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
}
