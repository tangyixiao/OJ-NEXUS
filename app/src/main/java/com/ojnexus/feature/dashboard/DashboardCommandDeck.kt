package com.ojnexus.feature.dashboard

import com.ojnexus.core.database.entity.ContestEntity
import com.ojnexus.core.model.ReviewQueueItem

data class DashboardSummary(
    val dueReviews: Int,
    val connectedJudges: Int,
    val solvedThisWeek: Int,
    val nextContestRemainingSeconds: Long?,
)

data class DashboardCountdown(
    val days: Long,
    val hours: Long,
    val minutes: Long,
)

fun deriveDashboardSummary(
    reviews: List<ReviewQueueItem>,
    todayEpochDay: Long,
    enabledJudgeCount: Int,
    solvedThisWeek: Int,
    contests: List<ContestEntity>,
    nowSeconds: Long,
): DashboardSummary {
    val nextContestStart = contests.asSequence()
        .mapNotNull { it.startTimeSeconds }
        .filter { it > nowSeconds }
        .minOrNull()

    return DashboardSummary(
        dueReviews = reviews.count { it.dueDayIndex <= todayEpochDay },
        connectedJudges = enabledJudgeCount.coerceAtLeast(0),
        solvedThisWeek = solvedThisWeek.coerceAtLeast(0),
        nextContestRemainingSeconds = nextContestStart?.minus(nowSeconds)?.coerceAtLeast(0L),
    )
}

fun dashboardCountdown(remainingSeconds: Long?): DashboardCountdown? {
    val seconds = remainingSeconds?.coerceAtLeast(0L) ?: return null
    return DashboardCountdown(
        days = seconds / SECONDS_PER_DAY,
        hours = (seconds % SECONDS_PER_DAY) / SECONDS_PER_HOUR,
        minutes = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE,
    )
}

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 60L * SECONDS_PER_MINUTE
private const val SECONDS_PER_DAY = 24L * SECONDS_PER_HOUR
