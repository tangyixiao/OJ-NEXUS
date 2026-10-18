package com.ojnexus.feature.analytics

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-level assertions for the analytics heatmap accessibility contract. The streak math
 * itself is a pure function covered by StreakCalculatorTest; this file pins the UI wiring.
 */
class AnalyticsHeatmapUiLayoutTest {

    @Test
    fun `heatmap cells announce their date`() {
        val source = Files.readString(
            Path.of("src/main/java/com/ojnexus/feature/analytics/AnalyticsScreen.kt"),
        )

        // The cell description carries the local date alongside intensity and solved count.
        assertTrue(source.contains("R.string.analytics_heatmap_cell_cd"))
        assertTrue(source.contains("formatDate(day.dayIndex"))
        // Padded cells are described as empty, not as zero-activity days.
        assertTrue(source.contains("R.string.analytics_heatmap_cell_padding_cd"))
    }

    @Test
    fun `totals surface both current and longest streak`() {
        val source = Files.readString(
            Path.of("src/main/java/com/ojnexus/feature/analytics/AnalyticsScreen.kt"),
        )

        assertTrue(source.contains("R.string.metric_streak"))
        assertTrue(source.contains("R.string.analytics_streak_longest"))
        assertTrue(source.contains("state.longestStreak"))
    }

    @Test
    fun `heatmap strings exist in both locales with matching placeholders`() {
        val english = Files.readString(Path.of("src/main/res/values/strings.xml"))
        val chinese = Files.readString(Path.of("src/main/res/values-zh-rCN/strings.xml"))

        assertTrue(english.contains("analytics_heatmap_cell_cd"))
        assertTrue(chinese.contains("analytics_heatmap_cell_cd"))
        assertTrue(english.contains("analytics_heatmap_cell_padding_cd"))
        assertTrue(chinese.contains("analytics_heatmap_cell_padding_cd"))
        assertTrue(english.contains("analytics_streak_longest"))
        assertTrue(chinese.contains("analytics_streak_longest"))
    }
}
