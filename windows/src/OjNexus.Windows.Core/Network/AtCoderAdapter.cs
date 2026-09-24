using System.Net.Http;
using System.Text.Json;
using OjNexus.Windows.Core.Contracts;
using OjNexus.Windows.Core.Domain;

namespace OjNexus.Windows.Core.Network;

public sealed class AtCoderAdapter : IJudgeAdapter
{
    private const string ApiBase = "https://kenkoooo.com/atcoder/atcoder-api/v3/";
    private readonly Func<HttpClient> _httpClientFactory;

    public AtCoderAdapter(Func<HttpClient> httpClientFactory)
    {
        ArgumentNullException.ThrowIfNull(httpClientFactory);
        _httpClientFactory = httpClientFactory;
    }

    public JudgeId Judge => JudgeId.AtCoder;

    public async Task<IReadOnlyList<SyncModuleOutcome>> SyncAsync(
        JudgeAccount account,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(account);
        if (!IsValidHandle(account.Handle))
        {
            return [new SyncModuleOutcome("SUBMISSIONS", SyncOperationStatus.Error, 0, 0, 0, nameof(SyncError.InvalidConfiguration))];
        }

        using var httpClient = _httpClientFactory();
        var result = await FetchAsync(
            httpClient,
            $"user/submissions?user={Uri.EscapeDataString(account.Handle)}&from_second=0",
            account.Handle,
            cancellationToken);
        return [new SyncModuleOutcome("SUBMISSIONS", result.Status, result.Count, result.Count, 0, result.FailureType, result.Payload)];
    }

    private static bool IsValidHandle(string handle)
    {
        if (handle.Length is < 1 or > 20)
        {
            return false;
        }

        foreach (var character in handle)
        {
            if (!((character is >= 'A' and <= 'Z')
                || (character is >= 'a' and <= 'z')
                || (character is >= '0' and <= '9')
                || character == '_'))
            {
                return false;
            }
        }

        return true;
    }

    private static StageResult Parse(JsonDocument document, string handle)
    {
        if (document.RootElement.ValueKind != JsonValueKind.Array)
        {
            throw new JsonException("AtCoder submissions response is not an array.");
        }

        var submissions = new List<AtCoderSubmission>();
        foreach (var item in document.RootElement.EnumerateArray())
        {
            submissions.Add(new AtCoderSubmission(
                GetInt64(item, "id"),
                GetInt64(item, "epoch_second"),
                GetString(item, "problem_id") ?? string.Empty,
                GetString(item, "contest_id") ?? string.Empty,
                GetString(item, "language") ?? string.Empty,
                GetDouble(item, "point"),
                GetInt32(item, "length"),
                GetString(item, "result") ?? string.Empty,
                GetInt64(item, "execution_time")));
        }

        return new StageResult(
            submissions.Count,
            SyncOperationStatus.Success,
            null,
            new AtCoderSubmissionsPayload(handle, submissions));
    }

    private async Task<StageResult> FetchAsync(
        HttpClient httpClient,
        string requestUri,
        string handle,
        CancellationToken cancellationToken)
    {
        try
        {
            using var response = await httpClient.GetAsync(ApiBase + requestUri, cancellationToken);
            if (!response.IsSuccessStatusCode)
            {
                return new StageResult(0, SyncOperationStatus.Error, nameof(SyncError.Api), null);
            }

            using var stream = await response.Content.ReadAsStreamAsync(cancellationToken);
            using var document = await JsonDocument.ParseAsync(stream, cancellationToken: cancellationToken);
            return Parse(document, handle);
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            throw;
        }
        catch (HttpRequestException)
        {
            return new StageResult(0, SyncOperationStatus.Offline, nameof(SyncError.Network), null);
        }
        catch (JsonException)
        {
            return new StageResult(0, SyncOperationStatus.Error, nameof(SyncError.Api), null);
        }
    }

    private static string? GetString(JsonElement element, string propertyName) =>
        element.ValueKind == JsonValueKind.Object
            && element.TryGetProperty(propertyName, out var value)
            && value.ValueKind == JsonValueKind.String
            ? value.GetString()
            : null;

    private static int GetInt32(JsonElement element, string propertyName) =>
        element.ValueKind == JsonValueKind.Object
            && element.TryGetProperty(propertyName, out var value)
            && value.ValueKind == JsonValueKind.Number
            && value.TryGetInt32(out var result)
            ? result
            : 0;

    private static long GetInt64(JsonElement element, string propertyName) =>
        element.ValueKind == JsonValueKind.Object
            && element.TryGetProperty(propertyName, out var value)
            && value.ValueKind == JsonValueKind.Number
            && value.TryGetInt64(out var result)
            ? result
            : 0;

    private static double GetDouble(JsonElement element, string propertyName) =>
        element.ValueKind == JsonValueKind.Object
            && element.TryGetProperty(propertyName, out var value)
            && value.ValueKind == JsonValueKind.Number
            && value.TryGetDouble(out var result)
            ? result
            : 0;

    private readonly record struct StageResult(int Count, SyncOperationStatus Status, string? FailureType, SyncModulePayload? Payload);
}
