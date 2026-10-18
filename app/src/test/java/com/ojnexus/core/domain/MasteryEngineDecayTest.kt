package com.ojnexus.core.domain

import com.ojnexus.core.model.KnowledgeArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mastery decay v2. The formula is `floor(score * days / 30)` capped at 30 points; the engine
 * reads no clock, so every boundary is a direct function call.
 */
class MasteryEngineDecayTest {

    private fun evidence(
        attempted: Int = 2,
        solved: Int = 2,
        attempts: Int = 2,
        failures: Int = 0,
        days: Int? = null,
    ) = KnowledgeEvidence(
        attemptedProblems = attempted,
        solvedProblems = solved,
        attempts = attempts,
        failures = failures,
        daysSinceLastSolved = days,
    )

    @Test
    fun `no decay when the area has never been solved`() {
        val result = MasteryEngine.evaluate(
            KnowledgeArea.GRAPH,
            evidence(solved = 0, days = null),
        )
        assertEquals(0, result.decay)
        assertFalse(result.reasons.contains(MasteryReason.DECAY))
    }

    @Test
    fun `zero days means no decay`() {
        val result = MasteryEngine.evaluate(KnowledgeArea.GRAPH, evidence(days = 0))
        assertEquals(0, result.decay)
        assertFalse(result.reasons.contains(MasteryReason.DECAY))
    }

    @Test
    fun `decay is applied proportionally`() {
        // Perfect coverage and efficiency give 100; 15 days of 30 removes half, capped at 30.
        val result = MasteryEngine.evaluate(KnowledgeArea.GRAPH, evidence(days = 15))
        assertEquals(MasteryEngine.MAX_DECAY, result.decay)
        assertEquals(70, result.score)
        assertTrue(result.reasons.contains(MasteryReason.DECAY))
    }

    @Test
    fun `decay below the cap removes its proportional share`() {
        // Base 100, 9 days of 30 -> floor(30) = 30 is the cap; 8 days -> floor(26.66) = 26.
        val eight = MasteryEngine.evaluate(KnowledgeArea.GRAPH, evidence(days = 8))
        assertEquals(26, eight.decay)
        assertEquals(74, eight.score)
    }

    @Test
    fun `decay floors instead of rounding`() {
        // Base 70 -> 70 * 8 / 30 = 18.66 -> 18.
        val result = MasteryEngine.evaluate(
            KnowledgeArea.MATH,
            evidence(attempted = 10, solved = 7, attempts = 10, days = 8),
        )
        assertEquals(18, result.decay)
    }

    @Test
    fun `decay is capped at the maximum`() {
        val result = MasteryEngine.evaluate(KnowledgeArea.GRAPH, evidence(days = 365))
        assertEquals(MasteryEngine.MAX_DECAY, result.decay)
        assertEquals(70, result.score)
    }

    @Test
    fun `decay is capped by the score it is applied to`() {
        // Base 10 with 30 silent days: 10 * 30 / 30 = 10, so the score floors at zero and the
        // applied decay equals the base rather than the formula's larger proportional value.
        val result = MasteryEngine.evaluate(
            KnowledgeArea.GRAPH,
            evidence(attempted = 100, solved = 10, attempts = 100, days = 30),
        )
        assertEquals(10, result.decay)
        assertEquals(0, result.score)
    }

    @Test
    fun `score zero floors decay at zero`() {
        // A failure log alone can zero the base, and decay then has nothing to remove.
        val result = MasteryEngine.evaluate(
            KnowledgeArea.GREEDY,
            evidence(attempted = 10, solved = 1, attempts = 10, failures = 5, days = 400),
        )
        assertEquals(0, result.decay)
        assertEquals(0, result.score)
    }

    @Test
    fun `score never goes below zero`() {
        val result = MasteryEngine.evaluate(
            KnowledgeArea.GREEDY,
            evidence(attempted = 10, solved = 1, attempts = 10, failures = 5, days = 400),
        )
        assertEquals(0, result.score)
    }

    @Test
    fun `decay travels with the result`() {
        val result = MasteryEngine.evaluate(KnowledgeArea.SEARCH, evidence(days = 30))
        assertEquals(30, result.decay)
        assertTrue(result.reasons.contains(MasteryReason.DECAY))
    }

    @Test
    fun `negative day counts are treated as zero`() {
        val fresh = MasteryEngine.evaluate(KnowledgeArea.GRAPH, evidence(days = 0))
        val stale = MasteryEngine.evaluate(KnowledgeArea.GRAPH, evidence(days = -5))
        assertEquals(fresh, stale)
    }

    @Test
    fun `areas with no evidence still report no evidence`() {
        val result = MasteryEngine.evaluate(
            KnowledgeArea.DATA_STRUCTURE,
            KnowledgeEvidence(0, 0, 0, 0, daysSinceLastSolved = 30),
        )
        assertEquals(0, result.score)
        assertEquals(0, result.decay)
        assertEquals(setOf(MasteryReason.NO_EVIDENCE), result.reasons)
    }

    @Test
    fun `documented constants match the formula`() {
        assertEquals(30, MasteryEngine.DECAY_FULL_DAYS)
        assertEquals(30, MasteryEngine.MAX_DECAY)
    }

    @Test
    fun `decay is a pure function of the evidence`() {
        val first = MasteryEngine.evaluate(KnowledgeArea.STRING, evidence(days = 12))
        val second = MasteryEngine.evaluate(KnowledgeArea.STRING, evidence(days = 12))
        assertEquals(first, second)
    }
}
