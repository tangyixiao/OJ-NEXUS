package com.ojnexus.app

import com.ojnexus.core.model.JudgeId

/** A deterministic action parsed from the command palette's free-form query. */
sealed interface PaletteQuery {
    data class SearchProblems(
        val judge: JudgeId?,
        val query: String,
    ) : PaletteQuery

    /** `review due` — jump straight into the scheduled review run. */
    data object ReviewDue : PaletteQuery

    /** `sprint 1800` — open training with the sprint band preselected around the target. */
    data class SprintPlan(val target: Int) : PaletteQuery

    /** `@cf unsolved 1600-1900` — open the library filtered to unsolved problems in a range. */
    data class UnsolvedRange(
        val judge: JudgeId?,
        val min: Int?,
        val max: Int?,
    ) : PaletteQuery
}

/** Judge aliases accepted as the first token (optionally `@`-prefixed). */
private fun judgeAlias(token: String): JudgeId? = when (token) {
    "cf", "codeforces" -> JudgeId.CODEFORCES
    "ac", "atcoder" -> JudgeId.ATCODER
    "lg", "luogu" -> JudgeId.LUOGU
    else -> null
}

private val EXACT_DIFFICULTY = Regex("^\\d+$")
private val DIFFICULTY_RANGE = Regex("^(\\d{0,4})-(\\d{0,4})$")

/**
 * Parses one difficulty token: `1600` (exact), `1600-1900` (closed),
 * `1600-` (open top), `-1900` (open bottom). Returns null for anything else.
 */
internal fun parseDifficultyRange(token: String): Pair<Int?, Int?>? =
    when {
        token.matches(EXACT_DIFFICULTY) -> token.toInt().let { it to it }
        else -> DIFFICULTY_RANGE.matchEntire(token)?.let { match ->
            val min = match.groupValues[1].takeIf { it.isNotEmpty() }?.toInt()
            val max = match.groupValues[2].takeIf { it.isNotEmpty() }?.toInt()
            if (min == null && max == null) null else min to max
        }
    }

/** `unsolved 1600-1900` payload after the judge (if any) was consumed. */
private fun unsolvedPayload(judge: JudgeId?, payload: String): PaletteQuery.UnsolvedRange? =
    when {
        payload.isEmpty() -> PaletteQuery.UnsolvedRange(judge, min = null, max = null)
        payload.contains(' ') -> null
        else -> parseDifficultyRange(payload)?.let { (min, max) ->
            PaletteQuery.UnsolvedRange(judge, min = min, max = max)
        }
    }

/**
 * Parses the small, local command grammar exposed by the palette.
 *
 * The first token selects an optional judge or a fixed command; the remainder is intentionally
 * kept as one query so titles and contest problem identifiers containing spaces remain
 * searchable. Text parsing only — no inference, no network.
 */
fun parsePaletteQuery(raw: String): PaletteQuery? {
    val parts = raw.trim().split(Regex("\\s+"), limit = 2)
    if (parts.size != 2) return null
    val prefixToken = parts[0].lowercase().removePrefix("@")
    val query = parts[1]
        .trim()
        .split(Regex("\\s+"))
        .joinToString(" ")
        .lowercase()
        .takeIf { it.isNotEmpty() }
        ?: return null

    judgeAlias(prefixToken)?.let { judge ->
        if (query == "unsolved") {
            return PaletteQuery.UnsolvedRange(judge, min = null, max = null)
        }
        if (query.startsWith("unsolved ")) {
            return unsolvedPayload(judge, query.removePrefix("unsolved ").trim())
        }
        return PaletteQuery.SearchProblems(judge = judge, query = query)
    }
    return when (prefixToken) {
        "search" -> PaletteQuery.SearchProblems(judge = null, query = query)
        "review" -> if (query == "due") PaletteQuery.ReviewDue else null
        "sprint" -> query.toIntOrNull()
            ?.takeIf { it.matchesDifficulty() }
            ?.let(PaletteQuery::SprintPlan)
        "unsolved" -> unsolvedPayload(judge = null, payload = query)
        else -> null
    }
}

private fun Int.matchesDifficulty(): Boolean = this in 100..3500
