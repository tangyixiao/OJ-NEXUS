using OjNexus.Windows.Core.Domain;

namespace OjNexus.Windows.Cli;

public abstract record CliCommand(bool Json);

public sealed record StatusCommand(bool Json) : CliCommand(Json);

public sealed record SyncCommand(JudgeId Judge, string? Handle, bool Force, bool Json) : CliCommand(Json);

public sealed record DataCommand(JudgeId Judge, string? Handle, bool Json) : CliCommand(Json);

public sealed record HistoryCommand(JudgeId? Judge, int Limit, bool Json) : CliCommand(Json);

public sealed record ProblemsListCommand(
    JudgeId? Judge,
    ProblemStatus? Status,
    string? Search,
    string? Tag,
    bool Favorite,
    int? DifficultyMin,
    int? DifficultyMax,
    ProblemSort Sort,
    int Offset,
    int Limit,
    bool Json) : CliCommand(Json);

public sealed record ProblemsShowCommand(JudgeId Judge, string ExternalId, bool Json) : CliCommand(Json);

public sealed record ReviewDueCommand(JudgeId? Judge, int Limit, bool Json) : CliCommand(Json);

public sealed record ReviewMarkCommand(JudgeId Judge, string Problem, ReviewResult Result, bool Json) : CliCommand(Json);

public sealed record ConfigShowCommand(bool Json) : CliCommand(Json);

public abstract record PortableCommand(bool Json) : CliCommand(Json);

public sealed record PortableInspectCommand(string File, bool Json) : PortableCommand(Json);

public sealed record PortableImportCommand(string File, bool Yes, bool Json) : PortableCommand(Json);

public sealed record PortableExportCommand(string File, bool Json) : PortableCommand(Json);

public sealed class CliParseException(string message) : Exception(message);

public static class CliParser
{
    private const int DefaultHistoryLimit = 20;
    private const int DefaultProblemsLimit = 50;
    private const string ProblemsListUsage =
        "problems list [--judge <judge>] [--status <unsolved|attempted|solved|review>] [--search <text>] [--tag <name>] [--favorite] [--difficulty-min <int>] [--difficulty-max <int>] [--sort <updated|difficulty|title|attempts>] [--offset <non-negative>] [--limit <positive>] [--json]";

    private const string ProblemsShowUsage =
        "problems show <judge>/<external-id> [--json]";

    private const string CommandUsage =
        "status | sync | data | history | problems list | problems show | review due | review mark | config show | portable inspect/import/export";

    public static CliCommand Parse(string[] args)
    {
        ArgumentNullException.ThrowIfNull(args);
        if (args.Length == 0)
        {
            throw new CliParseException($"MISSING COMMAND. USE: {CommandUsage}.");
        }

        return args[0] switch
        {
            "status" => new StatusCommand(ParseJsonOnly(args, 1, "status")),
            "sync" => ParseSync(args),
            "data" => ParseData(args),
            "history" => ParseHistory(args),
            "problems" => ParseProblems(args),
            "review" => ParseReview(args),
            "config" => ParseConfig(args),
            "portable" => ParsePortable(args),
            _ => throw new CliParseException($"UNKNOWN COMMAND '{args[0]}'. USE: {CommandUsage}."),
        };
    }

    private static CliCommand ParsePortable(string[] args)
    {
        if (args.Length < 2)
        {
            throw new CliParseException("PORTABLE REQUIRES SUBCOMMAND 'inspect', 'import', OR 'export'. USE: portable inspect --file <path> [--json] | portable import --file <path> [--yes] [--json] | portable export --file <path> [--json].");
        }

        var json = false;
        var yes = false;
        string? file = null;
        var start = 2;
        switch (args[1])
        {
            case "inspect":
                break;
            case "import":
                break;
            case "export":
                break;
            default:
                throw new CliParseException($"UNKNOWN SUBCOMMAND '{args[1]}'. USE: portable inspect --file <path> [--json] | portable import --file <path> [--yes] [--json] | portable export --file <path> [--json].");
        }

        for (var index = start; index < args.Length; index++)
        {
            switch (args[index])
            {
                case "--file":
                    EnsureNotAlreadySet(file is not null, "--file");
                    file = ReadNonBlankValue(args, ref index, "--file");
                    break;
                case "--yes":
                    if (args[1] != "import")
                    {
                        throw new CliParseException($"OPTION '--yes' IS ONLY VALID FOR 'portable import'.");
                    }

                    EnsureNotAlreadySet(yes, "--yes");
                    yes = true;
                    break;
                case "--json": EnsureNotAlreadySet(json, "--json"); json = true; break;
                default:
                    ThrowUnknownOption(args[index], args[1] == "import"
                        ? "portable import --file <path> [--yes] [--json]"
                        : $"portable {args[1]} --file <path> [--json]");
                    break;
            }
        }

        if (file is null)
        {
            throw new CliParseException($"MISSING REQUIRED OPTION '--file'. USE: portable {args[1]} --file <path> [--json]" + (args[1] == "import" ? " [--yes]." : "."));
        }

        return args[1] switch
        {
            "inspect" => new PortableInspectCommand(file, json),
            "import" => new PortableImportCommand(file, yes, json),
            _ => new PortableExportCommand(file, json),
        };
    }

    private static CliCommand ParseReview(string[] args)
    {
        if (args.Length < 2)
        {
            throw new CliParseException("REVIEW REQUIRES SUBCOMMAND 'due' OR 'mark'. USE: review due [--judge <judge>] [--limit <positive>] [--json] | review mark --judge <judge> --problem <external-id> --result <pass|hard|fail|skip> [--json].");
        }

        return args[1] switch
        {
            "due" => ParseReviewDue(args),
            "mark" => ParseReviewMark(args),
            _ => throw new CliParseException($"UNKNOWN SUBCOMMAND '{args[1]}'. USE: review due [--judge <judge>] [--limit <positive>] [--json] | review mark --judge <judge> --problem <external-id> --result <pass|hard|fail|skip> [--json]."),
        };
    }

    private static ReviewDueCommand ParseReviewDue(string[] args)
    {
        JudgeId? judge = null;
        var limit = DefaultProblemsLimit;
        var limitSpecified = false;
        var json = false;
        for (var index = 2; index < args.Length; index++)
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
                default: ThrowUnknownOption(args[index], "review due [--judge <judge>] [--limit <positive>] [--json]"); break;
            }
        }

        return new ReviewDueCommand(judge, limit, json);
    }

    private static ReviewMarkCommand ParseReviewMark(string[] args)
    {
        JudgeId? judge = null;
        string? problem = null;
        ReviewResult? result = null;
        var json = false;
        for (var index = 2; index < args.Length; index++)
        {
            switch (args[index])
            {
                case "--judge": EnsureNotSpecified(judge, "--judge"); judge = ParseJudge(ReadValue(args, ref index, "--judge")); break;
                case "--problem":
                    EnsureNotAlreadySet(problem is not null, "--problem");
                    problem = ReadNonBlankValue(args, ref index, "--problem");
                    break;
                case "--result":
                    EnsureNotAlreadySet(result is not null, "--result");
                    if (!ReviewResultParser.TryParse(ReadValue(args, ref index, "--result"), out var parsedResult))
                        throw new CliParseException("OPTION '--result' MUST BE 'pass', 'hard', 'fail', OR 'skip'.");
                    result = parsedResult;
                    break;
                case "--json": EnsureNotAlreadySet(json, "--json"); json = true; break;
                default: ThrowUnknownOption(args[index], "review mark --judge <judge> --problem <external-id> --result <pass|hard|fail|skip> [--json]"); break;
            }
        }

        if (judge is null || problem is null || result is null)
        {
            throw new CliParseException("MISSING REQUIRED OPTION. USE: review mark --judge <judge> --problem <external-id> --result <pass|hard|fail|skip> [--json].");
        }

        return new ReviewMarkCommand(judge.Value, problem, result.Value, json);
    }

    private static CliCommand ParseProblems(string[] args)
    {
        if (args.Length < 2)
        {
            throw new CliParseException($"PROBLEMS REQUIRES SUBCOMMAND 'list' OR 'show'. USE: {ProblemsListUsage} | {ProblemsShowUsage}.");
        }

        return args[1] switch
        {
            "list" => ParseProblemsList(args),
            "show" => ParseProblemsShow(args),
            _ => throw new CliParseException($"UNKNOWN SUBCOMMAND '{args[1]}'. USE: {ProblemsListUsage} | {ProblemsShowUsage}."),
        };
    }

    private static ProblemsListCommand ParseProblemsList(string[] args)
    {
        JudgeId? judge = null;
        ProblemStatus? status = null;
        string? search = null;
        string? tag = null;
        var favorite = false;
        int? difficultyMin = null;
        int? difficultyMax = null;
        var sort = ProblemSort.Updated;
        var sortSpecified = false;
        var offset = 0;
        var offsetSpecified = false;
        var limit = DefaultProblemsLimit;
        var limitSpecified = false;
        var json = false;
        for (var index = 2; index < args.Length; index++)
        {
            switch (args[index])
            {
                case "--judge": EnsureNotSpecified(judge, "--judge"); judge = ParseJudge(ReadValue(args, ref index, "--judge")); break;
                case "--status":
                    EnsureNotAlreadySet(status is not null, "--status");
                    if (!ProblemStatusParser.TryParse(ReadValue(args, ref index, "--status"), out var parsedStatus))
                        throw new CliParseException("OPTION '--status' MUST BE 'unsolved', 'attempted', 'solved', OR 'review'.");
                    status = parsedStatus;
                    break;
                case "--search":
                    EnsureNotAlreadySet(search is not null, "--search");
                    search = ReadNonBlankValue(args, ref index, "--search");
                    break;
                case "--tag":
                    EnsureNotAlreadySet(tag is not null, "--tag");
                    tag = ReadNonBlankValue(args, ref index, "--tag");
                    break;
                case "--favorite": EnsureNotAlreadySet(favorite, "--favorite"); favorite = true; break;
                case "--difficulty-min":
                    EnsureNotAlreadySet(difficultyMin is not null, "--difficulty-min");
                    difficultyMin = ReadNonNegativeInt(args, ref index, "--difficulty-min");
                    break;
                case "--difficulty-max":
                    EnsureNotAlreadySet(difficultyMax is not null, "--difficulty-max");
                    difficultyMax = ReadNonNegativeInt(args, ref index, "--difficulty-max");
                    break;
                case "--sort":
                    EnsureNotAlreadySet(sortSpecified, "--sort");
                    sortSpecified = true;
                    if (!ProblemSortParser.TryParse(ReadValue(args, ref index, "--sort"), out var parsedSort))
                        throw new CliParseException("OPTION '--sort' MUST BE 'updated', 'difficulty', 'title', OR 'attempts'.");
                    sort = parsedSort;
                    break;
                case "--offset":
                    EnsureNotAlreadySet(offsetSpecified, "--offset");
                    offsetSpecified = true;
                    offset = ReadNonNegativeInt(args, ref index, "--offset");
                    break;
                case "--limit":
                    EnsureNotAlreadySet(limitSpecified, "--limit");
                    limitSpecified = true;
                    if (!int.TryParse(ReadValue(args, ref index, "--limit"), out limit) || limit <= 0)
                        throw new CliParseException("OPTION '--limit' MUST BE A POSITIVE INTEGER.");
                    break;
                case "--json": EnsureNotAlreadySet(json, "--json"); json = true; break;
                default: ThrowUnknownOption(args[index], ProblemsListUsage); break;
            }
        }

        return new ProblemsListCommand(judge, status, search, tag, favorite, difficultyMin, difficultyMax, sort, offset, limit, json);
    }

    private static ProblemsShowCommand ParseProblemsShow(string[] args)
    {
        if (args.Length < 3)
        {
            throw new CliParseException($"MISSING PROBLEM KEY. USE: {ProblemsShowUsage}.");
        }

        var key = args[2];
        var separator = key.IndexOf('/', StringComparison.Ordinal);
        if (separator <= 0 || separator == key.Length - 1)
        {
            throw new CliParseException($"MALFORMED PROBLEM KEY '{key}'. USE: {ProblemsShowUsage}.");
        }

        var json = false;
        for (var index = 3; index < args.Length; index++)
        {
            switch (args[index])
            {
                case "--json": EnsureNotAlreadySet(json, "--json"); json = true; break;
                default: ThrowUnknownOption(args[index], ProblemsShowUsage); break;
            }
        }

        // The identity freeze: the judge folds through JudgeIdParser, the external id is the
        // remainder of the key VERBATIM — no trim, no case-fold.
        return new ProblemsShowCommand(ParseJudge(key[..separator]), key[(separator + 1)..], json);
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

    private static DataCommand ParseData(string[] args)
    {
        JudgeId? judge = null;
        string? handle = null;
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
                case "--json": EnsureNotAlreadySet(json, "--json"); json = true; break;
                default: ThrowUnknownOption(args[index], "data --judge <judge> [--handle <handle>] [--json]"); break;
            }
        }

        if (judge is null)
        {
            throw new CliParseException("MISSING REQUIRED OPTION '--judge'. USE: data --judge <judge> [--handle <handle>] [--json].");
        }

        return new DataCommand(judge.Value, handle, json);
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
        var json = false;
        for (var index = start; index < args.Length; index++)
        {
            if (args[index] == "--json")
            {
                EnsureNotAlreadySet(json, "--json");
                json = true;
                continue;
            }

            ThrowUnknownOption(args[index], $"{usage} [--json]");
        }

        return json;
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

    private static int ReadNonNegativeInt(string[] args, ref int index, string option)
    {
        if (!int.TryParse(ReadValue(args, ref index, option), out var value) || value < 0)
            throw new CliParseException($"OPTION '{option}' MUST BE A NON-NEGATIVE INTEGER.");
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
