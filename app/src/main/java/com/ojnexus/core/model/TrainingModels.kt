package com.ojnexus.core.model

/**
 * Unified problem as used across features. Built from the database aggregate; never stored
 * as a UI string.
 */
data class Problem(
    val id: Long,
    val key: ProblemKey,
    val title: String,
    /** Unified difficulty; null = unknown (the judge defines none). */
    val difficulty: Int?,
    val createdAt: Long,
    val updatedAt: Long,
    val firstSolvedAt: Long?,
    val lastAttemptAt: Long?,
    val attemptCount: Int,
    val solved: Boolean,
    val favorite: Boolean,
    val sourceUrl: String?,
    val tags: List<String>,
)

/**
 * Display status derived from independent dimensions (solved flag + attempts + review state).
 * REVIEW means the problem is in the review system — it may simultaneously be solved.
 */
enum class ProblemStatus {
    UNSOLVED,
    ATTEMPTED,
    SOLVED,
    REVIEW,
    ;

    companion object {
        fun of(solved: Boolean, attemptCount: Int, hasActiveReview: Boolean): ProblemStatus =
            if (hasActiveReview) {
                REVIEW
            } else if (solved) {
                SOLVED
            } else if (attemptCount > 0) {
                ATTEMPTED
            } else {
                UNSOLVED
            }
    }
}

/** One recorded attempt on a problem. */
data class Attempt(
    val id: Long,
    val problemId: Long,
    val timestamp: Long,
    val verdict: Verdict,
    val rawVerdict: String?,
    val durationMinutes: Int?,
    val language: String?,
    val note: String?,
)

/** Root-cause failure entry attached to a problem (and optionally an attempt). */
data class FailureEntry(
    val id: Long,
    val problemId: Long,
    val attemptId: Long?,
    val category: FailureCategory,
    val description: String,
    val createdAt: Long,
)

/** Structured notes for a problem. */
data class ProblemNotes(
    val problemId: Long,
    val keyInsight: String,
    val implementationNotes: String,
    val complexity: String,
    val general: String,
    val updatedAt: Long,
)

/** Active review state of a problem. */
data class ReviewState(
    val problemId: Long,
    val stage: Int,
    val dueAt: Long,
    val dueDayIndex: Long,
    val lastResult: ReviewResult?,
    val lastReviewedAt: Long?,
    val createdAt: Long,
)

/** Review outcomes accepted by the scheduler. */
enum class ReviewResult {
    PASS,
    HARD,
    FAIL,
    SKIP,
}

/** A TODAY task row. */
data class TrainingTask(
    val id: Long,
    val dateEpochDay: Long,
    val type: TaskType,
    val problemId: Long?,
    val problemTitle: String?,
    val title: String?,
    val completed: Boolean,
    val priority: Int,
    val sortOrder: Int,
    val createdAt: Long,
)

/** A problem attached to a training session. */
data class SessionProblem(
    val problemId: Long,
    val title: String,
    val solved: Boolean,
    val attempts: Int,
)

/** A training session with derived timing handled by SessionClock. */
data class TrainingSession(
    val id: Long,
    val type: TrainingType,
    val state: SessionState,
    val startedAt: Long,
    val pausedAt: Long?,
    val totalPausedMs: Long,
    val finishedAt: Long?,
    val targetDurationMin: Int?,
    val targetTag: String?,
    val note: String?,
)
