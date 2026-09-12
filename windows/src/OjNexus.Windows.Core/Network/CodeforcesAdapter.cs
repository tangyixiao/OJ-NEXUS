using System.Net.Http;
using System.Text.Json;
using OjNexus.Windows.Core.Contracts;
using OjNexus.Windows.Core.Domain;

namespace OjNexus.Windows.Core.Network;

public sealed class CodeforcesAdapter : IJudgeAdapter
{
    private const string ApiBase = "https://codeforces.com/api/";
    private readonly Func<HttpClient> _httpClientFactory;

    public CodeforcesAdapter(Func<HttpClient> httpClientFactory)
    {
        ArgumentNullException.ThrowIfNull(httpClientFactory);
        _httpClientFactory = httpClientFactory;
    }

    public JudgeId Judge => JudgeId.Codeforces;

    public async Task<IReadOnlyList<SyncModuleOutcome>> SyncAsync(
        JudgeAccount account,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(account);
        using var httpClient = _httpClientFactory();

        var profile = await FetchCountAsync(
            httpClient,
            $"user.info?handles={Uri.EscapeDataString(account.Handle)}",
            static document => document.RootElement.TryGetProperty("result", out var result)
                && result.ValueKind == JsonValueKind.Array
                && result.GetArrayLength() > 0
                ? 1 : 0,
            cancellationToken);

        var rating = await FetchCountAsync(
            httpClient,
            $"user.rating?handle={Uri.EscapeDataString(account.Handle)}",
            static document => CountArray(document, "result"),
            cancellationToken);

        var submissions = await FetchCountAsync(
            httpClient,
            $"user.status?handle={Uri.EscapeDataString(account.Handle)}&from=1&count=1000",
            static document => CountArray(document, "result"),
            cancellationToken);

        return
        [
            new SyncModuleOutcome("PROFILE", profile.Status, profile.Count, profile.Count, 0, profile.FailureType),
            new SyncModuleOutcome("RATING", rating.Status, rating.Count, rating.Count, 0, rating.FailureType),
            new SyncModuleOutcome("SUBMISSIONS", submissions.Status, submissions.Count, submissions.Count, 0, submissions.FailureType),
        ];
    }

    private static int CountArray(JsonDocument document, string propertyName) =>
        document.RootElement.TryGetProperty(propertyName, out var array)
            && array.ValueKind == JsonValueKind.Array
            ? array.GetArrayLength() : 0;

    private async Task<StageResult> FetchCountAsync(
        HttpClient httpClient,
        string requestUri,
        Func<JsonDocument, int> count,
        CancellationToken cancellationToken)
    {
        try
        {
            using var response = await httpClient.GetAsync(ApiBase + requestUri, cancellationToken);
            if (!response.IsSuccessStatusCode)
            {
                return new StageResult(0, SyncOperationStatus.Error, nameof(SyncError.Api));
            }

            using var stream = await response.Content.ReadAsStreamAsync(cancellationToken);
            using var document = await JsonDocument.ParseAsync(stream, cancellationToken: cancellationToken);
            if (!document.RootElement.TryGetProperty("status", out var status)
                || status.ValueKind != JsonValueKind.String
                || !string.Equals(status.GetString(), "OK", StringComparison.Ordinal))
            {
                return new StageResult(0, SyncOperationStatus.Error, nameof(SyncError.Api));
            }

            var imported = count(document);
            return new StageResult(imported, SyncOperationStatus.Success, null);
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            throw;
        }
        catch (HttpRequestException)
        {
            return new StageResult(0, SyncOperationStatus.Offline, nameof(SyncError.Network));
        }
        catch (JsonException)
        {
            return new StageResult(0, SyncOperationStatus.Error, nameof(SyncError.Api));
        }
    }

    private readonly record struct StageResult(int Count, SyncOperationStatus Status, string? FailureType);
}
