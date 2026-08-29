package com.ojnexus.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Unified problem row. Identity is (judge, externalId) — see [com.ojnexus.core.model.ProblemKey].
 *
 * `solved` (has the user an AC) and the review state (see [ReviewEntity]) are separate
 * dimensions on purpose: a problem can be solved AND still due for review.
 */
@Entity(
    tableName = "problems",
    indices = [
        Index(value = ["judge", "external_id"], unique = true),
        Index(value = ["judge"]),
        Index(value = ["solved"]),
        Index(value = ["updated_at"]),
    ],
)
data class ProblemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val judge: String,
    @ColumnInfo(name = "external_id") val externalId: String,
    val title: String,
    /** Unified difficulty rating. NULL means the judge does not define one — never 0. */
    val difficulty: Int?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "first_solved_at") val firstSolvedAt: Long? = null,
    @ColumnInfo(name = "last_attempt_at") val lastAttemptAt: Long? = null,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
    val solved: Boolean = false,
    val favorite: Boolean = false,
    @ColumnInfo(name = "source_url") val sourceUrl: String? = null,
)
