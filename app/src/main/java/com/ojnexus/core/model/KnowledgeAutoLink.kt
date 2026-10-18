package com.ojnexus.core.model

/**
 * Knowledge-area auto-linking.
 *
 * A problem's local tags are compared against a fixed alias table in code. The match is
 * deterministic and exact: a tag equals an alias after normalization, or it does not
 * match at all. Nothing here infers, scores, or ranks — an unknown tag simply links
 * nothing, and the user can still link every area by hand.
 */

/** Normalizes a tag or alias for comparison: trimmed, lower-cased, inner spaces collapsed. */
fun normalizeKnowledgeAlias(value: String): String =
    value.trim()
        .lowercase()
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ")

/** Alias table: exact, deterministic, code-owned. Judge tag spellings live here. */
val KNOWLEDGE_ALIASES: Map<KnowledgeArea, Set<String>> = mapOf(
    KnowledgeArea.DATA_STRUCTURE to setOf(
        "data structure",
        "data structures",
        "数据结构",
        "ds",
    ),
    KnowledgeArea.GRAPH to setOf("graph", "graphs", "graph theory", "图论", "图"),
    KnowledgeArea.DYNAMIC_PROGRAMMING to setOf(
        "dynamic programming",
        "dp",
        "动态规划",
    ),
    KnowledgeArea.STRING to setOf("string", "strings", "string matching", "字符串", "串"),
    KnowledgeArea.MATH to setOf(
        "math",
        "mathematics",
        "maths",
        "number theory",
        "数学",
        "数论",
    ),
    KnowledgeArea.GEOMETRY to setOf(
        "geometry",
        "computational geometry",
        "几何",
        "计算几何",
    ),
    KnowledgeArea.GREEDY to setOf("greedy", "greedy algorithms", "贪心", "贪心算法"),
    KnowledgeArea.SEARCH to setOf(
        "search",
        "searching",
        "binary search",
        "二分",
        "二分查找",
        "搜索",
    ),
    KnowledgeArea.CONSTRUCTION to setOf("construction", "constructive algorithms", "构造"),
    KnowledgeArea.GAME_THEORY to setOf("game theory", "games", "博弈", "博弈论", "sg 函数"),
)

/**
 * Knowledge areas a set of problem tags auto-links to, in [KnowledgeArea] declaration order.
 *
 * Every area is returned at most once even when several of its aliases match, and an alias is
 * never shared by two areas: the table is built so each alias belongs to exactly one area, so
 * the result is a stable function of the input.
 */
fun autoLinkedKnowledgeAreas(tags: Collection<String>): List<KnowledgeArea> {
    val normalized = tags.map { normalizeKnowledgeAlias(it) }.filter { it.isNotBlank() }
    if (normalized.isEmpty()) return emptyList()
    val aliasOwner = HashMap<String, KnowledgeArea>(normalized.size * 4)
    KNOWLEDGE_ALIASES.forEach { (area, aliases) ->
        aliases.forEach { alias -> aliasOwner.putIfAbsent(normalizeKnowledgeAlias(alias), area) }
    }
    return KnowledgeArea.entries.filter { area ->
        normalized.any { aliasOwner[it] == area }
    }
}

/**
 * Knowledge areas that stay auto-linked: the matched ones minus the ones the user
 * already linked, so the UI never shows a tag for a relation that already exists.
 */
fun pendingAutoLinks(tags: Collection<String>, linked: Set<KnowledgeArea>): List<KnowledgeArea> =
    autoLinkedKnowledgeAreas(tags).filterNot { it in linked }
