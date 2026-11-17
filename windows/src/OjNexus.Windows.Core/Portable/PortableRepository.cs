using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using OjNexus.Windows.Core.Storage;

namespace OjNexus.Windows.Core.Portable;

/// <summary>
/// Orchestration between the portable codec and the bounded archive store:
/// <c>PreviewAsync</c> is a pure read; <c>ApplyAsync</c> revalidates from the
/// bytes (never trusting a prior preview), no-ops on an identical identity and
/// otherwise swaps the whole archive in the store's single transaction;
/// <c>ExportAsync</c> rebuilds canonical bytes from archived rows and asserts
/// the rebuilt identity; <c>Cancel</c> aborts an in-flight apply at
/// cooperative pre-write checkpoints and never writes.
/// </summary>
public sealed class PortableRepository
{
    private readonly PortableCodec codec;
    private readonly PortableArchiveStore store;
    private int applyGeneration;

    public PortableRepository(PortableCodec codec, PortableArchiveStore store)
    {
        this.codec = codec ?? throw new ArgumentNullException(nameof(codec));
        this.store = store ?? throw new ArgumentNullException(nameof(store));
    }

    /// <summary>Read-only validation + preview. Performs zero store calls.</summary>
    public Task<PortablePreview> PreviewAsync(byte[] bytes, CancellationToken cancellationToken) =>
        Task.FromResult(codec.Preview(bytes));

    /// <summary>User-confirmed transactional apply of one package.</summary>
    public async Task<PortableApplyResult> ApplyAsync(byte[] bytes, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(bytes);
        var generation = Volatile.Read(ref applyGeneration);
        var package = codec.Validate(bytes); // PortableContractException propagates: whole-package reject, zero writes.

        var stored = await store.GetPackageAsync(cancellationToken);
        EnsureNotCancelled(generation);
        var previousRows = stored is null
            ? 0
            : (await store.GetRecordsAsync(stored.Identity, cancellationToken)).Count;

        if (stored is not null && string.Equals(stored.Identity, package.Identity, StringComparison.Ordinal))
        {
            var editableCount = CountTier(package, PortableTier.Editable);
            var storedOnlyCount = CountTier(package, PortableTier.StoredOnly);
            return new PortableApplyResult(
                package.Identity,
                editableCount,
                storedOnlyCount,
                RejectedCount: 0,
                RowsUpserted: 0,
                RowsDeleted: 0,
                Identical: true);
        }

        var header = new PortableArchivePackageRow(
            package.Identity,
            package.ScopeId,
            package.SourcePlatform,
            package.GeneratorJson,
            package.CreatedAtEpochMs,
            JsonSerializer.Serialize(package.Counts));
        var rows = new List<PortableArchiveRecordRow>();
        long ordinal = 0;
        foreach (var collection in PortableSpec.Collections)
        {
            foreach (var record in package.Records[collection])
            {
                var tier = collection is "problems" or "reviews"
                    ? PortableTier.Editable
                    : PortableTier.StoredOnly;
                rows.Add(new PortableArchiveRecordRow(
                    Collection: collection,
                    StableId: record.StableId,
                    SourceScopeId: record.SourceScopeId,
                    Origin: record.Origin,
                    State: tier == PortableTier.Editable ? "editable" : "stored_only",
                    PayloadJson: record.CanonicalJson,
                    SourceTag: package.SourceTag,
                    Synthetic: record.Synthetic,
                    SortOrder: ordinal++));
            }
        }

        EnsureNotCancelled(generation); // last pre-write checkpoint.
        await store.ReplaceAllAsync(header, rows, cancellationToken);
        return new PortableApplyResult(
            package.Identity,
            rows.Count(r => r.State == "editable"),
            rows.Count(r => r.State == "stored_only"),
            RejectedCount: 0,
            RowsUpserted: rows.Count,
            RowsDeleted: previousRows,
            Identical: false);
    }

    /// <summary>
    /// Rebuild the whole package from archived rows. The rebuilt SHA-256 must
    /// equal the stored identity, proving no accepted record was lost.
    /// </summary>
    public async Task<byte[]> ExportAsync(CancellationToken cancellationToken)
    {
        var stored = await store.GetPackageAsync(cancellationToken)
            ?? throw new InvalidOperationException("portable archive is empty; nothing to export");
        var records = await store.GetRecordsAsync(stored.Identity, cancellationToken);

        var entries = new List<ObjectEntry>
        {
            new("formatVersion", new PortableValue.Num("1")),
            new("scopeId", new PortableValue.Str(stored.ScopeId)),
            new("sourcePlatform", new PortableValue.Str(stored.SourcePlatform)),
            new("generator", PortableJson.Parse(Encoding.UTF8.GetBytes(stored.GeneratorJson))),
            new("createdAtEpochMs", new PortableValue.Num(
                stored.CreatedAtEpochMs.ToString(System.Globalization.CultureInfo.InvariantCulture))),
        };

        var counts = new List<ObjectEntry>();
        var arrays = new List<ObjectEntry>();
        foreach (var collection in PortableSpec.Collections)
        {
            var collectionRecords = records
                .Where(record => record.Collection == collection)
                .OrderBy(record => record.SortOrder)
                .ToList();
            counts.Add(new ObjectEntry(collection, new PortableValue.Num(
                collectionRecords.Count.ToString(System.Globalization.CultureInfo.InvariantCulture))));
            arrays.Add(new ObjectEntry(collection, new PortableValue.Arr(collectionRecords
                .Select(record => PortableJson.Parse(Encoding.UTF8.GetBytes(record.PayloadJson)))
                .ToList())));
        }

        entries.Add(new ObjectEntry("counts", new PortableValue.Obj(counts)));
        foreach (var array in arrays)
        {
            entries.Add(array);
        }

        var canonical = PortableJson.CanonicalBytes(new PortableValue.Obj(entries));
        var identity = Convert.ToHexString(SHA256.HashData(canonical)).ToLowerInvariant();
        if (!string.Equals(identity, stored.Identity, StringComparison.Ordinal))
        {
            throw new InvalidOperationException("portable archive identity mismatch");
        }

        return canonical;
    }

    /// <summary>Aborts an in-flight apply at its next pre-write checkpoint. Never writes.</summary>
    public void Cancel() => Interlocked.Increment(ref applyGeneration);

    private void EnsureNotCancelled(int generation)
    {
        if (Volatile.Read(ref applyGeneration) != generation)
        {
            throw new OperationCanceledException("portable apply was cancelled before any write");
        }
    }

    private static int CountTier(ValidatedPackage package, PortableTier tier)
    {
        var decisions = new PortableCodec().Classify(package);
        return decisions.Count(decision => decision.Tier == tier);
    }
}
