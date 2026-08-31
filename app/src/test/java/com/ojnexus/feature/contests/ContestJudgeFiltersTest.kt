package com.ojnexus.feature.contests

import com.ojnexus.core.model.JudgeId
import org.junit.Assert.assertEquals
import org.junit.Test

class ContestJudgeFiltersTest {
    @Test
    fun `contest filters expose every synced online judge in stable order`() {
        assertEquals(
            listOf(JudgeId.CODEFORCES, JudgeId.ATCODER, JudgeId.LUOGU),
            contestJudgeFilters(),
        )
    }
}
