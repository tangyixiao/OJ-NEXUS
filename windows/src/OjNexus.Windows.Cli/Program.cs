using OjNexus.Windows.Core.Contracts;
using OjNexus.Windows.Core.Domain;
using OjNexus.Windows.Core.Storage;

namespace OjNexus.Windows.Cli;

public static class Program
{
    public static async Task<int> Main(string[] args)
    {
        using var cancellationSource = new CancellationTokenSource();
        Console.CancelKeyPress += (_, eventArgs) =>
        {
            eventArgs.Cancel = true;
            cancellationSource.Cancel();
        };

        return await RunAsync(args, Console.Out, Console.Error, cancellationSource.Token);
    }

    public static async Task<int> RunAsync(
        string[] args,
        TextWriter output,
        TextWriter error,
        CancellationToken cancellationToken,
        string? dataDirectoryOverride = null,
        IReadOnlyDictionary<JudgeId, IJudgeAdapter>? adapters = null)
    {
        var wantsJson = args.Contains("--json", StringComparer.Ordinal);
        try
        {
            var command = CliParser.Parse(args);
            var stagedOutput = new StringWriter();
            var stagedError = new StringWriter();
            int exitCode;
            await using (var bootstrap = await Bootstrap.OpenAsync(dataDirectoryOverride, adapters, cancellationToken))
            {
                await using var operation = bootstrap.Session.BeginOperation(cancellationToken);
                exitCode = await ExecuteAsync(command, bootstrap, operation.Store, stagedOutput, stagedError, operation.CancellationToken);
            }
            await output.WriteAsync(stagedOutput.ToString());
            await error.WriteAsync(stagedError.ToString());
            return exitCode;
        }
        catch (CliParseException exception)
        {
            await error.WriteLineAsync($"ARGUMENT ERROR: {exception.Message}");
            return (int)CliExitCode.InvalidArguments;
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            await error.WriteLineAsync("SYNC: CANCELLED");
            return (int)CliExitCode.Cancelled;
        }
        catch (StorageException exception)
        {
            var code = exception.Failure.WireCode;
            if (wantsJson)
                await output.WriteLineAsync($"{{\"status\":\"blocked\",\"error\":\"{code}\"}}");
            else
                await output.WriteLineAsync($"LOCAL STORAGE UNAVAILABLE: {code}");
            return (int)CliExitCode.Storage;
        }
        catch (Exception)
        {
            await error.WriteLineAsync("CLI ERROR: UNEXPECTED FAILURE. INSPECT THE OPERATION WITHOUT EXPOSING INTERNAL DETAILS.");
            return (int)CliExitCode.GeneralError;
        }
    }

    private static async Task<int> ExecuteAsync(CliCommand command, Bootstrap bootstrap, ISyncStore store, TextWriter output, TextWriter error, CancellationToken cancellationToken)
    {
        switch (command)
        {
            case StatusCommand status:
            {
                var accounts = await store.GetAccountsAsync(cancellationToken);
                var operations = await store.GetRecentOperationsAsync(null, 1, cancellationToken);
                var result = new CliStatus(accounts.Count, operations.FirstOrDefault());
                await output.WriteLineAsync(status.Json ? ConsoleRenderer.RenderJson(result) : ConsoleRenderer.RenderHuman(result));
                return (int)CliExitCode.Success;
            }
            case HistoryCommand history:
            {
                var operations = await store.GetRecentOperationsAsync(history.Judge, history.Limit, cancellationToken);
                await output.WriteLineAsync(history.Json ? ConsoleRenderer.RenderJson(operations) : ConsoleRenderer.RenderHuman(operations));
                return (int)CliExitCode.Success;
            }
            case ProblemsListCommand problems:
            {
                var entries = await store.GetProblemsAsync(problems.Judge, problems.Status, problems.Limit, cancellationToken);
                await output.WriteLineAsync(problems.Json ? ConsoleRenderer.RenderJson(entries) : ConsoleRenderer.RenderHuman(entries));
                return (int)CliExitCode.Success;
            }
            case ReviewDueCommand reviewDue:
            {
                var entries = await store.GetDueReviewsAsync(reviewDue.Judge, reviewDue.Limit, cancellationToken);
                await output.WriteLineAsync(reviewDue.Json ? ConsoleRenderer.RenderJson(entries) : ConsoleRenderer.RenderHuman(entries));
                return (int)CliExitCode.Success;
            }
            case ReviewMarkCommand reviewMark:
            {
                var entry = await store.MarkReviewAsync(reviewMark.Judge, reviewMark.Problem, reviewMark.Result, DateTimeOffset.Now, cancellationToken);
                await output.WriteLineAsync(reviewMark.Json ? ConsoleRenderer.RenderJson(entry) : ConsoleRenderer.RenderHuman(entry));
                return (int)CliExitCode.Success;
            }
            case ConfigShowCommand configShow:
            {
                var accounts = await store.GetAccountsAsync(cancellationToken);
                var result = new CliConfig(bootstrap.Session.DataDirectory, bootstrap.Session.DatabasePath, accounts);
                await output.WriteLineAsync(configShow.Json ? ConsoleRenderer.RenderJson(result) : ConsoleRenderer.RenderHuman(result));
                return (int)CliExitCode.Success;
            }
            case DataCommand data:
            {
                var handle = await ResolveHandleAsync(data.Judge, data.Handle, store, cancellationToken);
                if (handle is null)
                {
                    await error.WriteLineAsync($"ARGUMENT ERROR: NO HANDLE CONFIGURED FOR {data.Judge.ToString().ToUpperInvariant()}. SUPPLY --handle <handle>.");
                    return (int)CliExitCode.InvalidArguments;
                }

                switch (data.Judge)
                {
                    case JudgeId.Codeforces:
                    {
                        var payload = await store.GetCodeforcesPayloadAsync(handle, cancellationToken);
                        await output.WriteLineAsync(data.Json ? ConsoleRenderer.RenderJson(payload) : ConsoleRenderer.RenderHuman(payload));
                        return (int)CliExitCode.Success;
                    }
                    case JudgeId.AtCoder:
                    {
                        var payload = await store.GetAtCoderPayloadAsync(handle, cancellationToken);
                        await output.WriteLineAsync(data.Json ? ConsoleRenderer.RenderJson(payload) : ConsoleRenderer.RenderHuman(payload));
                        return (int)CliExitCode.Success;
                    }
                    case JudgeId.Luogu:
                    {
                        var payload = await store.GetLuoguPayloadAsync(handle, cancellationToken);
                        await output.WriteLineAsync(data.Json ? ConsoleRenderer.RenderJson(payload) : ConsoleRenderer.RenderHuman(payload));
                        return (int)CliExitCode.Success;
                    }
                    default:
                    {
                        if (data.Json)
                        {
                            await output.WriteLineAsync($"{{\"status\":\"unavailable\",\"judge\":\"{data.Judge}\",\"error\":\"Payload is not available for this judge.\"}}");
                        }
                        else
                        {
                            await output.WriteLineAsync($"DATA: {data.Judge.ToString().ToUpperInvariant()}");
                            await output.WriteLineAsync("STATUS: UNAVAILABLE");
                            await output.WriteLineAsync("ERROR: PAYLOAD IS NOT AVAILABLE FOR THIS JUDGE.");
                        }

                        return (int)CliExitCode.Unavailable;
                    }
                }
            }
            case SyncCommand sync:
            {
                var handle = await ResolveHandleAsync(sync.Judge, sync.Handle, store, cancellationToken);
                if (handle is null)
                {
                    await error.WriteLineAsync($"ARGUMENT ERROR: NO HANDLE CONFIGURED FOR {sync.Judge.ToString().ToUpperInvariant()}. SUPPLY --handle <handle>.");
                    return (int)CliExitCode.InvalidArguments;
                }

                var account = JudgeAccount.Create(sync.Judge, handle);
                var report = await bootstrap.SyncService.RunAsync(account, sync.Force, store, cancellationToken);
                await output.WriteLineAsync(sync.Json ? ConsoleRenderer.RenderJson(report) : ConsoleRenderer.RenderHuman(report));
                return (int)CliExitCodeMapper.FromReport(report);
            }
            default:
                throw new InvalidOperationException("UNSUPPORTED CLI COMMAND.");
        }
    }

    private static async Task<string?> ResolveHandleAsync(JudgeId judge, string? handle, ISyncStore store, CancellationToken cancellationToken)
    {
        if (handle is not null) return handle;
        var accounts = await store.GetAccountsAsync(cancellationToken);
        return accounts.SingleOrDefault(account => account.Judge == judge)?.Handle;
    }
}
