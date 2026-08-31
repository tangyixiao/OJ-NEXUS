package com.ojnexus.judge.luogu

import com.ojnexus.core.database.entity.RemoteProblemEntity
import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.luogu.api.dto.LuoguProblemDto
import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
private data class LuoguProblemsetDumpDto(
    val pid: String = "",
    val type: String? = null,
    val difficulty: Int? = null,
    val tags: List<String> = emptyList(),
    val title: String = "",
)

/** Streams the official gzip NDJSON export without retaining the full catalog in memory. */
internal object LuoguProblemsetDumpParser {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    fun parse(input: InputStream, updatedAt: Long): Sequence<RemoteProblemEntity> = sequence {
        try {
            GZIPInputStream(input).bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    val dto = try {
                        json.decodeFromString<LuoguProblemsetDumpDto>(line)
                    } catch (error: SerializationException) {
                        throw LuoguApiError.ParseError(error)
                    } catch (error: IllegalArgumentException) {
                        throw LuoguApiError.ParseError(error)
                    }
                    if (dto.pid.isBlank() || dto.title.isBlank()) {
                        throw LuoguApiError.ParseError(
                            IllegalStateException("Luogu problemset row has no pid or title"),
                        )
                    }
                    yield(
                        LuoguMappers.toRemoteProblemEntity(
                            LuoguProblemDto(
                                pid = dto.pid,
                                type = dto.type,
                                name = dto.title,
                                difficulty = dto.difficulty,
                                tags = dto.tags,
                            ),
                            JudgeId.LUOGU,
                            updatedAt,
                        ),
                    )
                }
            }
        } catch (error: LuoguApiError) {
            throw error
        } catch (error: Exception) {
            throw LuoguApiError.ParseError(error)
        }
    }
}
