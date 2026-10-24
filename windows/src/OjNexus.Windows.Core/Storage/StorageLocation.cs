using System.ComponentModel;
using System.Runtime.InteropServices;
using Microsoft.Win32.SafeHandles;

namespace OjNexus.Windows.Core.Storage;

internal sealed record StorageLocation(string DataDirectory, string DatabasePath, string LockPath, string MarkerPath,
    string MarkerTempPath, string InitPath)
{
    public static StorageLocation Resolve(string? root)
    {
        var raw = WindowsPaths.GetDataDirectory(root);
        if (string.IsNullOrWhiteSpace(raw) || raw.Any(char.IsControl) || raw.StartsWith("\\\\", StringComparison.Ordinal)
            || raw.StartsWith("\\?", StringComparison.Ordinal) || raw.StartsWith("\\.", StringComparison.Ordinal)
            || raw.Contains("file:", StringComparison.OrdinalIgnoreCase))
            throw new StorageException(new(StorageFailureCode.UnsupportedLocation, "location"));
        foreach (var part in raw.Split(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar))
            if (part.EndsWith(' ') || part.EndsWith('.')) throw new StorageException(new(StorageFailureCode.UnsupportedLocation, "location"));
        var full = Path.GetFullPath(raw);
        var drive = new DriveInfo(Path.GetPathRoot(full)!);
        if (drive.DriveType is not (DriveType.Fixed or DriveType.Removable))
            throw new StorageException(new(StorageFailureCode.UnsupportedLocation, "location"));
        ValidateAncestors(full);
        return new(full, Path.Combine(full, "ojnexus.db"), Path.Combine(full, "ojnexus.lifecycle.lock"),
            Path.Combine(full, "ojnexus.initialized"), Path.Combine(full, "ojnexus.initialized.tmp"), Path.Combine(full, "ojnexus.init.db"));
    }

    public void ValidateControlledPaths(FileStream? heldLock = null)
    {
        ValidateAncestors(DataDirectory);
        foreach (var path in new[] { DatabasePath, LockPath, MarkerPath, MarkerTempPath, InitPath })
        {
            if (Directory.Exists(path)) throw new StorageException(new(StorageFailureCode.RecoveryBlocked, "inventory"));
            if (!File.Exists(path)) continue;
            var attrs = File.GetAttributes(path);
            if ((attrs & FileAttributes.ReparsePoint) != 0) throw new StorageException(new(StorageFailureCode.UnsupportedLocation, "location"));
            if (path == DatabasePath || path == LockPath)
            {
                var links = path == LockPath && heldLock is not null ? NativeFile.LinkCount(heldLock.SafeFileHandle) : NativeFile.LinkCount(path);
                if (links > 1) throw new StorageException(new(StorageFailureCode.UnsupportedLocation, "location"));
            }
        }
    }

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

internal static class NativeFile
{
    private const uint GenericRead = 0x80000000;
    private const uint ShareRead = 1, ShareWrite = 2, OpenAlways = 4, FileAttributeNormal = 0x80;
    [StructLayout(LayoutKind.Sequential)] private struct ByHandleFileInformation
    {
        public uint FileAttributes; public System.Runtime.InteropServices.ComTypes.FILETIME CreationTime, LastAccessTime, LastWriteTime;
        public uint VolumeSerialNumber, FileSizeHigh, FileSizeLow, NumberOfLinks, FileIndexHigh, FileIndexLow;
    }
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern SafeFileHandle CreateFile(string name, uint access, uint share, IntPtr security, uint creation, uint flags, IntPtr template);
    [DllImport("kernel32.dll", SetLastError = true)] private static extern bool GetFileInformationByHandle(SafeFileHandle handle, out ByHandleFileInformation info);

    public static uint LinkCount(string path)
    {
        using var handle = CreateFile(path, 0, ShareRead | ShareWrite, IntPtr.Zero, 3, FileAttributeNormal, IntPtr.Zero);
        if (handle.IsInvalid) throw new Win32Exception(Marshal.GetLastWin32Error());
        return LinkCount(handle);
    }

    public static uint LinkCount(SafeFileHandle handle)
    { if (!GetFileInformationByHandle(handle, out var info)) throw new Win32Exception(Marshal.GetLastWin32Error()); return info.NumberOfLinks; }

    public static FileStream Acquire(string path, bool exclusive)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        try { return new FileStream(path, FileMode.OpenOrCreate, FileAccess.ReadWrite, exclusive ? FileShare.None : FileShare.ReadWrite); }
        catch (IOException ex) when ((ex.HResult & 0xffff) is 32 or 33) { throw new StorageException(new(StorageFailureCode.Busy, "lease"), ex); }
    }
}
