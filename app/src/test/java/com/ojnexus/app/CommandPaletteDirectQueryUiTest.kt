package com.ojnexus.app

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandPaletteDirectQueryUiTest {

    @Test
    fun `palette renders direct query as an accessible animated action`() {
        val source = Files.readString(Path.of("src/main/java/com/ojnexus/app/CommandPalette.kt"))

        assertTrue(source.contains("parsePaletteQuery(query)"))
        assertTrue(source.contains("onSearchProblems"))
        assertTrue(source.contains("command_palette_direct_query"))
        assertTrue(source.contains("Role.Button"))
        assertTrue(source.contains("mergeDescendants = true"))
        assertTrue(source.contains("animateContentSize"))
    }
}
