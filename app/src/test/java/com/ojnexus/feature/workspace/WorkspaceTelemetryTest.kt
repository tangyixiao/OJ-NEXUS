package com.ojnexus.feature.workspace

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceTelemetryTest {

    @Test
    fun `source line count handles blank and multiline code`() {
        assertEquals(0, sourceLineCount(""))
        assertEquals(1, sourceLineCount("int main() {}\n"))
        assertEquals(3, sourceLineCount("int main() {\n  return 0;\n}\n"))
    }

    @Test
    fun `telemetry mirrors editable workspace state`() {
        val state = WorkspaceState(
            pid = "P1001",
            title = "A+B",
            code = "a\nb",
            language = "cxx/17/gcc",
            mode = WorkspaceMode.SUBMIT,
            draftState = WorkspaceDraftState.SAVED,
        )

        assertEquals(
            WorkspaceTelemetry(
                mode = WorkspaceMode.SUBMIT,
                language = "cxx/17/gcc",
                codeLines = 2,
                draftState = WorkspaceDraftState.SAVED,
            ),
            workspaceTelemetry(state),
        )
    }
}
