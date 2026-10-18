package com.ojnexus.feature.training

import com.ojnexus.core.domain.TrainingReason
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

/** Difficulty band a focus sprint walks through. */
enum class FocusSprintLadderBand {
    EASIER,
    FIT,
    STRETCH,
    ANY,
}

/** Deterministic, data-backed explanation shown on a sprint preview row. */
enum class FocusSprintReasonCode {
    REVIEW_DUE,
    WEAK_AREA,
    FAILURE_HISTORY,
    REVIEW_GAP,
    DIFFICULTY_FIT,
    UNSOLVED,
    UNRATED,
}

/** A sprint candidate enriched with its difficulty band and reason code. */
data class FocusSprintLadderItem(
    val item: FocusSprintItem,
    val difficulty: Int?,
    val targetDifficulty: Int?,
    val band: FocusSprintLadderBand,
    val reason: FocusSprintReasonCode,
    /** Days between today and the due day; null for non-due items. */
    val reviewGapDays: Int?,
)

/** Difficulty-ordered sprint derived from the same pools as [FocusSprintPlan]. */
data class FocusSprintLadderPlan(
    val items: List<FocusSprintLadderItem>,
    val band: FocusSprintLadderBand,
    val targetDifficulty: Int?,
) {
    val ids: List<Long> get() = items.map { it.item.problemId }
    val dueCount: Int get() = items.count { it.item.source == FocusSprintSource.DUE }
    val targetCount: Int get() = items.count { it.item.source == FocusSprintSource.TARGET }
    val unratedCount: Int get() = items.count { it.difficulty == null }
}

private const val EASIER_STEP = 200
private const val FIT_STEP = 200

/**
 * Bands a rating against the user target. A missing rating or a missing target is never
 * inferred: both collapse to [FocusSprintLadderBand.ANY] so the row stays honest.
 */
fun bandFor(difficulty: Int?, targetDifficulty: Int?): FocusSprintLadderBand {
    if (difficulty == null || targetDifficulty == null) return FocusSprintLadderBand.ANY
    return when {
        difficulty <= targetDifficulty - EASIER_STEP -> FocusSprintLadderBand.EASIER
        difficulty <= targetDifficulty + FIT_STEP -> FocusSprintLadderBand.FIT
        else -> FocusSprintLadderBand.STRETCH
    }
}

/**
 * Orders the focus sprint by ascending difficulty fit. Due reviews keep their urgency order
 * inside a band, ranked targets keep priority order, and unrated candidates trail rather than
 * being dropped or invented.
 */
fun buildFocusSprintLadder(
    buckets: ReviewBuckets,
    recommendations: List<TrainingRecommendation>,
    targetDifficulty: Int?,
    band: FocusSprintLadderBand,
    todayEpochDay: Long,
    limit: Int = DEFAULT_FOCUS_SPRINT_LIMIT,
): FocusSprintLadderPlan {
    if (limit <= 0) return FocusSprintLadderPlan(emptyList(), band, targetDifficulty)

    val dueItems = (buckets.overdue + buckets.dueToday)
        .sortedWith(
            compareBy<ReviewQueueItem> { it.dueDayIndex }
                .thenBy { it.dueAt }
                .thenBy { it.problemId },
        )
        .distinctBy { it.problemId }
    val dueIds = dueItems.mapTo(mutableSetOf()) { it.problemId }
    val targetItems = recommendations
        .sortedWith(
            compareByDescending<TrainingRecommendation> { it.priority }
                .thenBy { it.problemId },
        )
        .filter { it.problemId !in dueIds }
        .distinctBy { it.problemId }

    val candidates = dueItems.map { row ->
        FocusSprintLadderItem(
            item = FocusSprintItem(
                problemId = row.problemId,
                judge = row.judge.displayName,
                externalId = row.externalId,
                title = row.problemTitle,
                source = FocusSprintSource.DUE,
            ),
            difficulty = row.difficulty,
            targetDifficulty = targetDifficulty,
            band = bandFor(row.difficulty, targetDifficulty),
            reason = FocusSprintReasonCode.REVIEW_DUE,
            reviewGapDays = (row.dueDayIndex - todayEpochDay).coerceAtLeast(0L).toInt(),
        )
    } + targetItems.map { row ->
        FocusSprintLadderItem(
            item = FocusSprintItem(
                problemId = row.problemId,
                judge = row.judge,
                externalId = row.externalId,
                title = row.title,
                source = FocusSprintSource.TARGET,
            ),
            difficulty = row.difficulty,
            targetDifficulty = targetDifficulty,
            band = bandFor(row.difficulty, targetDifficulty),
            reason = if (row.difficulty == null) {
                FocusSprintReasonCode.UNRATED
            } else {
                reasonFor(row.reasons)
            },
            reviewGapDays = null,
        )
    }

    val ordered = candidates.sortedWith(
        compareBy<FocusSprintLadderItem> { bandRank(it.band) }
            .thenBy { it.difficulty == null }
            .thenBy { it.difficulty ?: Int.MAX_VALUE }
            .thenBy { if (it.item.source == FocusSprintSource.DUE) 0 else 1 }
            .thenBy { it.item.problemId },
    )
    val filtered = if (band == FocusSprintLadderBand.ANY) {
        ordered
    } else {
        ordered.filter { it.band == band || it.difficulty == null }
    }
    return FocusSprintLadderPlan(filtered.take(limit), band, targetDifficulty)
}

private fun bandRank(band: FocusSprintLadderBand): Int = when (band) {
    FocusSprintLadderBand.EASIER -> 0
    FocusSprintLadderBand.FIT -> 1
    FocusSprintLadderBand.STRETCH -> 2
    FocusSprintLadderBand.ANY -> 3
}

private fun reasonFor(reasons: Set<TrainingReason>): FocusSprintReasonCode = when {
    TrainingReason.LOW_MASTERY in reasons -> FocusSprintReasonCode.WEAK_AREA
    TrainingReason.FAILURE_HISTORY in reasons -> FocusSprintReasonCode.FAILURE_HISTORY
    TrainingReason.REVIEW_DUE in reasons -> FocusSprintReasonCode.REVIEW_GAP
    TrainingReason.DIFFICULTY_FIT in reasons -> FocusSprintReasonCode.DIFFICULTY_FIT
    else -> FocusSprintReasonCode.UNSOLVED
}
