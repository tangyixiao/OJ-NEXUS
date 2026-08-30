package com.ojnexus.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached profile snapshot, one per judge (PK is the judge id — it survives handle renames
 * and account reconnection). Every field except `handle` is optional: remote profiles vary.
 */
@Entity(tableName = "judge_profiles")
data class JudgeProfileEntity(
    /** [com.ojnexus.core.model.JudgeId] id. */
    @PrimaryKey val judge: String,
    /** Canonical handle this snapshot belongs to. */
    val handle: String,
    @ColumnInfo(name = "first_name") val firstName: String? = null,
    @ColumnInfo(name = "last_name") val lastName: String? = null,
    val country: String? = null,
    val city: String? = null,
    val organization: String? = null,
    val contribution: Long? = null,
    /** Current rating; null = unrated (never stored as 0). */
    val rating: Int? = null,
    @ColumnInfo(name = "max_rating") val maxRating: Int? = null,
    val rank: String? = null,
    @ColumnInfo(name = "max_rank") val maxRank: String? = null,
    @ColumnInfo(name = "friend_of_count") val friendOfCount: Int? = null,
    @ColumnInfo(name = "registration_time_seconds") val registrationTimeSeconds: Long? = null,
    @ColumnInfo(name = "last_online_time_seconds") val lastOnlineTimeSeconds: Long? = null,
    val avatar: String? = null,
    @ColumnInfo(name = "title_photo") val titlePhoto: String? = null,
    /** Luogu public profile fields; null for judges that do not expose them. */
    val introduction: String? = null,
    val slogan: String? = null,
    val badge: String? = null,
    val ranking: Int? = null,
    @ColumnInfo(name = "follower_count") val followerCount: Int? = null,
    @ColumnInfo(name = "following_count") val followingCount: Int? = null,
    @ColumnInfo(name = "passed_problem_count") val passedProblemCount: Int? = null,
    @ColumnInfo(name = "submitted_problem_count") val submittedProblemCount: Int? = null,
    @ColumnInfo(name = "ccf_level") val ccfLevel: Int? = null,
    @ColumnInfo(name = "xcpc_level") val xcpcLevel: Int? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
