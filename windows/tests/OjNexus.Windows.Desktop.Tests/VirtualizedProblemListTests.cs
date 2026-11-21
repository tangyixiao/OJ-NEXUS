using System.Collections.ObjectModel;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Markup;
using System.Windows.Media;
using OjNexus.Windows.Desktop.ViewModels;

namespace OjNexus.Windows.Desktop.Tests;

/// <summary>
/// Runs the test on workstations only: on window-less hosted CI runners the
/// behavioral probe creates a real WPF window and deadlocks (observed as a
/// 30s join timeout), and headless layout never realizes containers without
/// an HwndSource. CI gates the XAML structure pin instead; the probe's result
/// is recorded in the phase verification record.
/// </summary>
public sealed class WorkstationFactAttribute : FactAttribute
{
    public WorkstationFactAttribute()
    {
        if (!string.IsNullOrWhiteSpace(Environment.GetEnvironmentVariable("CI")))
        {
            Skip = "workstation-only: the STA virtualization probe creates a real window; CI pins the XAML structure instead";
        }
    }
}

/// <summary>
/// Phase 138 virtualization gate: the PROBLEMS list must not realize a container
/// per row. The behavioral half binds 10,000 rows to a ListBox configured exactly
/// like the shipped XAML (fixed viewport, recycling virtualization, content
/// scrolling) and asserts the realized container count stays bounded; the source
/// audit half pins those attributes in MainWindow.xaml so the configuration
/// cannot silently regress.
/// </summary>
public sealed class VirtualizedProblemListTests
{
    private const int RowCount = 10_000;
    private const int RealizedContainerBound = 200;

    [Fact]
    public void MainWindowProblemsList_PinsVirtualizationConfiguration()
    {
        var xaml = File.ReadAllText(FindMainWindowXamlPath());
        var listStart = xaml.IndexOf("AutomationProperties.AutomationId=\"ProblemList\"", StringComparison.Ordinal);
        Assert.True(listStart >= 0, "PROBLEMS list not found in MainWindow.xaml");
        var listEnd = xaml.IndexOf("</ListBox>", listStart, StringComparison.Ordinal);
        Assert.True(listEnd > listStart, "PROBLEMS list not closed in MainWindow.xaml");
        var list = xaml[listStart..listEnd];
        Assert.Contains("VirtualizingPanel.IsVirtualizing=\"True\"", list, StringComparison.Ordinal);
        Assert.Contains("VirtualizingPanel.VirtualizationMode=\"Recycling\"", list, StringComparison.Ordinal);
        Assert.Contains("ScrollViewer.CanContentScroll=\"True\"", list, StringComparison.Ordinal);
        Assert.Matches(@"Height=""\d+""", list);
    }

    [WorkstationFact]
    public void TenThousandRows_RealizeABoundedNumberOfContainers()
    {
        Exception? failure = null;
        int realized;
        bool deepestRealized;
        var thread = new Thread(() =>
        {
            try
            {
                var rows = new ObservableCollection<ProblemRow>(
                    Enumerable.Range(0, RowCount).Select(index => new ProblemRow(
                        "CODEFORCES", $"9000{index:D4}", $"Virtualization bulk row {index:D4}",
                        string.Empty, "UNSOLVED", "1970-01-01 00:00:00 UTC", "UNKNOWN", 0)));
                var listBox = CreateConfiguredList();
                listBox.ItemsSource = rows;
                var window = new Window
                {
                    Width = 760,
                    Height = 520,
                    Content = listBox,
                    ShowActivated = false,
                    ShowInTaskbar = false,
                    WindowStartupLocation = WindowStartupLocation.Manual,
                    Left = -2000,
                    Top = -2000,
                };
                window.Show();
                listBox.UpdateLayout();

                realized = CountRealizedContainers(listBox);
                deepestRealized = listBox.ItemContainerGenerator.ContainerFromIndex(RowCount - 1) is not null;

                window.Close();
            }
            catch (Exception exception)
            {
                failure = exception;
                realized = -1;
                deepestRealized = false;
            }
        });
        realized = -1;
        deepestRealized = false;
        thread.SetApartmentState(ApartmentState.STA);
        thread.Start();
        thread.Join(TimeSpan.FromSeconds(30));
        Assert.True(!thread.IsAlive, "STA virtualization probe did not finish in 30 seconds");
        if (failure is not null)
        {
            throw failure;
        }

        Assert.True(realized > 0, "no containers realized — the probe did not lay out");
        Assert.True(realized < RealizedContainerBound,
            $"realized {realized} containers for {RowCount} rows; virtualization is not bounding the visual tree");
        Assert.False(deepestRealized, "the last row's container was realized; the list rendered offscreen rows");
    }

    private static ListBox CreateConfiguredList()
    {
        var templateXaml = """
            <DataTemplate xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
                          xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml">
              <Border BorderBrush="#26364A" BorderThickness="1" Padding="16" Margin="0,0,0,10">
                <Grid>
                  <Grid.ColumnDefinitions>
                    <ColumnDefinition Width="130" />
                    <ColumnDefinition Width="170" />
                    <ColumnDefinition Width="*" />
                    <ColumnDefinition Width="90" />
                    <ColumnDefinition Width="90" />
                    <ColumnDefinition Width="110" />
                    <ColumnDefinition Width="Auto" />
                    <ColumnDefinition Width="Auto" />
                  </Grid.ColumnDefinitions>
                  <TextBlock Text="{Binding Judge}" />
                  <TextBlock Grid.Column="1" Text="{Binding ExternalId}" Margin="12,0,0,0" />
                  <TextBlock Grid.Column="2" Text="{Binding Title}" Margin="12,0,12,0" TextWrapping="Wrap" />
                  <TextBlock Grid.Column="3" Text="{Binding DifficultyLabel}" HorizontalAlignment="Right" />
                  <TextBlock Grid.Column="4" Text="{Binding AttemptCount, StringFormat='ATTEMPTS {0}'}" HorizontalAlignment="Right" />
                  <TextBlock Grid.Column="5" Text="{Binding Status}" HorizontalAlignment="Right" />
                  <Button Grid.Column="6" Content="DETAIL" Margin="16,0,0,0" />
                  <Button Grid.Column="7" Content="OPEN" Margin="10,0,0,0" />
                </Grid>
              </Border>
            </DataTemplate>
            """;
        var listBox = new ListBox
        {
            Width = 720,
            Height = 480,
            Background = Brushes.Transparent,
            BorderThickness = new Thickness(0),
            HorizontalContentAlignment = HorizontalAlignment.Stretch,
            ItemTemplate = (DataTemplate)XamlReader.Parse(templateXaml),
        };
        listBox.SetValue(VirtualizingPanel.IsVirtualizingProperty, true);
        listBox.SetValue(VirtualizingPanel.VirtualizationModeProperty, VirtualizationMode.Recycling);
        listBox.SetValue(ScrollViewer.CanContentScrollProperty, true);
        return listBox;
    }

    private static int CountRealizedContainers(ListBox listBox)
    {
        var count = 0;
        for (var index = 0; index < RowCount; index++)
        {
            if (listBox.ItemContainerGenerator.ContainerFromIndex(index) is not null)
            {
                count++;
            }
        }

        return count;
    }

    private static string FindMainWindowXamlPath()
    {
        var candidate = new DirectoryInfo(AppContext.BaseDirectory);
        while (candidate is not null && candidate.Name != "windows")
        {
            candidate = candidate.Parent;
        }

        Assert.NotNull(candidate);
        var path = Path.Combine(candidate!.FullName, "src", "OjNexus.Windows.Desktop", "MainWindow.xaml");
        Assert.True(File.Exists(path), $"MainWindow.xaml not found at {path}");
        return path;
    }
}
