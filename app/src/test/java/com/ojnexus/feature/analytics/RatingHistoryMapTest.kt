package com.ojnexus.feature.analytics

import com.ojnexus.core.database.entity.RatingChangeEntity
import com.ojnexus.core.data.repository.Totals
import com.ojnexus.core.model.JudgeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RatingHistoryMapTest {
    @Test
    fun `rating histories retain each judge and omit empty series`() {
        val luogu = rating("luogu-contest", 1500)
        val histories = ratingHistoriesByJudge(
            codeforces = emptyList(),
            atcoder = listOf(rating("atcoder-contest", 800)),
            luogu = listOf(luogu),
        )

        assertTrue(JudgeId.CODEFORCES !in histories)
        assertEquals(800, histories[JudgeId.ATCODER]?.single()?.newRating)
        assertEquals(luogu, histories[JudgeId.LUOGU]?.single())
    }

    @Test
    fun `rating history keeps analytics out of the empty state without local attempts`() {
        val totals = Totals(attempts = 0, ac = 0, problems = 0, solved = 0)
        val history = mapOf(JudgeId.LUOGU to listOf(rating("luogu-contest", 1500)))

        assertTrue(analyticsHasData(totals, history))
    }

    @Test
    fun `analytics remains empty when local data and rating histories are absent`() {
        assertTrue(analyticsHasNoData(Totals(0, 0, 0, 0), emptyMap()))
    }

    private fun rating(contest: String, newRating: Int) = RatingChangeEntity(
        judge = "luogu",
        handle = "tester",
        contestId = contest,
        contestName = contest,
        ratingUpdateTimeSeconds = 1,
        oldRating = newRating - 100,
        newRating = newRating,
        rank = 1,
    )
}
