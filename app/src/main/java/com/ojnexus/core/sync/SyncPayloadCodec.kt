package com.ojnexus.core.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Phase 119 — the deterministic wire form of one sealed sync batch (RFC 0001 §3/§4 storage
 * side). Encoding sorts rows by (table, rowKey) so the same op set always produces the same
 * bytes: a later transport phase can hash payloads without canonicalization worries. Decoding
 * never throws — garbage in, null out.
 */
@Serializable
data class SyncLogOpRow(
    val tableName: String,
    val rowKey: String,
    val kind: String,
    val payloadJson: String,
)

object SyncPayloadCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(ops: List<SyncLogOpRow>): String {
        val sorted = ops.sortedWith(compareBy({ it.tableName }, { it.rowKey }))
        return json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(SyncLogOpRow.serializer()),
            sorted,
        )
    }

    fun decode(raw: String): List<SyncLogOpRow>? = runCatching {
        json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(SyncLogOpRow.serializer()),
            raw,
        )
    }.getOrNull()
}
