package com.ojnexus.feature.problems

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProblemScopeNavigationTest {
    @Test
    fun `remote scope exposes a transition back to the local scope`() {
        assertTrue(shouldSwitchProblemScope(ProblemScope.REMOTE, ProblemScope.LIBRARY))
        assertFalse(shouldSwitchProblemScope(ProblemScope.LIBRARY, ProblemScope.LIBRARY))
    }
}
