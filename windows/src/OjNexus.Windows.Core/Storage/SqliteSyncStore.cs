using System.Globalization;
using Microsoft.Data.Sqlite;
using OjNexus.Windows.Core.Contracts;
using OjNexus.Windows.Core.Domain;
using OjNexus.Windows.Core.Sync;

namespace OjNexus.Windows.Core.Storage;

public sealed class SqliteSyncStore(SqliteConnectionFactory connectionFactory, IClock? clock = null) : ISyncStore
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
            try
            {
                accounts.Add(JudgeAccount.Create(
                    ParseJudge(reader.GetString(0)),
                    reader.GetString(1)) with { Enabled = reader.GetInt64(2) != 0 });
            }
            catch (ArgumentException)
            {
                // Malformed local rows must not re-enter the request boundary after restart.
            }
        }

        return accounts;
    }

    public async Task UpsertAccountAsync(JudgeAccount account, CancellationToken cancellationToken)
    {
        account = JudgeAccount.Create(account.Judge, account.Handle) with { Enabled = account.Enabled };
        using var connection = connectionFactory.OpenConnection();
        using var transaction = connection.BeginTransaction();
        await UpsertAccountAsync(connection, transaction, account, cancellationToken);
        await transaction.CommitAsync(cancellationToken);
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
            SELECT operation.id, operation.judge, operation.handle, account.enabled, operation.data_generation,
                   operation.started_at, operation.finished_at, operation.status,
                   operation.failure_category,
                   module.stage, module.status, module.attempted_count, module.imported_count,
                   module.updated_count, module.failure_type
            FROM (
                 SELECT id, judge, handle, data_generation, started_at, finished_at, status, failure_category
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

                try
                {
                    current = new OperationAccumulator(
                        operationId,
                        JudgeAccount.Create(ParseJudge(reader.GetString(1)), reader.GetString(2)) with { Enabled = reader.GetInt64(3) != 0 },
                        reader.GetString(4),
                        ParseTime(reader.GetString(5)),
                        reader.IsDBNull(6) ? null : ParseTime(reader.GetString(6)),
                        ParseStatus(reader.GetString(7)),
                        reader.IsDBNull(8) ? null : ParseError(reader.GetString(8)));
                }
                catch (ArgumentException)
                {
                    current = null;
                    continue;
                }
            }

            if (!reader.IsDBNull(9))
            {
                current.Modules.Add(ModuleFailureType.Normalize(new SyncModuleOutcome(
                    reader.GetString(9),
                    ParseStatus(reader.GetString(10)),
                    reader.GetInt32(11),
                    reader.GetInt32(12),
                    reader.GetInt32(13),
                    reader.IsDBNull(14) ? null : reader.GetString(14))));
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

    public async Task<LuoguPayloadSnapshot> GetLuoguPayloadAsync(
        string handle,
        CancellationToken cancellationToken)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(handle);
        handle = handle.Trim();
        using var connection = connectionFactory.OpenConnection();
        using var command = connection.CreateCommand();
        command.CommandText = """
            SELECT user_id, display_name, rating, submissions_count, contests_count, problems_count
            FROM luogu_payloads
            WHERE handle = $handle;
            """;
        command.Parameters.AddWithValue("$handle", handle);
        using var reader = await command.ExecuteReaderAsync(cancellationToken);
        if (!await reader.ReadAsync(cancellationToken))
        {
            return new LuoguPayloadSnapshot(null, 0, 0, 0);
        }

        var userId = reader.IsDBNull(0) ? (long?)null : reader.GetInt64(0);
        var profile = userId.HasValue
            ? new LuoguProfilePayload(
                handle,
                userId.Value,
                reader.GetString(1),
                reader.IsDBNull(2) ? null : reader.GetInt32(2))
            : null;
        return new LuoguPayloadSnapshot(profile, reader.GetInt32(3), reader.GetInt32(4), reader.GetInt32(5));
    }

    public async Task<long> OpenOperationAsync(
        JudgeAccount account,
        string dataGeneration,
        DateTimeOffset startedAt,
        CancellationToken cancellationToken)
    {
        account = JudgeAccount.Create(account.Judge, account.Handle) with { Enabled = account.Enabled };
        using var connection = connectionFactory.OpenConnection();
        using var transaction = connection.BeginTransaction();
        await UpsertAccountAsync(connection, transaction, account, cancellationToken);
        using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            INSERT INTO sync_operations (judge, handle, data_generation, started_at, finished_at, status, failure_category)
            VALUES ($judge, $handle, $dataGeneration, $startedAt, NULL, $status, NULL);
            SELECT last_insert_rowid();
            """;
        command.Parameters.AddWithValue("$judge", account.Judge.ToString());
        command.Parameters.AddWithValue("$handle", account.Handle);
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
                await DeriveProblemsFromCodeforcesAsync(connection, transaction, submissions, fetchedAt, cancellationToken);
                break;
            case AtCoderSubmissionsPayload atcoderSubmissions:
                await ReplaceAtCoderSubmissionsAsync(connection, transaction, atcoderSubmissions, fetchedAt, cancellationToken);
                await DeriveProblemsFromAtCoderAsync(connection, transaction, atcoderSubmissions, fetchedAt, cancellationToken);
                break;
            case LuoguProfilePayload luoguProfile:
                await UpsertLuoguProfileAsync(connection, transaction, luoguProfile, fetchedAt, cancellationToken);
                break;
            case LuoguCollectionPayload luoguCollection:
                await UpsertLuoguCollectionAsync(connection, transaction, luoguCollection, fetchedAt, cancellationToken);
                break;
        }
    }

    private static async Task UpsertLuoguProfileAsync(
        SqliteConnection connection,
        SqliteTransaction transaction,
        LuoguProfilePayload payload,
        DateTimeOffset fetchedAt,
        CancellationToken cancellationToken)
    {
        using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            INSERT INTO luogu_payloads
                (handle, user_id, display_name, rating, submissions_count, contests_count, problems_count, fetched_at)
            VALUES ($handle, $userId, $displayName, $rating, 0, 0, 0, $fetchedAt)
            ON CONFLICT(handle) DO UPDATE SET
                user_id = excluded.user_id,
                display_name = excluded.display_name,
                rating = excluded.rating,
                fetched_at = excluded.fetched_at;
            """;
        command.Parameters.AddWithValue("$handle", payload.Handle);
        command.Parameters.AddWithValue("$userId", payload.UserId);
        command.Parameters.AddWithValue("$displayName", payload.DisplayName);
        AddNullableParameter(command, "$rating", payload.Rating);
        command.Parameters.AddWithValue("$fetchedAt", FormatTime(fetchedAt));
        await command.ExecuteNonQueryAsync(cancellationToken);
    }

    private static async Task UpsertLuoguCollectionAsync(
        SqliteConnection connection,
        SqliteTransaction transaction,
        LuoguCollectionPayload payload,
        DateTimeOffset fetchedAt,
        CancellationToken cancellationToken)
    {
        var submissionsCount = payload.Collection == "SUBMISSIONS" ? payload.Count : 0;
        var contestsCount = payload.Collection == "CONTESTS" ? payload.Count : 0;
        var problemsCount = payload.Collection == "PROBLEMSET" ? payload.Count : 0;
        using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            INSERT INTO luogu_payloads
                (handle, user_id, display_name, rating, submissions_count, contests_count, problems_count, fetched_at)
            VALUES ($handle, NULL, $handle, NULL, $submissionsCount, $contestsCount, $problemsCount, $fetchedAt)
            ON CONFLICT(handle) DO UPDATE SET
                submissions_count = CASE WHEN $collection = 'SUBMISSIONS' THEN $count ELSE submissions_count END,
                contests_count = CASE WHEN $collection = 'CONTESTS' THEN $count ELSE contests_count END,
                problems_count = CASE WHEN $collection = 'PROBLEMSET' THEN $count ELSE problems_count END,
                fetched_at = excluded.fetched_at;
            """;
        command.Parameters.AddWithValue("$handle", payload.Handle);
        command.Parameters.AddWithValue("$submissionsCount", submissionsCount);
        command.Parameters.AddWithValue("$contestsCount", contestsCount);
        command.Parameters.AddWithValue("$problemsCount", problemsCount);
        command.Parameters.AddWithValue("$collection", payload.Collection);
        command.Parameters.AddWithValue("$count", payload.Count);
        command.Parameters.AddWithValue("$fetchedAt", FormatTime(fetchedAt));
        await command.ExecuteNonQueryAsync(cancellationToken);
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

    /// <summary>
    /// Derives problem-library rows from persisted Codeforces submissions in the SAME
    /// transaction as the submission replace. Rows are keyed (judge, external_id) where
    /// external_id = "{contestId}{index}" (the Android identity rule). An accepted verdict
    /// marks the problem solved — sticky, never reset.
    /// </summary>
    private static async Task DeriveProblemsFromCodeforcesAsync(
        SqliteConnection connection,
        SqliteTransaction transaction,
        CodeforcesSubmissionsPayload payload,
        DateTimeOffset fetchedAt,
        CancellationToken cancellationToken)
    {
        var seen = new Dictionary<string, (string Title, bool Solved)>(StringComparer.Ordinal);
        foreach (var item in payload.Items)
        {
            var externalId = ProblemUrls.CodeforcesExternalId(item.ContestId, item.ProblemIndex);
            if (externalId is null)
            {
                continue;
            }

            var solved = IsAcceptedVerdict(item.Verdict);
            var title = string.IsNullOrWhiteSpace(item.ProblemName)
                ? externalId
                : item.ProblemName.Trim();
            // Newest submissions arrive first; keep the first-seen title, but OR the flag up.
            if (seen.TryGetValue(externalId, out var existing))
            {
                seen[externalId] = (existing.Title, existing.Solved || solved);
            }
            else
            {
                seen[externalId] = (title, solved);
            }
        }

        foreach (var (externalId, info) in seen)
        {
            var url = ProblemUrls.For(JudgeId.Codeforces, externalId);
            if (url is null)
            {
                continue;
            }

            await UpsertProblemRowAsync(
                connection,
                transaction,
                JudgeId.Codeforces,
                externalId,
                info.Title,
                url,
                info.Solved,
                fetchedAt,
                cancellationToken);
        }
    }

    /// <summary>
    /// Derives problem rows from persisted AtCoder submissions; external_id =
    /// "{contestId}+{problemId}" so ProblemUrls can rebuild the public tasks link.
    /// </summary>
    private static async Task DeriveProblemsFromAtCoderAsync(
        SqliteConnection connection,
        SqliteTransaction transaction,
        AtCoderSubmissionsPayload payload,
        DateTimeOffset fetchedAt,
        CancellationToken cancellationToken)
    {
        var seen = new Dictionary<string, (string Title, bool Solved)>(StringComparer.Ordinal);
        foreach (var item in payload.Items)
        {
            if (string.IsNullOrWhiteSpace(item.ContestId) || string.IsNullOrWhiteSpace(item.ProblemId))
            {
                continue;
            }

            var externalId = $"{item.ContestId.Trim()}+{item.ProblemId.Trim()}";
            var solved = IsAcceptedVerdict(item.Result);
            var title = item.ProblemId.Trim();
            if (seen.TryGetValue(externalId, out var existing))
            {
                seen[externalId] = (existing.Title, existing.Solved || solved);
            }
            else
            {
                seen[externalId] = (title, solved);
            }
        }

        foreach (var (externalId, info) in seen)
        {
            var url = ProblemUrls.For(JudgeId.AtCoder, externalId);
            if (url is null)
            {
                continue;
            }

            await UpsertProblemRowAsync(
                connection,
                transaction,
                JudgeId.AtCoder,
                externalId,
                info.Title,
                url,
                info.Solved,
                fetchedAt,
                cancellationToken);
        }
    }

    private static bool IsAcceptedVerdict(string? verdict) =>
        verdict is not null
        && (verdict.Equals("OK", StringComparison.OrdinalIgnoreCase)
            || verdict.Equals("AC", StringComparison.OrdinalIgnoreCase)
            || verdict.StartsWith("Accepted", StringComparison.OrdinalIgnoreCase));

    private static async Task UpsertProblemRowAsync(
        SqliteConnection connection,
        SqliteTransaction transaction,
        JudgeId judge,
        string externalId,
        string title,
        string url,
        bool solved,
        DateTimeOffset seenAt,
        CancellationToken cancellationToken)
    {
        using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            INSERT INTO problems (judge, external_id, title, url, solved, first_seen_at, last_seen_at)
            VALUES ($judge, $externalId, $title, $url, $solved, $seenAt, $seenAt)
            ON CONFLICT(judge, external_id) DO UPDATE SET
                solved = MAX(problems.solved, excluded.solved),
                last_seen_at = excluded.last_seen_at;
            """;
        command.Parameters.AddWithValue("$judge", judge.ToString());
        command.Parameters.AddWithValue("$externalId", externalId);
        command.Parameters.AddWithValue("$title", title);
        command.Parameters.AddWithValue("$url", url);
        command.Parameters.AddWithValue("$solved", solved ? 1 : 0);
        command.Parameters.AddWithValue("$seenAt", FormatTime(seenAt));
        await command.ExecuteNonQueryAsync(cancellationToken);
    }

    public async Task<IReadOnlyList<ProblemEntry>> GetProblemsAsync(
        JudgeId? judge,
        ProblemStatus? status,
        int limit,
        CancellationToken cancellationToken)
    {
        if (limit <= 0)
        {
            return Array.Empty<ProblemEntry>();
        }

        using var connection = connectionFactory.OpenConnection();
        using var command = connection.CreateCommand();
        var statusValue = status switch
        {
            ProblemStatus.Solved => 1,
            ProblemStatus.Attempted => 0,
            _ => (int?)null,
        };
        command.CommandText = """
            SELECT judge, external_id, title, url, solved, first_seen_at, last_seen_at
            FROM problems
            WHERE ($judge IS NULL OR judge = $judge)
              AND ($status IS NULL OR solved = $status)
            ORDER BY last_seen_at DESC, judge ASC, external_id ASC
            LIMIT $limit;
            """;
        command.Parameters.AddWithValue("$judge", judge?.ToString() ?? (object)DBNull.Value);
        command.Parameters.AddWithValue("$status", statusValue is null ? (object)DBNull.Value : statusValue.Value);
        command.Parameters.AddWithValue("$limit", limit);
        using var reader = await command.ExecuteReaderAsync(cancellationToken);
        var problems = new List<ProblemEntry>();
        while (await reader.ReadAsync(cancellationToken))
        {
            if (!JudgeIdParser.TryParse(reader.GetString(0), out var rowJudge))
            {
                continue;
            }

            problems.Add(new ProblemEntry(
                rowJudge,
                reader.GetString(1),
                reader.GetString(2),
                reader.GetString(3),
                reader.GetInt64(4) != 0,
                ParseTime(reader.GetString(5)),
                ParseTime(reader.GetString(6))));
        }

        return problems;
    }

    public async Task<IReadOnlyList<ReviewEntry>> GetDueReviewsAsync(
        JudgeId? judge,
        int limit,
        CancellationToken cancellationToken)
    {
        if (limit <= 0)
        {
            return Array.Empty<ReviewEntry>();
        }

        var today = ReviewIntervalLadder.DayIndexFor(clock?.UtcNow ?? DateTimeOffset.UtcNow);
        using var connection = connectionFactory.OpenConnection();
        using var command = connection.CreateCommand();
        command.CommandText = """
            SELECT reviews.judge, reviews.external_id, problems.title, problems.url,
                   reviews.stage, reviews.due_at, reviews.due_day_index,
                   reviews.last_result, reviews.last_reviewed_at
            FROM reviews
            LEFT JOIN problems
              ON problems.judge = reviews.judge AND problems.external_id = reviews.external_id
            WHERE due_day_index <= $today
              AND ($judge IS NULL OR reviews.judge = $judge)
            ORDER BY reviews.due_day_index ASC, reviews.judge ASC, reviews.external_id ASC
            LIMIT $limit;
            """;
        command.Parameters.AddWithValue("$today", today);
        command.Parameters.AddWithValue("$judge", judge?.ToString() ?? (object)DBNull.Value);
        command.Parameters.AddWithValue("$limit", limit);
        using var reader = await command.ExecuteReaderAsync(cancellationToken);
        var reviews = new List<ReviewEntry>();
        while (await reader.ReadAsync(cancellationToken))
        {
            if (!JudgeIdParser.TryParse(reader.GetString(0), out var rowJudge))
            {
                continue;
            }

            ReviewResult? lastResult = null;
            if (!reader.IsDBNull(7) && ReviewResultParser.TryParse(reader.GetString(7), out var parsed))
            {
                lastResult = parsed;
            }
            reviews.Add(new ReviewEntry(
                rowJudge,
                reader.GetString(1),
                reader.IsDBNull(2) ? null : reader.GetString(2),
                reader.IsDBNull(3) ? null : reader.GetString(3),
                reader.GetInt32(4),
                ParseTime(reader.GetString(5)),
                reader.GetString(6),
                lastResult,
                reader.IsDBNull(8) ? null : ParseTime(reader.GetString(8))));
        }

        return reviews;
    }

    public async Task<ReviewEntry> MarkReviewAsync(
        JudgeId judge,
        string externalId,
        ReviewResult result,
        DateTimeOffset now,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(externalId))
        {
            throw new ArgumentException("External id must be a non-blank value.", nameof(externalId));
        }

        var trimmedId = externalId.Trim();
        using var connection = connectionFactory.OpenConnection();
        using var transaction = connection.BeginTransaction();

        int stage;
        string? lastResult;
        DateTimeOffset? lastReviewedAt;
        using (var existingCommand = connection.CreateCommand())
        {
            existingCommand.Transaction = transaction;
            existingCommand.CommandText = "SELECT stage, last_result, last_reviewed_at FROM reviews WHERE judge = $judge AND external_id = $externalId";
            existingCommand.Parameters.AddWithValue("$judge", judge.ToString());
            existingCommand.Parameters.AddWithValue("$externalId", trimmedId);
            using var reader = await existingCommand.ExecuteReaderAsync(cancellationToken);
            if (await reader.ReadAsync(cancellationToken))
            {
                stage = reader.GetInt32(0);
                lastResult = reader.IsDBNull(1) ? null : reader.GetString(1);
                lastReviewedAt = reader.IsDBNull(2) ? null : ParseTime(reader.GetString(2));
            }
            else
            {
                stage = 0;
                lastResult = null;
                lastReviewedAt = null;
            }
        }

        var (newStage, dueAt, dueDayIndex) = ReviewIntervalLadder.ScheduleNext(result, stage, now);
        var nowText = FormatTime(now);

        using var upsertCommand = connection.CreateCommand();
        upsertCommand.Transaction = transaction;
        upsertCommand.CommandText = """
            INSERT INTO reviews (judge, external_id, stage, due_at, due_day_index, last_result, last_reviewed_at, created_at)
            VALUES ($judge, $externalId, $stage, $dueAt, $dueDayIndex, $lastResult, $lastReviewedAt, $createdAt)
            ON CONFLICT(judge, external_id) DO UPDATE SET
                stage = excluded.stage,
                due_at = excluded.due_at,
                due_day_index = excluded.due_day_index,
                last_result = excluded.last_result,
                last_reviewed_at = excluded.last_reviewed_at;
            """;
        upsertCommand.Parameters.AddWithValue("$judge", judge.ToString());
        upsertCommand.Parameters.AddWithValue("$externalId", trimmedId);
        upsertCommand.Parameters.AddWithValue("$stage", newStage);
        upsertCommand.Parameters.AddWithValue("$dueAt", FormatTime(dueAt));
        upsertCommand.Parameters.AddWithValue("$dueDayIndex", dueDayIndex);
        upsertCommand.Parameters.AddWithValue("$lastResult", ReviewResultParser.ToWire(result));
        upsertCommand.Parameters.AddWithValue("$lastReviewedAt", nowText);
        upsertCommand.Parameters.AddWithValue("$createdAt", nowText);
        await upsertCommand.ExecuteNonQueryAsync(cancellationToken);

        using var logCommand = connection.CreateCommand();
        logCommand.Transaction = transaction;
        logCommand.CommandText = """
            INSERT INTO review_log (judge, external_id, result, stage, completed_at, day_index)
            VALUES ($judge, $externalId, $result, $stage, $completedAt, $dayIndex);
            """;
        logCommand.Parameters.AddWithValue("$judge", judge.ToString());
        logCommand.Parameters.AddWithValue("$externalId", trimmedId);
        logCommand.Parameters.AddWithValue("$result", ReviewResultParser.ToWire(result));
        logCommand.Parameters.AddWithValue("$stage", newStage);
        logCommand.Parameters.AddWithValue("$completedAt", nowText);
        logCommand.Parameters.AddWithValue("$dayIndex", ReviewIntervalLadder.DayIndexFor(now));
        await logCommand.ExecuteNonQueryAsync(cancellationToken);

        await transaction.CommitAsync(cancellationToken);

        string? title = null;
        string? url = null;
        using (var problemCommand = connection.CreateCommand())
        {
            problemCommand.CommandText = "SELECT title, url FROM problems WHERE judge = $judge AND external_id = $externalId";
            problemCommand.Parameters.AddWithValue("$judge", judge.ToString());
            problemCommand.Parameters.AddWithValue("$externalId", trimmedId);
            using var problemReader = await problemCommand.ExecuteReaderAsync(cancellationToken);
            if (await problemReader.ReadAsync(cancellationToken))
            {
                title = problemReader.GetString(0);
                url = problemReader.GetString(1);
            }
        }

        return new ReviewEntry(judge, trimmedId, title, url, newStage, dueAt, dueDayIndex, result, now);
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
        using (var existingCommand = connection.CreateCommand())
        {
            existingCommand.Transaction = transaction;
            existingCommand.CommandText = "SELECT handle FROM accounts WHERE judge = $judge";
            existingCommand.Parameters.AddWithValue("$judge", account.Judge.ToString());
            var existingHandle = await existingCommand.ExecuteScalarAsync(cancellationToken) as string;
            if (existingHandle is not null
                && !JudgeIdentity.HandlesMatch(account.Judge, existingHandle, account.Handle))
            {
                foreach (var table in PayloadTables)
                {
                    using var clearCommand = connection.CreateCommand();
                    clearCommand.Transaction = transaction;
                    clearCommand.CommandText = $"DELETE FROM {table} WHERE handle = $handle";
                    clearCommand.Parameters.AddWithValue("$handle", existingHandle);
                    await clearCommand.ExecuteNonQueryAsync(cancellationToken);
                }
            }
        }

        using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            INSERT INTO accounts (judge, handle, enabled) VALUES ($judge, $handle, $enabled)
            ON CONFLICT(judge) DO UPDATE SET handle = excluded.handle, enabled = excluded.enabled;
            """;
        AddAccountParameters(command, account);
        await command.ExecuteNonQueryAsync(cancellationToken);
    }

    private static readonly string[] PayloadTables =
    [
        "codeforces_profiles",
        "codeforces_ratings",
        "codeforces_submissions",
        "atcoder_submissions",
        "luogu_payloads",
    ];

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

    private static SyncError? ParseError(string value) =>
        Enum.TryParse<SyncError>(value, ignoreCase: false, out var error) ? error : null;

    private sealed class OperationAccumulator(
        long id,
        JudgeAccount account,
        string dataGeneration,
        DateTimeOffset startedAt,
        DateTimeOffset? finishedAt,
        SyncOperationStatus status,
        SyncError? error)
    {
        public long Id { get; } = id;
        public JudgeAccount Account { get; } = account;
        public string DataGeneration { get; } = dataGeneration;
        public DateTimeOffset StartedAt { get; } = startedAt;
        public DateTimeOffset? FinishedAt { get; } = finishedAt;
        public SyncOperationStatus Status { get; } = status;
        public SyncError? Error { get; } = error;
        public List<SyncModuleOutcome> Modules { get; } = [];

        public SyncOperation ToOperation() => new(Id, Account, DataGeneration, StartedAt, FinishedAt, Status, Modules, Error);
    }
}
