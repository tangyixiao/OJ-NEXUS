package com.ojnexus.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Training session. Timing is derived, never polled:
 * elapsed = now - startedAt - totalPausedMs - (pausedAt == null ? 0 : now - pausedAt).
 * Survives process recreation because every state change is persisted.
 */
@Entity(
    tableName = "training_sessions",
    indices = [
        Index(value = ["state"]),
        Index(value = ["day_index"]),
        Index(value = ["started_at"]),
    ],
)
data class TrainingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [com.ojnexus.core.model.TrainingType] name. */
    val type: String,
    /** [com.ojnexus.core.domain.SessionState] name. */
    val state: String,
    /** UTC epoch millis of the session start. */
    @ColumnInfo(name = "started_at") val startedAt: Long,
    /** When the session is currently paused: UTC epoch millis of the pause. */
    @ColumnInfo(name = "paused_at") val pausedAt: Long? = null,
    /** Accumulated pause duration before the current pause. */
    @ColumnInfo(name = "total_paused_ms") val totalPausedMs: Long = 0,
    @ColumnInfo(name = "finished_at") val finishedAt: Long? = null,
    @ColumnInfo(name = "target_duration_min") val targetDurationMin: Int? = null,
    @ColumnInfo(name = "target_tag") val targetTag: String? = null,
    val note: String? = null,
    /** Local calendar day the session started, as epoch day. */
    @ColumnInfo(name = "day_index") val dayIndex: Long,
)

/** A problem attached to a session with its in-session progress. */
@Entity(
    tableName = "training_session_problems",
    primaryKeys = ["session_id", "problem_id"],
    foreignKeys = [
        ForeignKey(
            entity = TrainingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProblemEntity::class,
            parentColumns = ["id"],
            childColumns = ["problem_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["problem_id"]),
    ],
)
data class TrainingSessionProblemEntity(
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "problem_id") val problemId: Long,
    val solved: Boolean = false,
    val attempts: Int = 0,
)
