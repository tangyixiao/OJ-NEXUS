package com.ojnexus.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A task on the TODAY list. Problems are optional (e.g. READ tasks). */
@Entity(
    tableName = "training_tasks",
    foreignKeys = [
        ForeignKey(
            entity = ProblemEntity::class,
            parentColumns = ["id"],
            childColumns = ["problem_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["date_epoch_day"]),
        Index(value = ["problem_id"]),
    ],
)
data class TrainingTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Local calendar day the task belongs to, as epoch day. */
    @ColumnInfo(name = "date_epoch_day") val dateEpochDay: Long,
    /** [com.ojnexus.core.model.TaskType] name. */
    val type: String,
    @ColumnInfo(name = "problem_id") val problemId: Long? = null,
    /** Free-text task title for tasks not bound to a problem. */
    val title: String? = null,
    val completed: Boolean = false,
    /** Higher = more important; drives display order. */
    val priority: Int = 50,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
