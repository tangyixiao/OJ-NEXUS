package com.ojnexus.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Root-cause log for a failed (or otherwise instructive) attempt. Not a copy of the attempt:
 * one attempt may produce one failure entry with an explanation.
 */
@Entity(
    tableName = "failure_entries",
    foreignKeys = [
        ForeignKey(
            entity = ProblemEntity::class,
            parentColumns = ["id"],
            childColumns = ["problem_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AttemptEntity::class,
            parentColumns = ["id"],
            childColumns = ["attempt_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["problem_id"]),
        Index(value = ["attempt_id"]),
    ],
)
data class FailureEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "problem_id") val problemId: Long,
    /** Optional link to the attempt that produced this failure. */
    @ColumnInfo(name = "attempt_id") val attemptId: Long? = null,
    /** [com.ojnexus.core.model.FailureCategory] name. */
    val category: String,
    val description: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "day_index") val dayIndex: Long,
)
