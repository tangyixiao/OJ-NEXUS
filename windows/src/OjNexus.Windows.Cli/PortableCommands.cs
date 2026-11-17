using System.Security.Cryptography;
using System.Text.Json;
using OjNexus.Windows.Core.Portable;
using OjNexus.Windows.Core.Storage;

namespace OjNexus.Windows.Cli;

/// <summary>
/// Handlers for the `portable` verb family: inspect, import, export. All
/// handlers run inside an open storage operation and speak uppercase
/// telemetry style. A rejected package is a valid outcome (exit 0); only
/// transport/storage failures and internal errors carry non-zero exits.
/// </summary>
public static class PortableCommands
{
    public static async Task<int> InspectAsync(
        PortableInspectCommand command,
        PortableRepository repository,
        TextWriter output,
        CancellationToken cancellationToken)
    {
        var bytes = await ReadPackageBytesAsync(command.File, command.Json, output, cancellationToken);
        var preview = await repository.PreviewAsync(bytes, cancellationToken);
        await output.WriteLineAsync(command.Json
            ? JsonSerializer.Serialize(RenderPreviewJson(preview))
            : RenderPreview(preview));
        return (int)CliExitCode.Success;
    }

    public static async Task<int> ImportAsync(
        PortableImportCommand command,
        PortableRepository repository,
        TextWriter output,
        CancellationToken cancellationToken)
    {
        var bytes = await ReadPackageBytesAsync(command.File, command.Json, output, cancellationToken);

        try
        {
            var preview = await repository.PreviewAsync(bytes, cancellationToken);

            if (!command.Yes)
            {
                // The confirm gate: printing the preview writes nothing.
                await output.WriteLineAsync(command.Json
                    ? JsonSerializer.Serialize(RenderPreviewJson(preview))
                    : RenderPreview(preview));
                if (!command.Json)
                {
                    await output.WriteLineAsync("CONFIRMATION REQUIRED: RERUN WITH --yes");
                }

                return (int)CliExitCode.Success;
            }

            var result = await repository.ApplyAsync(bytes, cancellationToken);
            await output.WriteLineAsync(command.Json
                ? JsonSerializer.Serialize(RenderApplyJson(result))
                : RenderApply(result));
            return (int)CliExitCode.Success;
        }
        catch (PortableContractException exception)
        {
            // A whole-package reject is a valid outcome: nothing was written.
            await output.WriteLineAsync(command.Json
                ? JsonSerializer.Serialize(new
                {
                    status = "rejected",
                    category = exception.Reject.Wire,
                    detail = exception.Reject.Detail,
                    location = exception.Reject.Location,
                })
                : $"REJECTED {exception.Reject.Wire}: {exception.Reject.Detail} @ {exception.Reject.Location}");
            return (int)CliExitCode.Success;
        }
    }

    public static async Task<int> ExportAsync(
        PortableExportCommand command,
        PortableRepository repository,
        TextWriter output,
        CancellationToken cancellationToken)
    {
        byte[] bytes;
        try
        {
            bytes = await repository.ExportAsync(cancellationToken);
        }
        catch (InvalidOperationException exception)
        {
            await output.WriteLineAsync(command.Json
                ? JsonSerializer.Serialize(new { status = "error", error = "ARCHIVE_EMPTY", detail = exception.Message })
                : $"ARCHIVE EMPTY: {exception.Message}");
            return (int)CliExitCode.GeneralError;
        }

        try
        {
            await File.WriteAllBytesAsync(command.File, bytes, cancellationToken);
        }
        catch (Exception exception) when (exception is IOException or UnauthorizedAccessException)
        {
            await output.WriteLineAsync(command.Json
                ? JsonSerializer.Serialize(new { status = "error", error = "STORAGE_IO_FAILURE" })
                : "EXPORT WRITE UNAVAILABLE: STORAGE_IO_FAILURE");
            return (int)CliExitCode.Storage;
        }

        var identity = Convert.ToHexString(SHA256.HashData(bytes)).ToLowerInvariant();
        await output.WriteLineAsync(command.Json
            ? JsonSerializer.Serialize(new { status = "exported", file = command.File, identity, bytes = bytes.Length })
            : $"EXPORTED {identity[..Math.Min(12, identity.Length)]} ({bytes.Length} BYTES) -> {command.File}");
        return (int)CliExitCode.Success;
    }

    /// <summary>Dispatch entry used by <see cref="Program"/>; keeps the verb switch in one place.</summary>
    public static Task<int> HandleAsync(
        PortableCommand command,
        PortableRepository repository,
        TextWriter output,
        CancellationToken cancellationToken) => command switch
    {
        PortableInspectCommand inspect => InspectAsync(inspect, repository, output, cancellationToken),
        PortableImportCommand import => ImportAsync(import, repository, output, cancellationToken),
        PortableExportCommand export => ExportAsync(export, repository, output, cancellationToken),
        _ => throw new InvalidOperationException("UNSUPPORTED PORTABLE COMMAND."),
    };

    private static async Task<byte[]> ReadPackageBytesAsync(
        string path,
        bool json,
        TextWriter output,
        CancellationToken cancellationToken)
    {
        try
        {
            return await File.ReadAllBytesAsync(path, cancellationToken);
        }
        catch (Exception exception) when (exception is FileNotFoundException or DirectoryNotFoundException or UnauthorizedAccessException or IOException)
        {
            await output.WriteLineAsync(json
                ? JsonSerializer.Serialize(new { status = "error", error = "STORAGE_IO_FAILURE" })
                : $"PACKAGE READ UNAVAILABLE: STORAGE_IO_FAILURE ({exception.GetType().Name})");
            throw new PortablePackageReadException();
        }
    }

    /// <summary>The staged-output-safe line RunAsync prints when the package read fails.</summary>
    public const string PackageReadUnavailableJson = "{\"status\":\"error\",\"error\":\"STORAGE_IO_FAILURE\"}";
    public const string PackageReadUnavailableHuman = "PACKAGE READ UNAVAILABLE: STORAGE_IO_FAILURE";

    private static string RenderPreview(PortablePreview preview)
    {
        var lines = new List<string>
        {
            $"IDENTITY {preview.Identity}",
            $"SCOPE {preview.ScopeId} PLATFORM {preview.SourcePlatform}",
            $"GENERATOR {preview.Generator}",
            "COUNTS:",
        };
        foreach (var collection in PortableSpec.Collections)
        {
            lines.Add($"  {collection} {preview.Counts.GetValueOrDefault(collection)}");
        }

        lines.Add("SOURCES:");
        if (preview.Sources.Count == 0)
        {
            lines.Add("  NONE");
        }
        else
        {
            foreach (var source in preview.Sources)
            {
                lines.Add($"  {source.SourceScopeId} {source.RecordCount}");
            }
        }

        lines.Add($"CAPABILITY EDITABLE {preview.EditableCount} / STORED-ONLY {preview.StoredOnlyCount} / REJECTED {preview.RejectedCount}");
        lines.Add("STORED-ONLY RECORDS KEEP SAVED ARCHIVE STATE");
        if (preview.Skipped.Count > 0)
        {
            lines.Add("SKIPPED:");
            foreach (var skipped in preview.Skipped)
            {
                lines.Add($"  {skipped.Collection} {skipped.StableId}");
            }
        }

        if (preview.Conflicts.Count > 0)
        {
            lines.Add("CONFLICTS:");
            foreach (var conflict in preview.Conflicts)
            {
                lines.Add($"  {conflict.Collection} {conflict.StableId} {conflict.Kind} {conflict.Detail}");
            }
        }

        return string.Join(Environment.NewLine, lines);
    }

    private static object RenderPreviewJson(PortablePreview preview) => new
    {
        status = "preview",
        identity = preview.Identity,
        scopeId = preview.ScopeId,
        sourcePlatform = preview.SourcePlatform,
        generator = preview.Generator,
        counts = preview.Counts,
        sources = preview.Sources.Select(s => new { sourceScopeId = s.SourceScopeId, recordCount = s.RecordCount }),
        editable = preview.EditableCount,
        storedOnly = preview.StoredOnlyCount,
        rejected = preview.RejectedCount,
        acceptAll = preview.AcceptAll,
    };

    private static string RenderApply(PortableApplyResult result) => result.Identical
        ? "ARCHIVE ALREADY MATCHES PACKAGE. NO CHANGES APPLIED."
        : $"APPLIED {result.RowsUpserted} UPSERTED / {result.RowsDeleted} DELETED";

    private static object RenderApplyJson(PortableApplyResult result) => new
    {
        status = "applied",
        identity = result.Identity,
        rowsUpserted = result.RowsUpserted,
        rowsDeleted = result.RowsDeleted,
        identical = result.Identical,
        editable = result.EditableCount,
        storedOnly = result.StoredOnlyCount,
        rejected = result.RejectedCount,
    };
}

/// <summary>Internal marker: the package file could not be read; Program maps it to exit code 6.</summary>
public sealed class PortablePackageReadException : Exception;
