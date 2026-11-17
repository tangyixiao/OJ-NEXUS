using Microsoft.Data.Sqlite;

namespace OjNexus.Windows.Core.Storage;

/// <summary>One archived portable package header. The archive holds a single package at a time. <see cref="GeneratorJson"/> is the canonical generator-object JSON.</summary>
public sealed record PortableArchivePackageRow(
    string Identity,
    string ScopeId,
    string SourcePlatform,
    string GeneratorJson,
    long CreatedAtEpochMs,
    string CountsJson);

/// <summary>One archived record row; <see cref="PayloadJson"/> is the canonical record JSON, stored verbatim.</summary>
public sealed record PortableArchiveRecordRow(
    string Collection,
    string StableId,
    string SourceScopeId,
    string Origin,
    string State,
    string PayloadJson,
    string SourceTag,
    bool Synthetic,
    long SortOrder);

/// <summary>
/// Bounded portable archive over the schema v8 tables. The repository owns
/// contract semantics; this store only persists whole packages transactionally.
/// Replaces are all-or-nothing: any failure before commit leaves the previous
/// archive untouched, and cancel/failure can never leave partial rows.
/// </summary>
public sealed class PortableArchiveStore
{
    private readonly SqliteConnectionFactory connectionFactory;
    private readonly OperationPermit _permit;

    internal PortableArchiveStore(SqliteConnectionFactory connectionFactory, OperationPermit permit)
    {
        this.connectionFactory = connectionFactory ?? throw new ArgumentNullException(nameof(connectionFactory));
        _permit = permit ?? throw new ArgumentNullException(nameof(permit));
    }

    private IDisposable EnterStoreCall() => connectionFactory.EnterStoreCall(_permit);
    private SqliteConnectionLease OpenLease() => connectionFactory.OpenLease(_permit);

    /// <summary>Swaps the whole archive to <paramref name="package"/> with <paramref name="records"/> in one transaction.</summary>
    public async Task ReplaceAllAsync(
        PortableArchivePackageRow package,
        IReadOnlyList<PortableArchiveRecordRow> records,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(package);
        ArgumentNullException.ThrowIfNull(records);
        using var storeCall = EnterStoreCall();
        using var lease = OpenLease();
        var connection = lease.Connection;
        using var transaction = connection.BeginTransaction();
        await ExecuteAsync(connection, transaction, "DELETE FROM portable_archive_records", cancellationToken);
        await ExecuteAsync(connection, transaction, "DELETE FROM portable_archive_packages", cancellationToken);

        using (var command = connection.CreateCommand())
        {
            command.Transaction = transaction;
            command.CommandText = """
                INSERT INTO portable_archive_packages (identity, scope_id, source_platform, generator, created_at_epoch_ms, counts_json)
                VALUES ($identity, $scopeId, $sourcePlatform, $generator, $createdAtEpochMs, $countsJson)
                """;
            command.Parameters.AddWithValue("$identity", package.Identity);
            command.Parameters.AddWithValue("$scopeId", package.ScopeId);
            command.Parameters.AddWithValue("$sourcePlatform", package.SourcePlatform);
            command.Parameters.AddWithValue("$generator", package.GeneratorJson);
            command.Parameters.AddWithValue("$createdAtEpochMs", package.CreatedAtEpochMs);
            command.Parameters.AddWithValue("$countsJson", package.CountsJson);
            await command.ExecuteNonQueryAsync(cancellationToken);
        }

        foreach (var record in records)
        {
            using var command = connection.CreateCommand();
            command.Transaction = transaction;
            command.CommandText = """
                INSERT INTO portable_archive_records
                    (identity, collection, stable_id, source_scope_id, origin, state, payload_json, source_tag, synthetic, sort_order)
                VALUES ($identity, $collection, $stableId, $sourceScopeId, $origin, $state, $payloadJson, $sourceTag, $synthetic, $sortOrder)
                """;
            command.Parameters.AddWithValue("$identity", package.Identity);
            command.Parameters.AddWithValue("$collection", record.Collection);
            command.Parameters.AddWithValue("$stableId", record.StableId);
            command.Parameters.AddWithValue("$sourceScopeId", record.SourceScopeId);
            command.Parameters.AddWithValue("$origin", record.Origin);
            command.Parameters.AddWithValue("$state", record.State);
            command.Parameters.AddWithValue("$payloadJson", record.PayloadJson);
            command.Parameters.AddWithValue("$sourceTag", record.SourceTag);
            command.Parameters.AddWithValue("$synthetic", record.Synthetic ? 1L : 0L);
            command.Parameters.AddWithValue("$sortOrder", record.SortOrder);
            await command.ExecuteNonQueryAsync(cancellationToken);
        }

        await transaction.CommitAsync(cancellationToken);
    }

    /// <summary>Read-only read of the archived package header, or null when the archive is empty.</summary>
    public async Task<PortableArchivePackageRow?> GetPackageAsync(CancellationToken cancellationToken)
    {
        using var storeCall = EnterStoreCall();
        using var lease = OpenLease();
        var connection = lease.Connection;
        using var command = connection.CreateCommand();
        command.CommandText = """
            SELECT identity, scope_id, source_platform, generator, created_at_epoch_ms, counts_json
            FROM portable_archive_packages
            LIMIT 1
            """;
        using var reader = await command.ExecuteReaderAsync(cancellationToken);
        if (!await reader.ReadAsync(cancellationToken))
        {
            return null;
        }

        return new PortableArchivePackageRow(
            reader.GetString(0),
            reader.GetString(1),
            reader.GetString(2),
            reader.GetString(3),
            reader.GetInt64(4),
            reader.GetString(5));
    }

    /// <summary>Read-only read of the archived records for <paramref name="identity"/>, in insertion order.</summary>
    public async Task<IReadOnlyList<PortableArchiveRecordRow>> GetRecordsAsync(string identity, CancellationToken cancellationToken)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(identity);
        using var storeCall = EnterStoreCall();
        using var lease = OpenLease();
        var connection = lease.Connection;
        using var command = connection.CreateCommand();
        command.CommandText = """
            SELECT collection, stable_id, source_scope_id, origin, state, payload_json, source_tag, synthetic, sort_order
            FROM portable_archive_records
            WHERE identity = $identity
            ORDER BY sort_order ASC
            """;
        command.Parameters.AddWithValue("$identity", identity);
        using var reader = await command.ExecuteReaderAsync(cancellationToken);
        var records = new List<PortableArchiveRecordRow>();
        while (await reader.ReadAsync(cancellationToken))
        {
            records.Add(new PortableArchiveRecordRow(
                reader.GetString(0),
                reader.GetString(1),
                reader.GetString(2),
                reader.GetString(3),
                reader.GetString(4),
                reader.GetString(5),
                reader.GetString(6),
                reader.GetInt64(7) != 0,
                reader.GetInt64(8)));
        }

        return records;
    }

    /// <summary>Removes every archived row. Used by tests; production flow replaces wholesale instead.</summary>
    public async Task ClearAsync(CancellationToken cancellationToken)
    {
        using var storeCall = EnterStoreCall();
        using var lease = OpenLease();
        var connection = lease.Connection;
        using var transaction = connection.BeginTransaction();
        await ExecuteAsync(connection, transaction, "DELETE FROM portable_archive_records", cancellationToken);
        await ExecuteAsync(connection, transaction, "DELETE FROM portable_archive_packages", cancellationToken);
        await transaction.CommitAsync(cancellationToken);
    }

    private static async Task ExecuteAsync(
        SqliteConnection connection,
        SqliteTransaction transaction,
        string commandText,
        CancellationToken cancellationToken)
    {
        using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = commandText;
        await command.ExecuteNonQueryAsync(cancellationToken);
    }
}
