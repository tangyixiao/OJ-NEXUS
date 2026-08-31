package com.ojnexus.feature.profile

import com.ojnexus.core.database.entity.JudgeProfileEntity
import com.ojnexus.core.model.JudgeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LuoguProfileSummaryTest {
    @Test
    fun `luogu profile summary preserves synchronized public fields`() {
        val summary = luoguProfileSummary(
            JudgeProfileEntity(
                judge = JudgeId.LUOGU.id,
                handle = "tourist",
                rating = 2_345,
                ranking = 17,
                passedProblemCount = 321,
                submittedProblemCount = 456,
                followerCount = 78,
                followingCount = 90,
                slogan = "keep solving",
                introduction = "public introduction",
                updatedAt = 1L,
            ),
        )

        requireNotNull(summary)
        assertEquals("tourist", summary.handle)
        assertEquals(17, summary.ranking)
        assertEquals(321, summary.passedProblemCount)
        assertEquals(456, summary.submittedProblemCount)
        assertEquals(78, summary.followerCount)
        assertEquals(90, summary.followingCount)
        assertEquals("keep solving", summary.slogan)
        assertEquals("public introduction", summary.introduction)
    }

    @Test
    fun `non-luogu profile is not rendered as luogu public data`() {
        assertNull(
            luoguProfileSummary(
                JudgeProfileEntity(
                    judge = JudgeId.CODEFORCES.id,
                    handle = "tourist",
                    rating = 3_900,
                    updatedAt = 1L,
                ),
            ),
        )
    }
}
