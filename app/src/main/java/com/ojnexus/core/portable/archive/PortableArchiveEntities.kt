package com.ojnexus.core.portable.archive

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Phase 132 — bounded domain-archive persistence (schema v16), additive tables only.
 *
 * Phase 132 has no live domain UI for the five portable collections, so every accepted
 * record is kept here with its provenance, capability tier and the canonical JSON of the
 * single record. That is enough to rebuild the exact package bytes on export (see
 * `PortableCodec.rebuild`) instead of replaying a cached blob.
 *
 * Nothing in these tables is a credential: the v1 contract rejects credential-shaped keys
 * before a package reaches the archive, and there is no reference to `judge_accounts`.
 */

/**
 * The single manifest row of an imported package, keyed by the package identity
 * (SHA-256 hex of the canonical bytes). Replacing an archive deletes this row first, so
 * the table never holds more than one row.
 */
@Entity(tableName = "portable_archive_packages")
data class PortableArchivePackageEntity(
    @PrimaryKey @ColumnInfo(name = "identity") val identity: String,
    @ColumnInfo(name = "format_version") val formatVersion: Int,
    @ColumnInfo(name = "scope_id") val scopeId: String,
    @ColumnInfo(name = "source_platform") val sourcePlatform: String,
    /** Canonical JSON of the package `generator` object. */
    @ColumnInfo(name = "generator_json") val generatorJson: String,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
    /** The exact canonical package bytes, kept as proof of lossless storage. */
    @ColumnInfo(name = "canonical_bytes") val canonicalBytes: ByteArray,
    @ColumnInfo(name = "source_tag") val sourceTag: String,
    @ColumnInfo(name = "imported_at_epoch_ms") val importedAtEpochMs: Long,
) {
    // ByteArray needs structural equality so tests and conflict checks compare content.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PortableArchivePackageEntity) return false
        return identity == other.identity &&
            formatVersion == other.formatVersion &&
            scopeId == other.scopeId &&
            sourcePlatform == other.sourcePlatform &&
            generatorJson == other.generatorJson &&
            createdAtEpochMs == other.createdAtEpochMs &&
            canonicalBytes.contentEquals(other.canonicalBytes) &&
            sourceTag == other.sourceTag &&
            importedAtEpochMs == other.importedAtEpochMs
    }

    override fun hashCode(): Int {
        var result = identity.hashCode()
        result = 31 * result + formatVersion
        result = 31 * result + scopeId.hashCode()
        result = 31 * result + sourcePlatform.hashCode()
        result = 31 * result + generatorJson.hashCode()
        result = 31 * result + createdAtEpochMs.hashCode()
        result = 31 * result + canonicalBytes.contentHashCode()
        result = 31 * result + sourceTag.hashCode()
        result = 31 * result + importedAtEpochMs.hashCode()
        return result
    }
}

/**
 * One accepted record, primary key `(collection, stableId)` so a re-import of the same
 * package identity upserts instead of duplicating. `ordinal` preserves the collection
 * array order the canonical writer must reproduce.
 */
@Entity(
    tableName = "portable_archive_records",
    primaryKeys = ["collection", "stable_id"],
    indices = [Index("package_identity")],
)
data class PortableArchiveRecordEntity(
    @ColumnInfo(name = "collection") val collection: String,
    @ColumnInfo(name = "stable_id") val stableId: String,
    @ColumnInfo(name = "source_scope_id") val sourceScopeId: String,
    @ColumnInfo(name = "origin") val origin: String,
    /** Canonical JSON of this single record, preserving every field. */
    @ColumnInfo(name = "canonical_json") val canonicalJson: String,
    /** `PortableTier` name: EDITABLE / STORED_ONLY / REJECTED. */
    @ColumnInfo(name = "tier") val tier: String,
    /** `PortableTierReason.wire`: live_domain_ui / bounded_archive_no_domain_ui / ... */
    @ColumnInfo(name = "tier_reason") val tierReason: String,
    @ColumnInfo(name = "synthetic") val synthetic: Boolean,
    @ColumnInfo(name = "source_tag") val sourceTag: String,
    @ColumnInfo(name = "package_identity") val packageIdentity: String,
    @ColumnInfo(name = "ordinal") val ordinal: Int,
)
