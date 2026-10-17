package com.ojnexus.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic alias matching. The table is the contract: an exact normalized alias links its
 * area, everything else links nothing.
 */
class KnowledgeAutoLinkTest {

    @Test
    fun `exact alias links its area`() {
        assertEquals(
            listOf(KnowledgeArea.DYNAMIC_PROGRAMMING),
            autoLinkedKnowledgeAreas(listOf("dp")),
        )
        assertEquals(
            listOf(KnowledgeArea.GRAPH),
            autoLinkedKnowledgeAreas(listOf("graph")),
        )
    }

    @Test
    fun `matching is case and whitespace insensitive`() {
        assertEquals(
            listOf(KnowledgeArea.GRAPH),
            autoLinkedKnowledgeAreas(listOf("  Graph   Theory  ")),
        )
        assertEquals(
            listOf(KnowledgeArea.SEARCH),
            autoLinkedKnowledgeAreas(listOf("BINARY SEARCH")),
        )
    }

    @Test
    fun `chinese aliases link their area`() {
        assertEquals(
            listOf(KnowledgeArea.DYNAMIC_PROGRAMMING),
            autoLinkedKnowledgeAreas(listOf("动态规划")),
        )
        assertEquals(
            listOf(KnowledgeArea.DATA_STRUCTURE),
            autoLinkedKnowledgeAreas(listOf("数据结构")),
        )
    }

    @Test
    fun `each area appears once when several aliases match`() {
        assertEquals(
            listOf(KnowledgeArea.GRAPH),
            autoLinkedKnowledgeAreas(listOf("graph", "graphs", "graph theory")),
        )
    }

    @Test
    fun `result follows enum declaration order not input order`() {
        assertEquals(
            listOf(KnowledgeArea.GRAPH, KnowledgeArea.DYNAMIC_PROGRAMMING, KnowledgeArea.GREEDY),
            autoLinkedKnowledgeAreas(listOf("graphs", "dp", "贪心")),
        )
    }

    @Test
    fun `unknown tags link nothing`() {
        assertTrue(autoLinkedKnowledgeAreas(listOf("flow", "trees", "srt")).isEmpty())
        assertTrue(autoLinkedKnowledgeAreas(emptyList()).isEmpty())
    }

    @Test
    fun `blank and whitespace-only tags are ignored`() {
        assertTrue(autoLinkedKnowledgeAreas(listOf("   ", "")).isEmpty())
    }

    @Test
    fun `alias table never maps one alias to two areas`() {
        val seen = mutableMapOf<String, KnowledgeArea>()
        KNOWLEDGE_ALIASES.forEach { (area, aliases) ->
            aliases.forEach { alias ->
                val previous = seen.put(normalizeKnowledgeAlias(alias), area)
                if (previous != null) {
                    throw AssertionError("alias '$alias' maps to both $previous and $area")
                }
            }
        }
        assertEquals(KnowledgeArea.entries.size, KNOWLEDGE_ALIASES.size)
    }

    @Test
    fun `every area owns at least one alias`() {
        assertTrue(KNOWLEDGE_ALIASES.keys.containsAll(KnowledgeArea.entries))
    }

    @Test
    fun `pending links drop areas already linked`() {
        val tags = listOf("dp", "graph", "贪心")
        assertEquals(
            listOf(KnowledgeArea.GRAPH, KnowledgeArea.DYNAMIC_PROGRAMMING, KnowledgeArea.GREEDY),
            autoLinkedKnowledgeAreas(tags),
        )
        assertEquals(
            listOf(KnowledgeArea.GRAPH, KnowledgeArea.GREEDY),
            pendingAutoLinks(tags, setOf(KnowledgeArea.DYNAMIC_PROGRAMMING)),
        )
        assertTrue(pendingAutoLinks(tags, autoLinkedKnowledgeAreas(tags).toSet()).isEmpty())
        assertFalse(pendingAutoLinks(tags, emptySet()).isEmpty())
    }

    @Test
    fun `pending links are a stable function of the same input`() {
        val tags = listOf("DP", " binary search ")
        val first = pendingAutoLinks(tags, emptySet())
        val second = pendingAutoLinks(tags, emptySet())
        assertEquals(first, second)
    }
}
