using Microsoft.Data.Sqlite;

namespace OjNexus.Windows.Core.Storage;

internal sealed class SqliteConnectionLease : IDisposable
{
    private readonly OperationCallLease? _reservation;
    private readonly Action? _beforeClose;
    private bool _closed;

    internal SqliteConnectionLease(SqliteConnection connection, OperationCallLease? reservation, Action? beforeClose)
    {
        Connection = connection;
        _reservation = reservation;
        _beforeClose = beforeClose;
    }

    internal SqliteConnection Connection { get; }

    public void Dispose()
    {
        if (_closed) return;
        _beforeClose?.Invoke();
        Connection.Close();
        Connection.Dispose();
        _reservation?.Dispose();
        _closed = true;
    }
}
