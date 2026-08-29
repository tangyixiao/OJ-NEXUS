package com.ojnexus.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One rated contest from the judge's rating history. Idempotency key is
 * (judge, contestId) — the app keeps one active account per judge, so re-syncing the full
 * history is an upsert that never duplicates rows. Rating data is per judge, not per
 * account row, so it survives account replacement.
 */
@Entity(
    tableName = "rating_changes",
    indices = [
        Index(value = ["judge", "contest_id"], unique = true),
        Index(value = ["judge", "rating_update_time_seconds"]),
    ],
)
data class RatingChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [com.ojnexus.core.model.JudgeId] id. */
    val judge: String,
    /** Canonical handle the history was fetched for. */
    val handle: String,
    @ColumnInfo(name = "contest_id") val contestId: Long,
    @ColumnInfo(name = "contest_name") val contestName: String,
    val rank: Int,
    @ColumnInfo(name = "old_rating") val oldRating: Int,
    @ColumnInfo(name = "new_rating") val newRating: Int,
    @ColumnInfo(name = "rating_update_time_seconds") val ratingUpdateTimeSeconds: Long,
)
