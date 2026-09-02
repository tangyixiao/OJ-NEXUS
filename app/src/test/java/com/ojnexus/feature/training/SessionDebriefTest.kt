package com.ojnexus.feature.training

import com.ojnexus.core.model.SessionProblem
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionDebriefTest {

    private val problems = listOf(
        SessionProblem(1L, "pending", 800, solved = false, attempts = 0),
        SessionProblem(2L, "attempted", 900, solved = false, attempts = 2),
        SessionProblem(3L, "solved", 1000, solved = true, attempts = 1),
    )

    @Test
    fun `debrief classifies solved attempted and pending lanes`() {
        assertEquals(SessionDebriefLane.PENDING, problems[0].debriefLane())
        assertEquals(SessionDebriefLane.ATTENTION, problems[1].debriefLane())
        assertEquals(SessionDebriefLane.SOLVED, problems[2].debriefLane())
    }

    @Test
    fun `debrief pulse counts each lane`() {
        assertEquals(
            SessionDebriefPulse(solved = 1, attention = 1, pending = 1),
            deriveSessionDebriefPulse(problems),
        )
        assertEquals(
            SessionDebriefPulse(solved = 0, attention = 0, pending = 0),
            deriveSessionDebriefPulse(emptyList()),
        )
    }

    @Test
    fun `debrief filtering preserves source order`() {
        assertEquals(problems, filterSessionDebrief(problems, null))
        assertEquals(
            listOf(2L),
            filterSessionDebrief(problems, SessionDebriefLane.ATTENTION).map { it.problemId },
        )
    }
}
