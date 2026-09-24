using System.IO;
using System.Linq;

namespace OjNexus.Windows.Desktop.Tests;

/// <summary>
/// Guards the public HTTP boundary: every Windows host must build judge clients through
/// <c>PublicHttpClientFactory</c> so cookie handling stays disabled.
/// </summary>
public sealed class DesktopPublicTransportAuditTests
{
    [Fact]
    public void WindowsSources_NeverConstructHttpClientDirectly()
    {
        var sourceRoot = FindWindowsSourceRoot();
        var offenders = Directory
            .EnumerateFiles(sourceRoot, "*.cs", SearchOption.AllDirectories)
            .Where(path => !IsBuildOutput(path))
            .Select(path => new
            {
                Path = path,
                Line = File
                    .ReadLines(path)
                    .Select((text, index) => new { Text = text, Number = index + 1 })
                    .FirstOrDefault(line => line.Text.Contains("new HttpClient(", System.StringComparison.Ordinal)),
            })
            .Where(candidate => candidate.Line is not null)
            .Select(candidate => $"{Relative(sourceRoot, candidate.Path)}:{candidate.Line!.Number}")
            .ToArray();

        Assert.Empty(offenders);
    }

    private static string FindWindowsSourceRoot()
    {
        var directory = new DirectoryInfo(System.AppContext.BaseDirectory);
        while (directory is not null)
        {
            var candidate = Path.Combine(directory.FullName, "windows", "src");
            if (Directory.Exists(candidate))
            {
                return candidate;
            }

            directory = directory.Parent;
        }

        Assert.Fail("Unable to locate the windows/src source tree from the test working directory.");
        throw new InvalidOperationException();
    }

    private static bool IsBuildOutput(string path)
    {
        var separator = Path.DirectorySeparatorChar;
        return path.Split(separator).Any(segment =>
            string.Equals(segment, "bin", System.StringComparison.OrdinalIgnoreCase) ||
            string.Equals(segment, "obj", System.StringComparison.OrdinalIgnoreCase));
    }

    private static string Relative(string root, string path) => Path.GetRelativePath(root, path);
}
