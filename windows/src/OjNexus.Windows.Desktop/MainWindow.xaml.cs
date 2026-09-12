using System.Windows;
using System.Windows.Controls;
using OjNexus.Windows.Desktop.ViewModels;

namespace OjNexus.Windows.Desktop;

public partial class MainWindow : Window
{
    private readonly CancellationTokenSource _lifetime = new();
    private readonly DesktopViewModel _viewModel;

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
        }
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
