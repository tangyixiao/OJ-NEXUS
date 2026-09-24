package com.ojnexus.feature.problems

import com.ojnexus.core.database.entity.RemoteProblemEntity
import com.ojnexus.core.model.JudgeId
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteProblemLinksTest {
    @Test
    fun `Luogu remote problems resolve to the canonical problem page`() {
        val problem = RemoteProblemEntity(
            judge = JudgeId.LUOGU.id,
            externalId = "P1001",
            name = "A+B Problem",
            contestId = null,
            index = null,
            rating = null,
            difficultySource = "luogu",
            points = null,
            updatedAt = 1,
        )

        assertEquals("https://www.luogu.com.cn/problem/P1001", remoteProblemUrl(problem))
    }
}
