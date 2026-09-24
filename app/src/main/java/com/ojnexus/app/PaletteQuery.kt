package com.ojnexus.app

import com.ojnexus.core.model.JudgeId

/** A deterministic action parsed from the command palette's free-form query. */
sealed interface PaletteQuery {
    data class SearchProblems(
        val judge: JudgeId?,
        val query: String,
    ) : PaletteQuery
}

/**
 * Parses the small, local command grammar exposed by the palette.
 *
 * The first token selects an optional judge; the remainder is intentionally kept as one query so
 * titles and contest problem identifiers containing spaces remain searchable.
 */
fun parsePaletteQuery(raw: String): PaletteQuery? {
    val parts = raw.trim().split(Regex("\\s+"), limit = 2)
    if (parts.size != 2) return null
    val prefix = parts[0].lowercase()
    val query = parts[1]
        .trim()
        .split(Regex("\\s+"))
        .joinToString(" ")
        .lowercase()
        .takeIf { it.isNotEmpty() }
        ?: return null
    val judge = when (prefix) {
        "cf", "codeforces" -> JudgeId.CODEFORCES
        "ac", "atcoder" -> JudgeId.ATCODER
        "lg", "luogu" -> JudgeId.LUOGU
        "search" -> null
        else -> return null
    }
    return PaletteQuery.SearchProblems(judge = judge, query = query)
}
