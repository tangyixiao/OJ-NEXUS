package com.ojnexus.feature.problems

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProblemLibraryTrainingWiringTest {

    @Test
    fun `library renders training rail from visible problems`() {
        val source = Files.readString(Path.of("src/main/java/com/ojnexus/feature/problems/ProblemsScreen.kt"))

        assertTrue(source.contains("onBuildTraining"))
        assertTrue(source.contains("LibraryTrainingActionRail"))
        assertTrue(source.contains("buildTrainingProblemIds(uiState.problems)"))
        assertFalse(source.substringAfter("private fun RemoteCatalogContent").contains("LibraryTrainingActionRail"))
    }

    @Test
    fun `app routes library training handoff through top-level training`() {
        val source = Files.readString(Path.of("src/main/java/com/ojnexus/app/NexusApp.kt"))

        assertTrue(source.contains("pendingTrainingProblemIds"))
        assertTrue(source.contains("var pendingTrainingProblemIds by rememberSaveable"))
        assertTrue(source.contains("navigateToTopLevel(NexusDestination.TRAINING.route)"))
    }
}
