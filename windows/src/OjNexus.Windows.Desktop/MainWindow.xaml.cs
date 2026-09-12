using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Threading;
using OjNexus.Windows.Desktop.ViewModels;

namespace OjNexus.Windows.Desktop;

public partial class MainWindow : Window
{
    private readonly CancellationTokenSource _lifetime = new();
    private readonly DesktopViewModel _viewModel;
    private readonly string? _screenshotDirectory = Environment.GetEnvironmentVariable("OJ_NEXUS_UI_SCREENSHOT_DIRECTORY");

    public MainWindow()
    {
        InitializeComponent();
        _viewModel = DesktopRuntime.Create();
        DataContext = _viewModel;
        Loaded += OnLoaded;
        Closed += OnClosed;
    }

    private async void OnLoaded(object sender, RoutedEventArgs e)
    {
        try
        {
            await _viewModel.RefreshAsync(_lifetime.Token);
            CaptureRenderedScreenshot(DesktopPage.Dashboard);
        }
        catch (OperationCanceledException) when (_lifetime.IsCancellationRequested)
        {
        }
    }

    private void Navigate_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button { Tag: string page } && Enum.TryParse<DesktopPage>(page, out var target))
        {
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
        await _viewModel.RefreshAsync(_lifetime.Token);
    }

    private async void ConnectorAction_Click(object sender, RoutedEventArgs e)
    {
        if (sender is not Button { Tag: ConnectorRow row })
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
            await _viewModel.SyncConnectorAsync(row, _lifetime.Token);
        }
        else
        {
            await _viewModel.SaveConnectorAsync(row, _lifetime.Token);
        }
    }

    private async void HistoryFilter_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (e.AddedItems.Count == 0 || e.AddedItems[0] is not string judgeFilter)
        {
            return;
        }

        await _viewModel.SelectHistoryJudgeAsync(judgeFilter, _lifetime.Token);
    }

    private async void RetryHistory_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button { Tag: HistoryRow row })
        {
            await _viewModel.RetryHistoryAsync(row, _lifetime.Token);
        }
    }

    private void OnClosed(object? sender, EventArgs e)
    {
        _lifetime.Cancel();
        _viewModel.Dispose();
        _lifetime.Dispose();
    }
}
