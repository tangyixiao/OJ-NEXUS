using System.Globalization;
using Microsoft.Data.Sqlite;
using OjNexus.Windows.Core.Contracts;
using OjNexus.Windows.Core.Domain;

namespace OjNexus.Windows.Core.Storage;

public sealed class SqliteSyncStore(SqliteConnectionFactory connectionFactory) : ISyncStore
{
    private const int CompletedOperationRetention = 20;

    public async Task<IReadOnlyList<JudgeAccount>> GetAccountsAsync(CancellationToken cancellationToken)
    {
        using var connection = connectionFactory.OpenConnection();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT judge, handle, enabled FROM accounts ORDER BY judge ASC";
        using var reader = await command.ExecuteReaderAsync(cancellationToken);
        var accounts = new List<JudgeAccount>();
        while (await reader.ReadAsync(cancellationToken))
        {
            accounts.Add(new JudgeAccount(ParseJudge(reader.GetString(0)), reader.GetString(1), reader.GetInt64(2) != 0));
        }

        return accounts;
    }

    public async Task UpsertAccountAsync(JudgeAccount account, CancellationToken cancellationToken)
    {
        using var connection = connectionFactory.OpenConnection();
        using var command = connection.CreateCommand();
        command.CommandText = """
            INSERT INTO accounts (judge, handle, enabled) VALUES ($judge, $handle, $enabled)
            ON CONFLICT(judge) DO UPDATE SET handle = excluded.handle, enabled = excluded.enabled;
            """;
        AddAccountParameters(command, account);
        await command.ExecuteNonQueryAsync(cancellationToken);
    }

    public async Task<IReadOnlyList<SyncOperation>> GetRecentOperationsAsync(
        JudgeId? judge,
        int limit,
        CancellationToken cancellationToken)
    {
        if (limit <= 0)
        {
            return Array.Empty<SyncOperation>();
        }

        using var connection = connectionFactory.OpenConnection();
        using var command = connection.CreateCommand();
        command.CommandText = """
            SELECT operation.id, operation.judge, account.handle, account.enabled, operation.data_generation,
                   operation.started_at, operation.finished_at, operation.status,
                   module.stage, module.status, module.attempted_count, module.imported_count,
                   module.updated_count, module.failure_type
            FROM (
                SELECT id, judge, data_generation, started_at, finished_at, status
                FROM sync_operations
                WHERE ($judge IS NULL OR judge = $judge)
                ORDER BY started_at DESC, id DESC
                LIMIT $limit
            ) AS operation
            INNER JOIN accounts AS account ON account.judge = operation.judge
            LEFT JOIN sync_modules AS module ON module.operation_id = operation.id
            ORDER BY operation.started_at DESC, operation.id DESC, module.completed_at ASC, module.rowid ASC;
            """;
        command.Parameters.AddWithValue("$judge", judge?.ToString() ?? (object)DBNull.Value);
        command.Parameters.AddWithValue("$limit", limit);
        using var reader = await command.ExecuteReaderAsync(cancellationToken);
        var operations = new List<SyncOperation>();
        OperationAccumulator? current = null;
        while (await reader.ReadAsync(cancellationToken))
        {
            var operationId = reader.GetInt64(0);
            if (current is null || current.Id != operationId)
            {
                if (current is not null)
                {
                    operations.Add(current.ToOperation());
                }

                current = new OperationAccumulator(
                    operationId,
                    new JudgeAccount(ParseJudge(reader.GetString(1)), reader.GetString(2), reader.GetInt64(3) != 0),
                    reader.GetString(4),
                    ParseTime(reader.GetString(5)),
                    reader.IsDBNull(6) ? null : ParseTime(reader.GetString(6)),
                    ParseStatus(reader.GetString(7)));
            }

            if (!reader.IsDBNull(8))
            {
                current.Modules.Add(new SyncModuleOutcome(
                    reader.GetString(8),
                    ParseStatus(reader.GetString(9)),
                    reader.GetInt32(10),
                    reader.GetInt32(11),
                    reader.GetInt32(12),
                    reader.IsDBNull(13) ? null : reader.GetString(13)));
            }
        }

        if (current is not null)
        {
            operations.Add(current.ToOperation());
        }

        return operations;
    }

    public async Task<long> OpenOperationAsync(
        JudgeAccount account,
        string dataGeneration,
        DateTimeOffset startedAt,
        CancellationToken cancellationToken)
    {
        using var connection = connectionFactory.OpenConnection();
        using var transaction = connection.BeginTransaction();
        await UpsertAccountAsync(connection, transaction, account, cancellationToken);
        using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            INSERT INTO sync_operations (judge, data_generation, started_at, finished_at, status, failure_category)
            VALUES ($judge, $dataGeneration, $startedAt, NULL, $status, NULL);
            SELECT last_insert_rowid();
            """;
        command.Parameters.AddWithValue("$judge", account.Judge.ToString());
        command.Parameters.AddWithValue("$dataGeneration", dataGeneration);
        command.Parameters.AddWithValue("$startedAt", FormatTime(startedAt));
        command.Parameters.AddWithValue("$status", SyncOperationStatus.Running.ToString());
        var operationId = Convert.ToInt64(await command.ExecuteScalarAsync(cancellationToken), CultureInfo.InvariantCulture);
        await transaction.CommitAsync(cancellationToken);
        return operationId;
    }

    public async Task AppendModuleAsync(
        long operationId,
        SyncModuleOutcome outcome,
        DateTimeOffset completedAt,
        CancellationToken cancellationToken)
    {
        using var connection = connectionFactory.OpenConnection();
        using var transaction = connection.BeginTransaction();
        using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            INSERT INTO sync_modules (operation_id, stage, status, attempted_count, imported_count, updated_count, failure_type, completed_at)
            VALUES ($operationId, $stage, $status, $attemptedCount, $importedCount, $updatedCount, $failureType, $completedAt)
            ON CONFLICT(operation_id, stage) DO UPDATE SET
                status = excluded.status,
                attempted_count = excluded.attempted_count,
                imported_count = excluded.imported_count,
                updated_count = excluded.updated_count,
                failure_type = excluded.failure_type,
                completed_at = excluded.completed_at;
            """;
        command.Parameters.AddWithValue("$operationId", operationId);
        command.Parameters.AddWithValue("$stage", outcome.Stage);
        command.Parameters.AddWithValue("$status", outcome.Status.ToString());
        command.Parameters.AddWithValue("$attemptedCount", outcome.AttemptedCount);
        command.Parameters.AddWithValue("$importedCount", outcome.ImportedCount);
        command.Parameters.AddWithValue("$updatedCount", outcome.UpdatedCount);
        command.Parameters.AddWithValue("$failureType", outcome.FailureType ?? (object)DBNull.Value);
        command.Parameters.AddWithValue("$completedAt", FormatTime(completedAt));
        await command.ExecuteNonQueryAsync(cancellationToken);
        await transaction.CommitAsync(cancellationToken);
    }

    public async Task CloseOperationAsync(
        long operationId,
        SyncOperationStatus status,
        SyncError? error,
        DateTimeOffset finishedAt,
        CancellationToken cancellationToken)
    {
        using var connection = connectionFactory.OpenConnection();
        using var transaction = connection.BeginTransaction();
        using var closeCommand = connection.CreateCommand();
        closeCommand.Transaction = transaction;
        closeCommand.CommandText = """
            UPDATE sync_operations
            SET status = $status, failure_category = $failureCategory, finished_at = $finishedAt
            WHERE id = $operationId;
            """;
        closeCommand.Parameters.AddWithValue("$status", status.ToString());
        closeCommand.Parameters.AddWithValue("$failureCategory", error?.ToString() ?? (object)DBNull.Value);
        closeCommand.Parameters.AddWithValue("$finishedAt", FormatTime(finishedAt));
        closeCommand.Parameters.AddWithValue("$operationId", operationId);
        await closeCommand.ExecuteNonQueryAsync(cancellationToken);

        using var pruneCommand = connection.CreateCommand();
        pruneCommand.Transaction = transaction;
        pruneCommand.CommandText = """
            DELETE FROM sync_operations
            WHERE judge = (SELECT judge FROM sync_operations WHERE id = $operationId)
              AND finished_at IS NOT NULL
              AND id NOT IN (
                  SELECT id FROM sync_operations
                  WHERE judge = (SELECT judge FROM sync_operations WHERE id = $operationId)
                    AND finished_at IS NOT NULL
                  ORDER BY started_at DESC, id DESC
                  LIMIT 20
              );
            """;
        pruneCommand.Parameters.AddWithValue("$operationId", operationId);
        await pruneCommand.ExecuteNonQueryAsync(cancellationToken);
        await transaction.CommitAsync(cancellationToken);
    }

    private static async Task UpsertAccountAsync(SqliteConnection connection, SqliteTransaction transaction, JudgeAccount account, CancellationToken cancellationToken)
    {
        using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            INSERT INTO accounts (judge, handle, enabled) VALUES ($judge, $handle, $enabled)
            ON CONFLICT(judge) DO UPDATE SET handle = excluded.handle, enabled = excluded.enabled;
            """;
        AddAccountParameters(command, account);
        await command.ExecuteNonQueryAsync(cancellationToken);
    }

    private static void AddAccountParameters(SqliteCommand command, JudgeAccount account)
    {
        command.Parameters.AddWithValue("$judge", account.Judge.ToString());
        command.Parameters.AddWithValue("$handle", account.Handle);
        command.Parameters.AddWithValue("$enabled", account.Enabled ? 1 : 0);
    }

    private static string FormatTime(DateTimeOffset value) => value.ToUniversalTime().ToString("O", CultureInfo.InvariantCulture);

    private static DateTimeOffset ParseTime(string value) => DateTimeOffset.Parse(value, CultureInfo.InvariantCulture, DateTimeStyles.RoundtripKind);

    private static JudgeId ParseJudge(string value) => Enum.Parse<JudgeId>(value, ignoreCase: false);

    private static SyncOperationStatus ParseStatus(string value) => Enum.Parse<SyncOperationStatus>(value, ignoreCase: false);

    private sealed class OperationAccumulator(long id, JudgeAccount account, string dataGeneration, DateTimeOffset startedAt, DateTimeOffset? finishedAt, SyncOperationStatus status)
    {
        public long Id { get; } = id;
        public JudgeAccount Account { get; } = account;
        public string DataGeneration { get; } = dataGeneration;
        public DateTimeOffset StartedAt { get; } = startedAt;
        public DateTimeOffset? FinishedAt { get; } = finishedAt;
        public SyncOperationStatus Status { get; } = status;
        public List<SyncModuleOutcome> Modules { get; } = [];

        public SyncOperation ToOperation() => new(Id, Account, DataGeneration, StartedAt, FinishedAt, Status, Modules);
    }
}
