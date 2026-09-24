using System.Net;
using System.Net.Http;
using System.Text;
using OjNexus.Windows.Core.Domain;
using OjNexus.Windows.Core.Network;

namespace OjNexus.Windows.Core.Tests;

public sealed class AtCoderAdapterTests
{
    [Fact]
    public async Task SyncAsync_WithSuccessfulResponse_ParsesPublicSubmissions()
    {
        var handler = new StubHandler
        {
            ["https://kenkoooo.com/atcoder/atcoder-api/v3/user/submissions?user=tourist&from_second=0"] =
                """[{"id":101,"epoch_second":1700000000,"problem_id":"abc300_a","contest_id":"abc300","user_id":"tourist","language":"C++ (GCC 12.2.0)","point":100,"length":1234,"result":"AC","execution_time":42},{"id":102,"epoch_second":1700000001,"problem_id":"abc300_b","contest_id":"abc300","language":"Python (CPython 3.11.4)","point":0,"length":2345,"result":"WA","execution_time":0}]""",
        };
        var adapter = new AtCoderAdapter(() => new HttpClient(handler));

        var outcomes = await adapter.SyncAsync(JudgeAccount.Create(JudgeId.AtCoder, " tourist "), CancellationToken.None);

        var outcome = Assert.Single(outcomes);
        Assert.Equal("SUBMISSIONS", outcome.Stage);
        Assert.Equal(SyncOperationStatus.Success, outcome.Status);
        Assert.Equal(2, outcome.ImportedCount);
        var payload = Assert.IsType<AtCoderSubmissionsPayload>(outcome.Payload);
        Assert.Equal("tourist", payload.Handle);
        Assert.Equal(
            [
                new AtCoderSubmission(101, 1_700_000_000, "abc300_a", "abc300", "C++ (GCC 12.2.0)", 100, 1234, "AC", 42),
                new AtCoderSubmission(102, 1_700_000_001, "abc300_b", "abc300", "Python (CPython 3.11.4)", 0, 2345, "WA", 0),
            ],
            payload.Items);
    }

    [Fact]
    public async Task SyncAsync_WithTransportFailure_ReportsOfflineWithoutPayload()
    {
        var adapter = new AtCoderAdapter(static () => new HttpClient(new ThrowingHandler()));

        var outcome = Assert.Single(await adapter.SyncAsync(JudgeAccount.Create(JudgeId.AtCoder, "tourist"), CancellationToken.None));

        Assert.Equal(SyncOperationStatus.Offline, outcome.Status);
        Assert.Equal(nameof(SyncError.Network), outcome.FailureType);
        Assert.Null(outcome.Payload);
    }

    [Fact]
    public async Task SyncAsync_WithInvalidHandle_RejectsBeforeCreatingHttpClient()
    {
        var factoryCalls = 0;
        var adapter = new AtCoderAdapter(() =>
        {
            factoryCalls++;
            return new HttpClient();
        });

        var outcome = Assert.Single(await adapter.SyncAsync(new JudgeAccount(JudgeId.AtCoder, "tourist name"), CancellationToken.None));

        Assert.Equal(SyncOperationStatus.Error, outcome.Status);
        Assert.Equal(nameof(SyncError.InvalidConfiguration), outcome.FailureType);
        Assert.Equal(0, factoryCalls);
    }

    private sealed class StubHandler : HttpMessageHandler
    {
        private readonly Dictionary<string, string> _responses = new(StringComparer.Ordinal);

        public string this[string url]
        {
            set => _responses[url] = value;
        }

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            if (!_responses.TryGetValue(request.RequestUri!.ToString(), out var body))
            {
                return Task.FromResult(new HttpResponseMessage(HttpStatusCode.NotFound));
            }

            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(body, Encoding.UTF8, "application/json"),
            });
        }
    }

    private sealed class ThrowingHandler : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
            Task.FromException<HttpResponseMessage>(new HttpRequestException("DNS failure"));
    }
}
