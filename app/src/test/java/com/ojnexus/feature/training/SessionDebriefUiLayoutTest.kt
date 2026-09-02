package com.ojnexus.feature.training

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDebriefUiLayoutTest {

    @Test
    fun `terminal summary owns both debrief actions`() {
        val source = Files.readString(
            Path.of("src/main/java/com/ojnexus/feature/training/SessionScreen.kt"),
        )
        val summary = source.substringAfter("private fun SessionSummaryView(")
            .substringBefore("private fun SessionProgressBoard(")

        assertTrue(summary.contains("SessionDebriefPanel"))
        assertTrue(summary.contains("onOpenReview"))
        assertTrue(summary.contains("onOpenProblem"))
    }
}
