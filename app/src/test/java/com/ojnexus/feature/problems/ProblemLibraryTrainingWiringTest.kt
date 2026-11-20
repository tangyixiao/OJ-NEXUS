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
        // Phase 137: the visible window comes from the SQL-backed PagingSource snapshot,
        // never from a full-list state field.
        assertTrue(source.contains("collectAsLazyPagingItems"))
        assertTrue(source.contains("buildTrainingProblemIds(visibleProblems)"))
        assertFalse(source.contains("uiState.problems"))
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
