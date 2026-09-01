package com.ojnexus.judge.luogu.api.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray

@Serializable
data class LuoguProblemDetailResponse(
    val status: Int = 200,
    val template: String? = null,
    val instance: String? = null,
    val data: LuoguProblemDetailData? = null,
)

@Serializable
data class LuoguProblemDetailData(
    val problem: LuoguProblemDetailDto? = null,
)

/** Public problem payload returned by Luogu's `problem.show` content-only page. */
@Serializable
data class LuoguProblemDetailDto(
    val pid: String = "",
    val type: String? = null,
    val name: String = "",
    val difficulty: Int? = null,
    val tags: List<Int> = emptyList(),
    val totalSubmit: Int? = null,
    val totalAccepted: Int? = null,
    val contenu: LuoguProblemContentDto? = null,
    val content: LuoguProblemContentDto? = null,
    @Serializable(with = LuoguProblemSamplesSerializer::class)
    val samples: List<String> = emptyList(),
    val limits: LuoguProblemLimitsDto? = null,
)

@Serializable
data class LuoguProblemContentDto(
    val name: String? = null,
    val background: String? = null,
    val description: String? = null,
    val formatI: String? = null,
    val formatO: String? = null,
    val hint: String? = null,
    val locale: String? = null,
)

@Serializable
data class LuoguProblemLimitsDto(
    val time: List<Int> = emptyList(),
    val memory: List<Int> = emptyList(),
)

/**
 * Luogu has returned both flat strings and nested [input, output] sample pairs over time.
 * Keep the app's existing alternating string contract while accepting both wire shapes.
 */
object LuoguProblemSamplesSerializer : KSerializer<List<String>> {
    override val descriptor: SerialDescriptor = ListSerializer(String.serializer()).descriptor

    override fun deserialize(decoder: Decoder): List<String> {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("Luogu samples require a JSON decoder")
        return flatten(jsonDecoder.decodeJsonElement())
    }

    override fun serialize(encoder: Encoder, value: List<String>) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("Luogu samples require a JSON encoder")
        jsonEncoder.encodeJsonElement(buildJsonArray { value.forEach { add(JsonPrimitive(it)) } })
    }

    private fun flatten(element: JsonElement): List<String> = when (element) {
        is JsonPrimitive -> element.asSampleString()
        is JsonArray -> element.flatMap { item ->
            when (item) {
                is JsonPrimitive -> item.asSampleString()
                is JsonArray -> item.flatMap { nested -> nested.asSampleElementString() }
                else -> throw SerializationException("Luogu sample entry is not a string")
            }
        }
        else -> throw SerializationException("Luogu samples are not an array")
    }

    private fun JsonElement.asSampleElementString(): List<String> =
        (this as? JsonPrimitive)?.asSampleString()
            ?: throw SerializationException("Luogu sample value is not a string")

    private fun JsonPrimitive.asSampleString(): List<String> =
        if (isString) listOf(content) else throw SerializationException("Luogu sample value is not a string")
}
