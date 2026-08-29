package com.ojnexus.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Judge-side problem catalog (Codeforces problemset today; AtCoder/Luogu later).
 * Deliberately SEPARATE from the local training library (`problems`): the catalog holds
 * tens of thousands of rows and must never pollute the user's problem list. Remote tags
 * are stored here as a serialized list — they are remote metadata, not user tags, so a
 * sync can never clobber the user's own tags on the local problem row.
 */
@Entity(
    tableName = "remote_problems",
    primaryKeys = ["judge", "external_id"],
    indices = [
        Index(value = ["judge", "rating"]),
        Index(value = ["name"]),
    ],
)
data class RemoteProblemEntity(
    /** [com.ojnexus.core.model.JudgeId] id. */
    val judge: String,
    @ColumnInfo(name = "external_id") val externalId: String,
    @ColumnInfo(name = "contest_id") val contestId: String? = null,
    /** Judge-side problem index within the contest, e.g. "C". */
    val index: String? = null,
    val name: String,
    val type: String? = null,
    /** Unified difficulty rating; null = unrated (never 0). */
    val rating: Int? = null,
    /** [com.ojnexus.core.model.DifficultySource] name. */
    @ColumnInfo(name = "difficulty_source") val difficultySource: String = "UNKNOWN",
    val points: Double? = null,
    /** Remote tag strings, serialized; display + search only. */
    val tags: String = "[]",
    @ColumnInfo(name = "solved_count") val solvedCount: Int? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Long = updatedAt,
)
