package com.ojnexus.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ContestMarkerTest {
    @Test
    fun `marker cycle advances through local contest states`() {
        assertEquals(ContestMarker.WORKING, ContestMarker.NONE.next())
        assertEquals(ContestMarker.SOLVED, ContestMarker.WORKING.next())
        assertEquals(ContestMarker.SKIPPED, ContestMarker.SOLVED.next())
        assertEquals(ContestMarker.NONE, ContestMarker.SKIPPED.next())
    }
}
