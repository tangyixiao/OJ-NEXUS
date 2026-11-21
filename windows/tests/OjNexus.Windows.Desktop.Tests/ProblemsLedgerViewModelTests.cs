using OjNexus.Windows.Core.Contracts;
using OjNexus.Windows.Core.Domain;
using OjNexus.Windows.Core.Storage;
using OjNexus.Windows.Core.Sync;
using OjNexus.Windows.Desktop.ViewModels;

namespace OjNexus.Windows.Desktop.Tests;

/// <summary>Phase 138 ledger acceptance: persisted filters, clear-filters entry,
/// detail-back retention, page accumulation and summary honesty.</summary>
public sealed class ProblemsLedgerViewModelTests
{
    private static ProblemEntry SeedEntry(JudgeId judge, string externalId, string title, int difficulty, bool favorite, bool solved, int attempts, bool inReview, DateTimeOffset updated) =>
        new(judge, externalId, title, ProblemUrls.For(judge, externalId) ?? string.Empty, solved,
            updated.AddMinutes(-10), updated.AddMinutes(-5), updated, difficulty, favorite, attempts, inReview, []);

    [Fact]
    public async Task CommittedFiltersPersistAndSurviveViewModelRestart()
    {
        var store = new InMemorySyncStore();
        store.SeedProblem(SeedEntry(JudgeId.Codeforces, "1001", "Watermelon Wars", 1200, false, true, 3, false, Epoch(100)));
        store.SeedProblem(SeedEntry(JudgeId.Codeforces, "1002", "Theatre of Squares", 900, true, false, 1, false, Epoch(90)));
        var first = CreateViewModel(store);
        await first.RefreshProblemsAsync(resetPaging: true, CancellationToken.None);
        Assert.Equal(2, first.Problems.Count);

        await first.SetProblemSearchAsync("watermelon", CancellationToken.None);
        Assert.Single(first.Problems);

        var restarted = CreateViewModel(store);
        await restarted.RefreshProblemsAsync(resetPaging: true, CancellationToken.None);
        Assert.Equal("watermelon", restarted.ProblemSearchText);
        Assert.Single(restarted.Problems);
        Assert.Equal("1001", restarted.Problems[0].ExternalId);
        await first.DisposeAsync();
        await restarted.DisposeAsync();
    }

    [Fact]
    public async Task ClearProblemFilters_RestoresUnfilteredPageAndOffersTheEmptyStateExit()
    {
        var store = new InMemorySyncStore();
        store.SeedProblem(SeedEntry(JudgeId.Codeforces, "2001", "Alpha Gate", 800, false, true, 2, false, Epoch(50)));
        store.SeedProblem(SeedEntry(JudgeId.AtCoder, "abc100+abc100_a", "Beta Gate", 1100, false, false, 1, false, Epoch(40)));
        var vm = CreateViewModel(store);
        await vm.SelectProblemJudgeAsync("CODEFORCES", CancellationToken.None);
        Assert.Single(vm.Problems);
        Assert.True(vm.HasActiveProblemFilters);

        await vm.ClearProblemFiltersAsync(CancellationToken.None);

        Assert.Equal(2, vm.Problems.Count);
        Assert.False(vm.HasActiveProblemFilters);
        Assert.False(vm.ShowFilteredEmptyState);
        await vm.DisposeAsync();
    }

    [Fact]
    public async Task DetailBackKeepsFiltersAndPageIntact()
    {
        var store = new InMemorySyncStore();
        for (var index = 0; index < 130; index++)
        {
            store.SeedProblem(SeedEntry(JudgeId.Codeforces, $"{5000 + index}", $"Title {index:D4}", 1000, false, false, index + 1, false, Epoch(1000 - index)));
        }

        var vm = CreateViewModel(store);
        await vm.SetProblemSearchAsync("Title 0", CancellationToken.None);
        var pageOne = vm.Problems.Count;
        await vm.LoadMoreProblemsAsync(CancellationToken.None);
        Assert.True(vm.Problems.Count > pageOne);

        await vm.OpenProblemDetailAsync(vm.Problems[0], CancellationToken.None);
        Assert.NotNull(vm.SelectedProblemDetail);
        Assert.True(vm.IsProblemDetailVisible);

        vm.CloseProblemDetail();
        Assert.Null(vm.SelectedProblemDetail);
        Assert.False(vm.IsProblemDetailVisible);
        Assert.Equal("Title 0", vm.ProblemSearchText);
        Assert.True(vm.HasActiveProblemFilters);
        Assert.True(vm.Problems.Count > pageOne);
        await vm.DisposeAsync();
    }

    [Fact]
    public async Task SummaryAndCountsNeverDeriveFromTheVisiblePage()
    {
        var store = new InMemorySyncStore();
        for (var index = 0; index < 150; index++)
        {
            store.SeedProblem(SeedEntry(JudgeId.Codeforces, $"{6000 + index}", $"Bulk {index:D4}", 1000 + index, false, index % 3 == 0, index, index % 7 == 0, Epoch(2000 - index)));
        }

        var vm = CreateViewModel(store);
        await vm.RefreshProblemsAsync(resetPaging: true, CancellationToken.None);

        Assert.Equal(60, vm.Problems.Count);
        Assert.Contains("MATCHES 150", vm.ProblemMatchSummary);
        Assert.Contains("LIBRARY 150", vm.ProblemMatchSummary);
        Assert.Contains("SOLVED 50", vm.ProblemMatchSummary);
        Assert.Contains("REVIEW 22", vm.ProblemMatchSummary);
        Assert.True(vm.HasMoreProblems);

        await vm.LoadMoreProblemsAsync(CancellationToken.None);
        Assert.Equal(120, vm.Problems.Count);
        Assert.True(vm.HasMoreProblems);
        await vm.DisposeAsync();
    }

    private static DesktopViewModel CreateViewModel(InMemorySyncStore store) =>
        new(new StorageOperationScope(store),
            new SyncService(new Dictionary<JudgeId, IJudgeAdapter>(), new FixedClock(), () => "ledger-test"),
            "TEST-DATA");

    private sealed class FixedClock : IClock
    {
        public DateTimeOffset UtcNow => DateTimeOffset.Parse("2026-11-21T00:00:00+00:00");
    }

    private static DateTimeOffset Epoch(long ms) => DateTimeOffset.FromUnixTimeMilliseconds(ms);
}
