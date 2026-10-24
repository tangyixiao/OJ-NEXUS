using OjNexus.Windows.Core.Contracts;
using OjNexus.Windows.Core.Domain;
using OjNexus.Windows.Core.Storage;
using OjNexus.Windows.Core.Sync;
using OjNexus.Windows.Desktop.ViewModels;

namespace OjNexus.Windows.Desktop.Tests;

public sealed class DesktopViewModelTests
{
    [Fact]
    public async Task DisposeAsync_WaitsUntilSyncFinalRefreshAndCleanupDrainComplete()
    {
        var inner = new InMemorySyncStore();
        var refreshStarted = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var allowRefresh = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var store = new FaultingSyncStore(inner, _ => null, async method =>
        {
            if (method == nameof(ISyncStore.GetAccountsAsync))
            {
                refreshStarted.TrySetResult();
                await allowRefresh.Task;
            }
        });
        var source = new StorageOperationScope(store);
        var adapter = new SuccessfulAdapter(JudgeId.Codeforces);
        var vm = new DesktopViewModel(source,
            new SyncService(new Dictionary<JudgeId, IJudgeAdapter> { [JudgeId.Codeforces] = adapter }, new FixedClock(), () => "desktop-drain"),
            "TEST-DATA");
        var connector = vm.Connectors.Single(row => row.Judge == JudgeId.Codeforces);
        connector.Handle = "tourist";

        var action = vm.SyncConnectorAsync(connector, CancellationToken.None);
        await refreshStarted.Task.WaitAsync(TimeSpan.FromSeconds(5));
        var shutdown = vm.DisposeAsync().AsTask();
        Assert.False(shutdown.IsCompleted);
        allowRefresh.TrySetResult();
        await action.WaitAsync(TimeSpan.FromSeconds(5));
        await shutdown.WaitAsync(TimeSpan.FromSeconds(5));
    }

    [Fact]
    public async Task SyncConnectorAsync_StorageFailureSurvivesSuccessfulFinalRefresh()
    {
        var inner = new InMemorySyncStore();
        var store = new FaultingSyncStore(inner, method =>
            method == nameof(ISyncStore.AppendModuleAsync) ? new IOException("private path") : null);
        var vm = CreateViewModelWithStore(store, new SuccessfulAdapter(JudgeId.Codeforces));
        var connector = vm.Connectors.Single(row => row.Judge == JudgeId.Codeforces);
        connector.Handle = "tourist";

        var success = await vm.SyncConnectorAsync(connector, CancellationToken.None);

        Assert.False(success);
        Assert.Equal("ERROR", vm.StatusText);
        Assert.Equal("STORAGE_IO_FAILURE", vm.LastError);
        await vm.DisposeAsync();
    }

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
    public async Task Refresh_DoesNotProjectOldHandleOperationToChangedAccount()
    {
        var store = new InMemorySyncStore();
        var oldAccount = JudgeAccount.Create(JudgeId.Codeforces, "old-handle");
        var newAccount = JudgeAccount.Create(JudgeId.Codeforces, "new-handle");
        var startedAt = DateTimeOffset.Parse("2026-09-12T01:02:03+00:00");
        await store.UpsertAccountAsync(oldAccount, CancellationToken.None);
        var operationId = await store.OpenOperationAsync(oldAccount, "old-generation", startedAt, CancellationToken.None);
        await store.CloseOperationAsync(operationId, SyncOperationStatus.Success, null, startedAt, CancellationToken.None);
        await store.UpsertAccountAsync(newAccount, CancellationToken.None);

        var viewModel = CreateViewModel(store);

        await viewModel.RefreshAsync(CancellationToken.None);

        var connector = viewModel.Connectors.Single(row => row.Judge == JudgeId.Codeforces);
        Assert.Equal("new-handle", connector.Handle);
        Assert.Equal("NOT SYNCED", connector.Status);
        Assert.Equal("NONE", connector.LastSync);
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
    public async Task DisableConnector_PersistsStateAndPreventsSync()
    {
        var store = new InMemorySyncStore();
        var adapter = new SuccessfulAdapter(JudgeId.Codeforces);
        var viewModel = CreateViewModel(store, adapter);
        var connector = viewModel.Connectors.Single(row => row.Judge == JudgeId.Codeforces);
        connector.Handle = "tourist";

        Assert.True(await viewModel.SaveConnectorAsync(connector, CancellationToken.None));
        Assert.True(await viewModel.SetConnectorEnabledAsync(connector, false, CancellationToken.None));

        Assert.False(connector.IsEnabled);
        Assert.False((await store.GetAccountsAsync(CancellationToken.None)).Single().Enabled);
        Assert.False(await viewModel.SyncConnectorAsync(connector, CancellationToken.None));
        Assert.Equal("ACCOUNT DISABLED", viewModel.LastError);
        Assert.Equal(0, adapter.CallCount);
    }

    [Fact]
    public async Task SaveConnector_PreservesDisabledStateForEquivalentHandle()
    {
        var store = new InMemorySyncStore();
        var viewModel = CreateViewModel(store);
        var connector = viewModel.Connectors.Single(row => row.Judge == JudgeId.Codeforces);
        connector.Handle = "tourist";

        Assert.True(await viewModel.SaveConnectorAsync(connector, CancellationToken.None));
        Assert.True(await viewModel.SetConnectorEnabledAsync(connector, false, CancellationToken.None));
        connector.Handle = "  Tourist  ";

        Assert.True(await viewModel.SaveConnectorAsync(connector, CancellationToken.None));

        var account = Assert.Single(await store.GetAccountsAsync(CancellationToken.None));
        Assert.Equal("Tourist", account.Handle);
        Assert.False(account.Enabled);
        Assert.False(connector.IsEnabled);
    }

    [Fact]
    public async Task SyncAll_SyncsOnlyConfiguredEnabledConnectors()
    {
        var store = new InMemorySyncStore();
        var codeforcesAdapter = new SuccessfulAdapter(JudgeId.Codeforces);
        var atcoderAdapter = new SuccessfulAdapter(JudgeId.AtCoder);
        var luoguAdapter = new SuccessfulAdapter(JudgeId.Luogu);
        var viewModel = CreateViewModel(store, codeforcesAdapter, atcoderAdapter, luoguAdapter);
        var codeforces = viewModel.Connectors.Single(row => row.Judge == JudgeId.Codeforces);
        var atcoder = viewModel.Connectors.Single(row => row.Judge == JudgeId.AtCoder);
        var luogu = viewModel.Connectors.Single(row => row.Judge == JudgeId.Luogu);
        codeforces.Handle = "tourist";
        atcoder.Handle = "tourist";
        luogu.Handle = "2";

        Assert.True(await viewModel.SaveConnectorAsync(codeforces, CancellationToken.None));
        Assert.True(await viewModel.SaveConnectorAsync(atcoder, CancellationToken.None));
        Assert.True(await viewModel.SaveConnectorAsync(luogu, CancellationToken.None));
        Assert.True(await viewModel.SetConnectorEnabledAsync(luogu, false, CancellationToken.None));

        Assert.True(await viewModel.SyncAllAsync(CancellationToken.None));

        Assert.Equal(1, codeforcesAdapter.CallCount);
        Assert.Equal(1, atcoderAdapter.CallCount);
        Assert.Equal(0, luoguAdapter.CallCount);
        Assert.Equal("SUCCESS", codeforces.Status);
        Assert.Equal("SUCCESS", atcoder.Status);
        Assert.Equal("NOT SYNCED", luogu.Status);
    }

    [Fact]
    public async Task SyncAll_CancelledLastConnectorReportsCancelledBatch()
    {
        var store = new InMemorySyncStore();
        var adapter = new BlockingAdapter(JudgeId.Codeforces);
        var viewModel = CreateViewModel(store, adapter);
        var connector = viewModel.Connectors.Single(row => row.Judge == JudgeId.Codeforces);
        connector.Handle = "tourist";
        Assert.True(await viewModel.SaveConnectorAsync(connector, CancellationToken.None));

        var syncAll = viewModel.SyncAllAsync(CancellationToken.None);
        await adapter.Started.Task;
        viewModel.CancelSync();

        Assert.False(await syncAll);
        Assert.Equal("CANCELLED", viewModel.StatusText);
        Assert.Equal("CANCELLED", connector.Status);
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
    public async Task CancelSync_CancelsEveryActiveConnectorWithoutLeavingAStuckOperation()
    {
        var store = new InMemorySyncStore();
        var codeforcesAdapter = new BlockingAdapter(JudgeId.Codeforces);
        var luoguAdapter = new BlockingAdapter(JudgeId.Luogu);
        var viewModel = CreateViewModel(store, codeforcesAdapter, luoguAdapter);
        var codeforces = viewModel.Connectors.Single(row => row.Judge == JudgeId.Codeforces);
        var luogu = viewModel.Connectors.Single(row => row.Judge == JudgeId.Luogu);
        codeforces.Handle = "tourist";
        luogu.Handle = "2";

        var codeforcesSync = viewModel.SyncConnectorAsync(codeforces, CancellationToken.None);
        var luoguSync = viewModel.SyncConnectorAsync(luogu, CancellationToken.None);
        await Task.WhenAll(codeforcesAdapter.Started.Task, luoguAdapter.Started.Task);

        viewModel.CancelSync();
        await Task.WhenAll(codeforcesSync, luoguSync).WaitAsync(TimeSpan.FromSeconds(2));

        Assert.Equal("CANCELLED", codeforces.Status);
        Assert.Equal("CANCELLED", luogu.Status);
    }

    [Fact]
    public async Task CancelSync_ForOneConnectorLeavesOtherSyncRunning()
    {
        var store = new InMemorySyncStore();
        var codeforcesAdapter = new BlockingAdapter(JudgeId.Codeforces);
        var luoguAdapter = new BlockingAdapter(JudgeId.Luogu);
        var viewModel = CreateViewModel(store, codeforcesAdapter, luoguAdapter);
        var codeforces = viewModel.Connectors.Single(row => row.Judge == JudgeId.Codeforces);
        var luogu = viewModel.Connectors.Single(row => row.Judge == JudgeId.Luogu);
        codeforces.Handle = "tourist";
        luogu.Handle = "2";

        var codeforcesSync = viewModel.SyncConnectorAsync(codeforces, CancellationToken.None);
        var luoguSync = viewModel.SyncConnectorAsync(luogu, CancellationToken.None);
        await Task.WhenAll(codeforcesAdapter.Started.Task, luoguAdapter.Started.Task);

        viewModel.CancelSync(codeforces);
        await codeforcesSync.WaitAsync(TimeSpan.FromSeconds(2));

        Assert.True(luogu.IsSyncing);
        Assert.False(luoguSync.IsCompleted);

        viewModel.CancelSync(luogu);
        await luoguSync.WaitAsync(TimeSpan.FromSeconds(2));
        Assert.Equal("CANCELLED", codeforces.Status);
        Assert.Equal("CANCELLED", luogu.Status);
    }

    [Fact]
    public async Task SyncConnector_RejectsDuplicateForTheSameConnector()
    {
        var store = new InMemorySyncStore();
        var adapter = new BlockingAdapter(JudgeId.Codeforces);
        var viewModel = CreateViewModel(store, adapter);
        var connector = viewModel.Connectors.Single(row => row.Judge == JudgeId.Codeforces);
        connector.Handle = "tourist";

        var firstSync = viewModel.SyncConnectorAsync(connector, CancellationToken.None);
        await adapter.Started.Task;

        var duplicateResult = await viewModel.SyncConnectorAsync(connector, CancellationToken.None)
            .WaitAsync(TimeSpan.FromSeconds(2));

        Assert.False(duplicateResult);
        Assert.True(connector.IsSyncing);

        viewModel.CancelSync(connector);
        await firstSync.WaitAsync(TimeSpan.FromSeconds(2));
        Assert.Equal("CANCELLED", connector.Status);
    }

    [Fact]
    public async Task SyncConnector_FailedOperationKeepsTypedErrorAfterRefresh()
    {
        var store = new InMemorySyncStore();
        var viewModel = CreateViewModel(store, new ThrowingAdapter(JudgeId.Codeforces));
        var connector = viewModel.Connectors.Single(row => row.Judge == JudgeId.Codeforces);
        connector.Handle = "tourist";

        var synced = await viewModel.SyncConnectorAsync(connector, CancellationToken.None);

        Assert.False(synced);
        Assert.Equal("ERROR", connector.Status);
        Assert.Equal("NETWORK", viewModel.LastError);
    }

    [Fact]
    public async Task SyncConnector_PartialOperationKeepsModuleFailureAfterRefresh()
    {
        var store = new InMemorySyncStore();
        var viewModel = CreateViewModel(store, new PartialAdapter(JudgeId.AtCoder));
        var connector = viewModel.Connectors.Single(row => row.Judge == JudgeId.AtCoder);
        connector.Handle = "tourist";

        var synced = await viewModel.SyncConnectorAsync(connector, CancellationToken.None);

        Assert.False(synced);
        Assert.Equal("PARTIAL", connector.Status);
        Assert.Equal("NETWORK", viewModel.LastError);
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

    [Fact]
    public async Task History_IsBoundedToFiveRowsAndMarksRetryableFailures()
    {
        var store = new InMemorySyncStore();
        var account = JudgeAccount.Create(JudgeId.Codeforces, "tourist");
        await store.UpsertAccountAsync(account, CancellationToken.None);
        for (var index = 0; index < 7; index++)
        {
            var startedAt = DateTimeOffset.Parse("2026-09-12T01:00:00+00:00").AddMinutes(index);
            var operationId = await store.OpenOperationAsync(account, $"generation-{index}", startedAt, CancellationToken.None);
            await store.AppendModuleAsync(operationId, new SyncModuleOutcome("PROFILE", SyncOperationStatus.Error, 1, 0, 0, "Network"), startedAt, CancellationToken.None);
            await store.CloseOperationAsync(operationId, SyncOperationStatus.Error, SyncError.Network, startedAt, CancellationToken.None);
        }

        var viewModel = CreateViewModel(store);
        await viewModel.RefreshAsync(CancellationToken.None);

        Assert.Equal(5, viewModel.History.Count);
        Assert.All(viewModel.History, row => Assert.True(row.CanRetry));
        Assert.Contains("NETWORK", viewModel.History[0].ModuleSummary, StringComparison.Ordinal);
    }

    [Fact]
    public async Task SelectHistoryJudge_FiltersRowsByJudge()
    {
        var store = new InMemorySyncStore();
        for (var index = 0; index < 21; index++)
        {
            await SeedCompletedOperationAsync(store, JudgeId.Codeforces, "tourist", 100 + index);
        }
        await SeedCompletedOperationAsync(store, JudgeId.AtCoder, "tourist", 1);
        var viewModel = CreateViewModel(store);

        await viewModel.SelectHistoryJudgeAsync("AtCoder", CancellationToken.None);

        Assert.Equal("ATCODER", viewModel.SelectedHistoryJudge);
        Assert.Single(viewModel.History);
        Assert.Equal("ATCODER", viewModel.History[0].Judge);
        Assert.False(viewModel.History[0].CanRetry);
    }

    [Fact]
    public async Task RetryHistory_RunsAFullSyncForRetryableOperation()
    {
        var store = new InMemorySyncStore();
        var account = JudgeAccount.Create(JudgeId.Codeforces, "tourist");
        await store.UpsertAccountAsync(account, CancellationToken.None);
        var startedAt = DateTimeOffset.Parse("2026-09-12T01:00:00+00:00");
        var operationId = await store.OpenOperationAsync(account, "failed-generation", startedAt, CancellationToken.None);
        await store.AppendModuleAsync(operationId, new SyncModuleOutcome("PROFILE", SyncOperationStatus.Error, 1, 0, 0, "Network"), startedAt, CancellationToken.None);
        await store.CloseOperationAsync(operationId, SyncOperationStatus.Error, SyncError.Network, startedAt, CancellationToken.None);
        var viewModel = CreateViewModel(store, new SuccessfulAdapter(JudgeId.Codeforces));
        await viewModel.RefreshAsync(CancellationToken.None);

        var retried = await viewModel.RetryHistoryAsync(Assert.Single(viewModel.History), CancellationToken.None);

        Assert.True(retried);
        Assert.Equal("SUCCESS", viewModel.Connectors.Single(row => row.Judge == JudgeId.Codeforces).Status);
    }

    [Fact]
    public async Task RetryHistory_RejectsOperationFromAnOldConfiguredHandle()
    {
        var store = new InMemorySyncStore();
        var oldAccount = JudgeAccount.Create(JudgeId.Codeforces, "old-handle");
        var currentAccount = JudgeAccount.Create(JudgeId.Codeforces, "new-handle");
        var startedAt = DateTimeOffset.Parse("2026-09-12T01:00:00+00:00");
        await store.UpsertAccountAsync(oldAccount, CancellationToken.None);
        var operationId = await store.OpenOperationAsync(oldAccount, "old-generation", startedAt, CancellationToken.None);
        await store.CloseOperationAsync(operationId, SyncOperationStatus.Error, SyncError.Network, startedAt, CancellationToken.None);
        await store.UpsertAccountAsync(currentAccount, CancellationToken.None);
        var viewModel = CreateViewModel(store, new SuccessfulAdapter(JudgeId.Codeforces));
        await viewModel.RefreshAsync(CancellationToken.None);

        var retried = await viewModel.RetryHistoryAsync(Assert.Single(viewModel.History), CancellationToken.None);

        Assert.False(retried);
        Assert.Equal("ACCOUNT HANDLE CHANGED", viewModel.LastError);
        Assert.Equal("new-handle", viewModel.Connectors.Single(row => row.Judge == JudgeId.Codeforces).Handle);
    }

    [Fact]
    public async Task Refresh_ProjectsProblemRowsWithJudgeAndStatusFilters()
    {
        var store = new InMemorySyncStore();
        var cfAccount = JudgeAccount.Create(JudgeId.Codeforces, "tourist");
        var atAccount = JudgeAccount.Create(JudgeId.AtCoder, "chokudai");
        var timestamp = DateTimeOffset.Parse("2026-09-12T01:00:00+00:00");
        await store.UpsertAccountAsync(cfAccount, CancellationToken.None);
        var cfOperation = await store.OpenOperationAsync(cfAccount, "generation-1", timestamp, CancellationToken.None);
        await store.AppendModuleAsync(
            cfOperation,
            new SyncModuleOutcome(
                "SUBMISSIONS",
                SyncOperationStatus.Success,
                2,
                2,
                0,
                null,
                new CodeforcesSubmissionsPayload("tourist",
                [
                    new CodeforcesSubmission(1, 2134, "C", "Watermelon", "OK", "GNU C++17", 5, 15, 262144, 1_700_000_000),
                    new CodeforcesSubmission(2, 2134, "D", "Theatre Square", "WA", "GNU C++17", 0, 0, 262144, 1_700_000_100),
                ])),
            timestamp,
            CancellationToken.None);
        await store.UpsertAccountAsync(atAccount, CancellationToken.None);
        var atOperation = await store.OpenOperationAsync(atAccount, "generation-2", timestamp, CancellationToken.None);
        await store.AppendModuleAsync(
            atOperation,
            new SyncModuleOutcome(
                "SUBMISSIONS",
                SyncOperationStatus.Success,
                1,
                1,
                0,
                null,
                new AtCoderSubmissionsPayload("chokudai", [new AtCoderSubmission(11, 100, "abc100_a", "abc100", "C++", 100, 10, "AC", 1)])),
            timestamp,
            CancellationToken.None);
        var viewModel = CreateViewModel(store);

        await viewModel.RefreshAsync(CancellationToken.None);

        Assert.Equal(3, viewModel.Problems.Count);
        Assert.All(viewModel.Problems, row => Assert.True(row.HasUrl));
        Assert.Contains(viewModel.Problems, row => row.Judge == "CODEFORCES" && row.Status == "SOLVED" && row.ExternalId == "2134C");
        Assert.Contains(viewModel.Problems, row => row.Judge == "CODEFORCES" && row.Status == "ATTEMPTED" && row.ExternalId == "2134D");

        await viewModel.SelectProblemStatusAsync("SOLVED", CancellationToken.None);

        Assert.Equal("SOLVED", viewModel.SelectedProblemStatus);
        Assert.Equal(2, viewModel.Problems.Count);

        await viewModel.SelectProblemJudgeAsync("CODEFORCES", CancellationToken.None);

        Assert.Equal("CODEFORCES", viewModel.SelectedProblemJudge);
        Assert.Single(viewModel.Problems);
        Assert.Equal("2134C", viewModel.Problems[0].ExternalId);
    }

    [Fact]
    public async Task SelectProblemJudge_UnknownFilterReportsError()
    {
        var viewModel = CreateViewModel(new InMemorySyncStore());

        await viewModel.SelectProblemJudgeAsync("MARS", CancellationToken.None);

        Assert.Equal("ERROR", viewModel.StatusText);
        Assert.Equal("UNKNOWN JUDGE FILTER", viewModel.LastError);
    }

    private static async Task SeedCompletedOperationAsync(InMemorySyncStore store, JudgeId judge, string handle, int minute)
    {
        var account = JudgeAccount.Create(judge, handle);
        await store.UpsertAccountAsync(account, CancellationToken.None);
        var startedAt = DateTimeOffset.Parse("2026-09-12T01:00:00+00:00").AddMinutes(minute);
        var operationId = await store.OpenOperationAsync(account, $"generation-{judge}-{minute}", startedAt, CancellationToken.None);
        await store.AppendModuleAsync(operationId, new SyncModuleOutcome("PROFILE", SyncOperationStatus.Success, 1, 1, 0, null), startedAt, CancellationToken.None);
        await store.CloseOperationAsync(operationId, SyncOperationStatus.Success, null, startedAt, CancellationToken.None);
    }

    private static DesktopViewModel CreateViewModel(InMemorySyncStore store, params IJudgeAdapter[] adapters)
    {
        var adapterMap = adapters.ToDictionary(adapter => adapter.Judge);
        var service = new SyncService(adapterMap, new FixedClock(), static () => "desktop-test");
        return new DesktopViewModel(new StorageOperationScope(store), service, "TEST-DATA");
    }

    private static DesktopViewModel CreateViewModelWithStore(ISyncStore store, params IJudgeAdapter[] adapters)
    {
        var adapterMap = adapters.ToDictionary(adapter => adapter.Judge);
        var service = new SyncService(adapterMap, new FixedClock(), static () => "desktop-test");
        return new DesktopViewModel(new StorageOperationScope(store), service, "TEST-DATA");
    }

    private sealed class FixedClock : IClock
    {
        public DateTimeOffset UtcNow => DateTimeOffset.Parse("2026-09-12T01:02:03+00:00");
    }

    private sealed class SuccessfulAdapter(JudgeId judge) : IJudgeAdapter
    {
        public JudgeId Judge { get; } = judge;

        public int CallCount { get; private set; }

        public Task<IReadOnlyList<SyncModuleOutcome>> SyncAsync(JudgeAccount account, CancellationToken cancellationToken)
        {
            CallCount++;
            return Task.FromResult<IReadOnlyList<SyncModuleOutcome>>([
                new SyncModuleOutcome("PROFILE", SyncOperationStatus.Success, 1, 1, 0, null),
            ]);
        }
    }

    private sealed class ThrowingAdapter(JudgeId judge) : IJudgeAdapter
    {
        public JudgeId Judge { get; } = judge;

        public Task<IReadOnlyList<SyncModuleOutcome>> SyncAsync(JudgeAccount account, CancellationToken cancellationToken) =>
            throw new InvalidOperationException("fixture failure");
    }

    private sealed class PartialAdapter(JudgeId judge) : IJudgeAdapter
    {
        public JudgeId Judge { get; } = judge;

        public Task<IReadOnlyList<SyncModuleOutcome>> SyncAsync(JudgeAccount account, CancellationToken cancellationToken) =>
            Task.FromResult<IReadOnlyList<SyncModuleOutcome>>([
                new SyncModuleOutcome("SUBMISSIONS", SyncOperationStatus.Error, 1, 0, 0, "Network"),
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
