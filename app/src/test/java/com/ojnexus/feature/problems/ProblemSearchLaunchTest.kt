package com.ojnexus.feature.problems

import com.ojnexus.core.model.JudgeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProblemSearchLaunchTest {

    @Test
    fun `direct search carries query and judge into the library filter`() {
        assertEquals(
            ProblemSearchLaunch(
                filter = ProblemFilter(query = "1029e", judge = JudgeId.CODEFORCES),
                scope = ProblemScope.LIBRARY,
            ),
            problemSearchLaunch("1029e", JudgeId.CODEFORCES),
        )
        assertEquals(
            ProblemSearchLaunch(
                filter = ProblemFilter(query = "segment tree", judge = null),
                scope = ProblemScope.LIBRARY,
            ),
            problemSearchLaunch("segment tree", null),
        )
    }

    @Test
    fun `missing direct search remains unconfigured`() {
        assertNull(problemSearchLaunch(null, null))
    }
}
