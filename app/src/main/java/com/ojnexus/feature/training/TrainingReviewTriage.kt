package com.ojnexus.feature.training

import com.ojnexus.core.model.ReviewQueueItem

enum class ReviewQueueFilter {
    ALL,
    DUE_NOW,
    UPCOMING,
}

data class ReviewQueueSummary(
    val overdue: Int,
    val dueToday: Int,
    val upcoming: Int,
    val total: Int,
    val nextDueProblemId: Long?,
)

fun reviewQueueSummary(buckets: ReviewBuckets): ReviewQueueSummary {
    val dueNow = buckets.overdue + buckets.dueToday
    val nextDue = dueNow.minWithOrNull(
        compareBy<ReviewQueueItem> { it.dueDayIndex }
            .thenBy { it.dueAt }
            .thenBy { it.problemId },
    )
    return ReviewQueueSummary(
        overdue = buckets.overdue.size,
        dueToday = buckets.dueToday.size,
        upcoming = buckets.upcoming.size,
        total = dueNow.size + buckets.upcoming.size,
        nextDueProblemId = nextDue?.problemId,
    )
}

fun filterReviewBuckets(
    buckets: ReviewBuckets,
    filter: ReviewQueueFilter,
): ReviewBuckets = when (filter) {
    ReviewQueueFilter.ALL -> ReviewBuckets(
        overdue = buckets.overdue.toList(),
        dueToday = buckets.dueToday.toList(),
        upcoming = buckets.upcoming.toList(),
    )
    ReviewQueueFilter.DUE_NOW -> ReviewBuckets(
        overdue = buckets.overdue.toList(),
        dueToday = buckets.dueToday.toList(),
    )
    ReviewQueueFilter.UPCOMING -> ReviewBuckets(
        upcoming = buckets.upcoming.toList(),
    )
}
