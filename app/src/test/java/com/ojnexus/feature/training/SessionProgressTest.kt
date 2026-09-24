package com.ojnexus.feature.training

import com.ojnexus.core.model.SessionProblem
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionProgressTest {

    @Test
    fun `pulse keeps empty queue neutral`() {
        assertEquals(
            SessionProgressPulse(total = 0, solved = 0, attempted = 0, pending = 0),
            deriveSessionProgressPulse(emptyList()),
        )
    }

    @Test
    fun `pulse separates solved attempted and pending`() {
        val problems = listOf(
            SessionProblem(1L, "pending", 800, solved = false, attempts = 0),
            SessionProblem(2L, "attempted", 900, solved = false, attempts = 2),
            SessionProblem(3L, "solved", 1000, solved = true, attempts = 1),
        )

        assertEquals(
            SessionProgressPulse(total = 3, solved = 1, attempted = 2, pending = 1),
            deriveSessionProgressPulse(problems),
        )
    }

    @Test
    fun `empty pulse has no progress fraction`() {
        assertEquals(0f, sessionProgressFraction(deriveSessionProgressPulse(emptyList())))
    }
}
