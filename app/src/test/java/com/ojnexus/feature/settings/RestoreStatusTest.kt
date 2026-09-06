package com.ojnexus.feature.settings

import com.ojnexus.core.data.restore.RestoreFailure
import com.ojnexus.core.data.restore.RestoreOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestoreStatusTest {
    @Test
    fun `restore outcome maps to stable dismissible settings status`() {
        assertEquals(RestoreStatus.APPLIED, restoreStatusFor(RestoreOutcome.Applied("g2")))
        assertEquals(
            RestoreStatus.ROLLED_BACK,
            restoreStatusFor(RestoreOutcome.RolledBack("g1", RestoreFailure.INTEGRITY_FAILURE)),
        )
        assertEquals(RestoreStatus.REJECTED, restoreStatusFor(RestoreOutcome.Rejected(RestoreFailure.SCHEMA_MISMATCH)))
        assertNull(restoreStatusFor(RestoreOutcome.NothingToRestore))
    }
}
