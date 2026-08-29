package com.ojnexus.core.domain

import com.ojnexus.core.model.ProblemKey
import com.ojnexus.core.model.ProblemStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityScorerTest {

    @Test
    fun `empty day scores zero`() {
        val day = DayActivity(0, solved = 0, attempts = 0, reviewsCompleted = 0, trainingMs = 0)
        assertEquals(0, ActivityScorer.score(day))
        assertEquals(0, ActivityScorer.intensity(day))
    }

    @Test
    fun `weights are solved x3 attempts x1 reviews x2 minutes x1`() {
        // 2 solved, 3 other attempts, 1 review, 30 min training -> 6 + 3 + 2 + 30.
        val day = DayActivity(0, solved = 2, attempts = 5, reviewsCompleted = 1, trainingMs = 30 * 60_000L)
        assertEquals(41, ActivityScorer.score(day))
        assertEquals(4, ActivityScorer.intensity(day))
    }

    @Test
    fun `attempts beyond solves count once`() {
        val day = DayActivity(0, solved = 1, attempts = 3)
        // 3 + 2 other attempts = 5 -> bucket 2.
        assertEquals(5, ActivityScorer.score(day))
        assertEquals(2, ActivityScorer.intensity(day))
    }

    @Test
    fun `intensity buckets match the documented bands`() {
        fun intensity(score: Int): Int {
            val day = DayActivity(0, solved = score, attempts = 0)
            // solved*3 with attempts=solved keeps otherAttempts 0.
            return ActivityScorer.intensity(day)
        }
        // score = 3*solved: 0->0, 1..2 unreachable directly, so probe via policy edges:
        assertEquals(0, ActivityScorer.intensity(DayActivity(0, 0, 0, 0, 0)))
        assertEquals(1, ActivityScorer.intensity(DayActivity(0, solved = 0, attempts = 1))) // 1
        assertEquals(2, ActivityScorer.intensity(DayActivity(0, solved = 1, attempts = 1))) // 3
        assertEquals(3, ActivityScorer.intensity(DayActivity(0, solved = 2, attempts = 2))) // 6
        assertEquals(4, ActivityScorer.intensity(DayActivity(0, solved = 4, attempts = 4))) // 12
    }
}

class ProblemStatusTest {

    @Test
    fun `no attempts is unsolved`() {
        assertEquals(ProblemStatus.UNSOLVED, ProblemStatus.of(solved = false, attemptCount = 0, hasActiveReview = false))
    }

    @Test
    fun `attempts without ac is attempted`() {
        assertEquals(ProblemStatus.ATTEMPTED, ProblemStatus.of(solved = false, attemptCount = 2, hasActiveReview = false))
    }

    @Test
    fun `ac is solved`() {
        assertEquals(ProblemStatus.SOLVED, ProblemStatus.of(solved = true, attemptCount = 1, hasActiveReview = false))
    }

    @Test
    fun `review overrides even when solved`() {
        // Solved problems can still be in the review system.
        assertEquals(ProblemStatus.REVIEW, ProblemStatus.of(solved = true, attemptCount = 3, hasActiveReview = true))
        assertEquals(ProblemStatus.REVIEW, ProblemStatus.of(solved = false, attemptCount = 1, hasActiveReview = true))
    }
}

class ProblemKeyTest {

    @Test
    fun `same external id on different judges never collides`() {
        val a = ProblemKey(com.ojnexus.core.model.JudgeId.LUOGU, "P1000")
        val b = ProblemKey(com.ojnexus.core.model.JudgeId.CODEFORCES, "P1000")
        org.junit.Assert.assertNotEquals(a, b)
        org.junit.Assert.assertNotEquals(a.toString(), b.toString())
    }

    @Test
    fun `same key on same judge is equal`() {
        assertEquals(
            ProblemKey(com.ojnexus.core.model.JudgeId.CODEFORCES, "1919F"),
            ProblemKey(com.ojnexus.core.model.JudgeId.CODEFORCES, "1919F"),
        )
    }
}
