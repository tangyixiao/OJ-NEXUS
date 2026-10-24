using System.ComponentModel;
using System.Runtime.InteropServices;
using Microsoft.Win32.SafeHandles;

namespace OjNexus.Windows.Core.Storage;

internal sealed record StorageLocation(string DataDirectory, string DatabasePath, string LockPath, string MarkerPath,
    string MarkerTempPath, string InitPath)
{
    private static readonly string[] ExactArtifactNames =
    [
        "ojnexus.db", "ojnexus.lifecycle.lock", "ojnexus.initialized", "ojnexus.initialized.tmp",
        "ojnexus.init.db", "ojnexus.db-wal", "ojnexus.db-shm", "ojnexus.db-journal",
        "ojnexus.init.db-wal", "ojnexus.init.db-shm", "ojnexus.init.db-journal"
    ];

    public static StorageLocation Resolve(string? root)
    {
        var raw = WindowsPaths.GetDataDirectory(root);
        if (string.IsNullOrWhiteSpace(raw) || raw.Any(char.IsControl) || raw.StartsWith("\\\\", StringComparison.Ordinal)
            || raw.StartsWith("//", StringComparison.Ordinal) || raw.StartsWith("\\?", StringComparison.Ordinal)
            || raw.StartsWith("\\.", StringComparison.Ordinal) || raw.StartsWith("\\??\\", StringComparison.Ordinal)
            || raw.Contains("file:", StringComparison.OrdinalIgnoreCase) || IsDriveRelative(raw) || HasAmbiguousColon(raw))
            throw new StorageException(new(StorageFailureCode.UnsupportedLocation, "location"));
        foreach (var part in raw.Split(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar))
        {
            if (part is "." or ".." or "") continue;
            if (part.EndsWith(' ') || part.EndsWith('.'))
                throw new StorageException(new(StorageFailureCode.UnsupportedLocation, "location"));
        }
        var full = Path.GetFullPath(raw);
        var drive = new DriveInfo(Path.GetPathRoot(full)!);
        if (drive.DriveType is not (DriveType.Fixed or DriveType.Removable))
            throw new StorageException(new(StorageFailureCode.UnsupportedLocation, "location"));
        ValidateAncestors(full);
        return new(full, Path.Combine(full, "ojnexus.db"), Path.Combine(full, "ojnexus.lifecycle.lock"),
            Path.Combine(full, "ojnexus.initialized"), Path.Combine(full, "ojnexus.initialized.tmp"), Path.Combine(full, "ojnexus.init.db"));
    }

    public StorageInventory Inventory(FileStream? heldLock = null)
    {
        ValidateAncestors(DataDirectory);
        var entries = Directory.EnumerateFileSystemEntries(DataDirectory)
            .Select(path => (Path: path, Name: Path.GetFileName(path)))
            .ToArray();
        var found = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var restoreEntries = new List<string>();
        foreach (var (path, name) in entries)
        {
            var isRestore = name.StartsWith("ojnexus.restore.", StringComparison.OrdinalIgnoreCase);
            var canonical = isRestore ? null : ExactArtifactNames.FirstOrDefault(artifact =>
                string.Equals(artifact, name, StringComparison.OrdinalIgnoreCase));
            if (!isRestore && canonical is null) continue;

            if (!isRestore)
            {
                var matches = entries.Count(entry => string.Equals(entry.Name, canonical, StringComparison.OrdinalIgnoreCase));
                if (matches > 1)
                    throw new StorageException(new(StorageFailureCode.UnsupportedLocation, "location"));

                if (!string.Equals(name, canonical, StringComparison.Ordinal)
                    && !File.Exists(Path.Combine(DataDirectory, canonical!))
                    && !Directory.Exists(Path.Combine(DataDirectory, canonical!)))
                    throw new StorageException(new(StorageFailureCode.UnsupportedLocation, "location"));
            }

            if (isRestore) restoreEntries.Add(path);
            else found.Add(canonical!);

            var attributes = File.GetAttributes(path);
            if ((attributes & FileAttributes.ReparsePoint) != 0)
                throw new StorageException(new(StorageFailureCode.UnsupportedLocation, "location"));
            if ((attributes & FileAttributes.Directory) != 0)
            {
                throw new StorageException(new(StorageFailureCode.RecoveryBlocked, "inventory"));
            }
            if (!isRestore && (canonical is "ojnexus.db" or "ojnexus.lifecycle.lock"))
            {
                var links = canonical == "ojnexus.lifecycle.lock" && heldLock is not null
                    ? NativeFile.LinkCount(heldLock.SafeFileHandle)
                    : NativeFile.LinkCount(path);
                if (links > 1) throw new StorageException(new(StorageFailureCode.UnsupportedLocation, "location"));
            }
        }

        bool Has(string name) => found.Contains(name);
        return new StorageInventory(
            Has("ojnexus.db"), Has("ojnexus.lifecycle.lock"), Has("ojnexus.initialized"), Has("ojnexus.initialized.tmp"),
            Has("ojnexus.init.db"), Has("ojnexus.db-wal"), Has("ojnexus.db-shm"), Has("ojnexus.db-journal"),
            Has("ojnexus.init.db-wal"), Has("ojnexus.init.db-shm"), Has("ojnexus.init.db-journal"), restoreEntries);
    }

    private static bool IsDriveRelative(string value) => value.Length >= 2 && char.IsLetter(value[0]) && value[1] == ':'
        && (value.Length < 3 || value[2] is not ('\\' or '/'));

    private static bool HasAmbiguousColon(string value) =>
        value.Where((c, index) => c == ':' && !(index == 1 && char.IsLetter(value[0]))).Any();

    private static void ValidateAncestors(string path)
    {
        var current = new DirectoryInfo(path);
        while (current is not null)
        {
            if (current.Exists && (current.Attributes & FileAttributes.ReparsePoint) != 0)
                throw new StorageException(new(StorageFailureCode.UnsupportedLocation, "location"));
            current = current.Parent;
        }
    }
}

internal sealed record StorageInventory(
    bool HasDatabase,
    bool HasLock,
    bool HasMarker,
    bool HasMarkerTemp,
    bool HasInitDatabase,
    bool HasMainWal,
    bool HasMainShm,
    bool HasMainJournal,
    bool HasInitWal,
    bool HasInitShm,
    bool HasInitJournal,
    IReadOnlyList<string> RestoreEntries)
{
    public bool HasAnyMainCompanion => HasMainWal || HasMainShm || HasMainJournal;
    public bool HasAnyInitCompanion => HasInitWal || HasInitShm || HasInitJournal;
    public bool HasIncompleteEvidence => HasMarkerTemp || HasInitDatabase || HasAnyInitCompanion || RestoreEntries.Count != 0;
}

internal static class NativeFile
{
    private const uint ShareRead = 1, ShareWrite = 2, FileAttributeNormal = 0x80;
    [StructLayout(LayoutKind.Sequential)] private struct ByHandleFileInformation
    {
        public uint FileAttributes; public System.Runtime.InteropServices.ComTypes.FILETIME CreationTime, LastAccessTime, LastWriteTime;
        public uint VolumeSerialNumber, FileSizeHigh, FileSizeLow, NumberOfLinks, FileIndexHigh, FileIndexLow;
    }
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern SafeFileHandle CreateFile(string name, uint access, uint share, IntPtr security, uint creation, uint flags, IntPtr template);
    [DllImport("kernel32.dll", SetLastError = true)] private static extern bool GetFileInformationByHandle(SafeFileHandle handle, out ByHandleFileInformation info);
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true, EntryPoint = "CreateHardLinkW")]
    private static extern bool CreateHardLinkNative(string linkPath, string existingPath, IntPtr securityAttributes);

    public static uint LinkCount(string path)
    {
        using var handle = CreateFile(path, 0, ShareRead | ShareWrite, IntPtr.Zero, 3, FileAttributeNormal, IntPtr.Zero);
        if (handle.IsInvalid) throw new Win32Exception(Marshal.GetLastWin32Error());
        return LinkCount(handle);
    }

    public static uint LinkCount(SafeFileHandle handle)
    { if (!GetFileInformationByHandle(handle, out var info)) throw new Win32Exception(Marshal.GetLastWin32Error()); return info.NumberOfLinks; }

    public static void CreateHardLink(string linkPath, string existingPath)
    {
        if (!CreateHardLinkNative(linkPath, existingPath, IntPtr.Zero))
        {
            throw new Win32Exception(Marshal.GetLastWin32Error());
        }
    }

    public static FileStream Acquire(string path, bool exclusive)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        try { return new FileStream(path, FileMode.OpenOrCreate, FileAccess.ReadWrite, exclusive ? FileShare.None : FileShare.ReadWrite); }
        catch (IOException ex) when ((ex.HResult & 0xffff) is 32 or 33) { throw new StorageException(new(StorageFailureCode.Busy, "lease"), ex); }
    }
}
