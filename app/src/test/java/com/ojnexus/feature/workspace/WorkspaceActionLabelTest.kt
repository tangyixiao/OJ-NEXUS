package com.ojnexus.feature.workspace

import com.ojnexus.R
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceActionLabelTest {
    @Test
    fun `busy workspace uses working label`() {
        assertEquals(
            R.string.workspace_working,
            workspaceActionLabelRes(WorkspaceState(pid = "P1001", title = null, busy = true)),
        )
    }

    @Test
    fun `submit workspace uses submit label`() {
        assertEquals(
            R.string.workspace_mode_submit,
            workspaceActionLabelRes(
                WorkspaceState(pid = "P1001", title = null, mode = WorkspaceMode.SUBMIT),
            ),
        )
    }

    @Test
    fun `run workspace uses run label`() {
        assertEquals(
            R.string.workspace_mode_run,
            workspaceActionLabelRes(
                WorkspaceState(
                    pid = "P1001",
                    title = null,
                    mode = WorkspaceMode.RUN,
                    customRunAvailable = true,
                ),
            ),
        )
    }
}
