using System.Text;

if (args.Length != 2) return 2;
var lockPath = Path.Combine(args[0], "ojnexus.lifecycle.lock");
Directory.CreateDirectory(args[0]);
var exclusive = args[1] == "exclusive";
try
{
    using var lease = new FileStream(lockPath, FileMode.OpenOrCreate, FileAccess.ReadWrite,
        exclusive ? FileShare.None : FileShare.ReadWrite);
    Console.Out.WriteLine("READY");
    Console.Out.Flush();
    _ = Console.In.ReadLine();
    return 0;
}
catch (IOException)
{
    Console.Out.WriteLine("BUSY");
    Console.Out.Flush();
    return 10;
}
