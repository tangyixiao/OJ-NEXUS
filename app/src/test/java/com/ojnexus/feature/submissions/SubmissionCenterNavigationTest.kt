package com.ojnexus.feature.submissions

import org.junit.Assert.assertEquals
import org.junit.Test

class SubmissionCenterNavigationTest {
    @Test
    fun `submission workspace context retains the local title`() {
        assertEquals(
            SubmissionWorkspaceContext(pid = "P1001", title = "A+B"),
            submissionWorkspaceContext(pid = "P1001", title = "  A+B  "),
        )
    }

    @Test
    fun `submission workspace context falls back to pid for a blank title`() {
        assertEquals(
            SubmissionWorkspaceContext(pid = "P1001", title = null),
            submissionWorkspaceContext(pid = "P1001", title = "  "),
        )
    }
}
