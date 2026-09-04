package com.ojnexus.feature.training

import com.ojnexus.core.model.SessionProblem
import com.ojnexus.core.model.SessionState
import com.ojnexus.core.model.TrainingSession
import com.ojnexus.core.model.TrainingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionMomentumTest {

    @Test
    fun nextIsFirstUnsolvedRowInSessionOrder() {
        val problems = listOf(
            sessionProblem(id = 1L, solved = true),
            sessionProblem(id = 2L, attempts = 2),
            sessionProblem(id = 3L),
        )

        val state = deriveSessionMomentum(
            session = session(targetDurationMin = 25),
            problems = problems,
            elapsedMs = 5 * 60_000L,
            selectedProblemId = 2L,
        )

        assertEquals(2L, state.now?.problemId)
        assertEquals(2L, state.next?.problemId)
        assertEquals(2, state.pendingCount)
        assertEquals(20 * 60_000L, state.remainingTargetMs)
        assertFalse(state.isComplete)
    }

    @Test
    fun remainingTargetTimeHasZeroFloor() {
        val state = deriveSessionMomentum(
            session = session(targetDurationMin = 10),
            problems = listOf(sessionProblem(1L)),
            elapsedMs = 11 * 60_000L,
            selectedProblemId = null,
        )

        assertEquals(0L, state.remainingTargetMs)
    }

    @Test
    fun allSolvedSessionIsComplete() {
        val state = deriveSessionMomentum(
            session = session(),
            problems = listOf(
                sessionProblem(1L, solved = true),
                sessionProblem(2L, solved = true),
            ),
            elapsedMs = 0L,
            selectedProblemId = 1L,
        )

        assertTrue(state.isComplete)
        assertEquals(0, state.pendingCount)
        assertNull(state.next)
    }

    @Test
    fun staleSelectionIsNormalized() {
        val state = deriveSessionMomentum(
            session = session(),
            problems = listOf(sessionProblem(1L)),
            elapsedMs = 0L,
            selectedProblemId = 9L,
        )

        assertNull(state.selectedProblemId)
        assertNull(state.now)
    }

    @Test
    fun nullSessionAndEmptyProblemsAreSafe() {
        val state = deriveSessionMomentum(
            session = null,
            problems = emptyList(),
            elapsedMs = 99L,
            selectedProblemId = 1L,
        )

        assertNull(state.now)
        assertNull(state.next)
        assertNull(state.remainingTargetMs)
        assertEquals(0, state.pendingCount)
        assertFalse(state.isComplete)
    }

    private fun session(targetDurationMin: Int? = null) = TrainingSession(
        id = 1L,
        type = TrainingType.PRACTICE,
        state = SessionState.RUNNING,
        startedAt = 1L,
        pausedAt = null,
        totalPausedMs = 0L,
        finishedAt = null,
        targetDurationMin = targetDurationMin,
        targetTag = null,
        note = null,
    )

    private fun sessionProblem(
        id: Long,
        solved: Boolean = false,
        attempts: Int = 0,
    ) = SessionProblem(
        problemId = id,
        title = "Problem $id",
        difficulty = null,
        solved = solved,
        attempts = attempts,
    )
}
