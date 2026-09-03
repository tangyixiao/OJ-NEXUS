package com.ojnexus.feature.problems

import com.ojnexus.core.model.JudgeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProblemSearchPrefillTest {

    @Test
    fun `direct search carries query and judge into the library filter`() {
        assertEquals(
            ProblemFilter(query = "1029e", judge = JudgeId.CODEFORCES),
            problemSearchPrefill("1029e", JudgeId.CODEFORCES),
        )
        assertEquals(
            ProblemFilter(query = "segment tree", judge = null),
            problemSearchPrefill("segment tree", null),
        )
    }

    @Test
    fun `missing direct search remains unconfigured`() {
        assertNull(problemSearchPrefill(null, null))
    }
}
