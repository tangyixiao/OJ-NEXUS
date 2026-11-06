package com.ojnexus.core.portable

/**
 * Frozen bounded domain-archive contract for phase 132.
 *
 * The archive stores accepted portable records with their provenance and
 * capability tier so a later phase can project them into domain tables. It is
 * deliberately an interface: the Room implementation (task 2) and the fake used
 * by unit tests (tasks 3 and 5) share this one contract.
 *
 * Controller-owned, frozen. No Room/Android imports here.
 */

/** One accepted record, with saved state for stored-only items. */
data class ArchivedRecord(
    val collection: String,
    val stableId: String,
    val sourceScopeId: String,
    val origin: String,
    /** Canonical JSON bytes of this single record, preserving every field. */
    val canonicalJson: String,
    val tier: PortableTier,
    val tierReason: PortableTierReason,
    val synthetic: Boolean,
    val sourceTag: String,
    val packageIdentity: String,
    /** Position inside its collection, so rebuild reproduces array order exactly. */
    val ordinal: Int,
)

/** Manifest metadata needed to rebuild the exact package bytes on export. */
data class ArchivedManifest(
    val identity: String,
    val formatVersion: Int,
    val scopeId: String,
    val sourcePlatform: String,
    /** Canonical JSON of the generator object. */
    val generatorJson: String,
    val createdAtEpochMs: Long,
    /** The exact canonical package bytes, kept as proof of lossless storage. */
    val canonicalBytes: ByteArray,
    val sourceTag: String,
    val importedAtEpochMs: Long,
)

/** Everything the archive needs to reconstruct a package. */
data class PortableArchive(
    val manifest: ArchivedManifest?,
    val records: List<ArchivedRecord>,
)

/** Result counts of a transactional replaceAll. */
data class ApplyStats(
    val rowsUpserted: Int,
    val rowsDeleted: Int,
)

/**
 * The bounded archive. All reads are pure; the only write is [replaceAll],
 * which MUST execute in one transaction (all-or-nothing).
 */
interface PortableArchiveStore {
    /** Load manifest + records. Pure read; never mutates. */
    suspend fun load(): PortableArchive

    /** Number of archived records. Used by tests to prove preview/cancel are read-only. */
    suspend fun recordCount(): Int

    /** Stored identity, or null when the archive is empty. */
    suspend fun currentIdentity(): String?

    /** Atomically replace the whole archive. Returns counts of the operation. */
    suspend fun replaceAll(archive: PortableArchive): ApplyStats

    /** Wipe the archive (used by tests / explicit reset, never by preview/cancel). */
    suspend fun clear()
}
