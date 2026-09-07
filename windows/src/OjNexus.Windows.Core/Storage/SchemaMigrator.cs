using Microsoft.Data.Sqlite;

namespace OjNexus.Windows.Core.Storage;

public static class SchemaMigrator
{
    public static void Migrate(string connectionString)
    {
        using var connection = new SqliteConnection(connectionString);
        connection.Open();
        using var transaction = connection.BeginTransaction();
        Execute(connection, transaction, """
            CREATE TABLE IF NOT EXISTS schema_metadata (
                key TEXT NOT NULL PRIMARY KEY,
                value TEXT NOT NULL
            );
            """);
        Execute(connection, transaction, """
            CREATE TABLE IF NOT EXISTS accounts (
                judge TEXT NOT NULL PRIMARY KEY,
                handle TEXT NOT NULL,
                enabled INTEGER NOT NULL
            );
            """);
        Execute(connection, transaction, """
            CREATE TABLE IF NOT EXISTS sync_operations (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                judge TEXT NOT NULL,
                data_generation TEXT NOT NULL,
                started_at TEXT NOT NULL,
                finished_at TEXT NULL,
                status TEXT NOT NULL,
                failure_category TEXT NULL,
                FOREIGN KEY (judge) REFERENCES accounts(judge)
            );
            """);
        Execute(connection, transaction, """
            CREATE TABLE IF NOT EXISTS sync_modules (
                operation_id INTEGER NOT NULL,
                stage TEXT NOT NULL,
                status TEXT NOT NULL,
                attempted_count INTEGER NOT NULL,
                imported_count INTEGER NOT NULL,
                updated_count INTEGER NOT NULL,
                failure_type TEXT NULL,
                completed_at TEXT NOT NULL,
                PRIMARY KEY (operation_id, stage),
                FOREIGN KEY (operation_id) REFERENCES sync_operations(id) ON DELETE CASCADE
            );
            """);
        using var versionCommand = connection.CreateCommand();
        versionCommand.Transaction = transaction;
        versionCommand.CommandText = "INSERT INTO schema_metadata (key, value) VALUES ($key, $value) ON CONFLICT(key) DO UPDATE SET value = excluded.value";
        versionCommand.Parameters.AddWithValue("$key", "schema_version");
        versionCommand.Parameters.AddWithValue("$value", "1");
        versionCommand.ExecuteNonQuery();
        transaction.Commit();
    }

    private static void Execute(SqliteConnection connection, SqliteTransaction transaction, string commandText)
    {
        using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = commandText;
        command.ExecuteNonQuery();
    }
}
