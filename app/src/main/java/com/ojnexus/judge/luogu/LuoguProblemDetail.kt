package com.ojnexus.judge.luogu

import com.ojnexus.core.database.entity.RemoteProblemDetailEntity
import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.luogu.api.dto.LuoguProblemContentDto
import com.ojnexus.judge.luogu.api.dto.LuoguProblemDetailData
import com.ojnexus.judge.luogu.api.dto.LuoguProblemDetailDto
import kotlinx.serialization.json.Json

data class LuoguProblemDetail(
    val pid: String,
    val title: String,
    val difficulty: Int?,
    val tags: List<Int>,
    val totalSubmit: Int?,
    val totalAccepted: Int?,
    val background: String,
    val description: String,
    val inputFormat: String,
    val outputFormat: String,
    val hint: String,
    val samples: List<String>,
    val timeLimitMs: Int?,
    val memoryLimitMb: Int?,
)

object LuoguProblemDetailMapper {
    private val cacheJson = Json { ignoreUnknownKeys = true }

    fun toDomain(data: LuoguProblemDetailData): LuoguProblemDetail {
        val problem = requireNotNull(data.problem) { "Luogu problem payload is missing" }
        val content = problem.contenu ?: problem.content ?: LuoguProblemContentDto()
        return LuoguProblemDetail(
            pid = problem.pid,
            title = problem.name.ifBlank { content.name.orEmpty() },
            difficulty = problem.difficulty,
            tags = problem.tags,
            totalSubmit = problem.totalSubmit,
            totalAccepted = problem.totalAccepted,
            background = content.background.orEmpty(),
            description = content.description.orEmpty(),
            inputFormat = content.formatI.orEmpty(),
            outputFormat = content.formatO.orEmpty(),
            hint = content.hint.orEmpty(),
            samples = problem.samples,
            timeLimitMs = problem.limits?.time?.firstOrNull(),
            memoryLimitMb = problem.limits?.memory?.firstOrNull()?.div(1024),
        )
    }

    fun toCache(detail: LuoguProblemDetail, updatedAt: Long): RemoteProblemDetailEntity =
        RemoteProblemDetailEntity(
            judge = JudgeId.LUOGU.id,
            externalId = detail.pid,
            title = detail.title,
            difficulty = detail.difficulty,
            tagsJson = cacheJson.encodeToString(detail.tags),
            totalSubmit = detail.totalSubmit,
            totalAccepted = detail.totalAccepted,
            background = detail.background,
            description = detail.description,
            inputFormat = detail.inputFormat,
            outputFormat = detail.outputFormat,
            hint = detail.hint,
            samplesJson = cacheJson.encodeToString(detail.samples),
            timeLimitMs = detail.timeLimitMs,
            memoryLimitMb = detail.memoryLimitMb,
            updatedAt = updatedAt,
        )

    fun fromCache(entity: RemoteProblemDetailEntity): LuoguProblemDetail =
        LuoguProblemDetail(
            pid = entity.externalId,
            title = entity.title,
            difficulty = entity.difficulty,
            tags = cacheJson.decodeFromString(entity.tagsJson),
            totalSubmit = entity.totalSubmit,
            totalAccepted = entity.totalAccepted,
            background = entity.background,
            description = entity.description,
            inputFormat = entity.inputFormat,
            outputFormat = entity.outputFormat,
            hint = entity.hint,
            samples = cacheJson.decodeFromString(entity.samplesJson),
            timeLimitMs = entity.timeLimitMs,
            memoryLimitMb = entity.memoryLimitMb,
        )
}

class LuoguProblemDetailRepository(private val client: LuoguClient) {
    suspend fun fetch(pid: String): LuoguProblemDetail =
        LuoguProblemDetailMapper.toDomain(client.fetchProblem(pid).data ?: error("Luogu problem data is missing"))
}

sealed interface LuoguMarkdownBlock {
    data class Heading(val level: Int, val text: String) : LuoguMarkdownBlock
    data class Paragraph(val text: String) : LuoguMarkdownBlock
    data class Bullet(val text: String) : LuoguMarkdownBlock
    data class Code(val language: String, val text: String) : LuoguMarkdownBlock
    data class Quote(val text: String) : LuoguMarkdownBlock
    data object Divider : LuoguMarkdownBlock
}

/** Small, deterministic native renderer input. It intentionally ignores HTML and image URLs. */
object LuoguMarkdownParser {
    fun parse(markdown: String): List<LuoguMarkdownBlock> {
        val result = mutableListOf<LuoguMarkdownBlock>()
        val paragraph = mutableListOf<String>()
        val code = mutableListOf<String>()
        var codeLanguage = ""
        var inCode = false

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                result += LuoguMarkdownBlock.Paragraph(cleanInline(paragraph.joinToString(" ")))
                paragraph.clear()
            }
        }

        markdown.replace("\r\n", "\n").split('\n').forEach { rawLine ->
            val line = rawLine.trimEnd()
            if (line.trimStart().startsWith("```") && !inCode) {
                flushParagraph()
                inCode = true
                codeLanguage = line.trim().removePrefix("```").trim()
                return@forEach
            }
            if (line.trim() == "```" && inCode) {
                result += LuoguMarkdownBlock.Code(codeLanguage, code.joinToString("\n").trimEnd())
                code.clear()
                codeLanguage = ""
                inCode = false
                return@forEach
            }
            if (inCode) {
                code += rawLine
                return@forEach
            }
            val trimmed = line.trim()
            when {
                trimmed.isBlank() -> flushParagraph()
                trimmed.matches(Regex("#{1,6}\\s+.*")) -> {
                    flushParagraph()
                    val match = Regex("^(#{1,6})\\s+(.*)$").matchEntire(trimmed)!!
                    result += LuoguMarkdownBlock.Heading(match.groupValues[1].length, cleanInline(match.groupValues[2]))
                }
                trimmed.matches(Regex("(?:[-*+]\\s+|\\d+[.)]\\s+).+")) -> {
                    flushParagraph()
                    result += LuoguMarkdownBlock.Bullet(cleanInline(trimmed.replaceFirst(Regex("^(?:[-*+]|\\d+[.)])\\s+"), "")))
                }
                trimmed.startsWith(">") -> {
                    flushParagraph()
                    result += LuoguMarkdownBlock.Quote(cleanInline(trimmed.removePrefix(">").trim()))
                }
                trimmed.matches(Regex("[-*_]{3,}")) -> {
                    flushParagraph()
                    result += LuoguMarkdownBlock.Divider
                }
                else -> paragraph += trimmed
            }
        }
        if (inCode) result += LuoguMarkdownBlock.Code(codeLanguage, code.joinToString("\n").trimEnd())
        flushParagraph()
        return result
    }

    private fun cleanInline(value: String): String = value
        .replace(Regex("!\\[([^]]*)\\]\\([^)]*\\)"), "$1")
        .replace(Regex("\\[([^]]+)\\]\\([^)]*\\)"), "$1")
        .replace(Regex("<[^>]*>"), "")
        .replace(Regex("[*_]{1,3}"), "")
        .replace(Regex("`([^`]*)`"), "$1")
        .trim()
}
