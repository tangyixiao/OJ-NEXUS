package com.ojnexus.core.data.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Row count of one table inside a snapshot, keyed by table name. */
@Serializable
data class ManifestTableCount(
    @SerialName("table") val table: String,
    @SerialName("rows") val rows: Int,
)

/**
 * Metadata written next to the database inside a snapshot archive. The manifest is provenance:
 * it records what was exported, never what must be restored to.
 */
@Serializable
data class SnapshotManifest(
    @SerialName("schema_version") val schemaVersion: Int,
    /** Phase 73 data-generation stamp of the exporting device. */
    @SerialName("data_generation") val dataGeneration: String,
    @SerialName("created_at_epoch_ms") val createdAtEpochMs: Long,
    /** One row count per required table; sorted by table name on encode. */
    @SerialName("table_counts") val tableCounts: List<ManifestTableCount>,
    @SerialName("app_version_name") val appVersionName: String? = null,
) {
    fun rowsFor(table: String): Int? = tableCounts.firstOrNull { it.table == table }?.rows
}

/**
 * Deterministic JSON codec for [SnapshotManifest]. Decode returns null on any malformed input —
 * it never throws, mirroring the NoteTemplateCodec philosophy.
 */
object SnapshotManifestCodec {
    const val MANIFEST_ENTRY = "manifest.json"
    const val DATABASE_ENTRY = "database.db"

    private val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    fun encode(manifest: SnapshotManifest): String = json.encodeToString(
        SnapshotManifest.serializer(),
        manifest.copy(
            tableCounts = manifest.tableCounts.sortedBy { it.table },
        ),
    )

    fun decode(raw: String): SnapshotManifest? = runCatching {
        json.decodeFromString(SnapshotManifest.serializer(), raw)
    }.getOrNull()
}
