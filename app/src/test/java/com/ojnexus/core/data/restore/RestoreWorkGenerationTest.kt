package com.ojnexus.core.data.restore

import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreWorkGenerationTest {
    @Test
    fun `matching generation proceeds and an old generation is stale`() {
        assertEquals(
            RestoreWorkDecision.PROCEED,
            decideRestoreWorkGeneration(expected = "g2", current = "g2"),
        )
        assertEquals(
            RestoreWorkDecision.STALE,
            decideRestoreWorkGeneration(expected = "g1", current = "g2"),
        )
    }

    @Test
    fun `missing generation is treated as legacy work`() {
        assertEquals(
            RestoreWorkDecision.LEGACY,
            decideRestoreWorkGeneration(expected = null, current = "g2"),
        )
    }
}
