using OjNexus.Windows.Core.Domain;

namespace OjNexus.Windows.Cli;

public abstract record CliCommand(bool Json);

public sealed record StatusCommand(bool Json) : CliCommand(Json);

public sealed record SyncCommand(JudgeId Judge, string? Handle, bool Force, bool Json) : CliCommand(Json);

public sealed record HistoryCommand(JudgeId? Judge, int Limit, bool Json) : CliCommand(Json);

public sealed record ConfigShowCommand(bool Json) : CliCommand(Json);

public sealed class CliParseException(string message) : Exception(message);

public static class CliParser
{
    private const int DefaultHistoryLimit = 20;

    public static CliCommand Parse(string[] args)
    {
        ArgumentNullException.ThrowIfNull(args);
        if (args.Length == 0)
        {
            throw new CliParseException("MISSING COMMAND. USE: status | sync | history | config show.");
        }

        return args[0] switch
        {
            "status" => new StatusCommand(ParseJsonOnly(args, 1, "status")),
            "sync" => ParseSync(args),
            "history" => ParseHistory(args),
            "config" => ParseConfig(args),
            _ => throw new CliParseException($"UNKNOWN COMMAND '{args[0]}'. USE: status | sync | history | config show."),
        };
    }

    private static SyncCommand ParseSync(string[] args)
    {
        JudgeId? judge = null;
        string? handle = null;
        var force = false;
        var json = false;
        for (var index = 1; index < args.Length; index++)
        {
            switch (args[index])
            {
                case "--judge": EnsureNotSpecified(judge, "--judge"); judge = ParseJudge(ReadValue(args, ref index, "--judge")); break;
                case "--handle":
                    if (handle is not null) throw new CliParseException("OPTION '--handle' MAY ONLY BE SPECIFIED ONCE.");
                    handle = ReadNonBlankValue(args, ref index, "--handle");
                    break;
                case "--force": EnsureNotAlreadySet(force, "--force"); force = true; break;
                case "--json": EnsureNotAlreadySet(json, "--json"); json = true; break;
                default: ThrowUnknownOption(args[index], "sync --judge <judge> [--handle <handle>] [--force] [--json]"); break;
            }
        }

        if (judge is null)
        {
            throw new CliParseException("MISSING REQUIRED OPTION '--judge'. USE: sync --judge <judge> [--handle <handle>] [--force] [--json].");
        }

        return new SyncCommand(judge.Value, handle, force, json);
    }

    private static HistoryCommand ParseHistory(string[] args)
    {
        JudgeId? judge = null;
        var limit = DefaultHistoryLimit;
        var limitSpecified = false;
        var json = false;
        for (var index = 1; index < args.Length; index++)
        {
            switch (args[index])
            {
                case "--judge": EnsureNotSpecified(judge, "--judge"); judge = ParseJudge(ReadValue(args, ref index, "--judge")); break;
                case "--limit":
                    EnsureNotAlreadySet(limitSpecified, "--limit");
                    limitSpecified = true;
                    if (!int.TryParse(ReadValue(args, ref index, "--limit"), out limit) || limit <= 0)
                        throw new CliParseException("OPTION '--limit' MUST BE A POSITIVE INTEGER.");
                    break;
                case "--json": EnsureNotAlreadySet(json, "--json"); json = true; break;
                default: ThrowUnknownOption(args[index], "history [--judge <judge>] [--limit <positive>] [--json]"); break;
            }
        }

        return new HistoryCommand(judge, limit, json);
    }

    private static ConfigShowCommand ParseConfig(string[] args)
    {
        if (args.Length < 2 || args[1] != "show")
        {
            throw new CliParseException("CONFIG REQUIRES SUBCOMMAND 'show'. USE: config show [--json].");
        }

        return new ConfigShowCommand(ParseJsonOnly(args, 2, "config show"));
    }

    private static bool ParseJsonOnly(string[] args, int start, string usage)
    {
        if (args.Length == start) return false;
        if (args.Length == start + 1 && args[start] == "--json") return true;
        ThrowUnknownOption(args[start], $"{usage} [--json]");
        return false;
    }

    private static JudgeId ParseJudge(string value)
    {
        if (!JudgeIdParser.TryParse(value, out var judge))
            throw new CliParseException($"UNKNOWN JUDGE '{value}'. USE: Codeforces | AtCoder | Luogu.");
        return judge;
    }

    private static string ReadValue(string[] args, ref int index, string option)
    {
        if (index + 1 >= args.Length || args[index + 1].StartsWith("--", StringComparison.Ordinal))
            throw new CliParseException($"OPTION '{option}' REQUIRES A VALUE.");
        return args[++index];
    }

    private static string ReadNonBlankValue(string[] args, ref int index, string option)
    {
        var value = ReadValue(args, ref index, option).Trim();
        if (value.Length == 0) throw new CliParseException($"OPTION '{option}' REQUIRES A NON-BLANK VALUE.");
        return value;
    }

    private static void EnsureNotSpecified(JudgeId? value, string option)
    {
        if (value is not null) throw new CliParseException($"OPTION '{option}' MAY ONLY BE SPECIFIED ONCE.");
    }

    private static void EnsureNotAlreadySet(bool value, string option)
    {
        if (value) throw new CliParseException($"OPTION '{option}' MAY ONLY BE SPECIFIED ONCE.");
    }

    private static void ThrowUnknownOption(string option, string usage) =>
        throw new CliParseException($"UNKNOWN OPTION '{option}'. USE: {usage}.");
}
