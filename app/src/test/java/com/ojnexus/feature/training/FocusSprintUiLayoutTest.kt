package com.ojnexus.feature.training

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusSprintUiLayoutTest {

    @Test
    fun `training exposes an editable focus sprint preset`() {
        val source = Files.readString(
            Path.of("src/main/java/com/ojnexus/feature/training/TrainingScreen.kt"),
        )

        assertTrue(source.contains("FocusSprintPanel"))
        assertTrue(source.contains("initialSelectedIds"))
        assertTrue(source.contains("TrainingType.FOCUS"))
        assertTrue(source.contains("training_focus_sprint_tag"))
        assertTrue(source.contains("animateContentSize"))
        assertTrue(source.contains("reduceMotion"))
        assertTrue(source.contains("contentDescription"))
    }
}
