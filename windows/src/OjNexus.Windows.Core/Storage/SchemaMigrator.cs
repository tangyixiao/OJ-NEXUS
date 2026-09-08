using Microsoft.Data.Sqlite;

namespace OjNexus.Windows.Core.Storage;

public static class SchemaMigrator
{
    private const int CurrentSchemaVersion = 1;

    public static void Migrate(string connectionString)
    {
        using var connection = new SqliteConnection(connectionString);
        connection.Open();
        using var transaction = connection.BeginTransaction();

        var version = ReadSchemaVersion(connection, transaction);
        if (version is null)
        {
            if (HasUserTables(connection, transaction))
            {
                throw new InvalidOperationException("Database schema is missing schema_version metadata.");
            }

            ApplyVersionOneMigration(connection, transaction);
        }
        else if (version > CurrentSchemaVersion)
        {
            throw new InvalidOperationException($"Database schema version {version} is newer than the supported version {CurrentSchemaVersion}.");
        }
        else if (version == 0)
        {
            ApplyVersionOneMigration(connection, transaction);
        }
        else if (version == CurrentSchemaVersion)
        {
            ValidateVersionOneSchema(connection, transaction);
        }

        transaction.Commit();
    }

    private static int? ReadSchemaVersion(SqliteConnection connection, SqliteTransaction transaction)
    {
        if (!TableExists(connection, transaction, "schema_metadata"))
        {
            return null;
        }

        using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = "SELECT value FROM schema_metadata WHERE key = 'schema_version'";
        var value = command.ExecuteScalar();
        if (value is null || value is DBNull)
        {
            throw new InvalidOperationException("Database schema metadata does not contain schema_version.");
        }

        if (!int.TryParse(Convert.ToString(value, System.Globalization.CultureInfo.InvariantCulture), out var version) || version < 0)
        {
            throw new InvalidOperationException("Database schema_version metadata is invalid.");
        }

        return version;
    }

    private static bool HasUserTables(SqliteConnection connection, SqliteTransaction transaction)
    {
        using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = "SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%')";
        return Convert.ToInt64(command.ExecuteScalar(), System.Globalization.CultureInfo.InvariantCulture) != 0;
    }

    private static bool TableExists(SqliteConnection connection, SqliteTransaction transaction, string tableName)
    {
        using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = "SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = $tableName)";
        command.Parameters.AddWithValue("$tableName", tableName);
        return Convert.ToInt64(command.ExecuteScalar(), System.Globalization.CultureInfo.InvariantCulture) != 0;
    }

    private static void ApplyVersionOneMigration(SqliteConnection connection, SqliteTransaction transaction)
    {
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
        ValidateVersionOneSchema(connection, transaction);

        using var versionCommand = connection.CreateCommand();
        versionCommand.Transaction = transaction;
        versionCommand.CommandText = "INSERT INTO schema_metadata (key, value) VALUES ($key, $value) ON CONFLICT(key) DO UPDATE SET value = excluded.value";
        versionCommand.Parameters.AddWithValue("$key", "schema_version");
        versionCommand.Parameters.AddWithValue("$value", CurrentSchemaVersion.ToString(System.Globalization.CultureInfo.InvariantCulture));
        versionCommand.ExecuteNonQuery();
    }

    private static void ValidateVersionOneSchema(SqliteConnection connection, SqliteTransaction transaction)
    {
        ValidateTable(
            connection,
            transaction,
            "schema_metadata",
            new ColumnDefinition("key", true, 1),
            new ColumnDefinition("value", true, 0));
        ValidateTable(
            connection,
            transaction,
            "accounts",
            new ColumnDefinition("judge", true, 1),
            new ColumnDefinition("handle", true, 0),
            new ColumnDefinition("enabled", true, 0));
        ValidateTable(
            connection,
            transaction,
            "sync_operations",
            new ColumnDefinition("id", true, 1),
            new ColumnDefinition("judge", true, 0),
            new ColumnDefinition("data_generation", true, 0),
            new ColumnDefinition("started_at", true, 0),
            new ColumnDefinition("finished_at", false, 0),
            new ColumnDefinition("status", true, 0),
            new ColumnDefinition("failure_category", false, 0));
        ValidateTable(
            connection,
            transaction,
            "sync_modules",
            new ColumnDefinition("operation_id", true, 1),
            new ColumnDefinition("stage", true, 2),
            new ColumnDefinition("status", true, 0),
            new ColumnDefinition("attempted_count", true, 0),
            new ColumnDefinition("imported_count", true, 0),
            new ColumnDefinition("updated_count", true, 0),
            new ColumnDefinition("failure_type", false, 0),
            new ColumnDefinition("completed_at", true, 0));
        ValidateForeignKey(connection, transaction, "sync_operations", "judge", "accounts", "judge", cascadeDelete: false);
        ValidateForeignKey(connection, transaction, "sync_modules", "operation_id", "sync_operations", "id", cascadeDelete: true);
    }

    private static void ValidateTable(
        SqliteConnection connection,
        SqliteTransaction transaction,
        string tableName,
        params ColumnDefinition[] requiredColumns)
    {
        if (!TableExists(connection, transaction, tableName))
        {
            throw new InvalidOperationException($"Database schema version 1 is missing table '{tableName}'.");
        }

        using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = $"PRAGMA table_info([{tableName}])";
        using var reader = command.ExecuteReader();
        var columns = new Dictionary<string, (bool NotNull, int PrimaryKeyOrder)>(StringComparer.Ordinal);
        while (reader.Read())
        {
            columns[reader.GetString(1)] = (reader.GetInt64(3) != 0, reader.GetInt32(5));
        }

        foreach (var requiredColumn in requiredColumns)
        {
            if (!columns.TryGetValue(requiredColumn.Name, out var actual))
            {
                throw new InvalidOperationException($"Database schema version 1 has an incomplete table '{tableName}'.");
            }

            if (requiredColumn.NotNull && !actual.NotNull)
            {
                throw new InvalidOperationException($"Database schema version 1 has an invalid nullability constraint on '{tableName}.{requiredColumn.Name}'.");
            }

            if (actual.PrimaryKeyOrder != requiredColumn.PrimaryKeyOrder)
            {
                throw new InvalidOperationException($"Database schema version 1 has an invalid primary key on '{tableName}.{requiredColumn.Name}'.");
            }
        }

        if (columns.Any(column => column.Value.PrimaryKeyOrder > 0 && requiredColumns.All(required => required.Name != column.Key)))
        {
            throw new InvalidOperationException($"Database schema version 1 has an unexpected primary key on table '{tableName}'.");
        }
    }

    private static void ValidateForeignKey(
        SqliteConnection connection,
        SqliteTransaction transaction,
        string tableName,
        string fromColumn,
        string targetTable,
        string targetColumn,
        bool cascadeDelete)
    {
        using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = $"PRAGMA foreign_key_list([{tableName}])";
        using var reader = command.ExecuteReader();
        while (reader.Read())
        {
            if (string.Equals(reader.GetString(2), targetTable, StringComparison.Ordinal)
                && string.Equals(reader.GetString(3), fromColumn, StringComparison.Ordinal)
                && string.Equals(reader.GetString(4), targetColumn, StringComparison.Ordinal)
                && (!cascadeDelete || string.Equals(reader.GetString(6), "CASCADE", StringComparison.OrdinalIgnoreCase)))
            {
                return;
            }
        }

        var action = cascadeDelete ? " with ON DELETE CASCADE" : string.Empty;
        throw new InvalidOperationException($"Database schema version 1 is missing foreign key '{tableName}.{fromColumn}' -> '{targetTable}.{targetColumn}'{action}.");
    }

    private sealed record ColumnDefinition(string Name, bool NotNull, int PrimaryKeyOrder);

    private static void Execute(SqliteConnection connection, SqliteTransaction transaction, string commandText)
    {
        using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = commandText;
        command.ExecuteNonQuery();
    }
}
