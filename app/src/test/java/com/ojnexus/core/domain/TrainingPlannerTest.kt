package com.ojnexus.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingPlannerTest {
    @Test
    fun `priority explains an overdue unsolved problem at a fitting difficulty`() {
        val result = TrainingPlanner.rank(
            TrainingCandidate(
                solved = false,
                attemptCount = 2,
                failureCount = 2,
                reviewDue = true,
                difficulty = 1800,
                targetDifficulty = 1750,
                coverageValue = 1,
            ),
        )

        assertEquals(100, result.priority)
        assertEquals(
            setOf(
                TrainingReason.UNSOLVED,
                TrainingReason.REVIEW_DUE,
                TrainingReason.FAILURE_HISTORY,
                TrainingReason.DIFFICULTY_FIT,
                TrainingReason.COVERAGE_VALUE,
            ),
            result.reasons,
        )
    }

    @Test
    fun `solved candidate with no active signal has zero priority`() {
        val result = TrainingPlanner.rank(
            TrainingCandidate(
                solved = true,
                attemptCount = 4,
                failureCount = 0,
                reviewDue = false,
                difficulty = null,
                targetDifficulty = null,
                coverageValue = 0,
            ),
        )

        assertEquals(0, result.priority)
        assertTrue(result.reasons.isEmpty())
    }
}
