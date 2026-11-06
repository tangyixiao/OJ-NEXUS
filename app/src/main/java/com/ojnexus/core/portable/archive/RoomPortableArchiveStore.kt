package com.ojnexus.core.portable.archive

import com.ojnexus.core.portable.ApplyStats
import com.ojnexus.core.portable.ArchivedManifest
import com.ojnexus.core.portable.ArchivedRecord
import com.ojnexus.core.portable.PortableArchive
import com.ojnexus.core.portable.PortableArchiveStore
import com.ojnexus.core.portable.PortableTier
import com.ojnexus.core.portable.PortableTierReason

/**
 * The only Room-aware [PortableArchiveStore] (phase 132).
 *
 * Every write goes through [PortableArchiveDao.replaceAll] / [PortableArchiveDao.clearAll],
 * so the archive is replaced all-or-nothing. Reads never mutate; exceptions are never
 * swallowed — a broken archive must surface, not silently look empty.
 *
 * Count convention (asserted by `PortableArchiveStoreTest`):
 * - [ApplyStats.rowsDeleted] counts every package row plus every record row that existed
 *   before the replace (so a 3-record archive with a manifest reports 4).
 * - [ApplyStats.rowsUpserted] counts the rows actually inserted: one per record plus one
 *   for the manifest when it is present.
 */
class RoomPortableArchiveStore(private val dao: PortableArchiveDao) : PortableArchiveStore {

    override suspend fun load(): PortableArchive {
        val manifest = dao.manifest()?.toDomain()
        val records = dao.records().map { it.toDomain() }
        return PortableArchive(manifest, records)
    }

    override suspend fun recordCount(): Int = dao.recordCount()

    override suspend fun currentIdentity(): String? = dao.manifest()?.identity

    override suspend fun replaceAll(archive: PortableArchive): ApplyStats {
        val counts = dao.replaceAll(
            manifest = archive.manifest?.toEntity(),
            records = archive.records.map { it.toEntity(archive.manifest) },
        )
        return ApplyStats(rowsUpserted = counts.rowsUpserted, rowsDeleted = counts.rowsDeleted)
    }

    override suspend fun clear() = dao.clearAll()
}

internal fun PortableArchivePackageEntity.toDomain(): ArchivedManifest = ArchivedManifest(
    identity = identity,
    formatVersion = formatVersion,
    scopeId = scopeId,
    sourcePlatform = sourcePlatform,
    generatorJson = generatorJson,
    createdAtEpochMs = createdAtEpochMs,
    canonicalBytes = canonicalBytes,
    sourceTag = sourceTag,
    importedAtEpochMs = importedAtEpochMs,
)

internal fun ArchivedManifest.toEntity(): PortableArchivePackageEntity = PortableArchivePackageEntity(
    identity = identity,
    formatVersion = formatVersion,
    scopeId = scopeId,
    sourcePlatform = sourcePlatform,
    generatorJson = generatorJson,
    createdAtEpochMs = createdAtEpochMs,
    canonicalBytes = canonicalBytes,
    sourceTag = sourceTag,
    importedAtEpochMs = importedAtEpochMs,
)

/**
 * The record keeps its own [ArchivedRecord.packageIdentity]; the manifest identity is only a
 * fallback for a caller that built records without one. Tier and tier reason are stored as
 * their stable wire names, never as enum ordinals.
 */
internal fun ArchivedRecord.toEntity(
    manifest: ArchivedManifest?,
): PortableArchiveRecordEntity = PortableArchiveRecordEntity(
    collection = collection,
    stableId = stableId,
    sourceScopeId = sourceScopeId,
    origin = origin,
    canonicalJson = canonicalJson,
    tier = tier.name,
    tierReason = tierReason.wire,
    synthetic = synthetic,
    sourceTag = sourceTag,
    packageIdentity = packageIdentity.ifEmpty { manifest?.identity.orEmpty() },
    ordinal = ordinal,
)

internal fun PortableArchiveRecordEntity.toDomain(): ArchivedRecord = ArchivedRecord(
    collection = collection,
    stableId = stableId,
    sourceScopeId = sourceScopeId,
    origin = origin,
    canonicalJson = canonicalJson,
    tier = decodeTier(tier),
    tierReason = decodeTierReason(tierReason),
    synthetic = synthetic,
    sourceTag = sourceTag,
    packageIdentity = packageIdentity,
    ordinal = ordinal,
)

/**
 * Unknown stored tier names must not crash the app: a row written by a newer build is still
 * readable, and STORED_ONLY is the conservative tier (kept, never claimed editable).
 */
internal fun decodeTier(raw: String): PortableTier =
    PortableTier.entries.firstOrNull { it.name == raw } ?: PortableTier.STORED_ONLY

/** Unknown tier reasons fall back to the phase-132 archive reason, the same one the codec emits. */
internal fun decodeTierReason(raw: String): PortableTierReason =
    PortableTierReason.entries.firstOrNull { it.wire == raw }
        ?: PortableTierReason.BOUNDED_ARCHIVE_NO_DOMAIN_UI
