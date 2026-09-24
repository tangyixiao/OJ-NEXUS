package com.ojnexus.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.ojnexus.core.model.KnowledgeArea

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
                weaknessScore = 30,
            ),
        )

        assertEquals(100, result.priority)
        assertEquals(
            setOf(
                TrainingReason.UNSOLVED,
                TrainingReason.REVIEW_DUE,
                TrainingReason.FAILURE_HISTORY,
                TrainingReason.DIFFICULTY_FIT,
                TrainingReason.LOW_MASTERY,
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
            ),
        )

        assertEquals(0, result.priority)
        assertTrue(result.reasons.isEmpty())
    }

    @Test
    fun `target tolerance controls difficulty fit without inventing a target`() {
        val absent = TrainingPlanner.rank(
            TrainingCandidate(
                solved = false,
                attemptCount = 0,
                failureCount = 0,
                reviewDue = false,
                difficulty = 1800,
                targetDifficulty = null,
            ),
        )
        val narrow = TrainingPlanner.rank(
            TrainingCandidate(
                solved = false,
                attemptCount = 0,
                failureCount = 0,
                reviewDue = false,
                difficulty = 1800,
                targetDifficulty = 1600,
                targetTolerance = 100,
            ),
        )
        val wide = TrainingPlanner.rank(
            TrainingCandidate(
                solved = false,
                attemptCount = 0,
                failureCount = 0,
                reviewDue = false,
                difficulty = 1800,
                targetDifficulty = 1600,
                targetTolerance = 200,
            ),
        )

        assertEquals(35, absent.priority)
        assertEquals(35, narrow.priority)
        assertEquals(50, wide.priority)
    }

    @Test
    fun `weakness score is bounded and produces low mastery evidence`() {
        val score = TrainingPlanner.weaknessScore(
            linkedAreas = setOf(KnowledgeArea.GRAPH, KnowledgeArea.DYNAMIC_PROGRAMMING),
            masteryScores = mapOf(
                KnowledgeArea.GRAPH to 20,
                KnowledgeArea.DYNAMIC_PROGRAMMING to 40,
            ),
        )
        val result = TrainingPlanner.rank(
            TrainingCandidate(
                solved = true,
                attemptCount = 2,
                failureCount = 0,
                reviewDue = false,
                difficulty = null,
                targetDifficulty = null,
                weaknessScore = score,
            ),
        )

        assertEquals(30, score)
        assertEquals(20, result.priority)
        assertTrue(TrainingReason.LOW_MASTERY in result.reasons)
    }
}
