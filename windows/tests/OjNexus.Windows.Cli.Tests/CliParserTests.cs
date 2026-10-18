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
}
