using OjNexus.Windows.Core.Portable;
using OjNexus.Windows.Core.Storage;

namespace OjNexus.Windows.Cli;

/// <summary>
/// Handlers for the `portable` verb family: inspect, import, export.
/// All handlers run inside an open storage operation and speak uppercase telemetry style.
/// </summary>
public static class PortableCommands
{
    public static async Task<int> InspectAsync(
        PortableInspectCommand command,
        PortableRepository repository,
        TextWriter output,
        TextWriter error,
        CancellationToken cancellationToken)
    {
        var bytes = await ReadPackageBytesAsync(command.File, command.Json, output, error, cancellationToken);
        var preview = await repository.PreviewAsync(bytes, cancellationToken);
        await output.WriteLineAsync(command.Json ? ConsoleRenderer.RenderJson(preview) : RenderPreview(preview));
        return (int)CliExitCode.Success;
    }

    public static async Task<int> ImportAsync(
        PortableImportCommand command,
        PortableRepository repository,
        TextWriter output,
        TextWriter error,
        CancellationToken cancellationToken)
    {
        var bytes = await ReadPackageBytesAsync(command.File, command.Json, output, error, cancellationToken);
        var preview = await repository.PreviewAsync(bytes, cancellationToken);

        if (!command.Yes)
        {
            await output.WriteLineAsync(command.Json ? ConsoleRenderer.RenderJson(preview) : RenderPreview(preview));
            await output.WriteLineAsync("CONFIRMATION REQUIRED: RERUN WITH --yes");
            return (int)CliExitCode.Success;
        }

        try
        {
            var result = await repository.ApplyAsync(bytes, cancellationToken);
            await output.WriteLineAsync(command.Json ? ConsoleRenderer.RenderJson(result) : RenderApply(result));
            return (int)CliExitCode.Success;
        }
        catch (PortableContractException exception)
        {
            await output.WriteLineAsync(command.Json
                ? ConsoleRenderer.RenderJson(new PortableRejection(exception.Reject.Wire, exception.Message))
                : $"REJECTED {exception.Reject.Wire}: {exception.Message}");
            return (int)CliExitCode.Success;
        }
    }

    public static async Task<int> ExportAsync(
        PortableExportCommand command,
        PortableRepository repository,
        TextWriter output,
        TextWriter error,
        CancellationToken cancellationToken)
    {
        var bytes = await repository.ExportAsync(cancellationToken);
        await File.WriteAllBytesAsync(command.File, bytes, cancellationToken);
        var identity = repository.Identity;
        await output.WriteLineAsync(command.Json
            ? ConsoleRenderer.RenderJson(new PortableExportReceipt(command.File, identity))
            : $"EXPORTED {identity[..Math.Min(12, identity.Length)]}");
        return (int)CliExitCode.Success;
    }

    private static async Task<byte[]> ReadPackageBytesAsync(
        string path,
        bool json,
        TextWriter output,
        TextWriter error,
        CancellationToken cancellationToken)
    {
        try
        {
            return await File.ReadAllBytesAsync(path, cancellationToken);
        }
        catch (Exception exception) when (exception is FileNotFoundException or DirectoryNotFoundException or UnauthorizedAccessException or IOException)
        {
            if (json)
                await output.WriteLineAsync("{\"status\":\"error\",\"error\":\"STORAGE_IO_FAILURE\"}");
            else
                await output.WriteLineAsync($"PACKAGE READ UNAVAILABLE: STORAGE_IO_FAILURE ({exception.GetType().Name})");
            throw new PortableIoFailureException();
        }
    }

    private static string RenderPreview(PortablePreview preview) => $"{preview.Identity}";

    private static string RenderApply(PortableApplyResult result) => $"{result.Upserted}";

    private sealed record PortableRejection(string Wire, string Detail);

    private sealed record PortableExportReceipt(string File, string Identity);

    private sealed class PortableIoFailureException : Exception;
}
