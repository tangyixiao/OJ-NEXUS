package com.ojnexus.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Judge-agnostic contest row (Codeforces today; other judges later). The raw judge-side
 * phase is preserved alongside app-level UPCOMING/LIVE/ENDED derivation so a future
 * phase taxonomy never loses information. Gym contests are not synced.
 */
@Entity(
    tableName = "contests",
    primaryKeys = ["judge", "external_contest_id"],
    indices = [
        Index(value = ["judge", "start_time_seconds"]),
        Index(value = ["judge", "phase"]),
    ],
)
data class ContestEntity(
    /** [com.ojnexus.core.model.JudgeId] id. */
    val judge: String,
    @ColumnInfo(name = "external_contest_id") val externalContestId: String,
    val name: String,
    val type: String? = null,
    /** Raw judge-side phase string (BEFORE / CODING / FINISHED / …). */
    val phase: String,
    val frozen: Boolean = false,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Long,
    /** Start time, UTC epoch seconds ( judge-side convention); null = not yet announced. */
    @ColumnInfo(name = "start_time_seconds") val startTimeSeconds: Long? = null,
    @ColumnInfo(name = "relative_time_seconds") val relativeTimeSeconds: Long? = null,
    @ColumnInfo(name = "prepared_by") val preparedBy: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
