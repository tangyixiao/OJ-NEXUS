package com.ojnexus.feature.training

import com.ojnexus.core.model.SessionProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionCommandDeckTest {

    @Test
    fun nullSelectionStaysEmpty() {
        assertNull(normalizeSessionSelection(null, listOf(sessionProblem(1L))))
    }

    @Test
    fun existingSelectionIsRetained() {
        assertEquals(3L, normalizeSessionSelection(3L, listOf(sessionProblem(3L))))
    }

    @Test
    fun removedSelectionIsCleared() {
        assertNull(normalizeSessionSelection(9L, listOf(sessionProblem(2L))))
    }

    private fun sessionProblem(id: Long) = SessionProblem(
        problemId = id,
        title = "Problem $id",
        difficulty = null,
        solved = false,
        attempts = 0,
    )
}
