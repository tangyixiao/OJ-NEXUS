using System.Text.Json;
using Microsoft.Data.Sqlite;
using OjNexus.Windows.Core.Domain;
using OjNexus.Windows.Core.Sync;

namespace OjNexus.Windows.Core.Tests;

/// <summary>
/// Phase 138 parity gate: every phase-136 contract vector (contracts/problems/v1,
/// 28 golden cases with hand-derived expectations) must reproduce through the real
/// SQLite ledger query. Seeding writes contract-shaped rows directly (the payloads
/// carry no difficulty/tags); the query under test is the store's own SQL path.
/// </summary>
public sealed class ProblemContractVectorTests
{
    private static string GoldenDirectory =>
        Path.Combine(AppContext.BaseDirectory, "contracts", "problems", "v1", "golden");

    public static IEnumerable<object[]> VectorNames()
    {
        var casesPath = Path.Combine(GoldenDirectory, "cases.json");
        using var document = JsonDocument.Parse(File.ReadAllText(casesPath));
        foreach (var vector in document.RootElement.GetProperty("vectors").EnumerateArray())
        {
            yield return new object[] { vector.GetString()! };
        }
    }

    [Theory]
    [MemberData(nameof(VectorNames))]
    public async Task ContractVector_ReproducesThroughSqliteLedgerQuery(string vectorFile)
    {
        var vector = JsonDocument.Parse(File.ReadAllText(Path.Combine(GoldenDirectory, vectorFile)));
        using var storage = new StorageTestScope(NewRoot());
        SeedDataset(storage.DatabasePath, vector.RootElement.GetProperty("dataset"));
        var query = MapQuery(vector.RootElement.GetProperty("query"));

        var page = await storage.Store.QueryProblemsAsync(query, 0, 10_000, CancellationToken.None);

        var expected = vector.RootElement.GetProperty("expect")
            .EnumerateArray().Select(item => item.GetString()!).ToArray();
        var actual = page.Rows.Select(row => row.Key).ToArray();
        Assert.True(
            expected.SequenceEqual(actual, StringComparer.Ordinal),
            $"vector {vectorFile}: expected [{string.Join(", ", expected)}] but got [{string.Join(", ", actual)}]");
    }

    private static string NewRoot() =>
        Path.Combine(Path.GetTempPath(), "ojnexus-vector-" + Guid.NewGuid().ToString("N"));

    private static ProblemLedgerQuery MapQuery(JsonElement query)
    {
        JsonElement element;
        var search = query.TryGetProperty("search", out element) && element.ValueKind == JsonValueKind.String
            ? element.GetString()
            : null;
        ProblemStatus? status = null;
        if (query.TryGetProperty("status", out element) && element.ValueKind == JsonValueKind.String
            && ProblemStatusParser.TryParse(element.GetString()!, out var parsedStatus))
        {
            status = parsedStatus;
        }

        JudgeId? judge = null;
        if (query.TryGetProperty("judge", out element) && element.ValueKind == JsonValueKind.String
            && JudgeIdParser.TryParse(element.GetString()!, out var parsedJudge))
        {
            judge = parsedJudge;
        }

        var favoriteOnly = query.TryGetProperty("favoriteOnly", out element) && element.GetBoolean();
        var tag = query.TryGetProperty("tag", out element) && element.ValueKind == JsonValueKind.String
            ? element.GetString()
            : null;
        int? difficultyMin = query.TryGetProperty("difficultyMin", out element) && element.ValueKind == JsonValueKind.Number
            ? element.GetInt32()
            : null;
        int? difficultyMax = query.TryGetProperty("difficultyMax", out element) && element.ValueKind == JsonValueKind.Number
            ? element.GetInt32()
            : null;
        var sort = ProblemSort.Updated;
        if (query.TryGetProperty("sort", out element) && element.ValueKind == JsonValueKind.String)
        {
            ProblemSortParser.TryParse(element.GetString()!, out sort);
        }

        return new ProblemLedgerQuery(search, status, judge, favoriteOnly, tag, difficultyMin, difficultyMax, sort);
    }

    private static void SeedDataset(string databasePath, JsonElement dataset)
    {
        using var connection = new SqliteConnection(
            new SqliteConnectionStringBuilder { DataSource = databasePath, Mode = SqliteOpenMode.ReadWrite, Pooling = false, ForeignKeys = true }.ToString());
        connection.Open();
        using var transaction = connection.BeginTransaction();
        foreach (var record in dataset.EnumerateArray())
        {
            // Mirror the store's own write behavior: the judge column stores the
            // enum wire form ("Codeforces"), not the vector's lowercase key form.
            JudgeIdParser.TryParse(record.GetProperty("judge").GetString()!, out var judge);
            var externalId = record.GetProperty("externalId").GetString()!;
            var title = record.GetProperty("title").GetString()!;
            var difficulty = record.TryGetProperty("difficulty", out var difficultyElement) && difficultyElement.ValueKind == JsonValueKind.Number
                ? difficultyElement.GetInt32()
                : (int?)null;
            var favorite = record.GetProperty("favorite").GetBoolean();
            var solved = record.GetProperty("solved").GetBoolean();
            var attemptCount = record.GetProperty("attemptCount").GetInt32();
            var inReview = record.GetProperty("inReview").GetBoolean();
            var createdAt = Epoch(record.GetProperty("createdAt"));
            var updatedAt = Epoch(record.GetProperty("updatedAt"));

            using (var insert = connection.CreateCommand())
            {
                insert.Transaction = transaction;
                insert.CommandText = """
                    INSERT INTO problems (judge, external_id, title, url, solved, first_seen_at, last_seen_at,
                                          difficulty, favorite, attempt_count, updated_at, title_lower)
                    VALUES ($judge, $externalId, $title, '', $solved, $createdAt, $updatedAt,
                            $difficulty, $favorite, $attemptCount, $updatedAt, $titleLower);
                    """;
                insert.Parameters.AddWithValue("$judge", judge.ToString());
                insert.Parameters.AddWithValue("$externalId", externalId);
                insert.Parameters.AddWithValue("$title", title);
                insert.Parameters.AddWithValue("$solved", solved ? 1 : 0);
                insert.Parameters.AddWithValue("$createdAt", FormatTime(createdAt));
                insert.Parameters.AddWithValue("$updatedAt", FormatTime(updatedAt));
                insert.Parameters.AddWithValue("$difficulty", difficulty is null ? DBNull.Value : difficulty.Value);
                insert.Parameters.AddWithValue("$favorite", favorite ? 1 : 0);
                insert.Parameters.AddWithValue("$attemptCount", attemptCount);
                insert.Parameters.AddWithValue("$titleLower", title.ToLowerInvariant());
                insert.ExecuteNonQuery();
            }

            if (inReview)
            {
                using var review = connection.CreateCommand();
                review.Transaction = transaction;
                review.CommandText = """
                    INSERT INTO reviews (judge, external_id, stage, due_at, due_day_index, last_result, last_reviewed_at, created_at)
                    VALUES ($judge, $externalId, 1, $dueAt, '1990-01-01', NULL, NULL, $createdAt);
                    """;
                review.Parameters.AddWithValue("$judge", judge.ToString());
                review.Parameters.AddWithValue("$externalId", externalId);
                review.Parameters.AddWithValue("$dueAt", FormatTime(createdAt));
                review.Parameters.AddWithValue("$createdAt", FormatTime(createdAt));
                review.ExecuteNonQuery();
            }

            foreach (var tag in record.GetProperty("tags").EnumerateArray())
            {
                using var tagInsert = connection.CreateCommand();
                tagInsert.Transaction = transaction;
                tagInsert.CommandText = "INSERT INTO problem_tags (judge, external_id, name) VALUES ($judge, $externalId, $name);";
                tagInsert.Parameters.AddWithValue("$judge", judge.ToString());
                tagInsert.Parameters.AddWithValue("$externalId", externalId);
                tagInsert.Parameters.AddWithValue("$name", tag.GetString()!);
                tagInsert.ExecuteNonQuery();
            }
        }

        transaction.Commit();
    }

    private static DateTimeOffset Epoch(JsonElement element) =>
        DateTimeOffset.FromUnixTimeMilliseconds(element.GetInt64());

    private static string FormatTime(DateTimeOffset value) =>
        value.ToUniversalTime().ToString("O", System.Globalization.CultureInfo.InvariantCulture);
}
