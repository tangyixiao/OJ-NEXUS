package com.ojnexus.judge.atcoder

import com.ojnexus.judge.atcoder.api.dto.AtCoderSubmissionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AtCoderSubmissionCursorPlannerTest {
    private fun submission(id: Long, second: Long) = AtCoderSubmissionDto(
        result = "AC",
        problemId = "abc350_a",
        userId = "CaseUser",
        epochSecond = second,
        contestId = "abc350",
        id = id,
    )

    @Test
    fun `short page completes after persisting its maximum timestamp`() {
        val decision = AtCoderSubmissionCursorPlanner.plan(
            fromSecond = 0,
            page = listOf(submission(1, 100), submission(2, 101)),
            pageSize = 500,
            seenIds = emptySet(),
        )

        assertFalse(decision.shouldContinue)
        assertFalse(decision.stalled)
        assertEquals(101L, decision.durableCursorSecond)
    }

    @Test
    fun `full page repeats maximum second instead of skipping it`() {
        val page = (1L..500L).map { submission(it, 100 + it) }
        val decision = AtCoderSubmissionCursorPlanner.plan(0, page, 500, emptySet())

        assertTrue(decision.shouldContinue)
        assertEquals(600L, decision.nextFromSecond)
        assertEquals(600L, decision.durableCursorSecond)
    }

    @Test
    fun `new ids at the same cursor second count as progress`() {
        val page = (1L..500L).map { submission(it, 100) }
        val seen = (1L..499L).toSet()
        val decision = AtCoderSubmissionCursorPlanner.plan(100, page, 500, seen)

        assertTrue(decision.shouldContinue)
        assertFalse(decision.stalled)
        assertEquals(setOf(500L), decision.newIds)
        assertEquals(100L, decision.nextFromSecond)
    }

    @Test
    fun `identical full same-second page stalls safely`() {
        val page = (1L..500L).map { submission(it, 100) }
        val decision = AtCoderSubmissionCursorPlanner.plan(
            fromSecond = 100,
            page = page,
            pageSize = 500,
            seenIds = page.mapTo(mutableSetOf()) { it.id },
        )

        assertFalse(decision.shouldContinue)
        assertTrue(decision.stalled)
        assertEquals(100L, decision.durableCursorSecond)
    }
}
