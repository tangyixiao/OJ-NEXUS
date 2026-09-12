using System.Globalization;
using Microsoft.Data.Sqlite;
using OjNexus.Windows.Core.Contracts;
using OjNexus.Windows.Core.Domain;
using OjNexus.Windows.Core.Sync;

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
                current.Modules.Add(ModuleFailureType.Normalize(new SyncModuleOutcome(
                    reader.GetString(8),
                    ParseStatus(reader.GetString(9)),
                    reader.GetInt32(10),
                    reader.GetInt32(11),
                    reader.GetInt32(12),
                    reader.IsDBNull(13) ? null : reader.GetString(13))));
            }
        }

        if (current is not null)
        {
            operations.Add(current.ToOperation());
        }

        return operations;
    }

    public async Task<CodeforcesPayloadSnapshot> GetCodeforcesPayloadAsync(
        string handle,
        CancellationToken cancellationToken)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(handle);
        handle = handle.Trim();

        using var connection = connectionFactory.OpenConnection();
        CodeforcesProfilePayload? profile = null;
        using (var profileCommand = connection.CreateCommand())
        {
            profileCommand.CommandText = "SELECT handle, rating, rank, max_rating, max_rank FROM codeforces_profiles WHERE handle = $handle";
            profileCommand.Parameters.AddWithValue("$handle", handle);
            using var reader = await profileCommand.ExecuteReaderAsync(cancellationToken);
            if (await reader.ReadAsync(cancellationToken))
            {
                profile = new CodeforcesProfilePayload(
                    reader.GetString(0),
                    reader.IsDBNull(1) ? null : reader.GetInt32(1),
                    reader.IsDBNull(2) ? null : reader.GetString(2),
                    reader.IsDBNull(3) ? null : reader.GetInt32(3),
                    reader.IsDBNull(4) ? null : reader.GetString(4));
            }
        }

        var ratings = new List<CodeforcesRating>();
        using (var ratingsCommand = connection.CreateCommand())
        {
            ratingsCommand.CommandText = """
                SELECT contest_id, contest_name, rank, rating_update_time_seconds, old_rating, new_rating
                FROM codeforces_ratings
                WHERE handle = $handle
                ORDER BY rating_update_time_seconds ASC, contest_id ASC;
                """;
            ratingsCommand.Parameters.AddWithValue("$handle", handle);
            using var reader = await ratingsCommand.ExecuteReaderAsync(cancellationToken);
            while (await reader.ReadAsync(cancellationToken))
            {
                ratings.Add(new CodeforcesRating(
                    reader.GetInt32(0),
                    reader.GetString(1),
                    reader.GetInt32(2),
                    reader.GetInt64(3),
                    reader.GetInt32(4),
                    reader.GetInt32(5)));
            }
        }

        var submissions = new List<CodeforcesSubmission>();
        using (var submissionsCommand = connection.CreateCommand())
        {
            submissionsCommand.CommandText = """
                SELECT id, contest_id, problem_index, problem_name, verdict, programming_language,
                       passed_test_count, time_consumed_millis, memory_consumed_bytes, creation_time_seconds
                FROM codeforces_submissions
                WHERE handle = $handle
                ORDER BY creation_time_seconds ASC, id ASC;
                """;
            submissionsCommand.Parameters.AddWithValue("$handle", handle);
            using var reader = await submissionsCommand.ExecuteReaderAsync(cancellationToken);
            while (await reader.ReadAsync(cancellationToken))
            {
                submissions.Add(new CodeforcesSubmission(
                    reader.GetInt64(0),
                    reader.IsDBNull(1) ? null : reader.GetInt32(1),
                    reader.IsDBNull(2) ? null : reader.GetString(2),
                    reader.IsDBNull(3) ? null : reader.GetString(3),
                    reader.IsDBNull(4) ? null : reader.GetString(4),
                    reader.GetString(5),
                    reader.GetInt32(6),
                    reader.GetInt32(7),
                    reader.GetInt64(8),
                    reader.GetInt64(9)));
            }
        }

        return new CodeforcesPayloadSnapshot(profile, ratings, submissions);
    }

    public async Task<AtCoderPayloadSnapshot> GetAtCoderPayloadAsync(
        string handle,
        CancellationToken cancellationToken)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(handle);
        handle = handle.Trim();
        using var connection = connectionFactory.OpenConnection();
        using var command = connection.CreateCommand();
        command.CommandText = """
            SELECT id, epoch_second, problem_id, contest_id, language, point, source_length, result, execution_time_millis
            FROM atcoder_submissions
            WHERE handle = $handle
            ORDER BY epoch_second ASC, id ASC;
            """;
        command.Parameters.AddWithValue("$handle", handle);
        using var reader = await command.ExecuteReaderAsync(cancellationToken);
        var submissions = new List<AtCoderSubmission>();
        while (await reader.ReadAsync(cancellationToken))
        {
            submissions.Add(new AtCoderSubmission(
                reader.GetInt64(0),
                reader.GetInt64(1),
                reader.GetString(2),
                reader.GetString(3),
                reader.GetString(4),
                reader.GetDouble(5),
                reader.GetInt32(6),
                reader.GetString(7),
                reader.GetInt64(8)));
        }

        return new AtCoderPayloadSnapshot(submissions);
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
        var normalizedOutcome = ModuleFailureType.Normalize(outcome);
        using var connection = connectionFactory.OpenConnection();
        using var transaction = connection.BeginTransaction();
        using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            DELETE FROM sync_modules
            WHERE operation_id = $operationId AND stage = $stage;

            INSERT INTO sync_modules (operation_id, stage, status, attempted_count, imported_count, updated_count, failure_type, completed_at)
            VALUES ($operationId, $stage, $status, $attemptedCount, $importedCount, $updatedCount, $failureType, $completedAt)
            """;
        command.Parameters.AddWithValue("$operationId", operationId);
        command.Parameters.AddWithValue("$stage", normalizedOutcome.Stage);
        command.Parameters.AddWithValue("$status", normalizedOutcome.Status.ToString());
        command.Parameters.AddWithValue("$attemptedCount", normalizedOutcome.AttemptedCount);
        command.Parameters.AddWithValue("$importedCount", normalizedOutcome.ImportedCount);
        command.Parameters.AddWithValue("$updatedCount", normalizedOutcome.UpdatedCount);
        command.Parameters.AddWithValue("$failureType", normalizedOutcome.FailureType ?? (object)DBNull.Value);
        command.Parameters.AddWithValue("$completedAt", FormatTime(completedAt));
        await command.ExecuteNonQueryAsync(cancellationToken);
        await PersistPayloadAsync(connection, transaction, normalizedOutcome.Payload, completedAt, cancellationToken);
        await transaction.CommitAsync(cancellationToken);
    }

    private static async Task PersistPayloadAsync(
        SqliteConnection connection,
        SqliteTransaction transaction,
        SyncModulePayload? payload,
        DateTimeOffset fetchedAt,
        CancellationToken cancellationToken)
    {
        switch (payload)
        {
            case CodeforcesProfilePayload profile:
                using (var command = connection.CreateCommand())
                {
                    command.Transaction = transaction;
                    command.CommandText = """
                        INSERT INTO codeforces_profiles (handle, rating, rank, max_rating, max_rank, fetched_at)
                        VALUES ($handle, $rating, $rank, $maxRating, $maxRank, $fetchedAt)
                        ON CONFLICT(handle) DO UPDATE SET
                            rating = excluded.rating,
                            rank = excluded.rank,
                            max_rating = excluded.max_rating,
                            max_rank = excluded.max_rank,
                            fetched_at = excluded.fetched_at;
                        """;
                    command.Parameters.AddWithValue("$handle", profile.Handle);
                    AddNullableParameter(command, "$rating", profile.Rating);
                    AddNullableParameter(command, "$rank", profile.Rank);
                    AddNullableParameter(command, "$maxRating", profile.MaxRating);
                    AddNullableParameter(command, "$maxRank", profile.MaxRank);
                    command.Parameters.AddWithValue("$fetchedAt", FormatTime(fetchedAt));
                    await command.ExecuteNonQueryAsync(cancellationToken);
                }

                break;
            case CodeforcesRatingsPayload ratings:
                await ReplaceRatingsAsync(connection, transaction, ratings, fetchedAt, cancellationToken);
                break;
            case CodeforcesSubmissionsPayload submissions:
                await ReplaceSubmissionsAsync(connection, transaction, submissions, fetchedAt, cancellationToken);
                break;
            case AtCoderSubmissionsPayload atcoderSubmissions:
                await ReplaceAtCoderSubmissionsAsync(connection, transaction, atcoderSubmissions, fetchedAt, cancellationToken);
                break;
        }
    }

    private static async Task ReplaceRatingsAsync(
        SqliteConnection connection,
        SqliteTransaction transaction,
        CodeforcesRatingsPayload payload,
        DateTimeOffset fetchedAt,
        CancellationToken cancellationToken)
    {
        using (var deleteCommand = connection.CreateCommand())
        {
            deleteCommand.Transaction = transaction;
            deleteCommand.CommandText = "DELETE FROM codeforces_ratings WHERE handle = $handle";
            deleteCommand.Parameters.AddWithValue("$handle", payload.Handle);
            await deleteCommand.ExecuteNonQueryAsync(cancellationToken);
        }

        foreach (var item in payload.Items)
        {
            using var command = connection.CreateCommand();
            command.Transaction = transaction;
            command.CommandText = """
                INSERT INTO codeforces_ratings
                    (handle, contest_id, contest_name, rank, rating_update_time_seconds, old_rating, new_rating, fetched_at)
                VALUES ($handle, $contestId, $contestName, $rank, $updateTime, $oldRating, $newRating, $fetchedAt);
                """;
            command.Parameters.AddWithValue("$handle", payload.Handle);
            command.Parameters.AddWithValue("$contestId", item.ContestId);
            command.Parameters.AddWithValue("$contestName", item.ContestName);
            command.Parameters.AddWithValue("$rank", item.Rank);
            command.Parameters.AddWithValue("$updateTime", item.RatingUpdateTimeSeconds);
            command.Parameters.AddWithValue("$oldRating", item.OldRating);
            command.Parameters.AddWithValue("$newRating", item.NewRating);
            command.Parameters.AddWithValue("$fetchedAt", FormatTime(fetchedAt));
            await command.ExecuteNonQueryAsync(cancellationToken);
        }
    }

    private static async Task ReplaceSubmissionsAsync(
        SqliteConnection connection,
        SqliteTransaction transaction,
        CodeforcesSubmissionsPayload payload,
        DateTimeOffset fetchedAt,
        CancellationToken cancellationToken)
    {
        using (var deleteCommand = connection.CreateCommand())
        {
            deleteCommand.Transaction = transaction;
            deleteCommand.CommandText = "DELETE FROM codeforces_submissions WHERE handle = $handle";
            deleteCommand.Parameters.AddWithValue("$handle", payload.Handle);
            await deleteCommand.ExecuteNonQueryAsync(cancellationToken);
        }

        foreach (var item in payload.Items)
        {
            using var command = connection.CreateCommand();
            command.Transaction = transaction;
            command.CommandText = """
                INSERT INTO codeforces_submissions
                    (handle, id, contest_id, problem_index, problem_name, verdict, programming_language,
                     passed_test_count, time_consumed_millis, memory_consumed_bytes, creation_time_seconds, fetched_at)
                VALUES ($handle, $id, $contestId, $problemIndex, $problemName, $verdict, $language,
                        $passedTests, $timeMillis, $memoryBytes, $createdAt, $fetchedAt);
                """;
            command.Parameters.AddWithValue("$handle", payload.Handle);
            command.Parameters.AddWithValue("$id", item.Id);
            AddNullableParameter(command, "$contestId", item.ContestId);
            AddNullableParameter(command, "$problemIndex", item.ProblemIndex);
            AddNullableParameter(command, "$problemName", item.ProblemName);
            AddNullableParameter(command, "$verdict", item.Verdict);
            command.Parameters.AddWithValue("$language", item.ProgrammingLanguage);
            command.Parameters.AddWithValue("$passedTests", item.PassedTestCount);
            command.Parameters.AddWithValue("$timeMillis", item.TimeConsumedMillis);
            command.Parameters.AddWithValue("$memoryBytes", item.MemoryConsumedBytes);
            command.Parameters.AddWithValue("$createdAt", item.CreationTimeSeconds);
            command.Parameters.AddWithValue("$fetchedAt", FormatTime(fetchedAt));
            await command.ExecuteNonQueryAsync(cancellationToken);
        }
    }

    private static async Task ReplaceAtCoderSubmissionsAsync(
        SqliteConnection connection,
        SqliteTransaction transaction,
        AtCoderSubmissionsPayload payload,
        DateTimeOffset fetchedAt,
        CancellationToken cancellationToken)
    {
        using (var deleteCommand = connection.CreateCommand())
        {
            deleteCommand.Transaction = transaction;
            deleteCommand.CommandText = "DELETE FROM atcoder_submissions WHERE handle = $handle";
            deleteCommand.Parameters.AddWithValue("$handle", payload.Handle);
            await deleteCommand.ExecuteNonQueryAsync(cancellationToken);
        }

        foreach (var item in payload.Items)
        {
            using var command = connection.CreateCommand();
            command.Transaction = transaction;
            command.CommandText = """
                INSERT INTO atcoder_submissions
                    (handle, id, epoch_second, problem_id, contest_id, language, point, source_length,
                     result, execution_time_millis, fetched_at)
                VALUES ($handle, $id, $epochSecond, $problemId, $contestId, $language, $point, $sourceLength,
                        $result, $executionTime, $fetchedAt);
                """;
            command.Parameters.AddWithValue("$handle", payload.Handle);
            command.Parameters.AddWithValue("$id", item.Id);
            command.Parameters.AddWithValue("$epochSecond", item.EpochSecond);
            command.Parameters.AddWithValue("$problemId", item.ProblemId);
            command.Parameters.AddWithValue("$contestId", item.ContestId);
            command.Parameters.AddWithValue("$language", item.Language);
            command.Parameters.AddWithValue("$point", item.Point);
            command.Parameters.AddWithValue("$sourceLength", item.SourceLength);
            command.Parameters.AddWithValue("$result", item.Result);
            command.Parameters.AddWithValue("$executionTime", item.ExecutionTimeMillis);
            command.Parameters.AddWithValue("$fetchedAt", FormatTime(fetchedAt));
            await command.ExecuteNonQueryAsync(cancellationToken);
        }
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
                  LIMIT $retention
              );
            """;
        pruneCommand.Parameters.AddWithValue("$operationId", operationId);
        pruneCommand.Parameters.AddWithValue("$retention", CompletedOperationRetention);
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

    private static void AddNullableParameter<T>(SqliteCommand command, string name, T? value)
        where T : struct
    {
        command.Parameters.AddWithValue(name, value.HasValue ? value.Value : DBNull.Value);
    }

    private static void AddNullableParameter(SqliteCommand command, string name, string? value)
    {
        command.Parameters.AddWithValue(name, value ?? (object)DBNull.Value);
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
