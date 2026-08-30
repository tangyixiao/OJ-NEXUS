package com.ojnexus.core.domain

/** Local evidence supplied to the deterministic candidate ranking policy. */
data class TrainingCandidate(
    val solved: Boolean,
    val attemptCount: Int,
    val failureCount: Int,
    val reviewDue: Boolean,
    val difficulty: Int?,
    val targetDifficulty: Int?,
    val coverageValue: Int,
)

enum class TrainingReason {
    UNSOLVED,
    REVIEW_DUE,
    FAILURE_HISTORY,
    DIFFICULTY_FIT,
    COVERAGE_VALUE,
}

data class TrainingPriority(
    val priority: Int,
    val reasons: Set<TrainingReason>,
)

/**
 * Explainable candidate ranking. Every point comes from a named local signal; this is not an
 * opaque recommendation model and never contacts an OJ.
 */
object TrainingPlanner {
    fun rank(candidate: TrainingCandidate): TrainingPriority {
        val reasons = buildSet {
            if (!candidate.solved) add(TrainingReason.UNSOLVED)
            if (candidate.reviewDue) add(TrainingReason.REVIEW_DUE)
            if (candidate.failureCount > 0) add(TrainingReason.FAILURE_HISTORY)
            val difficultyGap = candidate.difficulty?.let { difficulty ->
                candidate.targetDifficulty?.let { target -> kotlin.math.abs(difficulty - target) }
            }
            if (difficultyGap != null && difficultyGap <= FIT_GAP) {
                add(TrainingReason.DIFFICULTY_FIT)
            }
            if (candidate.coverageValue > 0) add(TrainingReason.COVERAGE_VALUE)
        }
        val score = (if (!candidate.solved) UNSOLVED_POINTS else 0) +
            (if (candidate.reviewDue) REVIEW_POINTS else 0) +
            candidate.failureCount.coerceAtLeast(0).coerceAtMost(FAILURE_CAP) * FAILURE_POINTS +
            (if (TrainingReason.DIFFICULTY_FIT in reasons) DIFFICULTY_POINTS else 0) +
            (if (candidate.coverageValue > 0) COVERAGE_POINTS else 0)
        return TrainingPriority(score.coerceIn(0, 100), reasons)
    }

    private const val UNSOLVED_POINTS = 35
    private const val REVIEW_POINTS = 25
    private const val FAILURE_POINTS = 10
    private const val FAILURE_CAP = 2
    private const val DIFFICULTY_POINTS = 15
    private const val COVERAGE_POINTS = 5
    private const val FIT_GAP = 200
}
