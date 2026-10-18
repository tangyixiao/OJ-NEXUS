package com.ojnexus.judge.atcoder.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AtCoderSubmissionDto(
    @SerialName("execution_time") val executionTime: Int? = null,
    val point: Double? = null,
    val result: String,
    @SerialName("problem_id") val problemId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("epoch_second") val epochSecond: Long,
    @SerialName("contest_id") val contestId: String,
    val id: Long,
    val language: String? = null,
    val length: Int? = null,
)

@Serializable
data class AtCoderContestDto(
    @SerialName("start_epoch_second") val startEpochSecond: Long,
    @SerialName("rate_change") val rateChange: String,
    val id: String,
    @SerialName("duration_second") val durationSecond: Long,
    val title: String,
)

@Serializable
data class AtCoderMergedProblemDto(
    val id: String,
    @SerialName("contest_id") val contestId: String,
    @SerialName("problem_index") val problemIndex: String,
    val name: String,
    @SerialName("solver_count") val solverCount: Int? = null,
    val point: Double? = null,
)

@Serializable
data class AtCoderProblemModelDto(
    val slope: Double? = null,
    val intercept: Double? = null,
    val difficulty: Double? = null,
    @SerialName("rawDifficulty") val rawDifficulty: Double? = null,
    val discrimination: Double? = null,
    val variance: Double? = null,
    @SerialName("is_experimental") val isExperimental: Boolean = false,
)

/**
 * One row of the official `users/<handle>/history/json` rating history. Field names are
 * PascalCase exactly as AtCoder serves them. Unrated rows carry `NewRating = 0` and are
 * filtered out by the sync layer before mapping — the UI never renders a missing rating as
 * zero.
 */
@Serializable
data class AtCoderRatingChangeDto(
    @SerialName("IsRated") val isRated: Boolean,
    @SerialName("Place") val place: Int? = null,
    @SerialName("OldRating") val oldRating: Int? = null,
    @SerialName("NewRating") val newRating: Int,
    @SerialName("Performance") val performance: Int? = null,
    @SerialName("InnerPerformance") val innerPerformance: Int? = null,
    @SerialName("ContestScreenName") val contestScreenName: String,
    @SerialName("ContestName") val contestName: String,
    @SerialName("ContestNameEn") val contestNameEn: String? = null,
    /** Offset timestamp such as `2016-09-04T22:50:00+09:00`; parsed with [java.time.OffsetDateTime]. */
    @SerialName("EndTime") val endTime: String,
)
