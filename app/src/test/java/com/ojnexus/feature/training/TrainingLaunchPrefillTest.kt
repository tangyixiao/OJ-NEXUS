package com.ojnexus.feature.training

import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingLaunchPrefillTest {

    @Test
    fun `library handoff supplies initial problem IDs`() {
        assertEquals(
            listOf(4L, 8L),
            trainingDialogInitialProblemIds(
                focusSprintMode = false,
                focusSprintIds = listOf(99L),
                libraryProblemIds = listOf(4L, 8L),
            ),
        )
    }

    @Test
    fun `focus sprint IDs take precedence over library handoff`() {
        assertEquals(
            listOf(99L),
            trainingDialogInitialProblemIds(
                focusSprintMode = true,
                focusSprintIds = listOf(99L),
                libraryProblemIds = listOf(4L, 8L),
            ),
        )
    }
}
