package com.ojnexus.judge.luogu

import com.ojnexus.judge.luogu.api.dto.LuoguTrainingDetailResponse
import com.ojnexus.judge.luogu.api.dto.LuoguTrainingListResponse
import kotlinx.serialization.json.Json

/**
 * Extracts and decodes the `lentille-context` JSON embedded in Luogu's public training pages.
 * Never throws: any structural mismatch (missing marker, bad JSON, wrong template) yields null.
 */
object LuoguTrainingCodec {
    private const val MARKER = "lentille-context"

    /** Lenient like SnapshotManifestCodec — unknown keys are tolerated, garbage is not fatal. */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun extract(raw: String): String? {
        val markerIndex = raw.indexOf(MARKER)
        if (markerIndex < 0) return null
        val open = raw.indexOf('>', markerIndex)
        if (open < 0) return null
        val start = open + 1
        val end = raw.indexOf("</script>", start)
        if (end < 0) return null
        return raw.substring(start, end).trim().takeIf { it.isNotEmpty() }
    }

    fun decodeList(raw: String): LuoguTrainingListResponse? = runCatching {
        json.decodeFromString(LuoguTrainingListResponse.serializer(), raw)
    }.getOrNull()

    fun decodeDetail(raw: String): LuoguTrainingDetailResponse? = runCatching {
        json.decodeFromString(LuoguTrainingDetailResponse.serializer(), raw)
    }.getOrNull()

    /** Full pipeline: HTML → extracted JSON → decoded list payload; template must match. */
    fun parseList(html: String): LuoguTrainingListResponse? =
        extract(html)?.let(::decodeList)?.takeIf { it.template == "training.list" }

    /** Full pipeline: HTML → extracted JSON → decoded detail payload; template must match. */
    fun parseDetail(html: String): LuoguTrainingDetailResponse? =
        extract(html)?.let(::decodeDetail)?.takeIf { it.template == "training.show" }
}
