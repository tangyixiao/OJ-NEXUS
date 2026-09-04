package com.ojnexus.feature.training

import com.ojnexus.core.model.SessionProblem
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun viewModelWiresQuickResultToProblemRepository() {
        val source = Files.readString(Path.of("src/main/java/com/ojnexus/feature/training/SessionViewModel.kt"))

        assertTrue(source.contains("fun logAttempt(problemId: Long, verdict: Verdict)"))
        assertTrue(source.contains("problemRepository.addAttempt(problemId, verdict)"))
    }

    @Test
    fun runningSessionWiresSelectionToQuickActions() {
        val source = Files.readString(Path.of("src/main/java/com/ojnexus/feature/training/SessionScreen.kt"))

        assertTrue(source.contains("SessionQuickActions"))
        assertTrue(source.contains("normalizeSessionSelection"))
        assertTrue(source.contains("selectedProblemId"))
    }

    private fun sessionProblem(id: Long) = SessionProblem(
        problemId = id,
        title = "Problem $id",
        difficulty = null,
        solved = false,
        attempts = 0,
    )
}
