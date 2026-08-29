package com.ojnexus.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One recorded attempt (manual in Phase 1; synced submissions reuse this row later). */
@Entity(
    tableName = "attempts",
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
        Index(value = ["timestamp"]),
    ],
)
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "problem_id") val problemId: Long,
    /** UTC epoch millis of the attempt. */
    val timestamp: Long,
    /** Local calendar day of the attempt, as epoch day — stable day bucketing key. */
    @ColumnInfo(name = "day_index") val dayIndex: Long,
    /** Unified verdict name, see [com.ojnexus.core.model.Verdict]. */
    val verdict: String,
    /** Judge-side verdict as sent by the OJ; null for manual entries. */
    @ColumnInfo(name = "raw_verdict") val rawVerdict: String? = null,
    @ColumnInfo(name = "duration_min") val durationMinutes: Int? = null,
    val language: String? = null,
    val note: String? = null,
)
