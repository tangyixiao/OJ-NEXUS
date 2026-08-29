package com.ojnexus.core.model

/**
 * Full problem aggregate for the detail screen. Attempts and failures are ordered newest
 * first; the failure timeline interleaves them in the UI layer.
 */
data class ProblemDetail(
    val problem: Problem,
    val attempts: List<Attempt>,
    val failures: List<FailureEntry>,
    val notes: ProblemNotes?,
    val review: ReviewState?,
) {
    val hasActiveReview: Boolean get() = review != null
}

/** One row of the review queue (Training screen). */
data class ReviewQueueItem(
    val problemId: Long,
    val problemTitle: String,
    val judge: JudgeId,
    val difficulty: Int?,
    val stage: Int,
    val dueAt: Long,
    val dueDayIndex: Long,
    val lastResult: ReviewResult?,
)

/** One row of the cross-judge recent activity feed. */
data class RecentAttempt(
    val problemId: Long,
    val judge: JudgeId,
    val problemCode: String,
    val problemTitle: String,
    val verdict: Verdict,
    val timestamp: Long,
)
