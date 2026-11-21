using System.IO;
using System.Windows;
using System.Windows.Automation;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Threading;
using OjNexus.Windows.Core.Storage;
using OjNexus.Windows.Desktop.ViewModels;

namespace OjNexus.Windows.Desktop;

public partial class MainWindow : Window
{
    private readonly CancellationTokenSource _lifetime = new();
    private DesktopViewModel? _viewModel;
    private Task? _startupTask;
    private readonly DesktopCloseCoordinator _closeCoordinator = new();
    private readonly DesktopStorageStartupController _startupController = new();
    private bool _closing;
    private bool _allowClose;
    private readonly string? _screenshotDirectory = Environment.GetEnvironmentVariable("OJ_NEXUS_UI_SCREENSHOT_DIRECTORY");

    public MainWindow()
    {
        InitializeComponent();
        Loaded += OnLoaded;
        Closing += OnClosing;
    }

    private async void OnLoaded(object sender, RoutedEventArgs e)
    {
        _startupTask = OpenStorageAsync();
        await _startupTask;
    }

    private async Task OpenStorageAsync()
    {
        try
        {
            if (_closing || _viewModel is not null) return;
            RetryStorageOpen.IsEnabled = false;
            StorageStartupHeading.Text = "OPENING LOCAL STORAGE";
            StorageFailureCode.Text = "CHECKING";
            var result = await _startupController.OpenOrRetryAsync();
            if (_closing)
            {
                return;
            }
            if (result.Session is null)
            {
                ShowStorageBlocked(result.FailureCode ?? "STORAGE_IO_FAILURE");
                return;
            }

            var viewModel = DesktopRuntime.Create(
                result.Session,
                result.Session.DataDirectory,
                () => result.Session.BeginOperation(),
                result.Session.CreatePortableRepository);
            if (_closing)
            {
                await viewModel.DisposeAsync();
                return;
            }
            _viewModel = viewModel;
            DataContext = viewModel;
            StorageStartupPanel.Visibility = Visibility.Collapsed;
            NormalShell.Visibility = Visibility.Visible;
            await viewModel.RefreshAsync(_lifetime.Token);
            CaptureRenderedScreenshot(DesktopPage.Dashboard);
        }
        catch (OperationCanceledException) when (_lifetime.IsCancellationRequested)
        {
        }
        catch (Exception)
        {
            if (!_closing) ShowStorageBlocked("STORAGE_IO_FAILURE");
        }
    }

    private void ShowStorageBlocked(string code)
    {
        NormalShell.Visibility = Visibility.Collapsed;
        StorageStartupPanel.Visibility = Visibility.Visible;
        StorageStartupHeading.Text = "LOCAL STORAGE UNAVAILABLE";
        StorageFailureCode.Text = code;
        RetryStorageOpen.IsEnabled = true;
    }

    private async void RetryStorageOpen_Click(object sender, RoutedEventArgs e)
    {
        if (_closing || RetryStorageOpen.IsEnabled is false) return;
        _startupTask = OpenStorageAsync();
        await _startupTask;
    }

    private void ExitStorage_Click(object sender, RoutedEventArgs e) => Close();

    private async void OnClosing(object? sender, System.ComponentModel.CancelEventArgs e)
    {
        if (_allowClose) return;
        e.Cancel = true;
        _closing = true;
        NormalShell.IsEnabled = false;
        var attempt = _closeCoordinator.StartOrJoinAttempt(DrainAndCloseAsync);
        try
        {
            var result = await attempt;
            if (!result.Succeeded)
            {
                _closeCoordinator.ReleaseFailedAttempt(attempt);
                ShowCloseDrainFailure(result.FailureCode ?? "STORAGE_IO_FAILURE");
            }
        }
        catch (Exception)
        {
            // The event boundary must never publish async cleanup failures to WPF's dispatcher.
            _closeCoordinator.ReleaseFailedAttempt(attempt);
            ShowCloseDrainFailure("STORAGE_IO_FAILURE");
        }
    }

    private void ShowCloseDrainFailure(string code)
    {
        NormalShell.IsEnabled = false;
        StorageStartupPanel.Visibility = Visibility.Visible;
        StorageStartupHeading.Text = "LOCAL STORAGE UNAVAILABLE";
        StorageFailureCode.Text = code;
        RetryStorageOpen.IsEnabled = false;
        ExitStorage.IsEnabled = true;
    }

    private async Task DrainAndCloseAsync()
    {
        _lifetime.Cancel();
        var storageClose = _startupController.CloseAsync();
        if (_startupTask is not null)
        {
            try { await _startupTask; }
            catch (OperationCanceledException) { }
        }
        if (_viewModel is not null)
        {
            await _viewModel.DisposeAsync();
            _viewModel = null;
        }
        await storageClose;
        _lifetime.Dispose();
        _allowClose = true;
        // Let the canceled Closing event unwind before raising a fresh close request.
        _ = Dispatcher.BeginInvoke(DispatcherPriority.Normal, new Action(Close));
    }

    private void Navigate_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button { Tag: string page } && Enum.TryParse<DesktopPage>(page, out var target))
        {
            if (_viewModel is null) return;
            _viewModel.Navigate(target);
            CaptureRenderedScreenshot(target);
        }
    }

    private void CaptureRenderedScreenshot(DesktopPage page)
    {
        if (string.IsNullOrWhiteSpace(_screenshotDirectory))
        {
            return;
        }

        Dispatcher.BeginInvoke(DispatcherPriority.ApplicationIdle, new Action(() =>
        {
            try
            {
                UpdateLayout();
                var width = Math.Max(1, (int)Math.Ceiling(ActualWidth));
                var height = Math.Max(1, (int)Math.Ceiling(ActualHeight));
                var bitmap = new RenderTargetBitmap(width, height, 96, 96, PixelFormats.Pbgra32);
                bitmap.Render(this);

                Directory.CreateDirectory(_screenshotDirectory);
                var path = Path.Combine(_screenshotDirectory, page switch
                {
                    DesktopPage.Dashboard => "dashboard.png",
                    DesktopPage.Connectors => "connectors.png",
                    DesktopPage.History => "history.png",
                    DesktopPage.Problems => "problems.png",
                    DesktopPage.Portable => "portable.png",
                    _ => throw new ArgumentOutOfRangeException(nameof(page), page, null),
                });
                var temporaryPath = path + ".tmp";
                var encoder = new PngBitmapEncoder();
                encoder.Frames.Add(BitmapFrame.Create(bitmap));
                using (var stream = File.Create(temporaryPath))
                {
                    encoder.Save(stream);
                }

                File.Move(temporaryPath, path, true);
            }
            catch
            {
                // UI smoke falls back to an OS capture when an in-process render is unavailable.
            }
        }));
    }

    private async void Refresh_Click(object sender, RoutedEventArgs e)
    {
        if (_viewModel is not null) await SafeUiActionAsync(() => _viewModel.RefreshAsync(_lifetime.Token));
    }

    private void ImportPortable_Click(object sender, RoutedEventArgs e)
    {
        if (_viewModel is null) return;
        var path = _viewModel.PickPortableImportFile();
        _ = SafeUiActionAsync(() => _viewModel.ImportPortableAsync(path, _lifetime.Token));
    }

    private async void ConfirmPortableImport_Click(object sender, RoutedEventArgs e)
    {
        if (_viewModel is not null) await SafeUiActionAsync(() => _viewModel.ConfirmPortableImportAsync(_lifetime.Token));
    }

    private void CancelPortableImport_Click(object sender, RoutedEventArgs e)
    {
        _viewModel?.CancelPortableImport();
    }

    private void ExportPortable_Click(object sender, RoutedEventArgs e)
    {
        if (_viewModel is null) return;
        _ = SafeUiActionAsync(() => _viewModel.ExportPortableAsync(_lifetime.Token));
    }

    private async void SyncAll_Click(object sender, RoutedEventArgs e)
    {
        if (_viewModel is not null) await SafeUiActionAsync(async () => { await _viewModel.SyncAllAsync(_lifetime.Token); });
    }

    private async void ConnectorAction_Click(object sender, RoutedEventArgs e)
    {
        if (_viewModel is null || sender is not Button { Tag: ConnectorRow row })
        {
            return;
        }

        if (row.IsSyncing)
        {
            _viewModel.CancelSync(row);
            return;
        }

        if (row.IsConfigured)
        {
            if (row.IsEnabled)
            {
                await SafeUiActionAsync(async () => { await _viewModel.SyncConnectorAsync(row, _lifetime.Token); });
            }
        }
        else
        {
            await SafeUiActionAsync(async () => { await _viewModel.SaveConnectorAsync(row, _lifetime.Token); });
        }
    }

    private async void ConnectorToggle_Click(object sender, RoutedEventArgs e)
    {
        if (_viewModel is not null && sender is Button { Tag: ConnectorRow row })
        {
            await SafeUiActionAsync(async () => { await _viewModel.SetConnectorEnabledAsync(row, !row.IsEnabled, _lifetime.Token); });
        }
    }

    private async void HistoryFilter_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_viewModel is null || e.AddedItems.Count == 0 || e.AddedItems[0] is not string judgeFilter)
        {
            return;
        }

        await SafeUiActionAsync(() => _viewModel.SelectHistoryJudgeAsync(judgeFilter, _lifetime.Token));
    }

    private async void ProblemJudgeFilter_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_viewModel is null || e.AddedItems.Count == 0 || e.AddedItems[0] is not string judgeFilter)
        {
            return;
        }

        await SafeUiActionAsync(() => _viewModel.SelectProblemJudgeAsync(judgeFilter, _lifetime.Token));
    }

    private async void ProblemStatusFilter_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_viewModel is null || e.AddedItems.Count == 0 || e.AddedItems[0] is not string statusFilter)
        {
            return;
        }

        await SafeUiActionAsync(() => _viewModel.SelectProblemStatusAsync(statusFilter, _lifetime.Token));
    }

    private async void ProblemSortFilter_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_viewModel is null || e.AddedItems.Count == 0 || e.AddedItems[0] is not string sort)
        {
            return;
        }

        await SafeUiActionAsync(() => _viewModel.SelectProblemSortAsync(sort, _lifetime.Token));
    }

    private async void ProblemSearch_KeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key is not Key.Enter || _viewModel is null || sender is not System.Windows.Controls.TextBox box)
        {
            return;
        }

        await SafeUiActionAsync(() => _viewModel.SetProblemSearchAsync(box.Text, _lifetime.Token));
    }

    private async void ProblemSearch_LostFocus(object sender, RoutedEventArgs e)
    {
        if (_viewModel is null || sender is not System.Windows.Controls.TextBox box)
        {
            return;
        }

        await SafeUiActionAsync(() => _viewModel.SetProblemSearchAsync(box.Text, _lifetime.Token));
    }

    private async void ProblemTag_KeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key is not Key.Enter || _viewModel is null || sender is not System.Windows.Controls.TextBox box)
        {
            return;
        }

        await SafeUiActionAsync(() => _viewModel.SetProblemTagAsync(box.Text, _lifetime.Token));
    }

    private async void ProblemTag_LostFocus(object sender, RoutedEventArgs e)
    {
        if (_viewModel is null || sender is not System.Windows.Controls.TextBox box)
        {
            return;
        }

        await SafeUiActionAsync(() => _viewModel.SetProblemTagAsync(box.Text, _lifetime.Token));
    }

    private async void ProblemFavorite_Changed(object sender, RoutedEventArgs e)
    {
        if (_viewModel is null || sender is not System.Windows.Controls.CheckBox box)
        {
            return;
        }

        await SafeUiActionAsync(() => _viewModel.SetProblemFavoriteOnlyAsync(box.IsChecked == true, _lifetime.Token));
    }

    private async void ProblemDifficultyBounds_LostFocus(object sender, RoutedEventArgs e)
    {
        await CommitDifficultyBoundsAsync();
    }

    private async void ProblemDifficultyBounds_KeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key is Key.Enter)
        {
            await CommitDifficultyBoundsAsync();
        }
    }

    private async Task CommitDifficultyBoundsAsync()
    {
        if (_viewModel is null)
        {
            return;
        }

        var min = FindProblemControl<System.Windows.Controls.TextBox>("ProblemDifficultyMin")?.Text ?? string.Empty;
        var max = FindProblemControl<System.Windows.Controls.TextBox>("ProblemDifficultyMax")?.Text ?? string.Empty;
        await SafeUiActionAsync(() => _viewModel.SetProblemDifficultyBoundsAsync(min, max, _lifetime.Token));
    }

    private T? FindProblemControl<T>(string automationId) where T : DependencyObject
    {
        return FindVisualChild<T>(this, automationId);
    }

    private static T? FindVisualChild<T>(DependencyObject parent, string automationId) where T : DependencyObject
    {
        var count = System.Windows.Media.VisualTreeHelper.GetChildrenCount(parent);
        for (var index = 0; index < count; index++)
        {
            var child = System.Windows.Media.VisualTreeHelper.GetChild(parent, index);
            if (child is T matched
                && string.Equals(AutomationProperties.GetAutomationId(matched), automationId, StringComparison.Ordinal))
            {
                return matched;
            }

            var nested = FindVisualChild<T>(child, automationId);
            if (nested is not null)
            {
                return nested;
            }
        }

        return null;
    }

    private async void ClearProblemFilters_Click(object sender, RoutedEventArgs e)
    {
        if (_viewModel is null)
        {
            return;
        }

        await SafeUiActionAsync(() => _viewModel.ClearProblemFiltersAsync(_lifetime.Token));
        ResetProblemFilterBoxes();
    }

    private void ResetProblemFilterBoxes()
    {
        var search = FindProblemControl<System.Windows.Controls.TextBox>("ProblemSearch");
        if (search is not null) search.Text = string.Empty;
        var tag = FindProblemControl<System.Windows.Controls.TextBox>("ProblemTag");
        if (tag is not null) tag.Text = string.Empty;
        var min = FindProblemControl<System.Windows.Controls.TextBox>("ProblemDifficultyMin");
        if (min is not null) min.Text = string.Empty;
        var max = FindProblemControl<System.Windows.Controls.TextBox>("ProblemDifficultyMax");
        if (max is not null) max.Text = string.Empty;
    }

    private async void LoadMoreProblems_Click(object sender, RoutedEventArgs e)
    {
        if (_viewModel is null)
        {
            return;
        }

        await SafeUiActionAsync(() => _viewModel.LoadMoreProblemsAsync(_lifetime.Token));
    }

    private async void OpenProblemDetail_Click(object sender, RoutedEventArgs e)
    {
        if (_viewModel is null || sender is not Button { Tag: ProblemRow row })
        {
            return;
        }

        await SafeUiActionAsync(() => _viewModel.OpenProblemDetailAsync(row, _lifetime.Token));
    }

    private void CloseProblemDetail_Click(object sender, RoutedEventArgs e)
    {
        _viewModel?.CloseProblemDetail();
    }

    private void OpenProblemDetailUrl_Click(object sender, RoutedEventArgs e)
    {
        if (_viewModel?.SelectedProblemDetail is not { } detail || string.IsNullOrWhiteSpace(detail.Url))
        {
            return;
        }

        try
        {
            using var process = new System.Diagnostics.Process
            {
                StartInfo = new System.Diagnostics.ProcessStartInfo(detail.Url)
                {
                    UseShellExecute = true,
                },
            };
            process.Start();
        }
        catch
        {
            // The shell may refuse the URL (offline or no browser); the detail stays unchanged.
        }
    }

    private void OpenProblem_Click(object sender, RoutedEventArgs e)
    {
        if (sender is not Button { Tag: ProblemRow row } || string.IsNullOrWhiteSpace(row.Url))
        {
            return;
        }

        try
        {
            using var process = new System.Diagnostics.Process
            {
                StartInfo = new System.Diagnostics.ProcessStartInfo(row.Url)
                {
                    UseShellExecute = true,
                },
            };
            process.Start();
        }
        catch
        {
            // The shell may refuse the URL (offline or no browser); the ledger row stays unchanged.
        }
    }

    private async void RetryHistory_Click(object sender, RoutedEventArgs e)
    {
        if (_viewModel is not null && sender is Button { Tag: HistoryRow row })
        {
            await SafeUiActionAsync(async () => { await _viewModel.RetryHistoryAsync(row, _lifetime.Token); });
        }
    }

    private async Task SafeUiActionAsync(Func<Task> action)
    {
        try { await action(); }
        catch (OperationCanceledException) when (_lifetime.IsCancellationRequested) { }
        catch (Exception exception)
        {
            if (_viewModel is null) return;
            _viewModel.ReportStorageFailure(exception is StorageException storage
                ? storage.Failure.WireCode
                : "STORAGE_IO_FAILURE");
        }
    }
}
