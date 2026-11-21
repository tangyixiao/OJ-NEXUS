using OjNexus.Windows.Cli;
using OjNexus.Windows.Core.Domain;

namespace OjNexus.Windows.Cli.Tests;

public sealed class CliParserTests
{
    [Fact]
    public void Parse_SyncWithoutJudge_RejectsWithActionableError()
    {
        var exception = Assert.Throws<CliParseException>(() => CliParser.Parse(["sync"]));

        Assert.Contains("--judge", exception.Message, StringComparison.Ordinal);
    }

    [Theory]
    [InlineData("0")]
    [InlineData("-1")]
    [InlineData("not-a-number")]
    public void Parse_HistoryWithNonPositiveOrMalformedLimit_Rejects(string limit)
    {
        var exception = Assert.Throws<CliParseException>(() => CliParser.Parse(["history", "--limit", limit]));

        Assert.Contains("--limit", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Parse_UnknownCommand_RejectsWithSupportedCommands()
    {
        var exception = Assert.Throws<CliParseException>(() => CliParser.Parse(["download"]));

        Assert.Contains("status", exception.Message, StringComparison.OrdinalIgnoreCase);
        Assert.Contains("sync", exception.Message, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void Parse_SyncWithAllSupportedOptions_ReturnsTypedCommand()
    {
        var command = Assert.IsType<SyncCommand>(CliParser.Parse([
            "sync", "--judge", "codeforces", "--handle", "tourist", "--force", "--json",
        ]));

        Assert.Equal(JudgeId.Codeforces, command.Judge);
        Assert.Equal("tourist", command.Handle);
        Assert.True(command.Force);
        Assert.True(command.Json);
    }

    [Fact]
    public void Parse_StatusJson_ReturnsTypedJsonCommand()
    {
        var command = Assert.IsType<StatusCommand>(CliParser.Parse(["status", "--json"]));

        Assert.True(command.Json);
    }

    [Fact]
    public void Parse_DataWithJudgeHandleAndJson_ReturnsTypedCommand()
    {
        var command = Assert.IsType<DataCommand>(CliParser.Parse([
            "data", "--judge", "atcoder", "--handle", "tourist", "--json",
        ]));

        Assert.Equal(JudgeId.AtCoder, command.Judge);
        Assert.Equal("tourist", command.Handle);
        Assert.True(command.Json);
    }

    [Fact]
    public void Parse_UnknownFlag_RejectsWithFlagName()
    {
        var exception = Assert.Throws<CliParseException>(() => CliParser.Parse(["status", "--verbose"]));

        Assert.Contains("--verbose", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Parse_UnknownFlagAfterJson_ReportsUnknownFlagNotJson()
    {
        var exception = Assert.Throws<CliParseException>(() => CliParser.Parse(["status", "--json", "--verbose"]));

        Assert.Contains("--verbose", exception.Message, StringComparison.Ordinal);
        Assert.DoesNotContain("UNKNOWN OPTION '--json'", exception.Message, StringComparison.Ordinal);
    }

    [Theory]
    [InlineData("status")]
    [InlineData("config", "show")]
    public void Parse_DuplicateJson_RejectsWithOnceError(params string[] args)
    {
        var exception = Assert.Throws<CliParseException>(() => CliParser.Parse(args.Concat(["--json", "--json"]).ToArray()));

        Assert.Contains("ONCE", exception.Message, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void Parse_ProblemsListDefault_ReturnsDefaultLimit()
    {
        var command = Assert.IsType<ProblemsListCommand>(CliParser.Parse(["problems", "list"]));

        Assert.Null(command.Judge);
        Assert.Null(command.Status);
        Assert.Equal(50, command.Limit);
        Assert.False(command.Json);
    }

    [Fact]
    public void Parse_ProblemsListWithAllOptions_ReturnsTypedCommand()
    {
        var command = Assert.IsType<ProblemsListCommand>(CliParser.Parse([
            "problems", "list", "--judge", "codeforces", "--status", "solved", "--limit", "10", "--json",
        ]));

        Assert.Equal(JudgeId.Codeforces, command.Judge);
        Assert.Equal(ProblemStatus.Solved, command.Status);
        Assert.Equal(10, command.Limit);
        Assert.True(command.Json);
    }

    [Fact]
    public void Parse_ProblemsListWithoutListSubcommand_RejectsWithUsage()
    {
        var exception = Assert.Throws<CliParseException>(() => CliParser.Parse(["problems"]));

        Assert.Contains("list", exception.Message, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void Parse_ProblemsListWithInvalidStatus_Rejects()
    {
        var exception = Assert.Throws<CliParseException>(() => CliParser.Parse(["problems", "list", "--status", "great"]));

        Assert.Contains("--status", exception.Message, StringComparison.Ordinal);
    }

    [Theory]
    [InlineData("0")]
    [InlineData("-3")]
    public void Parse_ProblemsListWithNonPositiveLimit_Rejects(string limit)
    {
        var exception = Assert.Throws<CliParseException>(() => CliParser.Parse(["problems", "list", "--limit", limit]));

        Assert.Contains("--limit", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Parse_ProblemsListWithUnknownFlag_RejectsWithFlagName()
    {
        var exception = Assert.Throws<CliParseException>(() => CliParser.Parse(["problems", "list", "--watch"]));

        Assert.Contains("--watch", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Parse_ReviewDueDefault_ReturnsDefaultLimit()
    {
        var command = Assert.IsType<ReviewDueCommand>(CliParser.Parse(["review", "due"]));

        Assert.Null(command.Judge);
        Assert.Equal(50, command.Limit);
        Assert.False(command.Json);
    }

    [Fact]
    public void Parse_ReviewDueWithAllOptions_ReturnsTypedCommand()
    {
        var command = Assert.IsType<ReviewDueCommand>(CliParser.Parse([
            "review", "due", "--judge", "atcoder", "--limit", "5", "--json",
        ]));

        Assert.Equal(JudgeId.AtCoder, command.Judge);
        Assert.Equal(5, command.Limit);
        Assert.True(command.Json);
    }

    [Theory]
    [InlineData("0")]
    [InlineData("-2")]
    [InlineData("soon")]
    public void Parse_ReviewDueWithBadLimit_Rejects(string limit)
    {
        var exception = Assert.Throws<CliParseException>(() => CliParser.Parse(["review", "due", "--limit", limit]));

        Assert.Contains("--limit", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Parse_ReviewMarkWithAllOptions_ReturnsTypedCommand()
    {
        var command = Assert.IsType<ReviewMarkCommand>(CliParser.Parse([
            "review", "mark", "--judge", "codeforces", "--problem", "2134/C", "--result", "pass", "--json",
        ]));

        Assert.Equal(JudgeId.Codeforces, command.Judge);
        Assert.Equal("2134/C", command.Problem);
        Assert.Equal(ReviewResult.Pass, command.Result);
        Assert.True(command.Json);
    }

    [Theory]
    [InlineData("ok")]
    [InlineData("PASS-")]
    public void Parse_ReviewMarkWithInvalidResult_Rejects(string result)
    {
        var exception = Assert.Throws<CliParseException>(() => CliParser.Parse(
            ["review", "mark", "--judge", "codeforces", "--problem", "2134/C", "--result", result]));

        Assert.Contains("--result", exception.Message, StringComparison.Ordinal);
    }

    [Theory]
    [InlineData(new[] { "review", "mark", "--problem", "2134/C", "--result", "pass" }, "--judge")]
    [InlineData(new[] { "review", "mark", "--judge", "codeforces", "--result", "pass" }, "--problem")]
    [InlineData(new[] { "review", "mark", "--judge", "codeforces", "--problem", "2134/C" }, "--result")]
    public void Parse_ReviewMarkMissingRequiredOption_Rejects(string[] args, string option)
    {
        var exception = Assert.Throws<CliParseException>(() => CliParser.Parse(args));

        Assert.Contains(option, exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Parse_ReviewWithoutSubcommand_RejectsWithUsage()
    {
        var exception = Assert.Throws<CliParseException>(() => CliParser.Parse(["review"]));

        Assert.Contains("due", exception.Message, StringComparison.OrdinalIgnoreCase);
        Assert.Contains("mark", exception.Message, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void Parse_ReviewUnknownSubcommand_RejectsWithUsage()
    {
        var exception = Assert.Throws<CliParseException>(() => CliParser.Parse(["review", "list"]));

        Assert.Contains("due", exception.Message, StringComparison.OrdinalIgnoreCase);
        Assert.Contains("mark", exception.Message, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void Parse_ProblemsListWithNewFilters_ReturnsTypedCommand()
    {
        var command = Assert.IsType<ProblemsListCommand>(CliParser.Parse([
            "problems", "list", "--search", "water", "--tag", "dp", "--favorite", "--difficulty-min", "800",
            "--difficulty-max", "2100", "--sort", "difficulty", "--offset", "20",
            "--judge", "codeforces", "--status", "review", "--limit", "10", "--json",
        ]));

        Assert.Equal("water", command.Search);
        Assert.Equal("dp", command.Tag);
        Assert.True(command.Favorite);
        Assert.Equal(800, command.DifficultyMin);
        Assert.Equal(2100, command.DifficultyMax);
        Assert.Equal(ProblemSort.Difficulty, command.Sort);
        Assert.Equal(20, command.Offset);
        Assert.Equal(JudgeId.Codeforces, command.Judge);
        Assert.Equal(ProblemStatus.Review, command.Status);
        Assert.Equal(10, command.Limit);
        Assert.True(command.Json);
    }

    [Fact]
    public void Parse_ProblemsListDefaults_OffsetZeroSortUpdated()
    {
        var command = Assert.IsType<ProblemsListCommand>(CliParser.Parse(["problems", "list"]));

        Assert.Null(command.Search);
        Assert.Null(command.Tag);
        Assert.False(command.Favorite);
        Assert.Null(command.DifficultyMin);
        Assert.Null(command.DifficultyMax);
        Assert.Equal(ProblemSort.Updated, command.Sort);
        Assert.Equal(0, command.Offset);
    }

    [Fact]
    public void Parse_ProblemsListWithDuplicateFavorite_RejectsWithOnceError()
    {
        var exception = Assert.Throws<CliParseException>(() => CliParser.Parse(["problems", "list", "--favorite", "--favorite"]));

        Assert.Contains("--favorite", exception.Message, StringComparison.Ordinal);
        Assert.Contains("ONCE", exception.Message, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void Parse_ProblemsListWithDuplicateSearch_RejectsWithOnceError()
    {
        var exception = Assert.Throws<CliParseException>(() => CliParser.Parse(["problems", "list", "--search", "a", "--search", "b"]));

        Assert.Contains("--search", exception.Message, StringComparison.Ordinal);
        Assert.Contains("ONCE", exception.Message, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void Parse_ProblemsListWithInvalidSort_RejectsWithActionableError()
    {
        var exception = Assert.Throws<CliParseException>(() => CliParser.Parse(["problems", "list", "--sort", "newest"]));

        Assert.Contains("--sort", exception.Message, StringComparison.Ordinal);
        Assert.Contains("UPDATED", exception.Message, StringComparison.OrdinalIgnoreCase);
    }

    [Theory]
    [InlineData("-5")]
    [InlineData("soon")]
    public void Parse_ProblemsListWithBadOffset_Rejects(string offset)
    {
        var exception = Assert.Throws<CliParseException>(() => CliParser.Parse(["problems", "list", "--offset", offset]));

        Assert.Contains("--offset", exception.Message, StringComparison.Ordinal);
    }

    [Theory]
    [InlineData("-800")]
    [InlineData("hard")]
    public void Parse_ProblemsListWithBadDifficultyMin_Rejects(string difficulty)
    {
        var exception = Assert.Throws<CliParseException>(() => CliParser.Parse(["problems", "list", "--difficulty-min", difficulty]));

        Assert.Contains("--difficulty-min", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Parse_ProblemsShowWithKey_ReturnsTypedCommandWithVerbatimExternalId()
    {
        var command = Assert.IsType<ProblemsShowCommand>(CliParser.Parse(["problems", "show", "CODEFORCES/1234A", "--json"]));

        Assert.Equal(JudgeId.Codeforces, command.Judge);
        Assert.Equal("1234A", command.ExternalId);
        Assert.True(command.Json);
    }

    [Theory]
    [InlineData("codeforces")]
    [InlineData("codeforces/")]
    [InlineData("/1234A")]
    public void Parse_ProblemsShowWithMalformedKey_Rejects(string key)
    {
        var exception = Assert.Throws<CliParseException>(() => CliParser.Parse(["problems", "show", key]));

        Assert.Contains(key, exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Parse_ProblemsShowWithoutKey_RejectsWithUsage()
    {
        var exception = Assert.Throws<CliParseException>(() => CliParser.Parse(["problems", "show"]));

        Assert.Contains("problems show", exception.Message, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void Parse_ProblemsShowWithUnknownOption_RejectsWithFlagName()
    {
        var exception = Assert.Throws<CliParseException>(() => CliParser.Parse(["problems", "show", "codeforces/1234A", "--watch"]));

        Assert.Contains("--watch", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Parse_ProblemsWithoutSubcommand_RejectsWithUsage()
    {
        var exception = Assert.Throws<CliParseException>(() => CliParser.Parse(["problems"]));

        Assert.Contains("list", exception.Message, StringComparison.OrdinalIgnoreCase);
        Assert.Contains("show", exception.Message, StringComparison.OrdinalIgnoreCase);
    }
}
