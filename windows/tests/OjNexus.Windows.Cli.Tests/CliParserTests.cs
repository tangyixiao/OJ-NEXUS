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
}
