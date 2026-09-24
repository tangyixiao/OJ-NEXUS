using System.Text.Json;
using OjNexus.Windows.Core.Domain;

namespace OjNexus.Windows.Core.Sync;

public static class SyncReportProjector
{
    public static string ToJson(SyncReport report)
    {
        ArgumentNullException.ThrowIfNull(report);

        var modules = report.Operation.Modules;
        return JsonSerializer.Serialize(new
        {
            operation = new
            {
                id = report.Operation.Id,
                status = report.Operation.Status.ToString(),
                judge = report.Operation.Account.Judge.ToString(),
                moduleCounts = new
                {
                    total = modules.Count,
                    successful = modules.Count(module => module.Status == SyncOperationStatus.Success),
                    failed = modules.Count(module => module.Status != SyncOperationStatus.Success),
                },
            },
            status = report.Status.ToString(),
            error = report.Error?.ToString(),
        });
    }
}
