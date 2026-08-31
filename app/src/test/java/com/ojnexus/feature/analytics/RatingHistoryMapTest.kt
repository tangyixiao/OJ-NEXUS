package com.ojnexus.feature.analytics

import com.ojnexus.core.database.entity.RatingChangeEntity
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
