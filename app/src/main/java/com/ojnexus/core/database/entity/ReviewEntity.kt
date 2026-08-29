package com.ojnexus.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Active review schedule for a problem. One row per problem that is being reviewed.
 * Interval progression is owned by [com.ojnexus.core.domain.ReviewScheduler] — this row
 * only stores the scheduler's decision.
 */
@Entity(
    tableName = "reviews",
    foreignKeys = [
        ForeignKey(
            entity = ProblemEntity::class,
            parentColumns = ["id"],
            childColumns = ["problem_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["due_day_index"]),
    ],
)
data class ReviewEntity(
    @PrimaryKey @ColumnInfo(name = "problem_id") val problemId: Long,
    /** Index into the scheduler's interval ladder (0 = first interval). */
    val stage: Int,
    /** When the review is due, UTC epoch millis. */
    @ColumnInfo(name = "due_at") val dueAt: Long,
    /** Local calendar day the review is due, as epoch day. */
    @ColumnInfo(name = "due_day_index") val dueDayIndex: Long,
    /** Last result applied, null before the first completion. */
    @ColumnInfo(name = "last_result") val lastResult: String? = null,
    @ColumnInfo(name = "last_reviewed_at") val lastReviewedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Immutable log of completed reviews — feeds daily activity and history. */
@Entity(
    tableName = "review_log",
    foreignKeys = [
        ForeignKey(
            entity = ProblemEntity::class,
            parentColumns = ["id"],
            childColumns = ["problem_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["problem_id"]),
        Index(value = ["day_index"]),
    ],
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "problem_id") val problemId: Long,
    /** [com.ojnexus.core.model.ReviewResult] name. */
    val result: String,
    /** Stage the review was completed at. */
    val stage: Int,
    @ColumnInfo(name = "completed_at") val completedAt: Long,
    @ColumnInfo(name = "day_index") val dayIndex: Long,
)
