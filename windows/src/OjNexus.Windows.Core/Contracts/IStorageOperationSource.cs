namespace OjNexus.Windows.Core.Contracts;

public interface IStorageOperationSource : IAsyncDisposable
{
    Task RunAsync(Func<ISyncStore, CancellationToken, Task> work, CancellationToken cancellationToken);
    Task<T> RunAsync<T>(Func<ISyncStore, CancellationToken, Task<T>> work, CancellationToken cancellationToken);
}
