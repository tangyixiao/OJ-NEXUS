namespace OjNexus.Windows.Core.Storage;

public static class WindowsPaths
{
    public static string GetDataDirectory(string? rootOverride = null)
    {
        if (!string.IsNullOrWhiteSpace(rootOverride))
        {
            return rootOverride;
        }

        return Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "OJ-NEXUS");
    }
}
