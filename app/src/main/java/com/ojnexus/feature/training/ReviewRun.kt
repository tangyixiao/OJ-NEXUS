package com.ojnexus.feature.training

import com.ojnexus.core.model.ReviewQueueItem

/** Captures only the reviews due when a focused run starts. */
fun captureReviewRunQueue(
    queue: List<ReviewQueueItem>,
    todayEpochDay: Long,
): List<ReviewQueueItem> = queue
    .filter { it.dueDayIndex <= todayEpochDay }
    .sortedWith(compareBy<ReviewQueueItem> { it.dueAt }.thenBy { it.problemId })

/** Returns a determinate progress fraction for the run rail. */
fun reviewRunProgress(total: Int, completed: Int): Float =
    if (total <= 0) 0f else (completed.toFloat() / total).coerceIn(0f, 1f)
