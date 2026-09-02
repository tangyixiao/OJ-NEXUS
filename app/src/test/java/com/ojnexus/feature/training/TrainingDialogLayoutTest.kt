package com.ojnexus.feature.training

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingDialogLayoutTest {

    @Test
    fun `new session dialog keeps one vertical scroll container`() {
        val source = Files.readString(
            Path.of("src/main/java/com/ojnexus/feature/training/TrainingScreen.kt"),
        )
        val dialog = source.substringAfter("private fun NewSessionDialog(")
            .substringBefore("private fun SessionProblemQueueRow(")

        assertEquals(1, Regex("\\.verticalScroll\\(").findAll(dialog).count())
    }
}
