using OjNexus.Windows.Core.Storage;
using System.Diagnostics;
using System.Security.Cryptography;
using OjNexus.Windows.Core.Sync;
using Microsoft.Data.Sqlite;
using OjNexus.Windows.Core.Contracts;
using OjNexus.Windows.Core.Domain;

namespace OjNexus.Windows.Core.Tests;

public sealed class WindowsStorageTests
{
    [Fact]
    public async Task FirstOpenCreatesInitializedStoreAndSecondOpenCoexists()
    {
        var root = NewRoot();
        var firstResult = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.True(firstResult.IsReady, firstResult.Failure?.WireCode);
        var first = firstResult.Session!;
        Assert.True(File.Exists(Path.Combine(root, "ojnexus.initialized")));
        Assert.Equal("v1\n", File.ReadAllText(Path.Combine(root, "ojnexus.initialized")));
        var secondResult = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.True(secondResult.IsReady, secondResult.Failure?.WireCode);
        await first.DisposeAsync();
        await secondResult.Session!.DisposeAsync();
    }

    [Fact]
    public async Task ExistingZeroLengthDatabaseIsPreservedAndBlocked()
    {
        var root = NewRoot();
        Directory.CreateDirectory(root);
        var database = Path.Combine(root, "ojnexus.db");
        await File.WriteAllBytesAsync(database, []);
        var result = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.False(result.IsReady);
        Assert.NotNull(result.Failure);
        Assert.Equal(0, new FileInfo(database).Length);
    }

    [Fact]
    public async Task ActiveSessionsHoldSharedLifecycleLeaseUntilDisposed()
    {
        var root = NewRoot();
        var opened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.True(opened.IsReady, opened.Failure?.WireCode);
        var moved = false;
        try { File.Move(Path.Combine(root, "ojnexus.lifecycle.lock"), Path.Combine(root, "renamed.lock")); moved = true; }
        catch (IOException) { }
        Assert.False(moved);
        await opened.Session!.DisposeAsync();
        Assert.True(File.Exists(Path.Combine(root, "ojnexus.lifecycle.lock")));
    }

    [Fact]
    public async Task SeparateNativeProcessHoldsSharedLeaseAndExclusiveProcessReportsBusy()
    {
        var root = NewRoot();
        Directory.CreateDirectory(root);
        var host = FindHost();
        var children = new List<Process>();
        try
        {
            var holder = AddChild(children, Start(host, root, "shared"));
            Assert.Equal("READY", await holder.StandardOutput.ReadLineAsync().WaitAsync(TimeSpan.FromSeconds(10)));
            var lockPath = Path.Combine(root, "ojnexus.lifecycle.lock");
            Assert.Throws<IOException>(() => File.Move(lockPath, Path.Combine(root, "renamed.lock")));
            Assert.Throws<IOException>(() => File.Delete(lockPath));
            Assert.True(File.Exists(lockPath));

            var secondHolder = AddChild(children, Start(host, root, "shared"));
            Assert.Equal("READY", await secondHolder.StandardOutput.ReadLineAsync().WaitAsync(TimeSpan.FromSeconds(10)));
            var contender = AddChild(children, Start(host, root, "exclusive"));
            Assert.Equal("BUSY", await contender.StandardOutput.ReadLineAsync().WaitAsync(TimeSpan.FromSeconds(10)));
            await contender.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(10));
            Assert.Equal(10, contender.ExitCode);

            holder.Kill(entireProcessTree: true);
            secondHolder.Kill(entireProcessTree: true);
            await holder.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(10));
            await secondHolder.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(10));
            Assert.True(File.Exists(lockPath));

            var afterKill = AddChild(children, Start(host, root, "exclusive"));
            Assert.Equal("READY", await afterKill.StandardOutput.ReadLineAsync().WaitAsync(TimeSpan.FromSeconds(10)));
            var blockedSession = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
            Assert.False(blockedSession.IsReady);
            Assert.Equal("STORAGE_BUSY", blockedSession.Failure?.WireCode);
            var blockedShared = AddChild(children, Start(host, root, "shared"));
            Assert.Equal("BUSY", await blockedShared.StandardOutput.ReadLineAsync().WaitAsync(TimeSpan.FromSeconds(10)));
            await blockedShared.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(10));
            Assert.Equal(10, blockedShared.ExitCode);
            afterKill.StandardInput.WriteLine("release");
            await afterKill.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(10));
            Assert.Equal(0, afterKill.ExitCode);
        }
        finally
        {
            await CleanupChildrenAsync(children);
        }
    }

    [Fact]
    public async Task ClosedOperationContextCannotReuseItsStore()
    {
        var scope = new StorageOperationScope(new InMemorySyncStore());
        var context = scope.BeginOperation();
        var retained = context.Store;
        await context.DisposeAsync();
        var error = await Assert.ThrowsAsync<StorageException>(() => retained.GetAccountsAsync(CancellationToken.None));
        Assert.Equal("SESSION_CLOSED", error.Failure.WireCode);
        await scope.DisposeAsync();
    }

    [Fact]
    public async Task ConcurrentFirstRunProcessesReturnOnlyReadyOrBusyWithoutDamagingStore()
    {
        var root = NewRoot();
        var children = new List<Process>();
        try
        {
            var first = AddChild(children, Start(FindHost(), root, "open"));
            var second = AddChild(children, Start(FindHost(), root, "open"));
            Assert.Equal("PREPARED", await first.StandardOutput.ReadLineAsync().WaitAsync(TimeSpan.FromSeconds(10)));
            Assert.Equal("PREPARED", await second.StandardOutput.ReadLineAsync().WaitAsync(TimeSpan.FromSeconds(10)));
            first.StandardInput.WriteLine("go");
            second.StandardInput.WriteLine("go");
            var firstResult = await first.StandardOutput.ReadLineAsync().WaitAsync(TimeSpan.FromSeconds(20));
            var secondResult = await second.StandardOutput.ReadLineAsync().WaitAsync(TimeSpan.FromSeconds(20));
            Assert.Contains(firstResult, new[] { "READY", "BUSY" });
            Assert.Contains(secondResult, new[] { "READY", "BUSY" });
            Assert.Contains("READY", new[] { firstResult, secondResult });
            foreach (var child in new[] { first, second })
            {
                if (!child.HasExited)
                {
                    child.StandardInput.WriteLine("release");
                    await child.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(10));
                }
            }
            Assert.True(new FileInfo(Path.Combine(root, "ojnexus.db")).Length > 16);
            Assert.Equal("v1\n", File.ReadAllText(Path.Combine(root, "ojnexus.initialized")));
            var verify = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
            Assert.True(verify.IsReady, verify.Failure?.WireCode);
            await verify.Session!.DisposeAsync();
        }
        finally { await CleanupChildrenAsync(children); }
    }

    [Fact]
    public async Task RealHotRollbackJournalIsRecoveryBlockedAndPreserved()
    {
        var root = NewRoot();
        var baseline = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.True(baseline.IsReady, baseline.Failure?.WireCode);
        await baseline.Session!.DisposeAsync();
        var database = Path.Combine(root, "ojnexus.db");
        var host = FindHost();
        var child = Start(host, root, "hot-journal");
        try
        {
            var ready = await child.StandardOutput.ReadLineAsync().WaitAsync(TimeSpan.FromSeconds(10));
            if (ready != "READY")
            {
                await child.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(10));
                var diagnostics = await child.StandardError.ReadToEndAsync().WaitAsync(TimeSpan.FromSeconds(3));
                Assert.Fail($"Hot-journal helper exited {child.ExitCode}: {diagnostics}");
            }
            var journal = database + "-journal";
            Assert.True(File.Exists(journal));
            child.Kill(entireProcessTree: true);
            await child.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(10));
            var databaseHash = SHA256.HashData(await File.ReadAllBytesAsync(database));
            var journalBytes = await File.ReadAllBytesAsync(journal);
            Assert.True(journalBytes.Length > 512);
            Assert.Equal(new byte[] { 0xd9, 0xd5, 0x05, 0xf9, 0x20, 0xa1, 0x63, 0xd7 }, journalBytes[..8]);
            var journalHash = SHA256.HashData(journalBytes);

            var opened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
            Assert.Equal("RECOVERY_BLOCKED", opened.Failure?.WireCode ?? "READY");
            Assert.Equal(databaseHash, SHA256.HashData(await File.ReadAllBytesAsync(database)));
            Assert.Equal(journalHash, SHA256.HashData(await File.ReadAllBytesAsync(journal)));
        }
        finally
        {
            await CleanupChildrenAsync([child]);
        }
    }

    [Fact]
    public async Task ValidMarkerDoesNotAuthorizeAnOldSchemaWithoutMigration()
    {
        var root = NewRoot();
        var initial = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.True(initial.IsReady, initial.Failure?.WireCode);
        await initial.Session!.DisposeAsync();
        var database = Path.Combine(root, "ojnexus.db");
        using (var connection = new SqliteConnection(new SqliteConnectionStringBuilder { DataSource = database, Mode = SqliteOpenMode.ReadWrite, Pooling = false }.ToString()))
        {
            connection.Open();
            using var cmd = connection.CreateCommand();
            cmd.CommandText = "UPDATE schema_metadata SET value='6' WHERE key='schema_version'; DROP TABLE review_log; DROP TABLE reviews;";
            cmd.ExecuteNonQuery();
        }
        await File.WriteAllTextAsync(Path.Combine(root, "ojnexus.initialized"), "v1\n");
        var opened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.True(opened.IsReady, opened.Failure?.WireCode);
        await opened.Session!.DisposeAsync();
        using var verify = new SqliteConnection(new SqliteConnectionStringBuilder { DataSource = database, Mode = SqliteOpenMode.ReadOnly, Pooling = false }.ToString());
        verify.Open();
        using var version = verify.CreateCommand(); version.CommandText = "SELECT value FROM schema_metadata WHERE key='schema_version'";
        Assert.Equal("7", Convert.ToString(version.ExecuteScalar()));
    }

    [Fact]
    public async Task MarkerTempBlocksEvenWithValidMarkerAndCurrentDatabase()
    {
        var root = NewRoot();
        var initial = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.True(initial.IsReady, initial.Failure?.WireCode);
        await initial.Session!.DisposeAsync();
        await File.WriteAllTextAsync(Path.Combine(root, "ojnexus.initialized"), "v1\n");
        var evidence = Path.Combine(root, "ojnexus.initialized.tmp");
        await File.WriteAllTextAsync(evidence, "keep");
        var before = await File.ReadAllBytesAsync(evidence);
        var opened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.False(opened.IsReady);
        Assert.Equal("RECOVERY_BLOCKED", opened.Failure?.WireCode);
        Assert.Equal(before, await File.ReadAllBytesAsync(evidence));
        Assert.True(File.Exists(Path.Combine(root, "ojnexus.db")));
    }

    [Fact]
    public async Task RelativeDotSegmentAliasIsAcceptedAndSharesLocation()
    {
        var root = NewRoot();
        Directory.CreateDirectory(root);
        var alias = Path.Combine(Directory.GetParent(root)!.FullName, ".", "child", "..", Path.GetFileName(root));
        var opened = await WindowsStorage.OpenAsync(alias, null, CancellationToken.None);
        Assert.True(opened.IsReady, opened.Failure?.WireCode);
        await opened.Session!.DisposeAsync();
    }

    [Fact]
    public async Task CaseAndDotSegmentAliasesShareTheHeldPhysicalLock()
    {
        var root = NewRoot();
        var opened = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.True(opened.IsReady, opened.Failure?.WireCode);
        var alias = Path.Combine(Directory.GetParent(root)!.FullName.ToUpperInvariant(), ".", "child", "..", Path.GetFileName(root).ToUpperInvariant());
        var attempt = await WindowsStorage.OpenAsync(alias, null, CancellationToken.None);
        Assert.True(attempt.IsReady, attempt.Failure?.WireCode);
        await attempt.Session!.DisposeAsync();
        var error = Assert.Throws<StorageException>(() => NativeFile.Acquire(Path.Combine(alias, "ojnexus.lifecycle.lock"), exclusive: true));
        Assert.Equal("STORAGE_BUSY", error.Failure.WireCode);
        await opened.Session!.DisposeAsync();
    }

    [Fact]
    public async Task SessionUsesInjectedClockForDueReviewDayBoundary()
    {
        var root = NewRoot();
        var clock = new FixedClock(DateTimeOffset.Parse("2010-01-01T00:00:00Z"));
        var opened = await WindowsStorage.OpenAsync(root, clock, CancellationToken.None);
        Assert.True(opened.IsReady, opened.Failure?.WireCode);
        await using var session = opened.Session!;
        await session.RunAsync(async (store, _) =>
        {
            var account = JudgeAccount.Create(JudgeId.Codeforces, "clock-user");
            await store.UpsertAccountAsync(account, CancellationToken.None);
            await store.MarkReviewAsync(JudgeId.Codeforces, "clock-problem", ReviewResult.Fail,
                DateTimeOffset.Parse("2026-10-19T00:00:00Z"), CancellationToken.None);
        }, CancellationToken.None);
        var due = await session.RunAsync((store, _) => store.GetDueReviewsAsync(null, 10, CancellationToken.None), CancellationToken.None);
        Assert.Empty(due);
    }

    [Fact]
    public async Task FinalSharedRecheckRejectsSchemaChangedDuringExclusiveMaintenance()
    {
        var root = NewRoot();
        var initialized = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.True(initialized.IsReady, initialized.Failure?.WireCode);
        await initialized.Session!.DisposeAsync();
        File.Delete(Path.Combine(root, "ojnexus.initialized"));

        var changedDuringMaintenance = false;
        var opened = await WindowsStorage.OpenAsyncForTests(root, null, CancellationToken.None,
            new StartupTestHooks(OnStage: stage =>
            {
                if (stage != StartupStage.MaintenanceCompleted || changedDuringMaintenance) return;
                using var connection = new SqliteConnection(new SqliteConnectionStringBuilder
                {
                    DataSource = Path.Combine(root, "ojnexus.db"),
                    Mode = SqliteOpenMode.ReadWrite,
                    Pooling = false
                }.ToString());
                connection.Open();
                using var command = connection.CreateCommand();
                command.CommandText = "UPDATE schema_metadata SET value='6' WHERE key='schema_version'";
                command.ExecuteNonQuery();
                changedDuringMaintenance = true;
            }));

        Assert.True(changedDuringMaintenance);
        Assert.False(opened.IsReady);
        Assert.Equal("RECOVERY_BLOCKED", opened.Failure?.WireCode);
        Assert.Equal(6, ReadSchemaVersion(Path.Combine(root, "ojnexus.db")));
    }

    [Theory]
    [InlineData("InitialProbeCompleted")]
    [InlineData("FinalProbeCompleted")]
    [InlineData("ExclusiveProbeCompleted")]
    [InlineData("MaintenanceCompleted")]
    public async Task CancellationAtCompletedStartupStageClosesHandles(string stageName)
    {
        var stage = Enum.Parse<StartupStage>(stageName);
        var root = NewRoot();
        if (stage is StartupStage.InitialProbeCompleted or StartupStage.FinalProbeCompleted)
        {
            var baseline = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
            if (baseline.IsReady) await baseline.Session!.DisposeAsync();
            if (stage == StartupStage.FinalProbeCompleted)
            {
                File.Delete(Path.Combine(root, "ojnexus.initialized"));
            }
        }
        using var cancellation = new CancellationTokenSource();
        var canceledAtStage = false;
        var hooks = new StartupTestHooks(OnStage: current =>
        {
            if (current == stage)
            {
                canceledAtStage = true;
                cancellation.Cancel();
            }
        });
        var canceled = await Assert.ThrowsAnyAsync<OperationCanceledException>(() => WindowsStorage.OpenAsyncForTests(root, null, cancellation.Token, hooks));
        Assert.Equal(cancellation.Token, canceled.CancellationToken);
        Assert.True(canceledAtStage);
        using var nextOwner = NativeFile.Acquire(Path.Combine(root, "ojnexus.lifecycle.lock"), exclusive: true);
    }

    [Fact]
    public async Task CancellationDuringHeldStartupStageWaitsForOwnershipToClose()
    {
        var root = NewRoot();
        using var cancellation = new CancellationTokenSource();
        using var enteredStage = new ManualResetEventSlim();
        using var releaseStage = new ManualResetEventSlim();
        var opening = WindowsStorage.OpenAsyncForTests(root, null, cancellation.Token,
            new StartupTestHooks(OnStage: stage =>
            {
                if (stage != StartupStage.InitialProbeCompleted) return;
                enteredStage.Set();
                if (!releaseStage.Wait(TimeSpan.FromSeconds(10))) throw new TimeoutException("Test stage release timed out.");
            }));

        try
        {
            Assert.True(await Task.Run(() => enteredStage.Wait(TimeSpan.FromSeconds(5))));
            cancellation.Cancel();
            Assert.False(opening.IsCompleted);
            AssertBusy(Path.Combine(root, "ojnexus.lifecycle.lock"));
            releaseStage.Set();
            var canceled = await Assert.ThrowsAnyAsync<OperationCanceledException>(() => opening.WaitAsync(TimeSpan.FromSeconds(5)));
            Assert.Equal(cancellation.Token, canceled.CancellationToken);
            using var nextOwner = NativeFile.Acquire(Path.Combine(root, "ojnexus.lifecycle.lock"), exclusive: true);
        }
        finally
        {
            releaseStage.Set();
            if (!opening.IsCompleted)
                await opening.WaitAsync(TimeSpan.FromSeconds(5));
        }
    }

    [Theory]
    [InlineData("MarkerCreated")]
    [InlineData("InitializationStageCreated")]
    [InlineData("InitializationStageValidated")]
    [InlineData("DatabasePromoted")]
    public async Task InterruptedFirstCreationPreservesStageEvidence(string stageName)
    {
        var stage = Enum.Parse<StartupStage>(stageName);
        var root = NewRoot();
        var opened = await WindowsStorage.OpenAsyncForTests(root, null, CancellationToken.None,
            new StartupTestHooks(OnStage: current =>
            {
                if (current == stage) throw new IOException("injected interruption");
            }));
        Assert.False(opened.IsReady);
        var evidenceBefore = HashControlledFiles(root);
        if (stage == StartupStage.DatabasePromoted)
        {
            var completed = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
            Assert.True(completed.IsReady, completed.Failure?.WireCode);
            await completed.Session!.DisposeAsync();
        }
        else
        {
            var second = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
            Assert.False(second.IsReady);
            var expected = stage == StartupStage.MarkerCreated ? "RECOVERY_SOURCE_MISSING" : "RECOVERY_BLOCKED";
            Assert.Equal(expected, second.Failure?.WireCode);
        }
        Assert.Equal(evidenceBefore, HashControlledFiles(root));
    }

    private sealed class FixedClock(DateTimeOffset utcNow) : IClock
    {
        public DateTimeOffset UtcNow { get; } = utcNow;
    }

    private static Process Start(string host, string root, string mode) => Process.Start(new ProcessStartInfo("dotnet")
    {
        ArgumentList = { host, root, mode }, RedirectStandardInput = true, RedirectStandardOutput = true,
        RedirectStandardError = true, UseShellExecute = false, CreateNoWindow = true
    })!;

    private static Process AddChild(List<Process> children, Process child)
    {
        child.StandardInput.AutoFlush = true;
        children.Add(child);
        return child;
    }

    private static async Task CleanupChildrenAsync(IEnumerable<Process> children)
    {
        foreach (var child in children)
        {
            try
            {
                if (!child.HasExited)
                {
                    child.StandardInput.WriteLine("release");
                    await child.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(3));
                }
            }
            catch
            {
                try
                {
                    if (!child.HasExited) child.Kill(entireProcessTree: true);
                    await child.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(5));
                }
                catch { }
            }

            try { _ = await child.StandardOutput.ReadToEndAsync().WaitAsync(TimeSpan.FromSeconds(3)); } catch { }
            try { _ = await child.StandardError.ReadToEndAsync().WaitAsync(TimeSpan.FromSeconds(3)); } catch { }
            child.Dispose();
        }
    }

    private static string FindHost()
    {
        var directory = new DirectoryInfo(AppContext.BaseDirectory);
        while (directory is not null && !Directory.Exists(Path.Combine(directory.FullName, "windows"))) directory = directory.Parent;
        if (directory is null) throw new DirectoryNotFoundException("Repository root not found for native host.");
        var configuration = new DirectoryInfo(AppContext.BaseDirectory).Parent?.Name ?? "Release";
        return Path.Combine(directory.FullName, "windows", "tests", "OjNexus.Windows.StorageTestHost", "bin", configuration, "net8.0", "OjNexus.Windows.StorageTestHost.dll");
    }

    private static string NewRoot() => Path.Combine(Path.GetTempPath(), "ojnexus-storage-test-" + Guid.NewGuid().ToString("N"));

    private static IReadOnlyDictionary<string, string> HashControlledFiles(string root)
    {
        var names = new[]
        {
            "ojnexus.db", "ojnexus.lifecycle.lock", "ojnexus.initialized", "ojnexus.initialized.tmp",
            "ojnexus.init.db", "ojnexus.db-wal", "ojnexus.db-shm", "ojnexus.db-journal",
            "ojnexus.init.db-wal", "ojnexus.init.db-shm", "ojnexus.init.db-journal"
        };
        return names
            .Where(name => File.Exists(Path.Combine(root, name)))
            .ToDictionary(name => name, name => Convert.ToHexString(SHA256.HashData(File.ReadAllBytes(Path.Combine(root, name)))));
    }

    private static int ReadSchemaVersion(string databasePath)
    {
        using var connection = new SqliteConnection(new SqliteConnectionStringBuilder
        { DataSource = databasePath, Mode = SqliteOpenMode.ReadOnly, Pooling = false }.ToString());
        connection.Open();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT value FROM schema_metadata WHERE key='schema_version'";
        return int.Parse(Convert.ToString(command.ExecuteScalar(), System.Globalization.CultureInfo.InvariantCulture)!);
    }

    private static void AssertBusy(string path)
    {
        var failure = Assert.Throws<StorageException>(() => NativeFile.Acquire(path, exclusive: true));
        Assert.Equal("STORAGE_BUSY", failure.Failure.WireCode);
    }
}
