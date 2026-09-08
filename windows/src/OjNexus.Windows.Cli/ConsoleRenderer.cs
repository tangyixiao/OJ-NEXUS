using System.Text.Json;
using OjNexus.Windows.Core.Domain;
using OjNexus.Windows.Core.Sync;

namespace OjNexus.Windows.Cli;

public sealed record CliStatus(int AccountCount, SyncOperation? LastOperation);

public sealed record CliConfig(string DataDirectory, string DatabasePath, IReadOnlyList<JudgeAccount> Accounts);

public static class ConsoleRenderer
{
    public static string RenderHuman(CliStatus status) => string.Join(Environment.NewLine, "STATUS: READY", $"ACCOUNTS: {status.AccountCount}", $"LAST SYNC: {status.LastOperation?.Status.ToString().ToUpperInvariant() ?? "NONE"}");

    public static string RenderJson(CliStatus status) => JsonSerializer.Serialize(new { status = "ready", accountCount = status.AccountCount, lastSync = status.LastOperation is null ? null : ProjectOperation(status.LastOperation) });

    public static string RenderHuman(IReadOnlyList<SyncOperation> operations) => operations.Count == 0
        ? "HISTORY: EMPTY"
        : string.Join(Environment.NewLine, operations.Select(operation => $"OPERATION {operation.Id}: {operation.Account.Judge.ToString().ToUpperInvariant()} {operation.Status.ToString().ToUpperInvariant()}"));

    public static string RenderJson(IReadOnlyList<SyncOperation> operations) => JsonSerializer.Serialize(new { operations = operations.Select(ProjectOperation) });

    public static string RenderHuman(CliConfig config) => string.Join(Environment.NewLine, "CONFIG: REDACTED", $"DATA DIRECTORY: {config.DataDirectory}", $"DATABASE: {config.DatabasePath}", $"ACCOUNTS: {config.Accounts.Count}");

    public static string RenderJson(CliConfig config) => JsonSerializer.Serialize(new
    {
        dataDirectory = config.DataDirectory,
        databasePath = config.DatabasePath,
        accounts = config.Accounts.Select(account => new { judge = account.Judge.ToString(), handle = account.Handle, enabled = account.Enabled }),
    });

    public static string RenderHuman(SyncReport report) => string.Join(Environment.NewLine, $"SYNC: {report.Status.ToString().ToUpperInvariant()}", $"JUDGE: {report.Operation.Account.Judge.ToString().ToUpperInvariant()}", $"MODULES: {report.Operation.Modules.Count}", $"ERROR: {report.Error?.ToString().ToUpperInvariant() ?? "NONE"}");

    public static string RenderJson(SyncReport report) => SyncReportProjector.ToJson(report);

    private static object ProjectOperation(SyncOperation operation) => new
    {
        id = operation.Id,
        judge = operation.Account.Judge.ToString(),
        handle = operation.Account.Handle,
        status = operation.Status.ToString(),
        startedAt = operation.StartedAt,
        finishedAt = operation.FinishedAt,
        moduleCount = operation.Modules.Count,
    };
}
