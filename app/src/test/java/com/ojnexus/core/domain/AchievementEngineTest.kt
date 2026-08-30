package com.ojnexus.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementEngineTest {
    @Test
    fun `first solve unlocks first blood only`() {
        val unlocked = AchievementEngine.evaluate(AchievementEvidence(solved = 1)).filter { it.unlocked }
        assertEquals(setOf(AchievementId.FIRST_BLOOD), unlocked.map { it.id }.toSet())
    }

    @Test
    fun `evidence unlocks milestone achievements deterministically`() {
        val unlocked = AchievementEngine.evaluate(
            AchievementEvidence(
                solved = 10,
                activeDays = 7,
                currentStreak = 7,
                maxSolvedDifficulty = 1800,
                ratedContests = 1,
            ),
        ).filter { it.unlocked }.map { it.id }.toSet()

        assertTrue(AchievementId.FIRST_BLOOD in unlocked)
        assertTrue(AchievementId.TEN_SOLVED in unlocked)
        assertTrue(AchievementId.IRON_WILL in unlocked)
        assertTrue(AchievementId.RED_LINE in unlocked)
        assertTrue(AchievementId.CONTESTANT in unlocked)
    }
}
