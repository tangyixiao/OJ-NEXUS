package com.ojnexus.feature.analytics

import com.ojnexus.core.domain.ActivityPolicy
import com.ojnexus.core.domain.DayActivity

enum class AnalyticsWindow(val days: Int) {
    DAYS_14(14),
    DAYS_30(30),
    DAYS_90(90),
}

data class AnalyticsWindowSummary(
    val solved: Int,
    val attempts: Int,
    val activeDays: Int,
    val trainingMs: Long,
)

fun analyticsWindowDays(
    days: List<DayActivity>,
    window: AnalyticsWindow,
): List<DayActivity> {
    val count = window.days
    return if (days.size <= count) days.toList() else days.takeLast(count)
}

fun summarizeAnalyticsWindow(days: List<DayActivity>): AnalyticsWindowSummary =
    AnalyticsWindowSummary(
        solved = days.sumOf { it.solved },
        attempts = days.sumOf { it.attempts },
        activeDays = days.count(ActivityPolicy::isActive),
        trainingMs = days.sumOf { it.trainingMs },
    )
