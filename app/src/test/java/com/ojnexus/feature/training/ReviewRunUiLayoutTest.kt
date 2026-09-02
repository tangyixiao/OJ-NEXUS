package com.ojnexus.feature.training

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewRunUiLayoutTest {

    @Test
    fun `training pulse launches the continuous review run`() {
        val source = Files.readString(
            Path.of("src/main/java/com/ojnexus/feature/training/TrainingScreen.kt"),
        )

        assertTrue(source.contains("onOpenReviewRun"))
        assertTrue(source.contains("onStartRun"))
    }

    @Test
    fun `review run exposes outcome controls and reduced motion progress`() {
        val source = Files.readString(
            Path.of("src/main/java/com/ojnexus/feature/training/ReviewRunScreen.kt"),
        )

        assertTrue(source.contains("reviewRunProgress"))
        assertTrue(source.contains("ReviewResult.PASS"))
        assertTrue(source.contains("ReviewResult.HARD"))
        assertTrue(source.contains("ReviewResult.FAIL"))
        assertTrue(source.contains("ReviewResult.SKIP"))
        assertTrue(source.contains("animateContentSize"))
        assertTrue(source.contains("reduceMotion"))
    }
}
