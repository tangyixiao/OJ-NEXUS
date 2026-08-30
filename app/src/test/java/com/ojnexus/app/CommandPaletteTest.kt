package com.ojnexus.app

import org.junit.Assert.assertEquals
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
}
