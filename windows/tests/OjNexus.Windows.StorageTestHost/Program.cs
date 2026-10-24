using OjNexus.Windows.Core.Storage;

if (args.Length != 2) return 2;
if (args[1] == "open")
{
    Console.Out.WriteLine("PREPARED"); Console.Out.Flush();
    _ = Console.In.ReadLine();
    var result = await WindowsStorage.OpenAsync(args[0], null, CancellationToken.None);
    if (result.Session is null)
    { Console.Out.WriteLine(result.Failure?.WireCode == "STORAGE_BUSY" ? "BUSY" : "BLOCKED:" + result.Failure?.WireCode); return result.Failure?.WireCode == "STORAGE_BUSY" ? 10 : 20; }
    await using var session = result.Session;
    Console.Out.WriteLine("READY"); Console.Out.Flush();
    _ = Console.In.ReadLine();
    return 0;
}
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
