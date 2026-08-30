package com.ojnexus.app

import com.ojnexus.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandPaletteTest {
    @Test
    fun `search matches titles and keywords while preserving command order`() {
        val commands = listOf(
            PaletteCommand("problems", "PROBLEMS", "OPEN PROBLEM LIBRARY", setOf("library")),
            PaletteCommand("settings", "SETTINGS", "MANAGE LOCAL SETTINGS", setOf("preferences")),
            PaletteCommand("add", "ADD PROBLEM", "CREATE LOCAL PROBLEM", setOf("library")),
        )

        assertEquals(
            listOf("problems", "add"),
            filterCommands(commands, "library").map { it.id },
        )
    }

    @Test
    fun `blank search returns every command`() {
        val commands = listOf(
            PaletteCommand("dashboard", "DASHBOARD", "OPEN DASHBOARD"),
            PaletteCommand("profile", "PROFILE", "OPEN PROFILE"),
        )

        assertEquals(commands, filterCommands(commands, "  "))
    }

    @Test
    fun `submissions command uses stable id and resource-backed copy`() {
        val command = paletteCommandSpecs().single { it.id == "submissions" }

        assertEquals(R.string.submissions_title, command.titleRes)
        assertEquals(R.string.submissions_section_recent, command.descriptionRes)
        assertTrue(command.keywordRes.isNotEmpty())
        assertTrue(command.keywordRes.contains(R.string.submissions_check_result))
    }
}
