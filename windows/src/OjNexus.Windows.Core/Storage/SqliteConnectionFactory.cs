using Microsoft.Data.Sqlite;

namespace OjNexus.Windows.Core.Storage;

public sealed class SqliteConnectionFactory
{
    private const string DatabaseFileName = "ojnexus.db";
    private readonly string _connectionString;

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

    public string DatabasePath { get; }

    public SqliteConnection OpenConnection()
    {
        var connection = new SqliteConnection(_connectionString);
        connection.Open();
        return connection;
    }
}
