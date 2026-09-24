package com.ojnexus.feature.training

import com.ojnexus.core.model.ReviewQueueItem

/** The local signal that placed a problem in a focus sprint. */
enum class FocusSprintSource {
    DUE,
    TARGET,
}

/** A render-ready problem selected for a focus sprint. */
data class FocusSprintItem(
    val problemId: Long,
    val judge: String,
    val externalId: String?,
    val title: String,
    val source: FocusSprintSource,
)

/** Stable, user-visible plan derived from the current Training snapshot. */
data class FocusSprintPlan(
    val items: List<FocusSprintItem>,
) {
    val ids: List<Long> get() = items.map { it.problemId }
    val dueCount: Int get() = items.count { it.source == FocusSprintSource.DUE }
    val targetCount: Int get() = items.count { it.source == FocusSprintSource.TARGET }
}

/**
 * Selects an actionable local sprint: reviews due now have priority, then ranked targets fill
 * the remaining slots. The snapshot is intentionally deterministic so the dialog preview and
 * the IDs sent to the repository cannot disagree during one composition.
 */
fun buildFocusSprintPlan(
    buckets: ReviewBuckets,
    recommendations: List<TrainingRecommendation>,
    limit: Int = DEFAULT_FOCUS_SPRINT_LIMIT,
): FocusSprintPlan {
    if (limit <= 0) return FocusSprintPlan(emptyList())

    val due = (buckets.overdue + buckets.dueToday)
        .sortedWith(
            compareBy<ReviewQueueItem> { it.dueDayIndex }
                .thenBy { it.dueAt }
                .thenBy { it.problemId },
        )
        .map { item ->
            FocusSprintItem(
                problemId = item.problemId,
                judge = item.judge.displayName,
                externalId = item.externalId,
                title = item.problemTitle,
                source = FocusSprintSource.DUE,
            )
        }
        .distinctBy { it.problemId }

    val dueIds = due.mapTo(mutableSetOf()) { it.problemId }
    val targets = recommendations
        .asSequence()
        .sortedWith(
            compareByDescending<TrainingRecommendation> { it.priority }
                .thenBy { it.problemId },
        )
        .filter { it.problemId !in dueIds }
        .map { recommendation ->
            FocusSprintItem(
                problemId = recommendation.problemId,
                judge = recommendation.judge,
                externalId = recommendation.externalId,
                title = recommendation.title,
                source = FocusSprintSource.TARGET,
            )
        }
        .distinctBy { it.problemId }
        .toList()

    return FocusSprintPlan((due + targets).take(limit))
}

private const val DEFAULT_FOCUS_SPRINT_LIMIT = 5
