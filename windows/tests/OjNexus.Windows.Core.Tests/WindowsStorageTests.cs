using OjNexus.Windows.Core.Storage;
using System.Diagnostics;
using OjNexus.Windows.Core.Sync;

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
        using var holder = Start(host, root, "shared");
        Assert.Equal("READY", await holder.StandardOutput.ReadLineAsync().WaitAsync(TimeSpan.FromSeconds(10)));
        using var secondHolder = Start(host, root, "shared");
        Assert.Equal("READY", await secondHolder.StandardOutput.ReadLineAsync().WaitAsync(TimeSpan.FromSeconds(10)));
        using var contender = Start(host, root, "exclusive");
        Assert.Equal("BUSY", await contender.StandardOutput.ReadLineAsync().WaitAsync(TimeSpan.FromSeconds(10)));
        Assert.Equal(10, await contender.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(10)).ContinueWith(_ => contender.ExitCode));
        holder.Kill(entireProcessTree: true);
        secondHolder.Kill(entireProcessTree: true);
        await holder.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(10));
        await secondHolder.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(10));
        Assert.True(File.Exists(Path.Combine(root, "ojnexus.lifecycle.lock")));
        using var afterKill = Start(host, root, "exclusive");
        Assert.Equal("READY", await afterKill.StandardOutput.ReadLineAsync().WaitAsync(TimeSpan.FromSeconds(10)));
        using var blockedShared = Start(host, root, "shared");
        Assert.Equal("BUSY", await blockedShared.StandardOutput.ReadLineAsync().WaitAsync(TimeSpan.FromSeconds(10)));
        Assert.Equal(10, await blockedShared.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(10)).ContinueWith(_ => blockedShared.ExitCode));
        afterKill.StandardInput.WriteLine("release");
        Assert.Equal(0, await afterKill.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(10)).ContinueWith(_ => afterKill.ExitCode));
    }

    [Fact]
    public async Task ClosedOperationContextCannotReuseItsStore()
    {
        var scope = new StorageOperationScope(new InMemorySyncStore());
        var context = scope.BeginOperation();
        var retained = context.Store;
        await context.DisposeAsync();
        var error = Assert.Throws<StorageException>(() => { _ = retained.GetAccountsAsync(CancellationToken.None); });
        Assert.Equal("SESSION_CLOSED", error.Failure.WireCode);
        await scope.DisposeAsync();
    }

    [Fact]
    public async Task ConcurrentFirstRunProcessesReturnOnlyReadyOrBusyWithoutDamagingStore()
    {
        var root = NewRoot();
        using var first = Start(FindHost(), root, "open");
        using var second = Start(FindHost(), root, "open");
        Assert.Equal("PREPARED", await first.StandardOutput.ReadLineAsync().WaitAsync(TimeSpan.FromSeconds(10)));
        Assert.Equal("PREPARED", await second.StandardOutput.ReadLineAsync().WaitAsync(TimeSpan.FromSeconds(10)));
        first.StandardInput.WriteLine("go"); second.StandardInput.WriteLine("go");
        var firstResult = await first.StandardOutput.ReadLineAsync().WaitAsync(TimeSpan.FromSeconds(20));
        var secondResult = await second.StandardOutput.ReadLineAsync().WaitAsync(TimeSpan.FromSeconds(20));
        Assert.Contains(firstResult, new[] { "READY", "BUSY" });
        Assert.Contains(secondResult, new[] { "READY", "BUSY" });
        Assert.Contains("READY", new[] { firstResult, secondResult });
        foreach (var child in new[] { first, second })
        {
            if (!child.HasExited) { child.StandardInput.WriteLine("release"); await child.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(10)); }
        }
        Assert.True(new FileInfo(Path.Combine(root, "ojnexus.db")).Length > 16);
        Assert.Equal("v1\n", File.ReadAllText(Path.Combine(root, "ojnexus.initialized")));
        var verify = await WindowsStorage.OpenAsync(root, null, CancellationToken.None);
        Assert.True(verify.IsReady, verify.Failure?.WireCode);
        await verify.Session!.DisposeAsync();
    }

    private static Process Start(string host, string root, string mode) => Process.Start(new ProcessStartInfo("dotnet")
    {
        ArgumentList = { host, root, mode }, RedirectStandardInput = true, RedirectStandardOutput = true,
        RedirectStandardError = true, UseShellExecute = false, CreateNoWindow = true
    })!;

    private static string FindHost()
    {
        var directory = new DirectoryInfo(AppContext.BaseDirectory);
        while (directory is not null && !Directory.Exists(Path.Combine(directory.FullName, "windows"))) directory = directory.Parent;
        if (directory is null) throw new DirectoryNotFoundException("Repository root not found for native host.");
        return Path.Combine(directory.FullName, "windows", "tests", "OjNexus.Windows.StorageTestHost", "bin", "Release", "net8.0", "OjNexus.Windows.StorageTestHost.dll");
    }

    private static string NewRoot() => Path.Combine(Path.GetTempPath(), "ojnexus-storage-test-" + Guid.NewGuid().ToString("N"));
}
