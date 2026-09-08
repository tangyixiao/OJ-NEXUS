using OjNexus.Windows.Core.Domain;

namespace OjNexus.Windows.Cli;

public static class Program
{
    public static async Task<int> Main(string[] args)
    {
        try
        {
            var command = CliParser.Parse(args);
            var bootstrap = Bootstrap.Create();
            return await ExecuteAsync(command, bootstrap, Console.Out, CancellationToken.None);
        }
        catch (CliParseException exception)
        {
            Console.Error.WriteLine($"ARGUMENT ERROR: {exception.Message}");
            return (int)CliExitCode.InvalidArguments;
        }
        catch (OperationCanceledException)
        {
            Console.Error.WriteLine("SYNC: CANCELLED");
            return (int)CliExitCode.Cancelled;
        }
    }

    private static async Task<int> ExecuteAsync(CliCommand command, Bootstrap bootstrap, TextWriter output, CancellationToken cancellationToken)
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
            case SyncCommand sync:
            {
                var account = await ResolveAccountAsync(sync, bootstrap, cancellationToken);
                if (account is null)
                {
                    await Console.Error.WriteLineAsync($"ARGUMENT ERROR: NO HANDLE CONFIGURED FOR {sync.Judge.ToString().ToUpperInvariant()}. SUPPLY --handle <handle>.");
                    return (int)CliExitCode.InvalidArguments;
                }

                var report = await bootstrap.SyncService.RunAsync(account, sync.Force, cancellationToken);
                await output.WriteLineAsync(sync.Json ? ConsoleRenderer.RenderJson(report) : ConsoleRenderer.RenderHuman(report));
                return (int)CliExitCodeMapper.FromReport(report);
            }
            default:
                throw new InvalidOperationException("UNSUPPORTED CLI COMMAND.");
        }
    }

    private static async Task<JudgeAccount?> ResolveAccountAsync(SyncCommand command, Bootstrap bootstrap, CancellationToken cancellationToken)
    {
        if (command.Handle is not null) return JudgeAccount.Create(command.Judge, command.Handle);
        var accounts = await bootstrap.Store.GetAccountsAsync(cancellationToken);
        return accounts.SingleOrDefault(account => account.Judge == command.Judge);
    }
}
