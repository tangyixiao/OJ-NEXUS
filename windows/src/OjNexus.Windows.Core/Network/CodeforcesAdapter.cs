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

        var profile = await FetchAsync(
            httpClient,
            $"user.info?handles={Uri.EscapeDataString(account.Handle)}",
            document => ParseProfile(document, account.Handle),
            cancellationToken);

        var rating = await FetchAsync(
            httpClient,
            $"user.rating?handle={Uri.EscapeDataString(account.Handle)}",
            document => ParseRatings(document, account.Handle),
            cancellationToken);

        var submissions = await FetchAsync(
            httpClient,
            $"user.status?handle={Uri.EscapeDataString(account.Handle)}&from=1&count=1000",
            document => ParseSubmissions(document, account.Handle),
            cancellationToken);

        return
        [
            new SyncModuleOutcome("PROFILE", profile.Status, profile.Count, profile.Count, 0, profile.FailureType, profile.Payload),
            new SyncModuleOutcome("RATING", rating.Status, rating.Count, rating.Count, 0, rating.FailureType, rating.Payload),
            new SyncModuleOutcome("SUBMISSIONS", submissions.Status, submissions.Count, submissions.Count, 0, submissions.FailureType, submissions.Payload),
        ];
    }

    private static StageResult ParseProfile(JsonDocument document, string handle)
    {
        if (!TryGetResultArray(document, out var result) || result.GetArrayLength() == 0)
        {
            return new StageResult(0, SyncOperationStatus.Success, null, null);
        }

        var user = result[0];
        return new StageResult(
            1,
            SyncOperationStatus.Success,
            null,
            new CodeforcesProfilePayload(
                GetString(user, "handle") ?? handle,
                GetNullableInt32(user, "rating"),
                GetString(user, "rank"),
                GetNullableInt32(user, "maxRating"),
                GetString(user, "maxRank")));
    }

    private static StageResult ParseRatings(JsonDocument document, string handle)
    {
        var entries = new List<CodeforcesRating>();
        if (TryGetResultArray(document, out var result))
        {
            foreach (var item in result.EnumerateArray())
            {
                entries.Add(new CodeforcesRating(
                    GetInt32(item, "contestId"),
                    GetString(item, "contestName") ?? string.Empty,
                    GetInt32(item, "rank"),
                    GetInt64(item, "ratingUpdateTimeSeconds"),
                    GetInt32(item, "oldRating"),
                    GetInt32(item, "newRating")));
            }
        }

        return new StageResult(entries.Count, SyncOperationStatus.Success, null, new CodeforcesRatingsPayload(handle, entries));
    }

    private static StageResult ParseSubmissions(JsonDocument document, string handle)
    {
        var entries = new List<CodeforcesSubmission>();
        if (TryGetResultArray(document, out var result))
        {
            foreach (var item in result.EnumerateArray())
            {
                var problem = item.TryGetProperty("problem", out var problemElement)
                    && problemElement.ValueKind == JsonValueKind.Object
                    ? problemElement
                    : default;
                entries.Add(new CodeforcesSubmission(
                    GetInt64(item, "id"),
                    GetNullableInt32(item, "contestId"),
                    GetString(problem, "index"),
                    GetString(problem, "name"),
                    GetString(item, "verdict"),
                    GetString(item, "programmingLanguage") ?? string.Empty,
                    GetInt32(item, "passedTestCount"),
                    GetInt32(item, "timeConsumedMillis"),
                    GetInt64(item, "memoryConsumedBytes"),
                    GetInt64(item, "creationTimeSeconds")));
            }
        }

        return new StageResult(entries.Count, SyncOperationStatus.Success, null, new CodeforcesSubmissionsPayload(handle, entries));
    }

    private static bool TryGetResultArray(JsonDocument document, out JsonElement result) =>
        document.RootElement.TryGetProperty("result", out result) && result.ValueKind == JsonValueKind.Array;

    private static string? GetString(JsonElement element, string propertyName) =>
        element.ValueKind == JsonValueKind.Object
            && element.TryGetProperty(propertyName, out var value)
            && value.ValueKind == JsonValueKind.String
            ? value.GetString()
            : null;

    private static int GetInt32(JsonElement element, string propertyName) =>
        GetNullableInt32(element, propertyName) ?? 0;

    private static int? GetNullableInt32(JsonElement element, string propertyName) =>
        element.ValueKind == JsonValueKind.Object
            && element.TryGetProperty(propertyName, out var value)
            && value.ValueKind == JsonValueKind.Number
            && value.TryGetInt32(out var result)
            ? result
            : null;

    private static long GetInt64(JsonElement element, string propertyName) =>
        element.ValueKind == JsonValueKind.Object
            && element.TryGetProperty(propertyName, out var value)
            && value.ValueKind == JsonValueKind.Number
            && value.TryGetInt64(out var result)
            ? result
            : 0;

    private async Task<StageResult> FetchAsync(
        HttpClient httpClient,
        string requestUri,
        Func<JsonDocument, StageResult> parse,
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
            if (!document.RootElement.TryGetProperty("status", out var status)
                || status.ValueKind != JsonValueKind.String
                || !string.Equals(status.GetString(), "OK", StringComparison.Ordinal))
            {
                return new StageResult(0, SyncOperationStatus.Error, nameof(SyncError.Api), null);
            }

            return parse(document);
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

    private readonly record struct StageResult(int Count, SyncOperationStatus Status, string? FailureType, SyncModulePayload? Payload);
}
