using System.Net.Http;
using System.Text.Json;
using System.Text.RegularExpressions;
using OjNexus.Windows.Core.Contracts;
using OjNexus.Windows.Core.Domain;

namespace OjNexus.Windows.Core.Network;

public sealed class LuoguAdapter : IJudgeAdapter
{
    private const string ApiBase = "https://www.luogu.com.cn/";
    private static readonly Regex LentilleContext = new(
        "<script\\s+id=\\\"lentille-context\\\"[^>]*>(.*?)</script>",
        RegexOptions.Singleline | RegexOptions.IgnoreCase | RegexOptions.CultureInvariant | RegexOptions.Compiled);
    private readonly Func<HttpClient> _httpClientFactory;

    public LuoguAdapter(Func<HttpClient> httpClientFactory)
    {
        ArgumentNullException.ThrowIfNull(httpClientFactory);
        _httpClientFactory = httpClientFactory;
    }

    public JudgeId Judge => JudgeId.Luogu;

    public async Task<IReadOnlyList<SyncModuleOutcome>> SyncAsync(
        JudgeAccount account,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(account);
        if (!TryNormalizeUser(account.Handle, out var user))
        {
            return [new SyncModuleOutcome("PROFILE", SyncOperationStatus.Error, 0, 0, 0, nameof(SyncError.InvalidConfiguration))];
        }

        using var httpClient = _httpClientFactory();
        var profile = await FetchAsync(
            httpClient,
            $"api/user/info/{user}",
            body => ParseProfile(body, account.Handle.Trim()),
            cancellationToken);
        var submissions = await FetchAsync(
            httpClient,
            $"record/list?user={user}&page=1&_contentOnly=1",
            body => ParseContentList(body, "records", account.Handle.Trim()),
            cancellationToken);
        var contests = await FetchAsync(
            httpClient,
            "contest/list?page=1&_contentOnly=1",
            body => ParseContentList(body, "contests", account.Handle.Trim()),
            cancellationToken);
        var problems = await FetchAsync(
            httpClient,
            "problem/list?page=1&_contentOnly=1",
            body => ParseContentList(body, "problems", account.Handle.Trim()),
            cancellationToken);

        return
        [
            ToOutcome("PROFILE", profile),
            ToOutcome("SUBMISSIONS", submissions),
            ToOutcome("CONTESTS", contests),
            ToOutcome("PROBLEMSET", problems),
        ];
    }

    private static SyncModuleOutcome ToOutcome(string stage, StageResult result) =>
        new(stage, result.Status, result.Count, result.Count, 0, result.FailureType, result.Payload);

    private static bool TryNormalizeUser(string handle, out string user)
    {
        user = handle.Trim();
        if (user.StartsWith("uid:", StringComparison.OrdinalIgnoreCase))
        {
            user = user[4..];
        }

        return user.Length is >= 1 and <= 20
            && ulong.TryParse(user, out var value)
            && value > 0;
    }

    private static StageResult ParseProfile(string body, string handle)
    {
        using var document = JsonDocument.Parse(body);
        var root = document.RootElement;
        if (root.ValueKind != JsonValueKind.Object
            || !root.TryGetProperty("user", out var user)
            || user.ValueKind != JsonValueKind.Object
            || user.EnumerateObject().Any() is false)
        {
            throw new JsonException("Luogu profile response is missing user data.");
        }

        var userId = GetInt64(user, "uid");
        var displayName = GetString(user, "name") ?? handle;
        return new StageResult(
            1,
            SyncOperationStatus.Success,
            null,
            new LuoguProfilePayload(
                handle,
                userId,
                displayName,
                GetNullableInt32(user, "eloValue")));
    }

    private static StageResult ParseContentList(string body, string key, string handle)
    {
        using var document = ParseContentDocument(body);
        var root = document.RootElement;
        var content = root;
        if (content.ValueKind != JsonValueKind.Object)
        {
            throw new JsonException("Luogu content response is not an object.");
        }

        if (content.TryGetProperty("currentData", out var currentData) && currentData.ValueKind == JsonValueKind.Object)
        {
            content = currentData;
        }
        else if (content.TryGetProperty("data", out var data) && data.ValueKind == JsonValueKind.Object)
        {
            content = data;
        }

        if (!content.TryGetProperty(key, out var list))
        {
            throw new JsonException($"Luogu content response is missing {key}.");
        }

        if (list.ValueKind == JsonValueKind.Array)
        {
            return CollectionResult(handle, key, list.GetArrayLength());
        }

        if (list.ValueKind == JsonValueKind.Object
            && list.TryGetProperty("result", out var result)
            && result.ValueKind == JsonValueKind.Array)
        {
            return CollectionResult(handle, key, result.GetArrayLength());
        }

        throw new JsonException($"Luogu content response has invalid {key}.");
    }

    private static StageResult CollectionResult(string handle, string key, int count) =>
        new(
            count,
            SyncOperationStatus.Success,
            null,
            new LuoguCollectionPayload(handle, key switch
            {
                "records" => "SUBMISSIONS",
                "contests" => "CONTESTS",
                "problems" => "PROBLEMSET",
                _ => key.ToUpperInvariant(),
            }, count));

    private static JsonDocument ParseContentDocument(string body)
    {
        try
        {
            return JsonDocument.Parse(body);
        }
        catch (JsonException)
        {
            var match = LentilleContext.Match(body);
            if (!match.Success)
            {
                throw;
            }

            return JsonDocument.Parse(match.Groups[1].Value);
        }
    }

    private static string? GetString(JsonElement element, string propertyName) =>
        element.ValueKind == JsonValueKind.Object
            && element.TryGetProperty(propertyName, out var value)
            && value.ValueKind == JsonValueKind.String
            ? value.GetString()
            : null;

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
        Func<string, StageResult> parse,
        CancellationToken cancellationToken)
    {
        try
        {
            using var response = await httpClient.GetAsync(ApiBase + requestUri, cancellationToken);
            if (!response.IsSuccessStatusCode)
            {
                return new StageResult(0, SyncOperationStatus.Error, nameof(SyncError.Api));
            }

            var body = await response.Content.ReadAsStringAsync(cancellationToken);
            return parse(body);
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            throw;
        }
        catch (TaskCanceledException)
        {
            return new StageResult(0, SyncOperationStatus.Offline, nameof(SyncError.Network));
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

    private readonly record struct StageResult(
        int Count,
        SyncOperationStatus Status,
        string? FailureType,
        SyncModulePayload? Payload = null);
}
