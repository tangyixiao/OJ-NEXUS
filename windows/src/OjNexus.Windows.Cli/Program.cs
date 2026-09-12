using OjNexus.Windows.Core.Contracts;
using OjNexus.Windows.Core.Domain;

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
        try
        {
            var command = CliParser.Parse(args);
            var bootstrap = Bootstrap.Create(dataDirectoryOverride, adapters);
            return await ExecuteAsync(command, bootstrap, output, error, cancellationToken);
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
        catch (Exception)
        {
            await error.WriteLineAsync("CLI ERROR: UNEXPECTED FAILURE. INSPECT THE OPERATION WITHOUT EXPOSING INTERNAL DETAILS.");
            return (int)CliExitCode.GeneralError;
        }
    }

    private static async Task<int> ExecuteAsync(CliCommand command, Bootstrap bootstrap, TextWriter output, TextWriter error, CancellationToken cancellationToken)
    {
        switch (command)
        {
            case StatusCommand status:
            {
                var accounts = await bootstrap.Store.GetAccountsAsync(cancellationToken);
                var operations = await bootstrap.Store.GetRecentOperationsAsync(null, 1, cancellationToken);
                var result = new CliStatus(accounts.Count, operations.FirstOrDefault());
                await output.WriteLineAsync(status.Json ? ConsoleRenderer.RenderJson(result) : ConsoleRenderer.RenderHuman(result));
                return (int)CliExitCode.Success;
            }
            case HistoryCommand history:
            {
                var operations = await bootstrap.Store.GetRecentOperationsAsync(history.Judge, history.Limit, cancellationToken);
                await output.WriteLineAsync(history.Json ? ConsoleRenderer.RenderJson(operations) : ConsoleRenderer.RenderHuman(operations));
                return (int)CliExitCode.Success;
            }
            case ConfigShowCommand configShow:
            {
                var accounts = await bootstrap.Store.GetAccountsAsync(cancellationToken);
                var result = new CliConfig(bootstrap.DataDirectory, bootstrap.ConnectionFactory.DatabasePath, accounts);
                await output.WriteLineAsync(configShow.Json ? ConsoleRenderer.RenderJson(result) : ConsoleRenderer.RenderHuman(result));
                return (int)CliExitCode.Success;
            }
            case DataCommand data:
            {
                var handle = await ResolveHandleAsync(data.Judge, data.Handle, bootstrap, cancellationToken);
                if (handle is null)
                {
                    await error.WriteLineAsync($"ARGUMENT ERROR: NO HANDLE CONFIGURED FOR {data.Judge.ToString().ToUpperInvariant()}. SUPPLY --handle <handle>.");
                    return (int)CliExitCode.InvalidArguments;
                }

                switch (data.Judge)
                {
                    case JudgeId.Codeforces:
                    {
                        var payload = await bootstrap.Store.GetCodeforcesPayloadAsync(handle, cancellationToken);
                        await output.WriteLineAsync(data.Json ? ConsoleRenderer.RenderJson(payload) : ConsoleRenderer.RenderHuman(payload));
                        return (int)CliExitCode.Success;
                    }
                    case JudgeId.AtCoder:
                    {
                        var payload = await bootstrap.Store.GetAtCoderPayloadAsync(handle, cancellationToken);
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
                var handle = await ResolveHandleAsync(sync.Judge, sync.Handle, bootstrap, cancellationToken);
                if (handle is null)
                {
                    await error.WriteLineAsync($"ARGUMENT ERROR: NO HANDLE CONFIGURED FOR {sync.Judge.ToString().ToUpperInvariant()}. SUPPLY --handle <handle>.");
                    return (int)CliExitCode.InvalidArguments;
                }

                var account = JudgeAccount.Create(sync.Judge, handle);
                var report = await bootstrap.SyncService.RunAsync(account, sync.Force, cancellationToken);
                await output.WriteLineAsync(sync.Json ? ConsoleRenderer.RenderJson(report) : ConsoleRenderer.RenderHuman(report));
                return (int)CliExitCodeMapper.FromReport(report);
            }
            default:
                throw new InvalidOperationException("UNSUPPORTED CLI COMMAND.");
        }
    }

    private static async Task<string?> ResolveHandleAsync(JudgeId judge, string? handle, Bootstrap bootstrap, CancellationToken cancellationToken)
    {
        if (handle is not null) return handle;
        var accounts = await bootstrap.Store.GetAccountsAsync(cancellationToken);
        return accounts.SingleOrDefault(account => account.Judge == judge)?.Handle;
    }
}
