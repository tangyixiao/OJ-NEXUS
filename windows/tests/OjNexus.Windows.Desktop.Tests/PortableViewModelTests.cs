using System.Security.Cryptography;
using System.Text;
using OjNexus.Windows.Core.Portable;
using OjNexus.Windows.Core.Storage;
using OjNexus.Windows.Desktop.ViewModels;

namespace OjNexus.Windows.Desktop.Tests;

/// <summary>
/// Portable page tests run against a REAL guarded storage session (public
/// WindowsStorage.OpenAsync with a temp root) — the same guarded authority the
/// app uses. Pickers are stubbed; everything else is real.
/// </summary>
public sealed class PortableViewModelTests : IDisposable
{
    private const string PackageJson = """
        {"formatVersion":1,"scopeId":"s1","sourcePlatform":"android","generator":{"name":"scope-export","version":"1.0"},"createdAtEpochMs":1,"counts":{"problems":1,"attempts":0,"notes":1,"reviews":0,"sessions":0},"problems":[{"stableId":"p1","sourceScopeId":"s1","origin":"scope","judge":"codeforces","externalId":"1A","title":"T","solved":false,"dayKey":"2026-01-01"}],"attempts":[],"notes":[{"stableId":"n1","sourceScopeId":"s1","origin":"scope","problemId":"p1","keyInsight":"k","implementationNotes":"i","complexity":"c","general":"g","updatedEpochMs":1,"dayKey":"2026-01-01"}],"reviews":[],"sessions":[]}
        """;

    private readonly string _root;
    private readonly WindowsStorageSession _session;

    public PortableViewModelTests()
    {
        _root = Path.Combine(Path.GetTempPath(), "ojnexus-desktop-tests", Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(_root);
        var opened = WindowsStorage.OpenAsync(_root, null, CancellationToken.None).GetAwaiter().GetResult();
        _session = opened.Session ?? throw new InvalidOperationException(opened.Failure?.WireCode);
    }

    public void Dispose() => _session.DisposeAsync().AsTask().GetAwaiter().GetResult();

    private DesktopViewModel CreateViewModel() => DesktopRuntime.Create(
        _session,
        _root,
        () => _session.BeginOperation(),
        _session.CreatePortableRepository);

    private string WritePackage(string json)
    {
        var path = Path.Combine(_root, $"pkg-{Guid.NewGuid():N}.json");
        File.WriteAllText(path, json);
        return path;
    }

    /// <summary>Rebuilt canonical bytes via the repository's own export (null = empty archive).</summary>
    private byte[]? PeekCanonicalBytes()
    {
        using var operation = _session.BeginOperation();
        var repository = _session.CreatePortableRepository(operation);
        try
        {
            return repository.ExportAsync(CancellationToken.None).GetAwaiter().GetResult();
        }
        catch (InvalidOperationException)
        {
            return null;
        }
    }

    [Fact]
    public async Task Import_ValidPackage_ShowsPreviewReadyAndKeepsPendingBytes()
    {
        var viewModel = CreateViewModel();

        await viewModel.ImportPortableAsync(WritePackage(PackageJson), CancellationToken.None);

        Assert.Equal(64, viewModel.PortableIdentity.Length);
        Assert.Contains("CAPABILITY EDITABLE 1 / STORED-ONLY 1", viewModel.PortableSummary, StringComparison.Ordinal);
        Assert.Contains("PREVIEW READY", viewModel.PortableStatus, StringComparison.Ordinal);
        Assert.True(viewModel.HasPendingPortableImport);
    }

    [Fact]
    public async Task Cancel_DiscardsPendingPreviewWithoutAnyWrite()
    {
        var viewModel = CreateViewModel();
        await viewModel.ImportPortableAsync(WritePackage(PackageJson), CancellationToken.None);
        Assert.True(viewModel.HasPendingPortableImport);

        viewModel.CancelPortableImport();

        Assert.False(viewModel.HasPendingPortableImport);
        Assert.Contains("IMPORT CANCELLED", viewModel.PortableStatus, StringComparison.Ordinal);
        Assert.Null(PeekCanonicalBytes());
    }

    [Fact]
    public async Task Confirm_AppliesThePendingPackageAndReportsCounts()
    {
        var viewModel = CreateViewModel();
        await viewModel.ImportPortableAsync(WritePackage(PackageJson), CancellationToken.None);

        await viewModel.ConfirmPortableImportAsync(CancellationToken.None);

        Assert.Contains("APPLIED 2 UPSERTED / 0 DELETED", viewModel.PortableStatus, StringComparison.Ordinal);
        Assert.False(viewModel.HasPendingPortableImport);
        Assert.NotNull(PeekCanonicalBytes());
    }

    [Fact]
    public async Task Import_OutOfContractPackage_ShowsRejectedWithoutPendingState()
    {
        var viewModel = CreateViewModel();

        await viewModel.ImportPortableAsync(WritePackage("""{"formatVersion":2}"""), CancellationToken.None);

        Assert.Contains("REJECTED UNSUPPORTED_VERSION", viewModel.PortableStatus, StringComparison.Ordinal);
        Assert.False(viewModel.HasPendingPortableImport);
    }

    [Fact]
    public async Task CancelledPicker_IsACleanNoOp()
    {
        var viewModel = CreateViewModel();
        viewModel.PortableOpenFilePicker = () => null;

        await viewModel.ImportPortableAsync(viewModel.PickPortableImportFile(), CancellationToken.None);

        Assert.Equal("IMPORT CANCELLED", viewModel.PortableStatus);
        Assert.False(viewModel.HasPendingPortableImport);
    }

    [Fact]
    public async Task Export_WithEmptyArchive_ShowsArchiveEmpty()
    {
        var viewModel = CreateViewModel();
        var savePath = Path.Combine(_root, "export.json");
        viewModel.PortableSaveFilePicker = () => savePath;

        await viewModel.ExportPortableAsync(CancellationToken.None);

        Assert.Contains("ARCHIVE EMPTY", viewModel.PortableStatus, StringComparison.Ordinal);
        Assert.False(File.Exists(savePath));
    }

    [Fact]
    public async Task Export_AfterImport_WritesCanonicalBytesWithThePreviewIdentity()
    {
        var viewModel = CreateViewModel();
        var savePath = Path.Combine(_root, "export.json");
        viewModel.PortableSaveFilePicker = () => savePath;
        await viewModel.ImportPortableAsync(WritePackage(PackageJson), CancellationToken.None);
        var expectedIdentity = viewModel.PortableIdentity;
        await viewModel.ConfirmPortableImportAsync(CancellationToken.None);

        await viewModel.ExportPortableAsync(CancellationToken.None);

        Assert.Contains("EXPORT WRITTEN", viewModel.PortableStatus, StringComparison.Ordinal);
        Assert.True(File.Exists(savePath));
        var identity = Convert.ToHexString(SHA256.HashData(File.ReadAllBytes(savePath))).ToLowerInvariant();
        Assert.Equal(expectedIdentity, identity);
    }

    [Fact]
    public async Task Export_CancelledPicker_IsACleanNoOp()
    {
        var viewModel = CreateViewModel();
        viewModel.PortableSaveFilePicker = () => null;

        await viewModel.ExportPortableAsync(CancellationToken.None);

        Assert.Equal("EXPORT CANCELLED", viewModel.PortableStatus);
    }
}
