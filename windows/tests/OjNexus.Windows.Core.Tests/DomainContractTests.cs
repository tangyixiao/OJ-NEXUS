using System.Reflection;
using OjNexus.Windows.Core.Contracts;
using OjNexus.Windows.Core.Domain;
using OjNexus.Windows.Core.Storage;

namespace OjNexus.Windows.Core.Tests;

public sealed class DomainContractTests
{
    [Theory]
    [InlineData("codeforces", "Codeforces")]
    [InlineData("ATCODER", "AtCoder")]
    [InlineData("LuOgU", "Luogu")]
    public void JudgeIdParser_TryParse_AcceptsKnownJudgeCaseInsensitively(
        string value,
        string expectedJudge)
    {
        var judgeIdType = RequireCoreType("OjNexus.Windows.Core.Domain.JudgeId");
        var parserType = RequireCoreType("OjNexus.Windows.Core.Domain.JudgeIdParser");
        var tryParse = parserType.GetMethod("TryParse", BindingFlags.Public | BindingFlags.Static);
        Assert.NotNull(tryParse);
        var arguments = new object?[] { value, null };

        var parsed = Assert.IsType<bool>(tryParse!.Invoke(null, arguments));

        Assert.True(parsed);
        Assert.Equal(expectedJudge, arguments[1]?.ToString());
        Assert.Equal(judgeIdType, arguments[1]?.GetType());
    }

    [Fact]
    public void JudgeIdParser_TryParse_ReturnsFalseForUnknownJudge()
    {
        var parserType = RequireCoreType("OjNexus.Windows.Core.Domain.JudgeIdParser");
        var tryParse = parserType.GetMethod("TryParse", BindingFlags.Public | BindingFlags.Static);
        Assert.NotNull(tryParse);
        var arguments = new object?[] { "Kattis", null };

        var parsed = Assert.IsType<bool>(tryParse!.Invoke(null, arguments));

        Assert.False(parsed);
    }

    [Fact]
    public void JudgeAccount_Create_TrimsTheHandle()
    {
        var judgeIdType = RequireCoreType("OjNexus.Windows.Core.Domain.JudgeId");
        var accountType = RequireCoreType("OjNexus.Windows.Core.Domain.JudgeAccount");
        var create = accountType.GetMethod("Create", BindingFlags.Public | BindingFlags.Static);
        Assert.NotNull(create);

        var account = create!.Invoke(null, new[] { Enum.Parse(judgeIdType, "AtCoder"), "  tourist  " });

        Assert.Equal("tourist", accountType.GetProperty("Handle")?.GetValue(account));
    }

    [Fact]
    public void JudgeAccount_Create_RejectsBlankHandles()
    {
        var judgeIdType = RequireCoreType("OjNexus.Windows.Core.Domain.JudgeId");
        var accountType = RequireCoreType("OjNexus.Windows.Core.Domain.JudgeAccount");
        var create = accountType.GetMethod("Create", BindingFlags.Public | BindingFlags.Static);
        Assert.NotNull(create);

        var exception = Assert.Throws<TargetInvocationException>(
            () => create!.Invoke(null, new[] { Enum.Parse(judgeIdType, "Luogu"), "   " }));

        Assert.IsType<ArgumentException>(exception.InnerException);
    }

    [Theory]
    [InlineData(JudgeId.Codeforces, "tourist&handles=other")]
    [InlineData(JudgeId.Codeforces, "tourist#fragment")]
    [InlineData(JudgeId.Codeforces, "tourist用户")]
    [InlineData(JudgeId.AtCoder, "tourist.user")]
    [InlineData(JudgeId.AtCoder, "tourist/other")]
    [InlineData(JudgeId.AtCoder, "touristé")]
    [InlineData(JudgeId.Luogu, "uid:abc")]
    public void JudgeAccount_Create_RejectsHandlesOutsideJudgeBoundary(JudgeId judge, string handle)
    {
        var exception = Assert.Throws<ArgumentException>(
            () => JudgeAccount.Create(judge, handle));

        Assert.Contains("handle", exception.Message, StringComparison.OrdinalIgnoreCase);
    }

    [Theory]
    [InlineData(JudgeId.Codeforces, "Tourist", " tourist ", true)]
    [InlineData(JudgeId.AtCoder, "Tourist", "tourist", false)]
    [InlineData(JudgeId.Luogu, "uid:2", "2", true)]
    public void JudgeIdentity_HandlesMatch_UsesJudgeSpecificIdentity(
        JudgeId judge,
        string left,
        string right,
        bool expected)
    {
        Assert.Equal(expected, JudgeIdentity.HandlesMatch(judge, left, right));
    }

    [Fact]
    public void WindowsPaths_UsesProcessOverrideForAutomation()
    {
        const string variableName = "OJ_NEXUS_DATA_DIRECTORY";
        var previousValue = Environment.GetEnvironmentVariable(variableName);
        try
        {
            Environment.SetEnvironmentVariable(variableName, "TEST-AUTOMATION-DATA");

            Assert.Equal("TEST-AUTOMATION-DATA", WindowsPaths.GetDataDirectory());
        }
        finally
        {
            Environment.SetEnvironmentVariable(variableName, previousValue);
        }
    }

    [Fact]
    public void SyncOperation_PreservesStoreSuppliedModuleOrder()
    {
        var judgeIdType = RequireCoreType("OjNexus.Windows.Core.Domain.JudgeId");
        var accountType = RequireCoreType("OjNexus.Windows.Core.Domain.JudgeAccount");
        var outcomeType = RequireCoreType("OjNexus.Windows.Core.Domain.SyncModuleOutcome");
        var statusType = RequireCoreType("OjNexus.Windows.Core.Domain.SyncOperationStatus");
        var operationType = RequireCoreType("OjNexus.Windows.Core.Domain.SyncOperation");
        var judge = Enum.Parse(judgeIdType, "Codeforces");
        var success = Enum.Parse(statusType, "Success");
        var account = Activator.CreateInstance(accountType, judge, "tourist", true);
        var first = Activator.CreateInstance(outcomeType, "PROFILE", success, 1, 1, 0, null);
        var second = Activator.CreateInstance(outcomeType, "SUBMISSIONS", success, 2, 2, 0, null);
        var modules = Array.CreateInstance(outcomeType, 2);

        modules.SetValue(first, 0);
        modules.SetValue(second, 1);
        var operation = Activator.CreateInstance(
            operationType,
            42L,
            account,
            "generation-7",
            DateTimeOffset.Parse("2026-09-07T00:00:00+00:00"),
            null,
            success,
            modules);
        var moduleValue = operationType.GetProperty("Modules")?.GetValue(operation);
        Assert.NotNull(moduleValue);
        var storedModules = Assert.IsAssignableFrom<System.Collections.IEnumerable>(moduleValue);

        var stages = storedModules.Cast<object>()
            .Select(module => outcomeType.GetProperty("Stage")?.GetValue(module)?.ToString())
            .ToArray();

        Assert.Equal(new[] { "PROFILE", "SUBMISSIONS" }, stages);
    }

    [Fact]
    public void SyncOperation_DefensivelyCopiesStoreSuppliedModules()
    {
        var suppliedModules = new[]
        {
            new SyncModuleOutcome("PROFILE", SyncOperationStatus.Success, 1, 1, 0, null),
            new SyncModuleOutcome("SUBMISSIONS", SyncOperationStatus.Success, 2, 2, 0, null),
        };
        var operation = new SyncOperation(
            42L,
            JudgeAccount.Create(JudgeId.Codeforces, "tourist"),
            "generation-7",
            DateTimeOffset.Parse("2026-09-07T00:00:00+00:00"),
            null,
            SyncOperationStatus.Success,
            suppliedModules);

        suppliedModules[0] = new SyncModuleOutcome("MUTATED", SyncOperationStatus.Error, 0, 0, 0, "Test");
        Array.Reverse(suppliedModules);

        Assert.Equal(new[] { "PROFILE", "SUBMISSIONS" }, operation.Modules.Select(module => module.Stage));
    }

    [Fact]
    public async Task Contracts_CanBeImplementedAndUsedWithTheirDeclaredMembers()
    {
        var account = JudgeAccount.Create(JudgeId.AtCoder, "tourist");
        var adapter = new TestJudgeAdapter();
        var adapterOutcomes = await adapter.SyncAsync(account, CancellationToken.None);
        var store = new TestSyncStore();
        var startedAt = DateTimeOffset.Parse("2026-09-07T00:00:00+00:00");
        var finishedAt = startedAt.AddMinutes(1);

        await store.UpsertAccountAsync(account, CancellationToken.None);
        var accounts = await store.GetAccountsAsync(CancellationToken.None);
        var operationId = await store.OpenOperationAsync(account, "generation-7", startedAt, CancellationToken.None);
        await store.AppendModuleAsync(operationId, adapterOutcomes[0], finishedAt, CancellationToken.None);
        await store.CloseOperationAsync(operationId, SyncOperationStatus.Success, null, finishedAt, CancellationToken.None);
        var operations = await store.GetRecentOperationsAsync(JudgeId.AtCoder, 1, CancellationToken.None);
        IClock clock = new TestClock(finishedAt);

        Assert.Equal(JudgeId.AtCoder, adapter.Judge);
        Assert.Equal("PROFILE", adapterOutcomes[0].Stage);
        Assert.Equal(new[] { account }, accounts);
        Assert.Equal(42L, operationId);
        Assert.Equal(operationId, store.AppendedOperationId);
        Assert.Equal("PROFILE", store.AppendedOutcome?.Stage);
        Assert.Equal(SyncOperationStatus.Success, store.ClosedStatus);
        Assert.Single(operations);
        Assert.Equal(finishedAt, clock.UtcNow);
    }

    private static Type RequireCoreType(string fullName)
    {
        var type = Assembly.Load("OjNexus.Windows.Core").GetType(fullName);
        Assert.NotNull(type);
        return type!;
    }

    private sealed class TestJudgeAdapter : IJudgeAdapter
    {
        public JudgeId Judge => JudgeId.AtCoder;

        public Task<IReadOnlyList<SyncModuleOutcome>> SyncAsync(
            JudgeAccount account,
            CancellationToken cancellationToken) =>
            Task.FromResult<IReadOnlyList<SyncModuleOutcome>>(
                new[] { new SyncModuleOutcome("PROFILE", SyncOperationStatus.Success, 1, 1, 0, null) });
    }

    private sealed class TestSyncStore : ISyncStore
    {
        private readonly List<JudgeAccount> _accounts = [];
        private readonly List<SyncOperation> _operations = [];

        public long? AppendedOperationId { get; private set; }

        public SyncModuleOutcome? AppendedOutcome { get; private set; }

        public SyncOperationStatus? ClosedStatus { get; private set; }

        public Task<IReadOnlyList<JudgeAccount>> GetAccountsAsync(CancellationToken cancellationToken) =>
            Task.FromResult<IReadOnlyList<JudgeAccount>>(_accounts.ToArray());

        public Task UpsertAccountAsync(JudgeAccount account, CancellationToken cancellationToken)
        {
            _accounts.RemoveAll(candidate => candidate.Judge == account.Judge);
            _accounts.Add(account);
            return Task.CompletedTask;
        }

        public Task<IReadOnlyList<SyncOperation>> GetRecentOperationsAsync(
            JudgeId? judge,
            int limit,
            CancellationToken cancellationToken)
        {
            var operations = _operations
                .Where(operation => judge is null || operation.Account.Judge == judge)
                .Take(limit)
                .ToArray();
            return Task.FromResult<IReadOnlyList<SyncOperation>>(operations);
        }

        public Task<CodeforcesPayloadSnapshot> GetCodeforcesPayloadAsync(
            string handle,
            CancellationToken cancellationToken) =>
            Task.FromResult(new CodeforcesPayloadSnapshot(null, Array.Empty<CodeforcesRating>(), Array.Empty<CodeforcesSubmission>()));

        public Task<AtCoderPayloadSnapshot> GetAtCoderPayloadAsync(
            string handle,
            CancellationToken cancellationToken) =>
            Task.FromResult(new AtCoderPayloadSnapshot(Array.Empty<AtCoderSubmission>()));

        public Task<LuoguPayloadSnapshot> GetLuoguPayloadAsync(
            string handle,
            CancellationToken cancellationToken) =>
            Task.FromResult(new LuoguPayloadSnapshot(null, 0, 0, 0));

        public Task<long> OpenOperationAsync(
            JudgeAccount account,
            string dataGeneration,
            DateTimeOffset startedAt,
            CancellationToken cancellationToken)
        {
            const long operationId = 42L;
            _operations.Add(new SyncOperation(
                operationId,
                account,
                dataGeneration,
                startedAt,
                null,
                SyncOperationStatus.Running,
                Array.Empty<SyncModuleOutcome>()));
            return Task.FromResult(operationId);
        }

        public Task AppendModuleAsync(
            long operationId,
            SyncModuleOutcome outcome,
            DateTimeOffset completedAt,
            CancellationToken cancellationToken)
        {
            AppendedOperationId = operationId;
            AppendedOutcome = outcome;
            return Task.CompletedTask;
        }

        public Task CloseOperationAsync(
            long operationId,
            SyncOperationStatus status,
            SyncError? error,
            DateTimeOffset finishedAt,
            CancellationToken cancellationToken)
        {
            ClosedStatus = status;
            return Task.CompletedTask;
        }
    }

    private sealed class TestClock(DateTimeOffset utcNow) : IClock
    {
        public DateTimeOffset UtcNow { get; } = utcNow;
    }
}
