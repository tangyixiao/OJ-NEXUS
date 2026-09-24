package com.ojnexus.core.domain

import com.ojnexus.core.model.KnowledgeArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MasteryEngineTest {
    @Test
    fun `unattempted area is low mastery and asks for first solve`() {
        val result = MasteryEngine.evaluate(
            KnowledgeArea.GRAPH,
            KnowledgeEvidence(attemptedProblems = 0, solvedProblems = 0, attempts = 0, failures = 0),
        )

        assertEquals(0, result.score)
        assertEquals(setOf(MasteryReason.NO_EVIDENCE), result.reasons.toSet())
    }

    @Test
    fun `repeated failures reduce mastery and explain the training target`() {
        val result = MasteryEngine.evaluate(
            KnowledgeArea.DYNAMIC_PROGRAMMING,
            KnowledgeEvidence(attemptedProblems = 4, solvedProblems = 1, attempts = 8, failures = 3),
        )

        assertTrue(result.score in 0..100)
        assertTrue(MasteryReason.LOW_AC_RATE in result.reasons)
        assertTrue(MasteryReason.FAILURE_LOG in result.reasons)
    }

    @Test
    fun `solved first try evidence reaches strong mastery`() {
        val result = MasteryEngine.evaluate(
            KnowledgeArea.GREEDY,
            KnowledgeEvidence(attemptedProblems = 5, solvedProblems = 5, attempts = 5, failures = 0),
        )

        assertEquals(100, result.score)
        assertTrue(result.reasons.isEmpty())
    }
}
