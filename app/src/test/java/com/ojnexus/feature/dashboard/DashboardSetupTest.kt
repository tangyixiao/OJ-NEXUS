package com.ojnexus.feature.dashboard

import com.ojnexus.core.model.JudgeId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardSetupTest {
    @Test
    fun `empty connections need Luogu setup`() {
        assertTrue(shouldShowLuoguSetup(emptySet()))
    }

    @Test
    fun `Codeforces-only connections still need Luogu setup`() {
        assertTrue(shouldShowLuoguSetup(setOf(JudgeId.CODEFORCES)))
    }

    @Test
    fun `Luogu connection hides setup`() {
        assertFalse(shouldShowLuoguSetup(setOf(JudgeId.LUOGU)))
    }
}
