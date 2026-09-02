package com.ojnexus.feature.analytics

import com.ojnexus.core.domain.DayActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsFocusLensTest {
    private fun day(
        index: Long,
        solved: Int = 0,
        attempts: Int = 0,
        reviews: Int = 0,
        trainingMs: Long = 0,
    ) = DayActivity(
        dayIndex = index,
        solved = solved,
        attempts = attempts,
        reviewsCompleted = reviews,
        trainingMs = trainingMs,
    )

    @Test
    fun windowsReturnOrderedSuffixAndCopyShortSources() {
        val source = (1L..100L).map { day(it) }

        val last14 = analyticsWindowDays(source, AnalyticsWindow.DAYS_14)
        val last30 = analyticsWindowDays(source, AnalyticsWindow.DAYS_30)
        val last90 = analyticsWindowDays(source, AnalyticsWindow.DAYS_90)
        val shortSource = listOf(day(1), day(2))

        assertEquals(14, last14.size)
        assertEquals(87L, last14.first().dayIndex)
        assertEquals(100L, last14.last().dayIndex)
        assertEquals(30, last30.size)
        assertEquals(71L, last30.first().dayIndex)
        assertEquals(90, last90.size)
        assertEquals(11L, last90.first().dayIndex)
        assertEquals(shortSource, analyticsWindowDays(shortSource, AnalyticsWindow.DAYS_90))
        assertNotSame(shortSource, analyticsWindowDays(shortSource, AnalyticsWindow.DAYS_90))
        assertTrue(analyticsWindowDays(emptyList(), AnalyticsWindow.DAYS_14).isEmpty())
    }

    @Test
    fun summarySumsMetricsAndUsesActivityPolicyForActiveDays() {
        val summary = summarizeAnalyticsWindow(
            listOf(
                day(1, solved = 2, attempts = 3, reviews = 0, trainingMs = 120_000),
                day(2, solved = 0, attempts = 2, reviews = 0, trainingMs = 1_800_000),
                day(3, solved = 0, attempts = 0, reviews = 1, trainingMs = 0),
            ),
        )

        assertEquals(
            AnalyticsWindowSummary(
                solved = 2,
                attempts = 5,
                activeDays = 3,
                trainingMs = 1_920_000,
            ),
            summary,
        )
    }

    @Test
    fun emptySummaryHasZeroValues() {
        assertEquals(
            AnalyticsWindowSummary(solved = 0, attempts = 0, activeDays = 0, trainingMs = 0),
            summarizeAnalyticsWindow(emptyList()),
        )
    }
}
