using Microsoft.Data.Sqlite;
using OjNexus.Windows.Core.Contracts;
using OjNexus.Windows.Core.Domain;
using OjNexus.Windows.Core.Storage;

namespace OjNexus.Windows.Core.Tests;

public sealed class SqliteSyncStoreTests
{
    [Fact]
    public void Migrate_FreshDatabase_SetsSchemaVersionSeven()
    {
        using var database = new TemporaryDatabase();

        Assert.Equal("7", ReadSchemaVersion(database.DatabasePath));
    }

    [Fact]
    public void Migrate_RepeatsWithoutChangingSchemaVersionOrExistingData()
    {
        using var database = new TemporaryDatabase();
        ExecuteNonQuery(
            database.DatabasePath,
            "INSERT INTO accounts (judge, handle, enabled) VALUES ('Codeforces', 'tourist', 1)");

        SchemaMigrator.Migrate(database.ConnectionString);

        Assert.Equal("7", ReadSchemaVersion(database.DatabasePath));
        Assert.Equal("tourist", ReadAccountHandle(database.DatabasePath, "Codeforces"));
    }

    [Fact]
    public void Migrate_VersionZero_PreservesExistingDataAndAdvancesToVersionSeven()
    {
        using var database = new TemporaryDatabaseDirectory();
        ExecuteNonQuery(database.DatabasePath, VersionZeroSchemaSql);

        SchemaMigrator.Migrate(database.ConnectionString);

        Assert.Equal("7", ReadSchemaVersion(database.DatabasePath));
        Assert.Equal("legacy", ReadAccountHandle(database.DatabasePath, "Codeforces"));
        Assert.Equal(1L, CountModules(database.DatabasePath, 7));
        Assert.Equal("Success", ReadString(database.DatabasePath, "SELECT status FROM sync_operations WHERE id = 7"));
    }

    [Fact]
    public void Migrate_VersionOne_AddsPayloadTablesAndPreservesLedger()
    {
        using var database = new TemporaryDatabaseDirectory();
        ExecuteNonQuery(
            database.DatabasePath,
            VersionZeroSchemaSql
                .Replace("('schema_version', '0')", "('schema_version', '1')", StringComparison.Ordinal));

        SchemaMigrator.Migrate(database.ConnectionString);

        Assert.Equal("7", ReadSchemaVersion(database.DatabasePath));
        Assert.Equal("legacy", ReadAccountHandle(database.DatabasePath, "Codeforces"));
        Assert.True(TableExists(database.DatabasePath, "codeforces_profiles"));
        Assert.True(TableExists(database.DatabasePath, "luogu_payloads"));
        Assert.Equal("Success", ReadString(database.DatabasePath, "SELECT status FROM sync_operations WHERE id = 7"));
    }

    [Fact]
    public void Migrate_VersionThree_AddsLuoguPayloadTableAndPreservesExistingPayloadTables()
    {
        using var database = new TemporaryDatabase();
        ExecuteNonQuery(database.DatabasePath, "UPDATE schema_metadata SET value = '3' WHERE key = 'schema_version'; DROP TABLE luogu_payloads;");

        SchemaMigrator.Migrate(database.ConnectionString);

        Assert.Equal("7", ReadSchemaVersion(database.DatabasePath));
        Assert.True(TableExists(database.DatabasePath, "atcoder_submissions"));
        Assert.True(TableExists(database.DatabasePath, "luogu_payloads"));
    }

    [Fact]
    public void Migrate_VersionFour_AddsOperationHandleColumn()
    {
        using var database = new TemporaryDatabase();
        ExecuteNonQuery(
            database.DatabasePath,
            "ALTER TABLE sync_operations DROP COLUMN handle; UPDATE schema_metadata SET value = '4' WHERE key = 'schema_version';");

        SchemaMigrator.Migrate(database.ConnectionString);

        Assert.Equal("7", ReadSchemaVersion(database.DatabasePath));
        Assert.True(ColumnExists(database.DatabasePath, "sync_operations", "handle"));
    }

    [Fact]
    public async Task Store_IgnoresMalformedPersistedAccountsAndOperations()
    {
        using var database = new TemporaryDatabase();
        ExecuteNonQuery(
            database.DatabasePath,
            "INSERT INTO accounts (judge, handle, enabled) VALUES " +
            "('Codeforces', 'tourist', 1), ('AtCoder', 'tourist.user', 1);" +
            "INSERT INTO sync_operations (judge, handle, data_generation, started_at, finished_at, status, failure_category) " +
            "VALUES ('Codeforces', 'tourist&handle=other', 'generation-1', '2026-09-14T00:00:00.0000000+00:00', " +
            "'2026-09-14T00:00:01.0000000+00:00', 'Success', NULL);");

        var accounts = await database.Store.GetAccountsAsync(CancellationToken.None);
        var operations = await database.Store.GetRecentOperationsAsync(null, 10, CancellationToken.None);

        var account = Assert.Single(accounts);
        Assert.Equal(JudgeId.Codeforces, account.Judge);
        Assert.Equal("tourist", account.Handle);
        Assert.Empty(operations);
    }

    [Fact]
    public async Task Store_ChangingHandleClearsOldPayloadsButRetainsSyncHistory()
    {
        using var database = new TemporaryDatabase();
        var oldAccount = JudgeAccount.Create(JudgeId.Codeforces, "old-handle");
        var newAccount = JudgeAccount.Create(JudgeId.Codeforces, "new-handle");
        await database.Store.UpsertAccountAsync(oldAccount, CancellationToken.None);

        var startedAt = DateTimeOffset.Parse("2026-09-14T01:02:03+00:00");
        var operationId = await database.Store.OpenOperationAsync(oldAccount, "old-generation", startedAt, CancellationToken.None);
        await database.Store.CloseOperationAsync(operationId, SyncOperationStatus.Success, null, startedAt, CancellationToken.None);

        ExecuteNonQuery(
            database.DatabasePath,
            """
            INSERT INTO codeforces_profiles(handle, rating, rank, max_rating, max_rank, fetched_at)
            VALUES ('old-handle', 1200, 'pupil', 1300, 'expert', '2026-09-14T01:02:03.0000000+00:00');
            INSERT INTO codeforces_ratings(handle, contest_id, contest_name, rank, rating_update_time_seconds, old_rating, new_rating, fetched_at)
            VALUES ('old-handle', 1, 'Contest', 1, 1700000000, 1100, 1200, '2026-09-14T01:02:03.0000000+00:00');
            INSERT INTO codeforces_submissions(handle, id, contest_id, problem_index, problem_name, verdict, programming_language, passed_test_count, time_consumed_millis, memory_consumed_bytes, creation_time_seconds, fetched_at)
            VALUES ('old-handle', 1, 1, 'A', 'Problem', 'OK', 'GNU C++17', 10, 10, 1024, 1700000000, '2026-09-14T01:02:03.0000000+00:00');
            INSERT INTO atcoder_submissions(handle, id, epoch_second, problem_id, contest_id, language, point, source_length, result, execution_time_millis, fetched_at)
            VALUES ('old-handle', 1, 1700000000, 'abc001_a', 'abc001', 'C++', 100, 42, 'AC', 10, '2026-09-14T01:02:03.0000000+00:00');
            INSERT INTO luogu_payloads(handle, user_id, display_name, rating, submissions_count, contests_count, problems_count, fetched_at)
            VALUES ('old-handle', 2, 'Old Handle', 1200, 10, 2, 20, '2026-09-14T01:02:03.0000000+00:00');
            """);

        await database.Store.UpsertAccountAsync(newAccount, CancellationToken.None);

        Assert.Equal("new-handle", Assert.Single(await database.Store.GetAccountsAsync(CancellationToken.None)).Handle);
        var operations = await database.Store.GetRecentOperationsAsync(JudgeId.Codeforces, 10, CancellationToken.None);
        Assert.Single(operations);
        Assert.Equal(0L, CountRowsForHandle(database.DatabasePath, "old-handle"));
    }

    [Fact]
    public void Migrate_MalformedVersionZero_RollsBackWithoutChangingVersionOrData()
    {
        using var database = new TemporaryDatabaseDirectory();
        ExecuteNonQuery(
            database.DatabasePath,
            """
            CREATE TABLE schema_metadata (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL);
            CREATE TABLE accounts (judge TEXT NOT NULL PRIMARY KEY, handle TEXT NOT NULL);
            INSERT INTO schema_metadata (key, value) VALUES ('schema_version', '0');
            INSERT INTO accounts (judge, handle) VALUES ('Codeforces', 'legacy');
            """);

        Assert.Throws<InvalidOperationException>(() => SchemaMigrator.Migrate(database.ConnectionString));

        Assert.Equal("0", ReadSchemaVersion(database.DatabasePath));
        Assert.Equal("legacy", ReadAccountHandle(database.DatabasePath, "Codeforces"));
        Assert.False(TableExists(database.DatabasePath, "sync_operations"));
    }

    [Fact]
    public void Migrate_RejectsVersionOneSchemaWithUnexpectedNotNullConstraint()
    {
        using var database = new TemporaryDatabaseDirectory();
        ExecuteNonQuery(
            database.DatabasePath,
            VersionZeroSchemaSql
                .Replace("finished_at TEXT NULL", "finished_at TEXT NOT NULL", StringComparison.Ordinal)
                .Replace("('schema_version', '0')", "('schema_version', '1')", StringComparison.Ordinal));

        Assert.Throws<InvalidOperationException>(() => SchemaMigrator.Migrate(database.ConnectionString));
        Assert.Equal("1", ReadSchemaVersion(database.DatabasePath));
    }

    [Fact]
    public void Migrate_RejectsVersionOneSchemaMissingRequiredNotNullConstraint()
    {
        using var database = new TemporaryDatabaseDirectory();
        ExecuteNonQuery(
            database.DatabasePath,
            VersionZeroSchemaSql
                .Replace("started_at TEXT NOT NULL", "started_at TEXT NULL", StringComparison.Ordinal)
                .Replace("('schema_version', '0')", "('schema_version', '1')", StringComparison.Ordinal));

        Assert.Throws<InvalidOperationException>(() => SchemaMigrator.Migrate(database.ConnectionString));
        Assert.Equal("1", ReadSchemaVersion(database.DatabasePath));
    }

    [Fact]
    public void Migrate_RejectsVersionOneSchemaWithUnexpectedCascadeOnAccountForeignKey()
    {
        using var database = new TemporaryDatabaseDirectory();
        ExecuteNonQuery(
            database.DatabasePath,
            VersionZeroSchemaSql
                .Replace("FOREIGN KEY (judge) REFERENCES accounts(judge)", "FOREIGN KEY (judge) REFERENCES accounts(judge) ON DELETE CASCADE", StringComparison.Ordinal)
                .Replace("('schema_version', '0')", "('schema_version', '1')", StringComparison.Ordinal));

        Assert.Throws<InvalidOperationException>(() => SchemaMigrator.Migrate(database.ConnectionString));
        Assert.Equal("1", ReadSchemaVersion(database.DatabasePath));
    }

    [Fact]
    public void Migrate_RejectsVersionOneSchemaMissingModuleCascade()
    {
        using var database = new TemporaryDatabaseDirectory();
        ExecuteNonQuery(
            database.DatabasePath,
            VersionZeroSchemaSql
                .Replace("FOREIGN KEY (operation_id) REFERENCES sync_operations(id) ON DELETE CASCADE", "FOREIGN KEY (operation_id) REFERENCES sync_operations(id)", StringComparison.Ordinal)
                .Replace("('schema_version', '0')", "('schema_version', '1')", StringComparison.Ordinal));

        Assert.Throws<InvalidOperationException>(() => SchemaMigrator.Migrate(database.ConnectionString));
        Assert.Equal("1", ReadSchemaVersion(database.DatabasePath));
    }

    [Fact]
    public void Migrate_RejectsDatabaseVersionNewerThanSupportedWithoutChangingIt()
    {
        using var database = new TemporaryDatabaseDirectory();
        ExecuteNonQuery(database.DatabasePath, "CREATE TABLE schema_metadata (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)");
        ExecuteNonQuery(database.DatabasePath, "INSERT INTO schema_metadata (key, value) VALUES ('schema_version', '7')");

        Assert.Throws<InvalidOperationException>(() => SchemaMigrator.Migrate(database.ConnectionString));

        Assert.Equal("7", ReadSchemaVersion(database.DatabasePath));
        Assert.False(TableExists(database.DatabasePath, "accounts"));
    }

    [Fact]
    public void Migrate_RejectsIncompleteCurrentVersionSchema()
    {
        using var database = new TemporaryDatabaseDirectory();
        ExecuteNonQuery(database.DatabasePath, "CREATE TABLE schema_metadata (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)");
        ExecuteNonQuery(database.DatabasePath, "INSERT INTO schema_metadata (key, value) VALUES ('schema_version', '1')");

        Assert.Throws<InvalidOperationException>(() => SchemaMigrator.Migrate(database.ConnectionString));

        Assert.False(TableExists(database.DatabasePath, "accounts"));
    }

    [Fact]
    public void Migrate_CreatesOnlyTheRequiredSchemaTables()
    {
        using var database = new TemporaryDatabase();

        using var connection = new SqliteConnection($"Data Source={database.DatabasePath};Pooling=False");
        connection.Open();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name";
        using var reader = command.ExecuteReader();
        var tableNames = new List<string>();
        while (reader.Read())
        {
            tableNames.Add(reader.GetString(0));
        }

        Assert.Equal(new[] { "accounts", "atcoder_submissions", "codeforces_profiles", "codeforces_ratings", "codeforces_submissions", "luogu_payloads", "problems", "review_log", "reviews", "schema_metadata", "sync_modules", "sync_operations" }, tableNames);
    }

    [Fact]
    public async Task Store_RoundTripsAccountOperationAndModules()
    {
        using var database = new TemporaryDatabase();
        var account = JudgeAccount.Create(JudgeId.AtCoder, " tourist ");
        var startedAt = DateTimeOffset.Parse("2026-09-07T01:02:03+00:00");
        var completedAt = startedAt.AddMinutes(1);

        await database.Store.UpsertAccountAsync(account, CancellationToken.None);
        var operationId = await database.Store.OpenOperationAsync(account, "generation-9", startedAt, CancellationToken.None);
        await database.Store.AppendModuleAsync(operationId, new SyncModuleOutcome("PROFILE", SyncOperationStatus.Success, 2, 1, 1, null), completedAt, CancellationToken.None);
        await database.Store.AppendModuleAsync(operationId, new SyncModuleOutcome("SUBMISSIONS", SyncOperationStatus.Error, 3, 1, 0, "Api"), completedAt, CancellationToken.None);
        await database.Store.CloseOperationAsync(operationId, SyncOperationStatus.Partial, SyncError.Network, completedAt, CancellationToken.None);

        var accounts = await database.Store.GetAccountsAsync(CancellationToken.None);
        var operations = await database.Store.GetRecentOperationsAsync(JudgeId.AtCoder, 10, CancellationToken.None);

        Assert.Equal(new[] { account }, accounts);
        var operation = Assert.Single(operations);
        Assert.Equal(operationId, operation.Id);
        Assert.Equal("generation-9", operation.DataGeneration);
        Assert.Equal(startedAt, operation.StartedAt);
        Assert.Equal(completedAt, operation.FinishedAt);
        Assert.Equal(SyncOperationStatus.Partial, operation.Status);
        Assert.Equal(SyncError.Network, operation.Error);
        Assert.Collection(
            operation.Modules,
            profile => Assert.Equal(new SyncModuleOutcome("PROFILE", SyncOperationStatus.Success, 2, 1, 1, null), profile),
            submissions => Assert.Equal(new SyncModuleOutcome("SUBMISSIONS", SyncOperationStatus.Error, 3, 1, 0, "Api"), submissions));
        Assert.Equal("Network", ReadFailureCategory(database.DatabasePath, operationId));
    }

    [Fact]
    public async Task Store_PreservesOperationHandleWhenCurrentAccountHandleChanges()
    {
        using var database = new TemporaryDatabase();
        var originalAccount = JudgeAccount.Create(JudgeId.Codeforces, "old-handle");
        var changedAccount = JudgeAccount.Create(JudgeId.Codeforces, "new-handle");
        var startedAt = DateTimeOffset.Parse("2026-09-08T02:00:00+00:00");

        await database.Store.UpsertAccountAsync(originalAccount, CancellationToken.None);
        var operationId = await database.Store.OpenOperationAsync(
            originalAccount, "generation-old", startedAt, CancellationToken.None);
        await database.Store.CloseOperationAsync(
            operationId, SyncOperationStatus.Success, null, startedAt, CancellationToken.None);
        await database.Store.UpsertAccountAsync(changedAccount, CancellationToken.None);

        var operation = Assert.Single(
            await database.Store.GetRecentOperationsAsync(JudgeId.Codeforces, 10, CancellationToken.None));

        Assert.Equal("old-handle", operation.Account.Handle);
    }

    [Fact]
    public async Task Store_OrdersRecentOperationsByStartedAtThenIdDescending()
    {
        using var database = new TemporaryDatabase();
        var account = JudgeAccount.Create(JudgeId.Luogu, "uid:2");
        var startedAt = DateTimeOffset.Parse("2026-09-07T02:00:00+00:00");

        var firstId = await database.Store.OpenOperationAsync(account, "first", startedAt, CancellationToken.None);
        var secondId = await database.Store.OpenOperationAsync(account, "second", startedAt, CancellationToken.None);

        var operations = await database.Store.GetRecentOperationsAsync(JudgeId.Luogu, 10, CancellationToken.None);

        Assert.Equal(new[] { secondId, firstId }, operations.Select(operation => operation.Id));
    }

    [Fact]
    public async Task Store_AppendingDuplicateModuleStage_MovesLatestReplacementToEndWhenTimestampsMatch()
    {
        using var database = new TemporaryDatabase();
        var account = JudgeAccount.Create(JudgeId.Codeforces, "tourist");
        var startedAt = DateTimeOffset.Parse("2026-09-08T01:02:03+00:00");
        var operationId = await database.Store.OpenOperationAsync(account, "generation-4", startedAt, CancellationToken.None);

        await database.Store.AppendModuleAsync(operationId, new SyncModuleOutcome("A", SyncOperationStatus.Error, 1, 0, 0, "Network"), startedAt, CancellationToken.None);
        await database.Store.AppendModuleAsync(operationId, new SyncModuleOutcome("B", SyncOperationStatus.Success, 1, 1, 0, null), startedAt, CancellationToken.None);
        await database.Store.AppendModuleAsync(operationId, new SyncModuleOutcome("A", SyncOperationStatus.Success, 2, 2, 0, null), startedAt, CancellationToken.None);

        var operation = Assert.Single(await database.Store.GetRecentOperationsAsync(JudgeId.Codeforces, 10, CancellationToken.None));
        Assert.Collection(
            operation.Modules,
            first => Assert.Equal(new SyncModuleOutcome("B", SyncOperationStatus.Success, 1, 1, 0, null), first),
            second => Assert.Equal(new SyncModuleOutcome("A", SyncOperationStatus.Success, 2, 2, 0, null), second));
    }

    [Fact]
    public async Task Store_AppendingUntrustedFailureTypes_PersistsOnlyAllowedCategories()
    {
        const string sensitiveFailureType = "authorization=Bearer credential-value; body=secret HTTP body";
        using var database = new TemporaryDatabase();
        var account = JudgeAccount.Create(JudgeId.Codeforces, "tourist");
        var operationId = await database.Store.OpenOperationAsync(account, "generation-4", DateTimeOffset.Parse("2026-09-08T01:02:03+00:00"), CancellationToken.None);

        await database.Store.AppendModuleAsync(operationId, new SyncModuleOutcome("RAW", SyncOperationStatus.Error, 1, 0, 0, sensitiveFailureType), DateTimeOffset.Parse("2026-09-08T01:02:03+00:00"), CancellationToken.None);
        await database.Store.AppendModuleAsync(operationId, new SyncModuleOutcome("NUMERIC", SyncOperationStatus.Error, 1, 0, 0, "401"), DateTimeOffset.Parse("2026-09-08T01:02:03+00:00"), CancellationToken.None);

        var operation = Assert.Single(await database.Store.GetRecentOperationsAsync(JudgeId.Codeforces, 10, CancellationToken.None));

        Assert.All(operation.Modules, module => Assert.Equal("Api", module.FailureType));
        Assert.DoesNotContain(sensitiveFailureType, ReadAllModuleFailureTypes(database.DatabasePath), StringComparison.Ordinal);
        Assert.DoesNotContain("401", ReadAllModuleFailureTypes(database.DatabasePath), StringComparison.Ordinal);
    }

    [Fact]
    public async Task Store_RetainsTwentyCompletedOperationsAndAllActiveOperationsPerJudge()
    {
        using var database = new TemporaryDatabase();
        var account = JudgeAccount.Create(JudgeId.Codeforces, "tourist");
        var otherJudgeAccount = JudgeAccount.Create(JudgeId.AtCoder, "second");
        var startedAt = DateTimeOffset.Parse("2026-09-07T03:00:00+00:00");
        var completedIds = new List<long>();

        for (var index = 0; index < 25; index++)
        {
            var operationId = await database.Store.OpenOperationAsync(account, $"completed-{index}", startedAt.AddMinutes(index), CancellationToken.None);
            completedIds.Add(operationId);
            await database.Store.AppendModuleAsync(operationId, new SyncModuleOutcome("PROFILE", SyncOperationStatus.Success, 1, 1, 0, null), startedAt.AddMinutes(index + 1), CancellationToken.None);
            await database.Store.CloseOperationAsync(operationId, SyncOperationStatus.Success, null, startedAt.AddMinutes(index + 1), CancellationToken.None);
        }

        var activeIds = new List<long>();
        for (var index = 0; index < 5; index++)
        {
            activeIds.Add(await database.Store.OpenOperationAsync(account, $"active-{index}", startedAt.AddDays(1).AddMinutes(index), CancellationToken.None));
        }

        for (var index = 0; index < 25; index++)
        {
            var operationId = await database.Store.OpenOperationAsync(otherJudgeAccount, $"other-{index}", startedAt.AddMinutes(index), CancellationToken.None);
            await database.Store.CloseOperationAsync(operationId, SyncOperationStatus.Success, null, startedAt.AddMinutes(index + 1), CancellationToken.None);
        }

        var operations = await database.Store.GetRecentOperationsAsync(JudgeId.Codeforces, 100, CancellationToken.None);
        var otherJudgeOperations = await database.Store.GetRecentOperationsAsync(JudgeId.AtCoder, 100, CancellationToken.None);

        Assert.Equal(25, operations.Count);
        Assert.DoesNotContain(operations, operation => operation.Id == completedIds[0]);
        Assert.Equal(activeIds.OrderByDescending(id => id), operations.Take(5).Select(operation => operation.Id));
        Assert.Equal(20, operations.Count(operation => operation.FinishedAt is not null));
        Assert.Equal(5, operations.Count(operation => operation.FinishedAt is null));
        Assert.Equal(0L, CountModules(database.DatabasePath, completedIds[0]));
        Assert.Equal(20, otherJudgeOperations.Count);
        Assert.All(otherJudgeOperations, operation => Assert.Equal(JudgeId.AtCoder, operation.Account.Judge));
    }

    [Fact]
    public void Migrate_VersionSix_AddsReviewTables()
    {
        using var database = new TemporaryDatabase();
        ExecuteNonQuery(database.DatabasePath, "DROP TABLE reviews; DROP TABLE review_log; UPDATE schema_metadata SET value = '6' WHERE key = 'schema_version';");

        SchemaMigrator.Migrate(database.ConnectionString);

        Assert.Equal("7", ReadSchemaVersion(database.DatabasePath));
        Assert.True(TableExists(database.DatabasePath, "reviews"));
        Assert.True(TableExists(database.DatabasePath, "review_log"));
    }

    [Fact]
    public async Task Store_MarkReviewThenDueListUsesSharedLadderSemantics()
    {
        var now = DateTimeOffset.Parse("2026-10-18T09:00:00+00:00");
        using var database = new TemporaryDatabase(new FixedClock(now));

        var first = await database.Store.MarkReviewAsync(JudgeId.Codeforces, "2134/C", ReviewResult.Fail, now, CancellationToken.None);
        Assert.Equal(0, first.Stage);
        Assert.Equal(ReviewResult.Fail, first.LastResult);
        Assert.Equal(now.AddDays(1).ToLocalTime().ToString("yyyy-MM-dd"), first.DueDayIndex);

        // A FAIL schedules the next day, so nothing is due today.
        Assert.Empty(await database.Store.GetDueReviewsAsync(null, 10, CancellationToken.None));

        var pass = await database.Store.MarkReviewAsync(JudgeId.Codeforces, "2134/C", ReviewResult.Pass, now.AddDays(1), CancellationToken.None);
        Assert.Equal(1, pass.Stage);
        Assert.Equal(now.AddDays(1 + 3).ToLocalTime().ToString("yyyy-MM-dd"), pass.DueDayIndex);

        var hard = await database.Store.MarkReviewAsync(JudgeId.Codeforces, "2134/C", ReviewResult.Hard, now.AddDays(5), CancellationToken.None);
        Assert.Equal(1, hard.Stage);
        Assert.Equal(now.AddDays(5 + 1).ToLocalTime().ToString("yyyy-MM-dd"), hard.DueDayIndex);

        var skip = await database.Store.MarkReviewAsync(JudgeId.Codeforces, "2134/C", ReviewResult.Skip, now.AddDays(7), CancellationToken.None);
        Assert.Equal(1, skip.Stage);
        Assert.Equal(now.AddDays(7 + 3).ToLocalTime().ToString("yyyy-MM-dd"), skip.DueDayIndex);

        // Not due until the scheduled day arrives.
        Assert.Empty(await database.Store.GetDueReviewsAsync(null, 10, CancellationToken.None));
        ExecuteNonQuery(database.DatabasePath, "UPDATE reviews SET due_day_index = '2026-10-01' WHERE external_id = '2134/C'");
        Assert.Single(await database.Store.GetDueReviewsAsync(null, 10, CancellationToken.None));
        Assert.Empty(await database.Store.GetDueReviewsAsync(JudgeId.AtCoder, 10, CancellationToken.None));
    }

    [Fact]
    public async Task Store_GetDueReviewsUsesClockAndLocalDayKey()
    {
        var now = DateTimeOffset.Parse("2026-10-18T09:00:00+00:00");
        var clock = new FixedClock(now);
        using var database = new TemporaryDatabase(clock);

        var marked = await database.Store.MarkReviewAsync(
            JudgeId.Codeforces,
            "2134/C",
            ReviewResult.Fail,
            clock.UtcNow,
            CancellationToken.None);

        Assert.Equal(now.ToLocalTime().AddDays(1).ToString("yyyy-MM-dd"), marked.DueDayIndex);
        Assert.Empty(await database.Store.GetDueReviewsAsync(null, 10, CancellationToken.None));

        clock.UtcNow = now.AddDays(1);
        Assert.Equal(marked.DueDayIndex, ReviewIntervalLadder.DayIndexFor(clock.UtcNow));
        Assert.Single(await database.Store.GetDueReviewsAsync(null, 10, CancellationToken.None));

        clock.UtcNow = now.AddDays(2);
        Assert.Single(await database.Store.GetDueReviewsAsync(null, 10, CancellationToken.None));
    }

    [Fact]
    public async Task Store_MarkReviewCapsLadderAndJoinsProblemMetadata()
    {
        using var database = new TemporaryDatabase();
        var now = DateTimeOffset.Parse("2026-10-18T09:00:00+00:00");
        ExecuteNonQuery(
            database.DatabasePath,
            "INSERT INTO problems (judge, external_id, title, url, solved, first_seen_at, last_seen_at) VALUES ('AtCoder', 'abc100+abc100_a', 'A + B Problem 1', 'https://atcoder.jp/contests/abc100/tasks/abc100_a', 1, '2026-10-01T00:00:00.0000000+00:00', '2026-10-01T00:00:00.0000000+00:00');");

        var stage = 0;
        var at = now;
        ReviewEntry entry = null!;
        foreach (var _ in Enumerable.Range(0, 7))
        {
            entry = await database.Store.MarkReviewAsync(JudgeId.AtCoder, "abc100+abc100_a", ReviewResult.Pass, at, CancellationToken.None);
            stage = entry.Stage;
            at = at.AddDays(90);
        }

        Assert.Equal(ReviewIntervalLadder.MaxStageIndex, stage);
        Assert.Equal(ReviewIntervalLadder.MaxStageIndex, entry.Stage);
        Assert.Equal("A + B Problem 1", entry.Title);
        Assert.Equal("https://atcoder.jp/contests/abc100/tasks/abc100_a", entry.Url);

        Assert.Equal(7L, ReadScalarLong(database.DatabasePath, "SELECT COUNT(*) FROM review_log WHERE judge = 'AtCoder'"));
        Assert.Equal(1L, ReadScalarLong(database.DatabasePath, "SELECT COUNT(*) FROM reviews"));
    }

    [Fact]
    public async Task Store_MarkReviewAcceptsUnknownIdentityWithoutProblemRow()
    {
        using var database = new TemporaryDatabase();
        var entry = await database.Store.MarkReviewAsync(JudgeId.Luogu, "P1001", ReviewResult.Pass, DateTimeOffset.Parse("2026-10-18T09:00:00+00:00"), CancellationToken.None);

        Assert.Equal("P1001", entry.ExternalId);
        Assert.Null(entry.Title);
        Assert.Null(entry.Url);
        Assert.Equal(1, entry.Stage);
    }

    [Fact]
    public async Task Store_DueReviewsOrderAndLimitHonest()
    {
        using var database = new TemporaryDatabase();
        var now = DateTimeOffset.Parse("2026-10-18T09:00:00+00:00");
        await database.Store.MarkReviewAsync(JudgeId.Codeforces, "2134/D", ReviewResult.Fail, now, CancellationToken.None);
        await database.Store.MarkReviewAsync(JudgeId.AtCoder, "abc100+abc100_b", ReviewResult.Fail, now, CancellationToken.None);
        await database.Store.MarkReviewAsync(JudgeId.Codeforces, "2134/C", ReviewResult.Fail, now, CancellationToken.None);
        ExecuteNonQuery(database.DatabasePath, "UPDATE reviews SET due_day_index = '2026-10-01'");

        var due = await database.Store.GetDueReviewsAsync(null, 2, CancellationToken.None);

        // ORDER BY due_day_index ASC, judge ASC, external_id ASC — AtCoder sorts before Codeforces.
        Assert.Equal(2, due.Count);
        Assert.Equal(new[] { "abc100+abc100_b", "2134/C" }, due.Select(row => row.ExternalId).ToArray());
        Assert.All(due, row => Assert.Equal("2026-10-01", row.DueDayIndex));
    }

    [Fact]
    public async Task Store_DerivesProblemRowsFromCodeforcesSubmissionsWithStickySolved()
    {
        using var database = new TemporaryDatabase();
        var account = JudgeAccount.Create(JudgeId.Codeforces, "tourist");
        var timestamp = DateTimeOffset.Parse("2026-09-12T01:02:03+00:00");
        var operationId = await database.Store.OpenOperationAsync(account, "generation-1", timestamp, CancellationToken.None);

        await database.Store.AppendModuleAsync(
            operationId,
            new SyncModuleOutcome(
                "SUBMISSIONS",
                SyncOperationStatus.Success,
                2,
                2,
                0,
                null,
                new CodeforcesSubmissionsPayload("tourist",
                [
                    new CodeforcesSubmission(1, 2134, "C", "Watermelon", "WRONG_ANSWER", "GNU C++17", 0, 0, 262144, 1_700_000_000),
                ])),
            timestamp,
            CancellationToken.None);
        var laterTimestamp = timestamp.AddMinutes(1);
        var laterOperation = await database.Store.OpenOperationAsync(account, "generation-2", laterTimestamp, CancellationToken.None);
        await database.Store.AppendModuleAsync(
            laterOperation,
            new SyncModuleOutcome(
                "SUBMISSIONS",
                SyncOperationStatus.Success,
                1,
                1,
                0,
                null,
                new CodeforcesSubmissionsPayload("tourist",
                [
                    new CodeforcesSubmission(2, 2134, "C", "Watermelon", "OK", "GNU C++17", 5, 15, 262144, 1_700_000_100),
                    new CodeforcesSubmission(3, 2134, "D", "Theatre Square", "OK", "GNU C++17", 5, 15, 262144, 1_700_000_200),
                ])),
            laterTimestamp,
            CancellationToken.None);

        var problems = await database.Store.GetProblemsAsync(JudgeId.Codeforces, null, 50, CancellationToken.None);

        Assert.Equal(2, problems.Count);
        var watermelon = problems.Single(problem => problem.ExternalId == "2134C");
        Assert.True(watermelon.Solved);
        Assert.Equal("https://codeforces.com/contest/2134/problem/C", watermelon.Url);
        Assert.Equal(timestamp, watermelon.FirstSeenAt);
        Assert.Equal(laterTimestamp, watermelon.LastSeenAt);
        var theatreSquare = problems.Single(problem => problem.ExternalId == "2134D");
        Assert.True(theatreSquare.Solved);
    }

    [Fact]
    public async Task Store_DerivesAtCoderProblemRowsWithContestPlusProblemIdentity()
    {
        using var database = new TemporaryDatabase();
        var account = JudgeAccount.Create(JudgeId.AtCoder, "chokudai");
        var timestamp = DateTimeOffset.Parse("2026-09-12T02:00:00+00:00");
        var operationId = await database.Store.OpenOperationAsync(account, "generation-1", timestamp, CancellationToken.None);

        await database.Store.AppendModuleAsync(
            operationId,
            new SyncModuleOutcome(
                "SUBMISSIONS",
                SyncOperationStatus.Success,
                2,
                2,
                0,
                null,
                new AtCoderSubmissionsPayload("chokudai",
                [
                    new AtCoderSubmission(11, 100, "abc100_a", "abc100", "C++", 100, 10, "AC", 1),
                    new AtCoderSubmission(12, 200, "abc100_b", "abc100", "C++", 0, 10, "WA", 1),
                ])),
            timestamp,
            CancellationToken.None);

        var problems = await database.Store.GetProblemsAsync(JudgeId.AtCoder, null, 50, CancellationToken.None);

        Assert.Equal(2, problems.Count);
        var solved = problems.Single(problem => problem.ExternalId == "abc100+abc100_a");
        Assert.True(solved.Solved);
        Assert.Equal("https://atcoder.jp/contests/abc100/tasks/abc100_a", solved.Url);
        Assert.Equal("abc100_a", solved.Title);
        var attempted = problems.Single(problem => problem.ExternalId == "abc100+abc100_b");
        Assert.False(attempted.Solved);
    }

    [Fact]
    public async Task Store_GetProblemsAsync_FiltersByJudgeStatusAndLimit()
    {
        using var database = new TemporaryDatabase();
        var cfAccount = JudgeAccount.Create(JudgeId.Codeforces, "tourist");
        var atAccount = JudgeAccount.Create(JudgeId.AtCoder, "chokudai");
        var timestamp = DateTimeOffset.Parse("2026-09-12T03:00:00+00:00");
        var cfOperation = await database.Store.OpenOperationAsync(cfAccount, "generation-1", timestamp, CancellationToken.None);
        await database.Store.AppendModuleAsync(
            cfOperation,
            new SyncModuleOutcome(
                "SUBMISSIONS",
                SyncOperationStatus.Success,
                2,
                2,
                0,
                null,
                new CodeforcesSubmissionsPayload("tourist",
                [
                    new CodeforcesSubmission(1, 2134, "C", "Watermelon", "OK", "GNU C++17", 5, 15, 262144, 1_700_000_000),
                    new CodeforcesSubmission(2, 2134, "D", "Theatre Square", "WA", "GNU C++17", 0, 0, 262144, 1_700_000_100),
                ])),
            timestamp,
            CancellationToken.None);
        var atOperation = await database.Store.OpenOperationAsync(atAccount, "generation-2", timestamp, CancellationToken.None);
        await database.Store.AppendModuleAsync(
            atOperation,
            new SyncModuleOutcome(
                "SUBMISSIONS",
                SyncOperationStatus.Success,
                1,
                1,
                0,
                null,
                new AtCoderSubmissionsPayload("chokudai", [new AtCoderSubmission(11, 100, "abc100_a", "abc100", "C++", 100, 10, "AC", 1)])),
            timestamp,
            CancellationToken.None);

        var solvedOnly = await database.Store.GetProblemsAsync(null, ProblemStatus.Solved, 50, CancellationToken.None);
        var attemptedOnly = await database.Store.GetProblemsAsync(null, ProblemStatus.Attempted, 50, CancellationToken.None);
        var cfOnly = await database.Store.GetProblemsAsync(JudgeId.Codeforces, null, 50, CancellationToken.None);
        var limited = await database.Store.GetProblemsAsync(null, null, 1, CancellationToken.None);
        var empty = await database.Store.GetProblemsAsync(null, null, 0, CancellationToken.None);

        Assert.Equal(2, solvedOnly.Count);
        Assert.Single(attemptedOnly);
        Assert.Equal("2134D", attemptedOnly[0].ExternalId);
        Assert.Equal(2, cfOnly.Count);
        Assert.All(cfOnly, problem => Assert.Equal(JudgeId.Codeforces, problem.Judge));
        Assert.Single(limited);
        Assert.Empty(empty);
    }

    private static string? ReadFailureCategory(string databasePath, long operationId)
    {
        using var connection = new SqliteConnection($"Data Source={databasePath};Pooling=False");
        connection.Open();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT failure_category FROM sync_operations WHERE id = $operationId";
        command.Parameters.AddWithValue("$operationId", operationId);
        return command.ExecuteScalar() as string;
    }

    private static long CountModules(string databasePath, long operationId)
    {
        using var connection = new SqliteConnection($"Data Source={databasePath};Pooling=False");
        connection.Open();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT COUNT(*) FROM sync_modules WHERE operation_id = $operationId";
        command.Parameters.AddWithValue("$operationId", operationId);
        return (long)command.ExecuteScalar()!;
    }

    private static long CountRowsForHandle(string databasePath, string handle)
    {
        using var connection = OpenDirectConnection(databasePath);
        using var command = connection.CreateCommand();
        command.CommandText = """
            SELECT
                (SELECT COUNT(*) FROM codeforces_profiles WHERE handle = $handle) +
                (SELECT COUNT(*) FROM codeforces_ratings WHERE handle = $handle) +
                (SELECT COUNT(*) FROM codeforces_submissions WHERE handle = $handle) +
                (SELECT COUNT(*) FROM atcoder_submissions WHERE handle = $handle) +
                (SELECT COUNT(*) FROM luogu_payloads WHERE handle = $handle);
            """;
        command.Parameters.AddWithValue("$handle", handle);
        return (long)command.ExecuteScalar()!;
    }

    private static string ReadAllModuleFailureTypes(string databasePath)
    {
        using var connection = OpenDirectConnection(databasePath);
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT COALESCE(group_concat(failure_type, '|'), '') FROM sync_modules";
        return Convert.ToString(command.ExecuteScalar()) ?? string.Empty;
    }

    private static string? ReadSchemaVersion(string databasePath) => ReadString(databasePath, "SELECT value FROM schema_metadata WHERE key = 'schema_version'");

    private static long ReadScalarLong(string databasePath, string sql)
    {
        using var connection = new SqliteConnection($"Data Source={databasePath};Pooling=False");
        connection.Open();
        using var command = connection.CreateCommand();
        command.CommandText = sql;
        return Convert.ToInt64(command.ExecuteScalar(), System.Globalization.CultureInfo.InvariantCulture);
    }

    private static string? ReadAccountHandle(string databasePath, string judge)
    {
        using var connection = OpenDirectConnection(databasePath);
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT handle FROM accounts WHERE judge = $judge";
        command.Parameters.AddWithValue("$judge", judge);
        return command.ExecuteScalar() as string;
    }

    private static bool TableExists(string databasePath, string tableName)
    {
        using var connection = OpenDirectConnection(databasePath);
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = $tableName)";
        command.Parameters.AddWithValue("$tableName", tableName);
        return Convert.ToInt64(command.ExecuteScalar()) != 0;
    }

    private static bool ColumnExists(string databasePath, string tableName, string columnName)
    {
        using var connection = OpenDirectConnection(databasePath);
        using var command = connection.CreateCommand();
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

    private static string? ReadString(string databasePath, string commandText)
    {
        using var connection = OpenDirectConnection(databasePath);
        using var command = connection.CreateCommand();
        command.CommandText = commandText;
        return command.ExecuteScalar() as string;
    }

    private static void ExecuteNonQuery(string databasePath, string commandText)
    {
        using var connection = OpenDirectConnection(databasePath);
        using var command = connection.CreateCommand();
        command.CommandText = commandText;
        command.ExecuteNonQuery();
    }

    private static SqliteConnection OpenDirectConnection(string databasePath)
    {
        var connection = new SqliteConnection($"Data Source={databasePath};Pooling=False");
        connection.Open();
        return connection;
    }

    private const string VersionZeroSchemaSql = """
        CREATE TABLE schema_metadata (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL);
        CREATE TABLE accounts (
            judge TEXT NOT NULL PRIMARY KEY,
            handle TEXT NOT NULL,
            enabled INTEGER NOT NULL
        );
        CREATE TABLE sync_operations (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            judge TEXT NOT NULL,
            data_generation TEXT NOT NULL,
            started_at TEXT NOT NULL,
            finished_at TEXT NULL,
            status TEXT NOT NULL,
            failure_category TEXT NULL,
            FOREIGN KEY (judge) REFERENCES accounts(judge)
        );
        CREATE TABLE sync_modules (
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
        INSERT INTO schema_metadata (key, value) VALUES ('schema_version', '0');
        INSERT INTO accounts (judge, handle, enabled) VALUES ('Codeforces', 'legacy', 1);
        INSERT INTO sync_operations (id, judge, data_generation, started_at, finished_at, status, failure_category)
        VALUES (7, 'Codeforces', 'legacy-generation', '2026-09-07T00:00:00.0000000+00:00', '2026-09-07T00:01:00.0000000+00:00', 'Success', NULL);
        INSERT INTO sync_modules (operation_id, stage, status, attempted_count, imported_count, updated_count, failure_type, completed_at)
        VALUES (7, 'PROFILE', 'Success', 1, 1, 0, NULL, '2026-09-07T00:01:00.0000000+00:00');
        """;

    private class TemporaryDatabaseDirectory : IDisposable
    {
        private readonly string _directoryPath = Path.Combine(Path.GetTempPath(), "oj-nexus-tests", Guid.NewGuid().ToString("N"));

        public TemporaryDatabaseDirectory()
        {
            Directory.CreateDirectory(_directoryPath);
        }

        public string DatabasePath => Path.Combine(_directoryPath, "ojnexus.db");

        public string ConnectionString => new SqliteConnectionStringBuilder
        {
            DataSource = DatabasePath,
            ForeignKeys = true,
        }.ToString();

        public void Dispose()
        {
            SqliteConnection.ClearAllPools();
            if (Directory.Exists(_directoryPath))
            {
                Directory.Delete(_directoryPath, recursive: true);
            }
        }
    }

    private sealed class TemporaryDatabase : TemporaryDatabaseDirectory
    {
        public TemporaryDatabase(IClock? clock = null)
        {
            var factory = new SqliteConnectionFactory(Path.GetDirectoryName(DatabasePath));
            Store = new SqliteSyncStore(factory, clock);
            Assert.Equal(DatabasePath, factory.DatabasePath);
        }

        public SqliteSyncStore Store { get; }
    }

    private sealed class FixedClock(DateTimeOffset utcNow) : IClock
    {
        public DateTimeOffset UtcNow { get; set; } = utcNow;
    }
}
