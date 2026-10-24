using OjNexus.Windows.Core.Storage;
using Microsoft.Data.Sqlite;

if (args.Length != 2 || args[1] is not ("open" or "shared" or "exclusive" or "hot-journal")) return 2;
if (args[1] == "hot-journal")
{
    var databasePath = Path.Combine(args[0], "ojnexus.db");
    using var connection = new SqliteConnection(new SqliteConnectionStringBuilder
    { DataSource = databasePath, Mode = SqliteOpenMode.ReadWrite, Pooling = false, DefaultTimeout = 2 }.ToString());
    connection.Open();
    using (var cache = connection.CreateCommand())
    {
        cache.CommandText = "PRAGMA cache_size=1";
        cache.ExecuteNonQuery();
    }
    using var transaction = connection.BeginTransaction();
    using (var command = connection.CreateCommand())
    {
        command.Transaction = transaction;
        command.CommandText = "CREATE TABLE hot_journal_probe (id INTEGER PRIMARY KEY, payload TEXT NOT NULL)";
        command.ExecuteNonQuery();
    }
    for (var index = 0; index < 10000; index++)
    {
        using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = "INSERT INTO hot_journal_probe (payload) VALUES ($payload)";
        command.Parameters.AddWithValue("$payload", new string('x', 200));
        command.ExecuteNonQuery();
    }
    var journalPath = databasePath + "-journal";
    using (var journal = new FileStream(journalPath, FileMode.Open, FileAccess.ReadWrite, FileShare.ReadWrite)) journal.Flush(true);
    Console.Out.WriteLine("READY"); Console.Out.Flush();
    _ = Console.In.ReadLine();
    return 0;
}
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
Directory.CreateDirectory(args[0]);
try
{
    using var lease = NativeFile.Acquire(Path.Combine(args[0], "ojnexus.lifecycle.lock"), args[1] == "exclusive");
    Console.Out.WriteLine("READY");
    Console.Out.Flush();
    _ = Console.In.ReadLine();
    return 0;
}
catch (StorageException exception) when (exception.Failure.Code == StorageFailureCode.Busy)
{
    Console.Out.WriteLine("BUSY");
    Console.Out.Flush();
    return 10;
}
