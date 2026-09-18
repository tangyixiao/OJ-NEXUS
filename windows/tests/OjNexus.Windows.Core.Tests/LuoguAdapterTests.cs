using System.Net;
using System.Text;
using OjNexus.Windows.Core.Domain;
using OjNexus.Windows.Core.Network;

namespace OjNexus.Windows.Core.Tests;

public sealed class LuoguAdapterTests
{
    [Fact]
    public async Task SyncAsync_WithPublicResponses_ParsesFourStages()
    {
        var handler = new StubHandler
        {
            ["https://www.luogu.com.cn/api/user/info/2"] = """{"user":{"uid":2,"name":"demo"}}""",
            ["https://www.luogu.com.cn/record/list?user=2&page=1&_contentOnly=1"] = Content("records", """[{"id":11,"pid":"P1001","status":"Accepted"}]"""),
            ["https://www.luogu.com.cn/contest/list?page=1&_contentOnly=1"] = Content("contests", """[{"id":12,"name":"Demo Contest"}]"""),
            ["https://www.luogu.com.cn/problem/list?page=1&_contentOnly=1"] = Content("problems", """[{"pid":"P1001","name":"A+B Problem"}]"""),
        };
        var adapter = new LuoguAdapter(() => new HttpClient(handler));

        var outcomes = await adapter.SyncAsync(JudgeAccount.Create(JudgeId.Luogu, "uid:2"), CancellationToken.None);

        Assert.Collection(
            outcomes,
            stage =>
            {
                AssertStage(stage, "PROFILE", 1);
                var profile = Assert.IsType<LuoguProfilePayload>(stage.Payload);
                Assert.Equal(new LuoguProfilePayload("uid:2", 2, "demo", null), profile);
            },
            stage =>
            {
                AssertStage(stage, "SUBMISSIONS", 1);
                Assert.Equal(new LuoguCollectionPayload("uid:2", "SUBMISSIONS", 1), stage.Payload);
            },
            stage =>
            {
                AssertStage(stage, "CONTESTS", 1);
                Assert.Equal(new LuoguCollectionPayload("uid:2", "CONTESTS", 1), stage.Payload);
            },
            stage =>
            {
                AssertStage(stage, "PROBLEMSET", 1);
                Assert.Equal(new LuoguCollectionPayload("uid:2", "PROBLEMSET", 1), stage.Payload);
            });
    }

    [Theory]
    [InlineData(HttpStatusCode.Unauthorized)]
    [InlineData(HttpStatusCode.Forbidden)]
    public async Task SyncAsync_WhenAnonymousSubmissionsAreGated_ReportsAuthenticationFailureOnlyForThatStage(
        HttpStatusCode gatedStatus)
    {
        var handler = new StubHandler
        {
            ["https://www.luogu.com.cn/api/user/info/2"] = """{"user":{"uid":2,"name":"demo"}}""",
            ["https://www.luogu.com.cn/record/list?user=2&page=1&_contentOnly=1"] = string.Empty,
            ["https://www.luogu.com.cn/contest/list?page=1&_contentOnly=1"] = Content("contests", "[]"),
            ["https://www.luogu.com.cn/problem/list?page=1&_contentOnly=1"] = Content("problems", "[]"),
            Status = gatedStatus,
            StatusByUrl = new Dictionary<string, HttpStatusCode>
            {
                ["https://www.luogu.com.cn/api/user/info/2"] = HttpStatusCode.OK,
                ["https://www.luogu.com.cn/contest/list?page=1&_contentOnly=1"] = HttpStatusCode.OK,
                ["https://www.luogu.com.cn/problem/list?page=1&_contentOnly=1"] = HttpStatusCode.OK,
            },
        };
        var adapter = new LuoguAdapter(() => new HttpClient(handler));

        var outcomes = await adapter.SyncAsync(JudgeAccount.Create(JudgeId.Luogu, "2"), CancellationToken.None);

        Assert.Equal(SyncOperationStatus.Success, outcomes[0].Status);
        Assert.Equal(SyncOperationStatus.Error, outcomes[1].Status);
        Assert.Equal(nameof(SyncError.Authentication), outcomes[1].FailureType);
        Assert.Equal(SyncOperationStatus.Success, outcomes[2].Status);
        Assert.Equal(SyncOperationStatus.Success, outcomes[3].Status);
    }

    [Fact]
    public async Task SyncAsync_WithInvalidUserHandle_RejectsBeforeHttp()
    {
        var called = false;
        var adapter = new LuoguAdapter(() =>
        {
            called = true;
            return new HttpClient(new StubHandler());
        });

        var outcomes = await adapter.SyncAsync(new JudgeAccount(JudgeId.Luogu, "demo"), CancellationToken.None);

        var outcome = Assert.Single(outcomes);
        Assert.Equal("PROFILE", outcome.Stage);
        Assert.Equal(SyncOperationStatus.Error, outcome.Status);
        Assert.Equal(nameof(SyncError.InvalidConfiguration), outcome.FailureType);
        Assert.False(called);
    }

    private static string Content(string key, string value) =>
        $"<script id=\"lentille-context\" type=\"application/json\">{{\"data\":{{\"{key}\":{{\"result\":{value}}}}}}}</script>";

    private static void AssertStage(SyncModuleOutcome stage, string name, int count)
    {
        Assert.Equal(name, stage.Stage);
        Assert.Equal(SyncOperationStatus.Success, stage.Status);
        Assert.Equal(count, stage.ImportedCount);
        Assert.Null(stage.FailureType);
    }

    private sealed class StubHandler : HttpMessageHandler
    {
        private readonly Dictionary<string, string> _responses = new(StringComparer.Ordinal);

        public string this[string url]
        {
            set => _responses[url] = value;
        }

        public HttpStatusCode Status { get; init; } = HttpStatusCode.OK;

        public IReadOnlyDictionary<string, HttpStatusCode> StatusByUrl { get; init; } =
            new Dictionary<string, HttpStatusCode>(StringComparer.Ordinal);

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            var url = request.RequestUri!.ToString();
            var status = StatusByUrl.TryGetValue(url, out var specificStatus) ? specificStatus : Status;
            _responses.TryGetValue(url, out var body);
            return Task.FromResult(new HttpResponseMessage(status)
            {
                Content = new StringContent(body ?? string.Empty, Encoding.UTF8, "text/html"),
            });
        }
    }
}
