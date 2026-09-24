package com.ojnexus.feature.problems

import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.NoteField
import com.ojnexus.core.model.ProblemKey
import com.ojnexus.core.model.ProblemNoteEntry
import com.ojnexus.core.model.ProblemNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure filtering for the note index: no Room, no Compose, no network. */
class ProblemNoteFilterTest {

    private fun entry(
        externalId: String,
        title: String,
        judge: JudgeId = JudgeId.CODEFORCES,
        solved: Boolean = false,
        inReview: Boolean = false,
        attemptCount: Int = 0,
        keyInsight: String = "",
        implementation: String = "",
        complexity: String = "",
        general: String = "",
        updatedAt: Long = 0,
    ) = ProblemNoteEntry(
        problemId = externalId.hashCode().toLong(),
        key = ProblemKey(judge = judge, externalId = externalId),
        title = title,
        difficulty = 1400,
        attemptCount = attemptCount,
        solved = solved,
        inReview = inReview,
        notes = ProblemNotes(
            problemId = externalId.hashCode().toLong(),
            keyInsight = keyInsight,
            implementationNotes = implementation,
            complexity = complexity,
            general = general,
            updatedAt = updatedAt,
        ),
    )

    private val entries = listOf(
        entry(
            externalId = "1A",
            title = "Theatre Square",
            keyInsight = "binary search on answer",
            complexity = "O(n log C)",
            updatedAt = 30,
        ),
        entry(
            externalId = "abc300_f",
            title = "More Holidays",
            judge = JudgeId.ATCODER,
            solved = true,
            inReview = true,
            attemptCount = 4,
            implementation = "prefix sums with wraparound",
            updatedAt = 20,
        ),
        entry(
            externalId = "P1001",
            title = "A+B Problem",
            judge = JudgeId.LUOGU,
            attemptCount = 1,
            general = "warm up",
            updatedAt = 10,
        ),
    )

    @Test
    fun `an empty filter keeps every indexed note in repository order`() {
        assertEquals(entries, entries.applyNoteFilter(ProblemNoteFilter()))
    }

    @Test
    fun `the default filter is the cleared state`() {
        assertTrue(ProblemNoteFilter().isDefault)
        assertFalse(ProblemNoteFilter(query = "x").isDefault)
        assertFalse(ProblemNoteFilter(field = NoteField.GENERAL).isDefault)
        assertFalse(ProblemNoteFilter(judge = JudgeId.LUOGU).isDefault)
        assertFalse(ProblemNoteFilter(unsolvedOnly = true).isDefault)
    }

    @Test
    fun `all-scope search matches note text in any field`() {
        assertEquals(
            listOf("abc300_f"),
            entries.applyNoteFilter(ProblemNoteFilter(query = "wraparound")).map { it.key.externalId },
        )
        assertEquals(
            listOf("P1001"),
            entries.applyNoteFilter(ProblemNoteFilter(query = "WARM UP")).map { it.key.externalId },
        )
    }

    @Test
    fun `search also matches the problem title and its external id`() {
        assertEquals(
            listOf("1A"),
            entries.applyNoteFilter(ProblemNoteFilter(query = "theatre")).map { it.key.externalId },
        )
        assertEquals(
            listOf("abc300_f"),
            entries.applyNoteFilter(ProblemNoteFilter(query = "ABC300")).map { it.key.externalId },
        )
    }

    @Test
    fun `a scoped field search never reaches into another field`() {
        // "binary" only exists in the key-insight field of 1A.
        assertEquals(
            listOf("1A"),
            entries.applyNoteFilter(
                ProblemNoteFilter(query = "binary", field = NoteField.KEY_INSIGHT),
            ).map { it.key.externalId },
        )
        assertTrue(
            entries.applyNoteFilter(
                ProblemNoteFilter(query = "binary", field = NoteField.COMPLEXITY),
            ).isEmpty(),
        )
    }

    @Test
    fun `judge and unsolved filters combine with the search term`() {
        assertEquals(
            listOf("abc300_f"),
            entries.applyNoteFilter(ProblemNoteFilter(judge = JudgeId.ATCODER)).map { it.key.externalId },
        )
        assertEquals(
            listOf("1A", "P1001"),
            entries.applyNoteFilter(ProblemNoteFilter(unsolvedOnly = true)).map { it.key.externalId },
        )
        assertEquals(
            listOf("P1001"),
            entries.applyNoteFilter(
                ProblemNoteFilter(judge = JudgeId.LUOGU, unsolvedOnly = true),
            ).map { it.key.externalId },
        )
        assertTrue(
            entries.applyNoteFilter(
                ProblemNoteFilter(judge = JudgeId.LUOGU, query = "binary"),
            ).isEmpty(),
        )
    }

    @Test
    fun `a whitespace-only term is treated as no search`() {
        assertEquals(entries, entries.applyNoteFilter(ProblemNoteFilter(query = "   ")))
    }

    @Test
    fun `the summary counts the indexed set and the visible subset separately`() {
        val visible = entries.applyNoteFilter(ProblemNoteFilter(unsolvedOnly = true))

        val summary = summarizeNoteIndex(entries, visible)

        assertEquals(3, summary.indexed)
        assertEquals(2, summary.visible)
        assertEquals(2, summary.unsolved)
        assertEquals(1, summary.reviewed)
    }
}
