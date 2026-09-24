package com.ojnexus.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The note index only ever renders text the user actually saved, so the blank rules are the
 * contract that keeps an emptied notes row out of the list.
 */
class ProblemNoteIndexTest {

    private fun notes(
        keyInsight: String = "",
        implementation: String = "",
        complexity: String = "",
        general: String = "",
    ) = ProblemNotes(
        problemId = 1L,
        keyInsight = keyInsight,
        implementationNotes = implementation,
        complexity = complexity,
        general = general,
        updatedAt = 0L,
    )

    @Test
    fun `notes with no text anywhere have no content`() {
        assertFalse(notes().hasContent())
        assertFalse(notes(keyInsight = "   ", implementation = "\t", general = " ").hasContent())
    }

    @Test
    fun `a single filled field is enough to index the problem`() {
        assertTrue(notes(complexity = "O(n log n)").hasContent())
        assertTrue(notes(general = "watch the empty input").hasContent())
    }

    @Test
    fun `all-scope preview resolves to the first non-empty field in reading order`() {
        val entry = notes(keyInsight = "  ", implementation = "two pointers", complexity = "O(n)")

        assertEquals("two pointers", entry.textFor(NoteField.ALL))
    }

    @Test
    fun `a field-scoped preview never falls back to another field`() {
        val entry = notes(keyInsight = "binary search on answer", complexity = "O(n log C)")

        assertEquals("binary search on answer", entry.textFor(NoteField.KEY_INSIGHT))
        assertEquals("O(n log C)", entry.textFor(NoteField.COMPLEXITY))
        assertNull(entry.textFor(NoteField.GENERAL))
    }

    @Test
    fun `a blank field degrades to null instead of an empty string`() {
        assertNull(notes(keyInsight = "only insight").textFor(NoteField.IMPLEMENTATION))
        assertNull(notes().textFor(NoteField.ALL))
    }

    @Test
    fun `search scope covers every field only in all-scope`() {
        val entry = notes(
            keyInsight = "insight",
            implementation = "implementation",
            complexity = "complexity",
            general = "general",
        )

        assertEquals(
            listOf("insight", "implementation", "complexity", "general"),
            entry.searchScope(NoteField.ALL),
        )
        assertEquals(listOf("complexity"), entry.searchScope(NoteField.COMPLEXITY))
        assertEquals(emptyList<String>(), notes(keyInsight = "x").searchScope(NoteField.GENERAL))
    }
}
