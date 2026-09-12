using System.Net;
using System.Net.Http;
using System.Text;
using OjNexus.Windows.Core.Domain;
using OjNexus.Windows.Core.Network;

namespace OjNexus.Windows.Core.Tests;

public sealed class CodeforcesAdapterTests
{
    [Fact]
    public async Task SyncAsync_WithSuccessfulResponses_CountsAllStages()
    {
        var handler = new StubHandler
        {
            ["https://codeforces.com/api/user.info?handles=tourist"] = """{"status":"OK","result":[{"handle":"tourist","rating":3800}]}""",
            ["https://codeforces.com/api/user.rating?handle=tourist"] = """{"status":"OK","result":[{"contestId":1},{"contestId":2}]}""",
            ["https://codeforces.com/api/user.status?handle=tourist&from=1&count=1000"] = """{"status":"OK","result":[{"id":1},{"id":2},{"id":3}]}""",
        };
        var adapter = new CodeforcesAdapter(() => new HttpClient(handler));

        var outcomes = await adapter.SyncAsync(JudgeAccount.Create(JudgeId.Codeforces, "tourist"), CancellationToken.None);

        Assert.Collection(
            outcomes,
            stage =>
            {
                Assert.Equal("PROFILE", stage.Stage);
                Assert.Equal(SyncOperationStatus.Success, stage.Status);
                Assert.Equal(1, stage.ImportedCount);
                Assert.Null(stage.FailureType);
            },
            stage =>
            {
                Assert.Equal("RATING", stage.Stage);
                Assert.Equal(SyncOperationStatus.Success, stage.Status);
                Assert.Equal(2, stage.ImportedCount);
            },
            stage =>
            {
                Assert.Equal("SUBMISSIONS", stage.Stage);
                Assert.Equal(SyncOperationStatus.Success, stage.Status);
                Assert.Equal(3, stage.ImportedCount);
            });
    }

    [Fact]
    public async Task SyncAsync_WithApiFailureStatus_ReportsApiFailurePerStage()
    {
        var handler = new StubHandler
        {
            ["https://codeforces.com/api/user.info?handles=tourist"] = """{"status":"FAILED","comment":"handles: User with handle tourist not found"}""",
        };
        var adapter = new CodeforcesAdapter(() => new HttpClient(handler));

        var outcomes = await adapter.SyncAsync(JudgeAccount.Create(JudgeId.Codeforces, "tourist"), CancellationToken.None);

        Assert.All(outcomes, stage =>
        {
            Assert.Equal(SyncOperationStatus.Error, stage.Status);
            Assert.Equal(nameof(SyncError.Api), stage.FailureType);
            Assert.Equal(0, stage.ImportedCount);
        });
    }

    [Fact]
    public async Task SyncAsync_WithHttpError_ReportsApiFailure()
    {
        var handler = new StubHandler
        {
            ["https://codeforces.com/api/user.info?handles=tourist"] = "server error",
            Status = HttpStatusCode.InternalServerError,
        };
        var adapter = new CodeforcesAdapter(() => new HttpClient(handler));

        var outcomes = await adapter.SyncAsync(JudgeAccount.Create(JudgeId.Codeforces, "tourist"), CancellationToken.None);

        Assert.Equal(SyncOperationStatus.Error, outcomes[0].Status);
        Assert.Equal(nameof(SyncError.Api), outcomes[0].FailureType);
    }

    [Fact]
    public async Task SyncAsync_WithTransportFailure_ReportsNetworkAndOffline()
    {
        var handler = new ThrowingHandler();
        var adapter = new CodeforcesAdapter(() => new HttpClient(handler));

        var outcomes = await adapter.SyncAsync(JudgeAccount.Create(JudgeId.Codeforces, "tourist"), CancellationToken.None);

        Assert.All(outcomes, stage =>
        {
            Assert.Equal(SyncOperationStatus.Offline, stage.Status);
            Assert.Equal(nameof(SyncError.Network), stage.FailureType);
        });
    }

    [Fact]
    public async Task SyncAsync_WithCancellation_ThrowsOperationCanceled()
    {
        var handler = new StubHandler(new Dictionary<string, string>
        {
            ["https://codeforces.com/api/user.info?handles=tourist"] = """{"status":"OK","result":[]}""",
        })
        { Block = true };
        var adapter = new CodeforcesAdapter(() => new HttpClient(handler));
        using var cancellationSource = new CancellationTokenSource();

        var syncTask = adapter.SyncAsync(JudgeAccount.Create(JudgeId.Codeforces, "tourist"), cancellationSource.Token);
        cancellationSource.Cancel();

        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => syncTask);
    }

    private sealed class StubHandler : HttpMessageHandler
    {
        public StubHandler() : this(new Dictionary<string, string>()) { }

        public StubHandler(IReadOnlyDictionary<string, string> responses) => Responses = responses;

        public IReadOnlyDictionary<string, string> Responses { get; }

        public string this[string url]
        {
            set => ((Dictionary<string, string>)Responses)[url] = value;
        }

        public HttpStatusCode Status { get; init; } = HttpStatusCode.OK;

        public bool Block { get; init; }

        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            if (Block)
            {
                await Task.Delay(Timeout.InfiniteTimeSpan, cancellationToken);
            }

            if (!Responses.TryGetValue(request.RequestUri!.ToString(), out var body))
            {
                return new HttpResponseMessage(HttpStatusCode.NotFound);
            }

            return new HttpResponseMessage(Status)
            {
                Content = new StringContent(body, Encoding.UTF8, "application/json"),
            };
        }
    }

    private sealed class ThrowingHandler : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
            Task.FromException<HttpResponseMessage>(new HttpRequestException("DNS failure"));
    }
}
