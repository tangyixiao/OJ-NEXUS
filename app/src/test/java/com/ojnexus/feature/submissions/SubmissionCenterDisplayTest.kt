package com.ojnexus.feature.submissions

import com.ojnexus.R
import org.junit.Assert.assertEquals
import org.junit.Test

class SubmissionCenterDisplayTest {
    @Test
    fun `problem display context includes title and pid when both are available`() {
        assertEquals("A+B · P1001", submissionProblemDisplay("P1001", "A+B"))
    }

    @Test
    fun `problem display context falls back to pid when title is absent`() {
        assertEquals("P1001", submissionProblemDisplay("P1001", null))
    }

    @Test
    fun `details label tracks expansion state`() {
        assertEquals(R.string.submissions_details, submissionDetailsLabel(false))
        assertEquals(R.string.submissions_hide_details, submissionDetailsLabel(true))
    }
}
