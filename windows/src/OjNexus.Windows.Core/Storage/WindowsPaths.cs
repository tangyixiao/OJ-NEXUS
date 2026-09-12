namespace OjNexus.Windows.Core.Storage;

public static class WindowsPaths
{
    public static string GetDataDirectory(string? rootOverride = null)
    {
        if (!string.IsNullOrWhiteSpace(rootOverride))
        {
            return rootOverride;
        }

        var environmentOverride = Environment.GetEnvironmentVariable("OJ_NEXUS_DATA_DIRECTORY");
        if (!string.IsNullOrWhiteSpace(environmentOverride))
        {
            return environmentOverride;
        }

        return Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "OJ-NEXUS");
    }
}
