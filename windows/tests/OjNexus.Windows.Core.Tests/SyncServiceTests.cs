using OjNexus.Windows.Core.Contracts;
using OjNexus.Windows.Core.Domain;
using OjNexus.Windows.Core.Sync;

namespace OjNexus.Windows.Core.Tests;

public sealed class SyncServiceTests
{
    private static readonly DateTimeOffset Now = DateTimeOffset.Parse("2026-09-08T01:02:03+00:00");

    [Fact]
    public async Task RunAsync_Success_AppendsAdapterModulesInOrderAndClosesOperation()
    {
        var account = JudgeAccount.Create(JudgeId.Codeforces, "tourist");
        var adapter = new RecordingAdapter(JudgeId.Codeforces,
        [
            new SyncModuleOutcome("PROFILE", SyncOperationStatus.Success, 1, 1, 0, null),
            new SyncModuleOutcome("SUBMISSIONS", SyncOperationStatus.Success, 3, 2, 1, null),
        ]);
        var store = new InMemorySyncStore();
        var service = CreateService(adapter, store);

        var report = await service.RunAsync(account, force: false, CancellationToken.None);

        Assert.Equal(SyncOperationStatus.Success, report.Status);
        Assert.Null(report.Error);
        Assert.Equal(1, adapter.CallCount);
        var operation = Assert.Single(await store.GetRecentOperationsAsync(JudgeId.Codeforces, 10, CancellationToken.None));
        Assert.Equal(SyncOperationStatus.Success, operation.Status);
        Assert.Equal(Now, operation.StartedAt);
        Assert.Equal(Now, operation.FinishedAt);
        Assert.Equal(new[] { "PROFILE", "SUBMISSIONS" }, operation.Modules.Select(module => module.Stage));
    }

    [Fact]
    public async Task RunAsync_PartialModuleFailure_ClosesAsPartialWithoutAnUntypedReportError()
    {
        var account = JudgeAccount.Create(JudgeId.AtCoder, "tourist");
        var adapter = new RecordingAdapter(JudgeId.AtCoder,
        [
            new SyncModuleOutcome("PROFILE", SyncOperationStatus.Success, 1, 1, 0, null),
            new SyncModuleOutcome("SUBMISSIONS", SyncOperationStatus.Error, 4, 0, 0, "Api"),
        ]);
        var store = new InMemorySyncStore();

        var report = await CreateService(adapter, store).RunAsync(account, force: false, CancellationToken.None);

        Assert.Equal(SyncOperationStatus.Partial, report.Status);
        Assert.Null(report.Error);
        var operation = Assert.Single(await store.GetRecentOperationsAsync(JudgeId.AtCoder, 10, CancellationToken.None));
        Assert.Equal(SyncOperationStatus.Partial, operation.Status);
        Assert.Equal(new[] { "PROFILE", "SUBMISSIONS" }, operation.Modules.Select(module => module.Stage));
    }

    [Fact]
    public async Task RunAsync_AdapterException_MapsToNetworkAndDoesNotFabricateModules()
    {
        var account = JudgeAccount.Create(JudgeId.Luogu, "tourist");
        var adapter = new ThrowingAdapter(JudgeId.Luogu, new InvalidOperationException("secret HTTP body"));
        var store = new InMemorySyncStore();

        var report = await CreateService(adapter, store).RunAsync(account, force: false, CancellationToken.None);

        Assert.Equal(SyncOperationStatus.Error, report.Status);
        Assert.Equal(SyncError.Network, report.Error);
        var operation = Assert.Single(await store.GetRecentOperationsAsync(JudgeId.Luogu, 10, CancellationToken.None));
        Assert.Equal(SyncOperationStatus.Error, operation.Status);
        Assert.Empty(operation.Modules);
    }

    [Fact]
    public async Task RunAsync_Cancellation_ClosesOperationAsCancelled()
    {
        var account = JudgeAccount.Create(JudgeId.Codeforces, "tourist");
        var adapter = new CancellingAdapter(JudgeId.Codeforces);
        var store = new InMemorySyncStore();
        using var cancellation = new CancellationTokenSource();

        var run = CreateService(adapter, store).RunAsync(account, force: false, cancellation.Token);
        await adapter.Started.Task.WaitAsync(TimeSpan.FromSeconds(5));
        cancellation.Cancel();
        var report = await run;

        Assert.Equal(SyncOperationStatus.Cancelled, report.Status);
        Assert.Equal(SyncError.Cancelled, report.Error);
        var operation = Assert.Single(await store.GetRecentOperationsAsync(JudgeId.Codeforces, 10, CancellationToken.None));
        Assert.Equal(SyncOperationStatus.Cancelled, operation.Status);
        Assert.Empty(operation.Modules);
    }

    [Fact]
    public async Task RunAsync_MissingAdapter_ClosesOperationAsUnsupportedJudge()
    {
        var account = JudgeAccount.Create(JudgeId.AtCoder, "tourist");
        var store = new InMemorySyncStore();
        var service = new SyncService(
            new Dictionary<JudgeId, IJudgeAdapter>(),
            store,
            new TestClock(Now),
            () => "generation-4");

        var report = await service.RunAsync(account, force: false, CancellationToken.None);

        Assert.Equal(SyncOperationStatus.Error, report.Status);
        Assert.Equal(SyncError.UnsupportedJudge, report.Error);
        var operation = Assert.Single(await store.GetRecentOperationsAsync(JudgeId.AtCoder, 10, CancellationToken.None));
        Assert.Equal(SyncOperationStatus.Error, operation.Status);
        Assert.Empty(operation.Modules);
    }

    [Fact]
    public async Task RunAsync_DisabledAccount_RejectsUnlessForcedAndStillRecordsTheTerminalOperation()
    {
        var account = new JudgeAccount(JudgeId.Codeforces, "tourist", Enabled: false);
        var adapter = new RecordingAdapter(JudgeId.Codeforces,
        [new SyncModuleOutcome("PROFILE", SyncOperationStatus.Success, 1, 1, 0, null)]);
        var store = new InMemorySyncStore();
        var service = CreateService(adapter, store);

        var rejected = await service.RunAsync(account, force: false, CancellationToken.None);
        var forced = await service.RunAsync(account, force: true, CancellationToken.None);

        Assert.Equal(SyncError.InvalidConfiguration, rejected.Error);
        Assert.Equal(SyncOperationStatus.Error, rejected.Status);
        Assert.Equal(SyncOperationStatus.Success, forced.Status);
        Assert.Equal(1, adapter.CallCount);
        var operations = await store.GetRecentOperationsAsync(JudgeId.Codeforces, 10, CancellationToken.None);
        Assert.Equal(2, operations.Count);
        Assert.Empty(operations.Single(operation => operation.Id == rejected.Operation.Id).Modules);
    }

    [Fact]
    public async Task RunAsync_SameJudgeAndAccount_UsesOneAdapterAtATime()
    {
        var account = JudgeAccount.Create(JudgeId.Codeforces, "tourist");
        var adapter = new BlockingAdapter(JudgeId.Codeforces,
        [new SyncModuleOutcome("PROFILE", SyncOperationStatus.Success, 1, 1, 0, null)]);
        var store = new InMemorySyncStore();
        var service = CreateService(adapter, store);

        var first = service.RunAsync(account, force: false, CancellationToken.None);
        await adapter.FirstCallStarted.Task.WaitAsync(TimeSpan.FromSeconds(5));
        var second = service.RunAsync(account, force: false, CancellationToken.None);
        await Task.Yield();
        Assert.Equal(1, adapter.CallCount);

        adapter.AllowCallsToFinish.SetResult();
        await Task.WhenAll(first, second);

        Assert.Equal(2, adapter.CallCount);
        var operations = await store.GetRecentOperationsAsync(JudgeId.Codeforces, 10, CancellationToken.None);
        Assert.Equal(2, operations.Count);
        Assert.All(operations, operation => Assert.Equal(SyncOperationStatus.Success, operation.Status));
    }

    [Fact]
    public void ToJson_EmitsStableCamelCaseTypedFieldsWithoutSensitiveOrExceptionText()
    {
        var report = new SyncReport(
            new SyncOperation(
                7,
                new JudgeAccount(JudgeId.Luogu, "credential-value"),
                "generation-4",
                Now,
                Now,
                SyncOperationStatus.Error,
                [new SyncModuleOutcome("PROFILE", SyncOperationStatus.Error, 1, 0, 0, "secret HTTP body")]),
            SyncOperationStatus.Error,
            SyncError.Network);

        var json = SyncReportProjector.ToJson(report);

        Assert.Equal("{\"operation\":{\"id\":7,\"status\":\"Error\",\"judge\":\"Luogu\",\"moduleCounts\":{\"total\":1,\"successful\":0,\"failed\":1}},\"status\":\"Error\",\"error\":\"Network\"}", json);
        Assert.DoesNotContain("credential-value", json, StringComparison.Ordinal);
        Assert.DoesNotContain("secret HTTP body", json, StringComparison.Ordinal);
    }

    private static SyncService CreateService(IJudgeAdapter adapter, ISyncStore store) =>
        new(
            new Dictionary<JudgeId, IJudgeAdapter> { [adapter.Judge] = adapter },
            store,
            new TestClock(Now),
            () => "generation-4");

    private sealed class RecordingAdapter(JudgeId judge, IReadOnlyList<SyncModuleOutcome> outcomes) : IJudgeAdapter
    {
        public JudgeId Judge { get; } = judge;

        public int CallCount { get; private set; }

        public Task<IReadOnlyList<SyncModuleOutcome>> SyncAsync(JudgeAccount account, CancellationToken cancellationToken)
        {
            CallCount++;
            return Task.FromResult(outcomes);
        }
    }

    private sealed class ThrowingAdapter(JudgeId judge, Exception exception) : IJudgeAdapter
    {
        public JudgeId Judge { get; } = judge;

        public Task<IReadOnlyList<SyncModuleOutcome>> SyncAsync(JudgeAccount account, CancellationToken cancellationToken) =>
            Task.FromException<IReadOnlyList<SyncModuleOutcome>>(exception);
    }

    private sealed class CancellingAdapter(JudgeId judge) : IJudgeAdapter
    {
        public JudgeId Judge { get; } = judge;

        public TaskCompletionSource Started { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);

        public async Task<IReadOnlyList<SyncModuleOutcome>> SyncAsync(JudgeAccount account, CancellationToken cancellationToken)
        {
            Started.SetResult();
            await Task.Delay(Timeout.InfiniteTimeSpan, cancellationToken);
            return Array.Empty<SyncModuleOutcome>();
        }
    }

    private sealed class BlockingAdapter(JudgeId judge, IReadOnlyList<SyncModuleOutcome> outcomes) : IJudgeAdapter
    {
        public JudgeId Judge { get; } = judge;

        public int CallCount { get; private set; }

        public TaskCompletionSource FirstCallStarted { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);

        public TaskCompletionSource AllowCallsToFinish { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);

        public async Task<IReadOnlyList<SyncModuleOutcome>> SyncAsync(JudgeAccount account, CancellationToken cancellationToken)
        {
            CallCount++;
            FirstCallStarted.TrySetResult();
            await AllowCallsToFinish.Task.WaitAsync(cancellationToken);
            return outcomes;
        }
    }

    private sealed class TestClock(DateTimeOffset utcNow) : IClock
    {
        public DateTimeOffset UtcNow { get; } = utcNow;
    }
}
