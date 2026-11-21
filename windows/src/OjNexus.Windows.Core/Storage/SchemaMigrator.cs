using System.Globalization;
using Microsoft.Data.Sqlite;

namespace OjNexus.Windows.Core.Storage;

internal sealed class SchemaValidationException(string message, Exception? inner = null) : Exception(message, inner);

public static class SchemaMigrator
{
    private const int CurrentSchemaVersion = 9;

    internal static int InspectExisting(SqliteConnection connection)
    {
        using var transaction = connection.BeginTransaction(deferred: true);
        int version;
        try
        {
            version = ReadSchemaVersion(connection, transaction)
                ?? throw MissingSchemaMetadata(connection, transaction);
            if (version > CurrentSchemaVersion)
            {
                throw new NotSupportedException("Unsupported database schema.");
            }
            switch (version)
            {
                case 0: ValidateVersionOneSchema(connection, transaction); break;
                case 1: ValidateVersionOneSchema(connection, transaction); break;
                case 2: ValidateVersionTwoSchema(connection, transaction); break;
                case 3: ValidateVersionThreeSchema(connection, transaction); break;
                case 4: ValidateVersionFourSchema(connection, transaction); break;
                case 5: ValidateVersionFiveSchema(connection, transaction); break;
                case 6: ValidateVersionSixSchema(connection, transaction); break;
                case 7: ValidateVersionSevenSchema(connection, transaction); break;
                case 8: ValidateVersionEightSchema(connection, transaction); break;
                case 9: ValidateVersionNineSchema(connection, transaction); break;
            }
        }
        catch (NotSupportedException)
        {
            throw;
        }
        catch (InvalidOperationException exception)
        {
            throw new SchemaValidationException("Database schema validation failed.", exception);
        }
        using (var command = connection.CreateCommand())
        {
            command.Transaction = transaction;
            command.CommandText = "PRAGMA integrity_check";
            if (!string.Equals(Convert.ToString(command.ExecuteScalar()), "ok", StringComparison.OrdinalIgnoreCase))
                throw new InvalidDataException("Integrity check failed.");
        }
        using (var command = connection.CreateCommand())
        {
            command.Transaction = transaction;
            command.CommandText = "PRAGMA foreign_key_check";
            using var reader = command.ExecuteReader();
            if (reader.Read()) throw new InvalidDataException("Foreign key check failed.");
        }
        transaction.Commit();
        return version;
    }

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
            ApplyVersionTwoMigration(connection, transaction);
            ApplyVersionThreeMigration(connection, transaction);
            ApplyVersionFourMigration(connection, transaction);
            ApplyVersionFiveMigration(connection, transaction);
            ApplyVersionSixMigration(connection, transaction);
            ApplyVersionSevenMigration(connection, transaction);
            ApplyVersionEightMigration(connection, transaction);
            ApplyVersionNineMigration(connection, transaction);
        }
        else if (version > CurrentSchemaVersion)
        {
            throw new InvalidOperationException($"Database schema version {version} is newer than the supported version {CurrentSchemaVersion}.");
        }
        else if (version == 0)
        {
            ApplyVersionOneMigration(connection, transaction);
            ApplyVersionTwoMigration(connection, transaction);
            ApplyVersionThreeMigration(connection, transaction);
            ApplyVersionFourMigration(connection, transaction);
            ApplyVersionFiveMigration(connection, transaction);
            ApplyVersionSixMigration(connection, transaction);
            ApplyVersionSevenMigration(connection, transaction);
            ApplyVersionEightMigration(connection, transaction);
            ApplyVersionNineMigration(connection, transaction);
        }
        else if (version == 1)
        {
            ApplyVersionTwoMigration(connection, transaction);
            ApplyVersionThreeMigration(connection, transaction);
            ApplyVersionFourMigration(connection, transaction);
            ApplyVersionFiveMigration(connection, transaction);
            ApplyVersionSixMigration(connection, transaction);
            ApplyVersionSevenMigration(connection, transaction);
            ApplyVersionEightMigration(connection, transaction);
            ApplyVersionNineMigration(connection, transaction);
        }
        else if (version == 2)
        {
            ApplyVersionThreeMigration(connection, transaction);
            ApplyVersionFourMigration(connection, transaction);
            ApplyVersionFiveMigration(connection, transaction);
            ApplyVersionSixMigration(connection, transaction);
            ApplyVersionSevenMigration(connection, transaction);
            ApplyVersionEightMigration(connection, transaction);
            ApplyVersionNineMigration(connection, transaction);
        }
        else if (version == 3)
        {
            ApplyVersionFourMigration(connection, transaction);
            ApplyVersionFiveMigration(connection, transaction);
            ApplyVersionSixMigration(connection, transaction);
            ApplyVersionSevenMigration(connection, transaction);
            ApplyVersionEightMigration(connection, transaction);
            ApplyVersionNineMigration(connection, transaction);
        }
        else if (version == 4)
        {
            ApplyVersionFiveMigration(connection, transaction);
            ApplyVersionSixMigration(connection, transaction);
            ApplyVersionSevenMigration(connection, transaction);
            ApplyVersionEightMigration(connection, transaction);
            ApplyVersionNineMigration(connection, transaction);
        }
        else if (version == 5)
        {
            ApplyVersionSixMigration(connection, transaction);
            ApplyVersionSevenMigration(connection, transaction);
            ApplyVersionEightMigration(connection, transaction);
            ApplyVersionNineMigration(connection, transaction);
        }
        else if (version == 6)
        {
            ApplyVersionSevenMigration(connection, transaction);
            ApplyVersionEightMigration(connection, transaction);
            ApplyVersionNineMigration(connection, transaction);
        }
        else if (version == 7)
        {
            ApplyVersionEightMigration(connection, transaction);
            ApplyVersionNineMigration(connection, transaction);
        }
        else if (version == 8)
        {
            ApplyVersionNineMigration(connection, transaction);
        }
        else if (version == CurrentSchemaVersion)
        {
            ValidateVersionNineSchema(connection, transaction);
        }

        transaction.Commit();
    }

    /// <summary>
    /// A database FILE can exist with no metadata table while another process is
    /// inside its first-run initialization transaction (file created, tables still
    /// uncommitted). For the concurrent opener that state is transient — surface
    /// STORAGE_BUSY so the startup contract "concurrent first-run processes return
    /// only READY or BUSY" holds. When user tables exist without metadata the
    /// metadata loss is real corruption and keeps the original failure.
    /// </summary>
    private static Exception MissingSchemaMetadata(SqliteConnection connection, SqliteTransaction transaction) =>
        HasUserTables(connection, transaction)
            ? new InvalidOperationException("Missing schema metadata.")
            : new StorageException(new(StorageFailureCode.Busy, "startup"));

    private static int? ReadSchemaVersion(SqliteConnection connection, SqliteTransaction transaction)
    {
        if (!TableExists(connection, transaction, "schema_metadata"))
        {
            return null;
        }

        ValidateMetadataLayout(connection, transaction);

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

    private static void ValidateMetadataLayout(SqliteConnection connection, SqliteTransaction transaction)
    {
        using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = "PRAGMA table_info('schema_metadata')";
        using var reader = command.ExecuteReader();
        var columns = new Dictionary<string, (string Type, bool NotNull, int PrimaryKey)>(StringComparer.OrdinalIgnoreCase);
        while (reader.Read())
        {
            columns.Add(reader.GetString(1),
                (reader.GetString(2), reader.GetInt32(3) != 0, reader.GetInt32(5)));
        }

        if (columns.Count != 2
            || !columns.TryGetValue("key", out var key)
            || !columns.TryGetValue("value", out var value)
            || !string.Equals(key.Type, "TEXT", StringComparison.OrdinalIgnoreCase)
            || !key.NotNull
            || key.PrimaryKey != 1
            || !string.Equals(value.Type, "TEXT", StringComparison.OrdinalIgnoreCase)
            || !value.NotNull
            || value.PrimaryKey != 0)
        {
            throw new InvalidOperationException("Database schema metadata layout is invalid.");
        }
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

    private static bool ColumnExists(
        SqliteConnection connection,
        SqliteTransaction transaction,
        string tableName,
        string columnName)
    {
        using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = $"PRAGMA table_info([{tableName}])";
        using var reader = command.ExecuteReader();
        while (reader.Read())
        {
            if (string.Equals(reader.GetString(1), columnName, StringComparison.Ordinal))
            {
                return true;
            }
        }

        return false;
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
        versionCommand.Parameters.AddWithValue("$value", "1");
        versionCommand.ExecuteNonQuery();
    }

    private static void ApplyVersionTwoMigration(SqliteConnection connection, SqliteTransaction transaction)
    {
        Execute(connection, transaction, """
            CREATE TABLE IF NOT EXISTS codeforces_profiles (
                handle TEXT NOT NULL PRIMARY KEY,
                rating INTEGER NULL,
                rank TEXT NULL,
                max_rating INTEGER NULL,
                max_rank TEXT NULL,
                fetched_at TEXT NOT NULL
            );
            """);
        Execute(connection, transaction, """
            CREATE TABLE IF NOT EXISTS codeforces_ratings (
                handle TEXT NOT NULL,
                contest_id INTEGER NOT NULL,
                contest_name TEXT NOT NULL,
                rank INTEGER NOT NULL,
                rating_update_time_seconds INTEGER NOT NULL,
                old_rating INTEGER NOT NULL,
                new_rating INTEGER NOT NULL,
                fetched_at TEXT NOT NULL,
                PRIMARY KEY (handle, contest_id, rating_update_time_seconds)
            );
            """);
        Execute(connection, transaction, """
            CREATE TABLE IF NOT EXISTS codeforces_submissions (
                handle TEXT NOT NULL,
                id INTEGER NOT NULL,
                contest_id INTEGER NULL,
                problem_index TEXT NULL,
                problem_name TEXT NULL,
                verdict TEXT NULL,
                programming_language TEXT NOT NULL,
                passed_test_count INTEGER NOT NULL,
                time_consumed_millis INTEGER NOT NULL,
                memory_consumed_bytes INTEGER NOT NULL,
                creation_time_seconds INTEGER NOT NULL,
                fetched_at TEXT NOT NULL,
                PRIMARY KEY (handle, id)
            );
            """);
        Execute(connection, transaction, "CREATE INDEX IF NOT EXISTS idx_codeforces_ratings_handle_time ON codeforces_ratings(handle, rating_update_time_seconds DESC);");
        Execute(connection, transaction, "CREATE INDEX IF NOT EXISTS idx_codeforces_submissions_handle_time ON codeforces_submissions(handle, creation_time_seconds DESC, id DESC);");
        ValidateVersionTwoSchema(connection, transaction);

        using var versionCommand = connection.CreateCommand();
        versionCommand.Transaction = transaction;
        versionCommand.CommandText = "UPDATE schema_metadata SET value = '2' WHERE key = 'schema_version'";
        versionCommand.ExecuteNonQuery();
    }

    private static void ValidateVersionTwoSchema(SqliteConnection connection, SqliteTransaction transaction)
    {
        ValidateVersionOneSchema(connection, transaction);
        ValidateTable(
            connection,
            transaction,
            "codeforces_profiles",
            new ColumnDefinition("handle", true, 1),
            new ColumnDefinition("rating", false, 0),
            new ColumnDefinition("rank", false, 0),
            new ColumnDefinition("max_rating", false, 0),
            new ColumnDefinition("max_rank", false, 0),
            new ColumnDefinition("fetched_at", true, 0));
        ValidateTable(
            connection,
            transaction,
            "codeforces_ratings",
            new ColumnDefinition("handle", true, 1),
            new ColumnDefinition("contest_id", true, 2),
            new ColumnDefinition("contest_name", true, 0),
            new ColumnDefinition("rank", true, 0),
            new ColumnDefinition("rating_update_time_seconds", true, 3),
            new ColumnDefinition("old_rating", true, 0),
            new ColumnDefinition("new_rating", true, 0),
            new ColumnDefinition("fetched_at", true, 0));
        ValidateTable(
            connection,
            transaction,
            "codeforces_submissions",
            new ColumnDefinition("handle", true, 1),
            new ColumnDefinition("id", true, 2),
            new ColumnDefinition("contest_id", false, 0),
            new ColumnDefinition("problem_index", false, 0),
            new ColumnDefinition("problem_name", false, 0),
            new ColumnDefinition("verdict", false, 0),
            new ColumnDefinition("programming_language", true, 0),
            new ColumnDefinition("passed_test_count", true, 0),
            new ColumnDefinition("time_consumed_millis", true, 0),
            new ColumnDefinition("memory_consumed_bytes", true, 0),
            new ColumnDefinition("creation_time_seconds", true, 0),
            new ColumnDefinition("fetched_at", true, 0));
    }

    private static void ApplyVersionThreeMigration(SqliteConnection connection, SqliteTransaction transaction)
    {
        Execute(connection, transaction, """
            CREATE TABLE IF NOT EXISTS atcoder_submissions (
                handle TEXT NOT NULL,
                id INTEGER NOT NULL,
                epoch_second INTEGER NOT NULL,
                problem_id TEXT NOT NULL,
                contest_id TEXT NOT NULL,
                language TEXT NOT NULL,
                point REAL NOT NULL,
                source_length INTEGER NOT NULL,
                result TEXT NOT NULL,
                execution_time_millis INTEGER NOT NULL,
                fetched_at TEXT NOT NULL,
                PRIMARY KEY (handle, id)
            );
            """);
        Execute(connection, transaction, "CREATE INDEX IF NOT EXISTS idx_atcoder_submissions_handle_time ON atcoder_submissions(handle, epoch_second DESC, id DESC);");
        ValidateVersionThreeSchema(connection, transaction);

        using var versionCommand = connection.CreateCommand();
        versionCommand.Transaction = transaction;
        versionCommand.CommandText = "UPDATE schema_metadata SET value = '3' WHERE key = 'schema_version'";
        versionCommand.ExecuteNonQuery();
    }

    private static void ValidateVersionThreeSchema(SqliteConnection connection, SqliteTransaction transaction)
    {
        ValidateVersionTwoSchema(connection, transaction);
        ValidateTable(
            connection,
            transaction,
            "atcoder_submissions",
            new ColumnDefinition("handle", true, 1),
            new ColumnDefinition("id", true, 2),
            new ColumnDefinition("epoch_second", true, 0),
            new ColumnDefinition("problem_id", true, 0),
            new ColumnDefinition("contest_id", true, 0),
            new ColumnDefinition("language", true, 0),
            new ColumnDefinition("point", true, 0),
            new ColumnDefinition("source_length", true, 0),
            new ColumnDefinition("result", true, 0),
            new ColumnDefinition("execution_time_millis", true, 0),
            new ColumnDefinition("fetched_at", true, 0));
    }

    private static void ApplyVersionFourMigration(SqliteConnection connection, SqliteTransaction transaction)
    {
        Execute(connection, transaction, """
            CREATE TABLE IF NOT EXISTS luogu_payloads (
                handle TEXT NOT NULL PRIMARY KEY,
                user_id INTEGER NULL,
                display_name TEXT NOT NULL,
                rating INTEGER NULL,
                submissions_count INTEGER NOT NULL,
                contests_count INTEGER NOT NULL,
                problems_count INTEGER NOT NULL,
                fetched_at TEXT NOT NULL
            );
            """);
        ValidateVersionFourSchema(connection, transaction);

        using var versionCommand = connection.CreateCommand();
        versionCommand.Transaction = transaction;
        versionCommand.CommandText = "UPDATE schema_metadata SET value = '4' WHERE key = 'schema_version'";
        versionCommand.ExecuteNonQuery();
    }

    private static void ValidateVersionFourSchema(SqliteConnection connection, SqliteTransaction transaction)
    {
        ValidateVersionThreeSchema(connection, transaction);
        ValidateTable(
            connection,
            transaction,
            "luogu_payloads",
            new ColumnDefinition("handle", true, 1),
            new ColumnDefinition("user_id", false, 0),
            new ColumnDefinition("display_name", true, 0),
            new ColumnDefinition("rating", false, 0),
            new ColumnDefinition("submissions_count", true, 0),
            new ColumnDefinition("contests_count", true, 0),
            new ColumnDefinition("problems_count", true, 0),
             new ColumnDefinition("fetched_at", true, 0));
    }

    private static void ApplyVersionFiveMigration(SqliteConnection connection, SqliteTransaction transaction)
    {
        if (!ColumnExists(connection, transaction, "sync_operations", "handle"))
        {
            Execute(connection, transaction, "ALTER TABLE sync_operations ADD COLUMN handle TEXT NOT NULL DEFAULT ''");
        }

        Execute(connection, transaction, """
            UPDATE sync_operations
            SET handle = (
                SELECT handle FROM accounts WHERE accounts.judge = sync_operations.judge
            )
            WHERE handle = '';
            """);
        ValidateVersionFiveSchema(connection, transaction);

        using var versionCommand = connection.CreateCommand();
        versionCommand.Transaction = transaction;
        versionCommand.CommandText = "UPDATE schema_metadata SET value = '5' WHERE key = 'schema_version'";
        versionCommand.ExecuteNonQuery();
    }

    private static void ValidateVersionFiveSchema(SqliteConnection connection, SqliteTransaction transaction)
    {
        ValidateVersionFourSchema(connection, transaction);
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
            new ColumnDefinition("failure_category", false, 0),
            new ColumnDefinition("handle", true, 0));
    }

    private static void ApplyVersionSixMigration(SqliteConnection connection, SqliteTransaction transaction)
    {
        Execute(connection, transaction, """
            CREATE TABLE IF NOT EXISTS problems (
                judge TEXT NOT NULL,
                external_id TEXT NOT NULL,
                title TEXT NOT NULL,
                url TEXT NOT NULL,
                solved INTEGER NOT NULL DEFAULT 0,
                first_seen_at TEXT NOT NULL,
                last_seen_at TEXT NOT NULL,
                PRIMARY KEY (judge, external_id)
            );
            """);
        ValidateVersionSixSchema(connection, transaction);

        using var versionCommand = connection.CreateCommand();
        versionCommand.Transaction = transaction;
        versionCommand.CommandText = "UPDATE schema_metadata SET value = '6' WHERE key = 'schema_version'";
        versionCommand.ExecuteNonQuery();
    }

    private static void ValidateVersionSixSchema(SqliteConnection connection, SqliteTransaction transaction)
    {
        ValidateVersionFiveSchema(connection, transaction);
        ValidateTable(
            connection,
            transaction,
            "problems",
            new ColumnDefinition("judge", true, 1),
            new ColumnDefinition("external_id", true, 2),
            new ColumnDefinition("title", true, 0),
            new ColumnDefinition("url", true, 0),
            new ColumnDefinition("solved", true, 0),
            new ColumnDefinition("first_seen_at", true, 0),
            new ColumnDefinition("last_seen_at", true, 0));
    }

    private static void ApplyVersionSevenMigration(SqliteConnection connection, SqliteTransaction transaction)
    {
        Execute(connection, transaction, """
            CREATE TABLE IF NOT EXISTS reviews (
                judge TEXT NOT NULL,
                external_id TEXT NOT NULL,
                stage INTEGER NOT NULL,
                due_at TEXT NOT NULL,
                due_day_index TEXT NOT NULL,
                last_result TEXT NULL,
                last_reviewed_at TEXT NULL,
                created_at TEXT NOT NULL,
                PRIMARY KEY (judge, external_id)
            );
            """);
        Execute(connection, transaction, """
            CREATE TABLE IF NOT EXISTS review_log (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                judge TEXT NOT NULL,
                external_id TEXT NOT NULL,
                result TEXT NOT NULL,
                stage INTEGER NOT NULL,
                completed_at TEXT NOT NULL,
                day_index TEXT NOT NULL
            );
            """);
        Execute(connection, transaction, "CREATE INDEX IF NOT EXISTS idx_reviews_due_day ON reviews(due_day_index ASC, judge ASC, external_id ASC);");
        Execute(connection, transaction, "CREATE INDEX IF NOT EXISTS idx_review_log_identity ON review_log(judge, external_id, completed_at DESC);");
        ValidateVersionSevenSchema(connection, transaction);

        using var versionCommand = connection.CreateCommand();
        versionCommand.Transaction = transaction;
        versionCommand.CommandText = "UPDATE schema_metadata SET value = '7' WHERE key = 'schema_version'";
        versionCommand.ExecuteNonQuery();
    }

    private static void ValidateVersionSevenSchema(SqliteConnection connection, SqliteTransaction transaction)
    {
        ValidateVersionSixSchema(connection, transaction);
        ValidateTable(
            connection,
            transaction,
            "reviews",
            new ColumnDefinition("judge", true, 1),
            new ColumnDefinition("external_id", true, 2),
            new ColumnDefinition("stage", true, 0),
            new ColumnDefinition("due_at", true, 0),
            new ColumnDefinition("due_day_index", true, 0),
            new ColumnDefinition("last_result", false, 0),
            new ColumnDefinition("last_reviewed_at", false, 0),
            new ColumnDefinition("created_at", true, 0));
        ValidateTable(
            connection,
            transaction,
            "review_log",
            new ColumnDefinition("id", true, 1),
            new ColumnDefinition("judge", true, 0),
            new ColumnDefinition("external_id", true, 0),
            new ColumnDefinition("result", true, 0),
            new ColumnDefinition("stage", true, 0),
            new ColumnDefinition("completed_at", true, 0),
            new ColumnDefinition("day_index", true, 0));
    }

    private static void ApplyVersionEightMigration(SqliteConnection connection, SqliteTransaction transaction)
    {
        Execute(connection, transaction, """
            CREATE TABLE IF NOT EXISTS portable_archive_packages (
                identity TEXT NOT NULL PRIMARY KEY,
                scope_id TEXT NOT NULL,
                source_platform TEXT NOT NULL,
                generator TEXT NOT NULL,
                created_at_epoch_ms INTEGER NOT NULL,
                counts_json TEXT NOT NULL
            );
            """);
        Execute(connection, transaction, """
            CREATE TABLE IF NOT EXISTS portable_archive_records (
                identity TEXT NOT NULL,
                collection TEXT NOT NULL,
                stable_id TEXT NOT NULL,
                source_scope_id TEXT NOT NULL,
                origin TEXT NOT NULL,
                state TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                source_tag TEXT NOT NULL,
                synthetic INTEGER NOT NULL,
                sort_order INTEGER NOT NULL,
                PRIMARY KEY (identity, collection, stable_id),
                FOREIGN KEY (identity) REFERENCES portable_archive_packages(identity) ON DELETE CASCADE
            );
            """);
        Execute(connection, transaction, "CREATE INDEX IF NOT EXISTS idx_portable_archive_records_order ON portable_archive_records(identity, sort_order ASC);");
        ValidateVersionEightSchema(connection, transaction);

        using var versionCommand = connection.CreateCommand();
        versionCommand.Transaction = transaction;
        versionCommand.CommandText = "UPDATE schema_metadata SET value = '8' WHERE key = 'schema_version'";
        versionCommand.ExecuteNonQuery();
    }

    private static void ValidateVersionEightSchema(SqliteConnection connection, SqliteTransaction transaction)
    {
        ValidateVersionSevenSchema(connection, transaction);
        ValidateTable(
            connection,
            transaction,
            "portable_archive_packages",
            new ColumnDefinition("identity", true, 1),
            new ColumnDefinition("scope_id", true, 0),
            new ColumnDefinition("source_platform", true, 0),
            new ColumnDefinition("generator", true, 0),
            new ColumnDefinition("created_at_epoch_ms", true, 0),
            new ColumnDefinition("counts_json", true, 0));
        ValidateTable(
            connection,
            transaction,
            "portable_archive_records",
            new ColumnDefinition("identity", true, 1),
            new ColumnDefinition("collection", true, 2),
            new ColumnDefinition("stable_id", true, 3),
            new ColumnDefinition("source_scope_id", true, 0),
            new ColumnDefinition("origin", true, 0),
            new ColumnDefinition("state", true, 0),
            new ColumnDefinition("payload_json", true, 0),
            new ColumnDefinition("source_tag", true, 0),
            new ColumnDefinition("synthetic", true, 0),
            new ColumnDefinition("sort_order", true, 0));
        ValidateForeignKey(
            connection,
            transaction,
            "portable_archive_records",
            "identity",
            "portable_archive_packages",
            "identity",
            expectedDeleteAction: "CASCADE");
    }

    /// <summary>
    /// Phase 138: extends the problem ledger to the phase-136 contract shape —
    /// difficulty/favorite/attempt_count/updated_at/title_lower columns, the
    /// problem_tags table, ledger indexes and the app_settings key/value store
    /// for persisted UI filters. The composite (judge, external_id) foreign key
    /// on problem_tags is declared in DDL (enforced by SQLite) but only the
    /// table shape is validated here — the house FK validator checks
    /// single-column edges only.
    /// </summary>
    private static void ApplyVersionNineMigration(SqliteConnection connection, SqliteTransaction transaction)
    {
        // ADD COLUMN is not re-runnable; the in-house demote-fresh test pattern
        // (build current, demote the version, re-migrate) requires guarded adds.
        AddColumnIfMissing(connection, transaction, "problems", "difficulty INTEGER NULL");
        AddColumnIfMissing(connection, transaction, "problems", "favorite INTEGER NOT NULL DEFAULT 0");
        AddColumnIfMissing(connection, transaction, "problems", "attempt_count INTEGER NOT NULL DEFAULT 0");
        AddColumnIfMissing(connection, transaction, "problems", "updated_at TEXT NULL");
        AddColumnIfMissing(connection, transaction, "problems", "title_lower TEXT NULL");
        // updated_at backfills from last_seen_at in SQL; title_lower needs Unicode
        // lowering, which SQLite cannot do (lower() is ASCII-only) — backfill per
        // row from C# with the frozen simple-lowercase equivalent.
        Execute(connection, transaction, "UPDATE problems SET updated_at = last_seen_at WHERE updated_at IS NULL;");
        BackfillTitleLower(connection, transaction);
        Execute(connection, transaction, """
            CREATE TABLE IF NOT EXISTS problem_tags (
                judge TEXT NOT NULL,
                external_id TEXT NOT NULL,
                name TEXT NOT NULL,
                PRIMARY KEY (judge, external_id, name),
                FOREIGN KEY (judge, external_id) REFERENCES problems(judge, external_id) ON DELETE CASCADE
            );
            """);
        Execute(connection, transaction, """
            CREATE TABLE IF NOT EXISTS app_settings (
                key TEXT NOT NULL PRIMARY KEY,
                value TEXT NOT NULL
            );
            """);
        Execute(connection, transaction, "CREATE INDEX IF NOT EXISTS idx_problems_updated_at ON problems(updated_at ASC);");
        Execute(connection, transaction, "CREATE INDEX IF NOT EXISTS idx_problems_title_lower ON problems(title_lower ASC);");
        Execute(connection, transaction, "CREATE INDEX IF NOT EXISTS idx_problems_difficulty ON problems(difficulty ASC);");
        ValidateVersionNineSchema(connection, transaction);

        using var versionCommand = connection.CreateCommand();
        versionCommand.Transaction = transaction;
        versionCommand.CommandText = "UPDATE schema_metadata SET value = '9' WHERE key = 'schema_version'";
        versionCommand.ExecuteNonQuery();
    }

    /// <summary>Adds a column only when the table does not already carry it (re-runnable migration guard).</summary>
    private static void AddColumnIfMissing(SqliteConnection connection, SqliteTransaction transaction, string table, string columnDefinition)
    {
        var columnName = columnDefinition.Split(' ', StringSplitOptions.RemoveEmptyEntries)[0];
        using (var check = connection.CreateCommand())
        {
            check.Transaction = transaction;
            // The pragma argument must be a quoted STRING literal — the [bracket]
            // identifier form parses but pragma_table_info rejects it as misuse.
            check.CommandText = $"SELECT COUNT(*) FROM pragma_table_info('{table}') WHERE name = $name";
            check.Parameters.AddWithValue("$name", columnName);
            if (Convert.ToInt64(check.ExecuteScalar(), CultureInfo.InvariantCulture) > 0)
            {
                return;
            }
        }

        Execute(connection, transaction, $"ALTER TABLE [{table}] ADD COLUMN {columnDefinition};");
    }

    private static void BackfillTitleLower(SqliteConnection connection, SqliteTransaction transaction)
    {
        using var select = connection.CreateCommand();
        select.Transaction = transaction;
        select.CommandText = "SELECT judge, external_id, title FROM problems";
        using var reader = select.ExecuteReader();
        var rows = new List<(string Judge, string ExternalId, string Title)>();
        while (reader.Read())
        {
            rows.Add((reader.GetString(0), reader.GetString(1), reader.GetString(2)));
        }

        foreach (var (judge, externalId, title) in rows)
        {
            using var update = connection.CreateCommand();
            update.Transaction = transaction;
            update.CommandText = "UPDATE problems SET title_lower = $titleLower WHERE judge = $judge AND external_id = $externalId";
            update.Parameters.AddWithValue("$titleLower", title.ToLowerInvariant());
            update.Parameters.AddWithValue("$judge", judge);
            update.Parameters.AddWithValue("$externalId", externalId);
            update.ExecuteNonQuery();
        }
    }

    private static void ValidateVersionNineSchema(SqliteConnection connection, SqliteTransaction transaction)
    {
        ValidateVersionEightSchema(connection, transaction);
        ValidateTable(
            connection,
            transaction,
            "problems",
            new ColumnDefinition("judge", true, 1),
            new ColumnDefinition("external_id", true, 2),
            new ColumnDefinition("title", true, 0),
            new ColumnDefinition("url", true, 0),
            new ColumnDefinition("solved", true, 0),
            new ColumnDefinition("first_seen_at", true, 0),
            new ColumnDefinition("last_seen_at", true, 0),
            new ColumnDefinition("difficulty", false, 0),
            new ColumnDefinition("favorite", true, 0),
            new ColumnDefinition("attempt_count", true, 0),
            new ColumnDefinition("updated_at", false, 0),
            new ColumnDefinition("title_lower", false, 0));
        ValidateTable(
            connection,
            transaction,
            "problem_tags",
            new ColumnDefinition("judge", true, 1),
            new ColumnDefinition("external_id", true, 2),
            new ColumnDefinition("name", true, 3));
        ValidateTable(
            connection,
            transaction,
            "app_settings",
            new ColumnDefinition("key", true, 1),
            new ColumnDefinition("value", true, 0));
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
        ValidateForeignKey(connection, transaction, "sync_operations", "judge", "accounts", "judge", expectedDeleteAction: "NO ACTION");
        ValidateForeignKey(connection, transaction, "sync_modules", "operation_id", "sync_operations", "id", expectedDeleteAction: "CASCADE");
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

            if (actual.NotNull != requiredColumn.NotNull)
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
        string expectedDeleteAction)
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
                && string.Equals(reader.GetString(6), expectedDeleteAction, StringComparison.OrdinalIgnoreCase))
            {
                return;
            }
        }

        throw new InvalidOperationException($"Database schema version 1 is missing foreign key '{tableName}.{fromColumn}' -> '{targetTable}.{targetColumn}' with ON DELETE {expectedDeleteAction}.");
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
