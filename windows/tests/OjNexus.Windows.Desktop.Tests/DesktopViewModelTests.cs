using OjNexus.Windows.Core.Contracts;
using OjNexus.Windows.Core.Domain;
using OjNexus.Windows.Core.Sync;
using OjNexus.Windows.Desktop.ViewModels;

namespace OjNexus.Windows.Desktop.Tests;

public sealed class DesktopViewModelTests
{
    [Fact]
    public async Task Refresh_ProjectsAllJudgesAndLatestLocalOperation()
    {
        var store = new InMemorySyncStore();
        var account = JudgeAccount.Create(JudgeId.Codeforces, "tourist");
        await store.UpsertAccountAsync(account, CancellationToken.None);
        var startedAt = DateTimeOffset.Parse("2026-09-12T01:02:03+00:00");
        var operationId = await store.OpenOperationAsync(account, "test", startedAt, CancellationToken.None);
        await store.AppendModuleAsync(operationId, new SyncModuleOutcome("PROFILE", SyncOperationStatus.Success, 1, 1, 0, null), startedAt, CancellationToken.None);
        await store.CloseOperationAsync(operationId, SyncOperationStatus.Success, null, startedAt, CancellationToken.None);
        var viewModel = CreateViewModel(store);

        await viewModel.RefreshAsync(CancellationToken.None);

        Assert.Equal(1, viewModel.AccountCount);
        Assert.Equal(1, viewModel.ConnectedJudgeCount);
        Assert.Equal(3, viewModel.Connectors.Count);
        Assert.Equal("SUCCESS", viewModel.Connectors.Single(row => row.Judge == JudgeId.Codeforces).Status);
        Assert.Equal(DesktopPage.Dashboard, viewModel.CurrentPage);
        Assert.Equal("DASHBOARD", viewModel.PageTitle);
    }

    [Fact]
    public async Task SaveAndSyncConnector_PersistsAccountAndProjectsSuccess()
    {
        var store = new InMemorySyncStore();
        var viewModel = CreateViewModel(store, new SuccessfulAdapter(JudgeId.AtCoder));
        var connector = viewModel.Connectors.Single(row => row.Judge == JudgeId.AtCoder);
        connector.Handle = "tourist";

        await viewModel.SaveConnectorAsync(connector, CancellationToken.None);
        await viewModel.SyncConnectorAsync(connector, CancellationToken.None);

        var accounts = await store.GetAccountsAsync(CancellationToken.None);
        Assert.Equal(new JudgeAccount(JudgeId.AtCoder, "tourist"), Assert.Single(accounts));
        Assert.Equal("SUCCESS", viewModel.Connectors.Single(row => row.Judge == JudgeId.AtCoder).Status);
        Assert.Equal("READY", viewModel.StatusText);
    }

    [Fact]
    public async Task CancelSync_LeavesExplicitCancelledState()
    {
        var store = new InMemorySyncStore();
        var adapter = new BlockingAdapter(JudgeId.Luogu);
        var viewModel = CreateViewModel(store, adapter);
        var connector = viewModel.Connectors.Single(row => row.Judge == JudgeId.Luogu);
        connector.Handle = "2";
        await viewModel.SaveConnectorAsync(connector, CancellationToken.None);

        var syncTask = viewModel.SyncConnectorAsync(connector, CancellationToken.None);
        await adapter.Started.Task;
        viewModel.CancelSync();
        await syncTask;

        Assert.Equal("CANCELLED", viewModel.Connectors.Single(row => row.Judge == JudgeId.Luogu).Status);
    }

    [Fact]
    public async Task SaveConnector_RejectsBlankHandleAndExplainsError()
    {
        var viewModel = CreateViewModel(new InMemorySyncStore());
        var connector = viewModel.Connectors.Single(row => row.Judge == JudgeId.Codeforces);

        var saved = await viewModel.SaveConnectorAsync(connector, CancellationToken.None);

        Assert.False(saved);
        Assert.False(connector.IsConfigured);
        Assert.Equal("INVALID", connector.Status);
        Assert.Equal("HANDLE REQUIRED", viewModel.LastError);
    }

    private static DesktopViewModel CreateViewModel(InMemorySyncStore store, params IJudgeAdapter[] adapters)
    {
        var adapterMap = adapters.ToDictionary(adapter => adapter.Judge);
        var service = new SyncService(adapterMap, store, new FixedClock(), static () => "desktop-test");
        return new DesktopViewModel(store, service, "TEST-DATA");
    }

    private sealed class FixedClock : IClock
    {
        public DateTimeOffset UtcNow => DateTimeOffset.Parse("2026-09-12T01:02:03+00:00");
    }

    private sealed class SuccessfulAdapter(JudgeId judge) : IJudgeAdapter
    {
        public JudgeId Judge { get; } = judge;

        public Task<IReadOnlyList<SyncModuleOutcome>> SyncAsync(JudgeAccount account, CancellationToken cancellationToken) =>
            Task.FromResult<IReadOnlyList<SyncModuleOutcome>>([
                new SyncModuleOutcome("PROFILE", SyncOperationStatus.Success, 1, 1, 0, null),
            ]);
    }

    private sealed class BlockingAdapter(JudgeId judge) : IJudgeAdapter
    {
        public JudgeId Judge { get; } = judge;

        public TaskCompletionSource<bool> Started { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);

        public async Task<IReadOnlyList<SyncModuleOutcome>> SyncAsync(JudgeAccount account, CancellationToken cancellationToken)
        {
            Started.SetResult(true);
            await Task.Delay(Timeout.InfiniteTimeSpan, cancellationToken);
            return [];
        }
    }
}
