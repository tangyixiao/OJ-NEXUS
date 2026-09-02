package com.ojnexus.feature.dashboard

import com.ojnexus.core.database.entity.ContestEntity
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.ReviewQueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardCommandDeckTest {
    @Test
    fun `summary counts only due reviews and enabled judges`() {
        val reviews = listOf(
            ReviewQueueItem(1L, "A", JudgeId.CODEFORCES, 800, 0, 1_000L, 10L, null),
            ReviewQueueItem(2L, "B", JudgeId.CODEFORCES, 900, 1, 1_000L, 11L, null),
            ReviewQueueItem(3L, "C", JudgeId.CODEFORCES, 1_000, 0, 1_000L, 12L, null),
        )

        val summary = deriveDashboardSummary(
            reviews = reviews,
            todayEpochDay = 11L,
            enabledJudgeCount = 2,
            solvedThisWeek = 3,
            contests = emptyList(),
            nowSeconds = 1_000L,
        )

        assertEquals(DashboardSummary(2, 2, 3, null), summary)
    }

    @Test
    fun `summary selects earliest future contest and clamps remaining time`() {
        val contests = listOf(
            ContestEntity("CODEFORCES", "late", "Late", "CONTEST", "BEFORE", false, 3_600L, 1_500L, null, null, 1_000L),
            ContestEntity("CODEFORCES", "early", "Early", "CONTEST", "BEFORE", false, 3_600L, 1_200L, null, null, 1_000L),
            ContestEntity("CODEFORCES", "past", "Past", "CONTEST", "FINISHED", false, 3_600L, 900L, null, null, 1_000L),
        )

        val summary = deriveDashboardSummary(
            reviews = emptyList(),
            todayEpochDay = 11L,
            enabledJudgeCount = 0,
            solvedThisWeek = 0,
            contests = contests,
            nowSeconds = 1_000L,
        )

        assertEquals(200L, summary.nextContestRemainingSeconds)
    }

    @Test
    fun `countdown returns null for no contest and zero for an expired snapshot`() {
        assertNull(dashboardCountdown(null))
        assertEquals(DashboardCountdown(days = 0L, hours = 0L, minutes = 0L), dashboardCountdown(0L))
        assertEquals(
            DashboardCountdown(days = 2L, hours = 3L, minutes = 4L),
            dashboardCountdown(2L * 86_400L + 3L * 3_600L + 4L * 60L + 59L),
        )
        assertEquals(DashboardCountdown(days = 0L, hours = 0L, minutes = 0L), dashboardCountdown(-1L))
    }

    @Test
    fun `ui state carries the command summary`() {
        val summary = DashboardSummary(1, 2, 3, 4L)
        val state = DashboardUiState(
            todayTasks = emptyList(),
            week = WeekSummary(3, 4, 5L),
            currentStreak = 1,
            longestStreak = 2,
            nextReview = null,
            recent = emptyList(),
            loadWeek = emptyList(),
            summary = summary,
            nowSeconds = 100L,
        )

        assertEquals(summary, state.summary)
    }
}
