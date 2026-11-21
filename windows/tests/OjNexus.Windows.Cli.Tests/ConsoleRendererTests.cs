using System.Text.Json;
using OjNexus.Windows.Cli;
using OjNexus.Windows.Core.Domain;

namespace OjNexus.Windows.Cli.Tests;

public sealed class ConsoleRendererTests
{
    [Fact]
    public void RenderConfigJson_ContainsOnlyPublicConfiguration()
    {
        var output = ConsoleRenderer.RenderJson(new CliConfig(
            @"C:\Users\example\AppData\Local\OJ-NEXUS",
            @"C:\Users\example\AppData\Local\OJ-NEXUS\ojnexus.db",
            [new JudgeAccount(JudgeId.Codeforces, "tourist")]));

        using var document = JsonDocument.Parse(output);
        Assert.Equal(@"C:\Users\example\AppData\Local\OJ-NEXUS", document.RootElement.GetProperty("dataDirectory").GetString());
        Assert.Equal("Codeforces", document.RootElement.GetProperty("accounts")[0].GetProperty("judge").GetString());
        Assert.DoesNotContain("password", output, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("token", output, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("cookie", output, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("session", output, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("rawHttp", output, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void RenderHuman_UsesUppercaseTelemetryStyle()
    {
        var output = ConsoleRenderer.RenderHuman(new CliStatus(2, null));

        Assert.Equal(string.Join(Environment.NewLine, "STATUS: READY", "ACCOUNTS: 2", "LAST SYNC: NONE"), output);
    }

    [Fact]
    public void RenderAtCoderPayloadJson_UsesStablePublicFields()
    {
        var output = ConsoleRenderer.RenderJson(new AtCoderPayloadSnapshot(
            [new AtCoderSubmission(11, 100, "abc100_a", "abc100", "C++", 100, 10, "AC", 1)]));

        using var document = JsonDocument.Parse(output);
        Assert.Equal("AtCoder", document.RootElement.GetProperty("judge").GetString());
        Assert.Equal("abc100_a", document.RootElement.GetProperty("submissions")[0].GetProperty("problemId").GetString());
        Assert.DoesNotContain("password", output, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("cookie", output, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("rawHttp", output, StringComparison.OrdinalIgnoreCase);
    }

    [Theory]
    [InlineData(SyncOperationStatus.Success, null, 0)]
    [InlineData(SyncOperationStatus.Partial, null, 1)]
    [InlineData(SyncOperationStatus.Partial, SyncError.Api, 1)]
    [InlineData(SyncOperationStatus.Error, SyncError.InvalidConfiguration, 2)]
    [InlineData(SyncOperationStatus.Offline, SyncError.Offline, 3)]
    [InlineData(SyncOperationStatus.Error, SyncError.Network, 3)]
    [InlineData(SyncOperationStatus.Partial, SyncError.Authentication, 4)]
    [InlineData(SyncOperationStatus.Cancelled, SyncError.Cancelled, 5)]
    public void FromReport_MapsEveryDocumentedExitCategory(
        SyncOperationStatus status,
        SyncError? error,
        int expected)
    {
        var report = new SyncReport(CreateOperation(status), status, error);

        Assert.Equal(expected, (int)CliExitCodeMapper.FromReport(report));
    }

    private static SyncOperation CreateOperation(SyncOperationStatus status) => new(
        7,
        new JudgeAccount(JudgeId.Codeforces, "tourist"),
        "test",
        DateTimeOffset.Parse("2026-09-08T00:00:00+00:00"),
        DateTimeOffset.Parse("2026-09-08T00:01:00+00:00"),
        status,
        []);

    [Fact]
    public void RenderProblemsPageHuman_ShowsMatchesAndPageHeaderAboveRows()
    {
        var page = new ProblemLedgerPage(
            [CreateProblem("2134C"), CreateProblem("2134D")],
            Offset: 2,
            Limit: 2,
            new ProblemLedgerSummary(Visible: 5, Total: 9, Solved: 3, Review: 1, Favorites: 2));

        var output = ConsoleRenderer.RenderHuman(page);

        var lines = output.Split(Environment.NewLine);
        Assert.Equal("MATCHES 5", lines[0]);
        Assert.Equal("PAGE 2 OF 3", lines[1]);
        Assert.Equal($"1. CODEFORCES 2134C [SOLVED] Watermelon", lines[2]);
        Assert.Equal("2. CODEFORCES 2134D [ATTEMPTED] Theatre Square", lines[3]);
    }

    [Fact]
    public void RenderProblemsPageHuman_EmptyRows_KeepsEmptyState()
    {
        var page = new ProblemLedgerPage(
            [],
            Offset: 0,
            Limit: 50,
            new ProblemLedgerSummary(Visible: 0, Total: 0, Solved: 0, Review: 0, Favorites: 0));

        Assert.Equal("PROBLEMS: EMPTY", ConsoleRenderer.RenderHuman(page));
    }

    [Fact]
    public void RenderProblemsPageJson_UsesQueryAndRowsShape()
    {
        var query = new ProblemLedgerQuery(
            Search: "water",
            Status: ProblemStatus.Solved,
            Judge: JudgeId.Codeforces,
            FavoriteOnly: true,
            Tag: "dp",
            DifficultyMin: 800,
            DifficultyMax: 2100,
            Sort: ProblemSort.Difficulty);
        var page = new ProblemLedgerPage(
            [CreateProblem("2134C")],
            Offset: 4,
            Limit: 10,
            new ProblemLedgerSummary(Visible: 1, Total: 9, Solved: 3, Review: 1, Favorites: 2));

        var output = ConsoleRenderer.RenderJson(page, query);

        using var document = JsonDocument.Parse(output);
        var queryElement = document.RootElement.GetProperty("query");
        Assert.Equal("water", queryElement.GetProperty("search").GetString());
        Assert.Equal("SOLVED", queryElement.GetProperty("status").GetString());
        Assert.Equal("Codeforces", queryElement.GetProperty("judge").GetString());
        Assert.True(queryElement.GetProperty("favoriteOnly").GetBoolean());
        Assert.Equal("dp", queryElement.GetProperty("tag").GetString());
        Assert.Equal(800, queryElement.GetProperty("difficultyMin").GetInt32());
        Assert.Equal(2100, queryElement.GetProperty("difficultyMax").GetInt32());
        Assert.Equal("DIFFICULTY", queryElement.GetProperty("sort").GetString());
        Assert.Equal(1, document.RootElement.GetProperty("totalMatches").GetInt32());
        Assert.Equal(4, document.RootElement.GetProperty("offset").GetInt32());
        Assert.Equal(10, document.RootElement.GetProperty("limit").GetInt32());
        var rows = document.RootElement.GetProperty("rows");
        Assert.Equal(1, rows.GetArrayLength());
        Assert.Equal("2134C", rows[0].GetProperty("externalId").GetString());
        Assert.Equal("SOLVED", rows[0].GetProperty("status").GetString());
    }

    [Fact]
    public void RenderProblemDetailHuman_ShowsOneFieldPerLine()
    {
        var problem = CreateProblem("2134C", favorite: true, difficulty: 1200, tags: ["dp", "math"]);

        var output = ConsoleRenderer.RenderHuman(problem);

        Assert.Equal(string.Join(Environment.NewLine,
            "PROBLEM: CODEFORCES 2134C",
            "TITLE: Watermelon",
            "URL: https://codeforces.com/contest/2134/problem/C",
            "STATUS: SOLVED",
            "DIFFICULTY: 1200",
            "FAVORITE: YES",
            "ATTEMPTS: 1",
            "IN REVIEW: NO",
            "TAGS: dp, math",
            $"FIRST SEEN: {problem.FirstSeenAt.ToString("O", System.Globalization.CultureInfo.InvariantCulture)}",
            $"LAST SEEN: {problem.LastSeenAt.ToString("O", System.Globalization.CultureInfo.InvariantCulture)}",
            $"UPDATED: {problem.UpdatedAt.ToString("O", System.Globalization.CultureInfo.InvariantCulture)}"), output);
    }

    [Fact]
    public void RenderProblemDetailJson_MatchesRowShape()
    {
        var problem = CreateProblem("2134C", difficulty: null, tags: []);

        var output = ConsoleRenderer.RenderJson(problem);

        using var document = JsonDocument.Parse(output);
        Assert.Equal("Codeforces", document.RootElement.GetProperty("judge").GetString());
        Assert.Equal("2134C", document.RootElement.GetProperty("externalId").GetString());
        Assert.Equal("Watermelon", document.RootElement.GetProperty("title").GetString());
        Assert.Equal(JsonValueKind.Null, document.RootElement.GetProperty("difficulty").ValueKind);
        Assert.False(document.RootElement.GetProperty("favorite").GetBoolean());
        Assert.Empty(document.RootElement.GetProperty("tags").EnumerateArray());
    }

    private static ProblemEntry CreateProblem(
        string externalId,
        bool favorite = false,
        int? difficulty = null,
        string[]? tags = null) => new(
        JudgeId.Codeforces,
        externalId,
        externalId == "2134D" ? "Theatre Square" : "Watermelon",
        $"https://codeforces.com/contest/2134/problem/{externalId[^1]}",
        Solved: externalId != "2134D",
        DateTimeOffset.Parse("2026-09-12T01:02:03+00:00"),
        DateTimeOffset.Parse("2026-09-12T01:02:03+00:00"),
        DateTimeOffset.Parse("2026-09-12T01:02:03+00:00"),
        difficulty,
        favorite,
        AttemptCount: 1,
        InReview: false,
        tags ?? []);
}
