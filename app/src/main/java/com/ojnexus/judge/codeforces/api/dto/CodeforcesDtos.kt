package com.ojnexus.judge.codeforces.api.dto

import kotlinx.serialization.Serializable

/**
 * Typed Codeforces DTOs. Every field is optional unless the official schema guarantees it,
 * `ignoreUnknownKeys` is enabled on the client Json, and unknown future values degrade to
 * null/OTHER instead of crashing the sync (API compatibility rule).
 */

@Serializable
data class CfUserDto(
    val handle: String,
    val email: String? = null,
    val vkId: String? = null,
    val openId: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val country: String? = null,
    val city: String? = null,
    val organization: String? = null,
    val contribution: Long? = null,
    val rank: String? = null,
    val rating: Int? = null,
    val maxRank: String? = null,
    val maxRating: Int? = null,
    val lastOnlineTimeSeconds: Long? = null,
    val registrationTimeSeconds: Long? = null,
    val friendOfCount: Int? = null,
    val avatar: String? = null,
    val titlePhoto: String? = null,
)

@Serializable
data class CfProblemDto(
    val contestId: Long? = null,
    val problemsetName: String? = null,
    val index: String,
    val name: String,
    val type: String? = null,
    val points: Double? = null,
    val rating: Int? = null,
    val tags: List<String> = emptyList(),
)

@Serializable
data class CfProblemStatisticsDto(
    val contestId: Long? = null,
    val index: String,
    val solvedCount: Int,
)

@Serializable
data class CfSubmissionDto(
    val id: Long,
    /** May be absent for problemset submissions. */
    val contestId: Long? = null,
    val creationTimeSeconds: Long,
    val relativeTimeSeconds: Long? = null,
    val problem: CfProblemDto,
    val programmingLanguage: String? = null,
    val verdict: String? = null,
    val passedTestCount: Int? = null,
    /** Millis on Codeforces; stored as execution_time_ms. */
    val timeConsumedMillis: Int? = null,
    /** Bytes on Codeforces; stored as memory_bytes. */
    val memoryConsumedBytes: Long? = null,
    val participantType: String? = null,
    val testset: String? = null,
)

@Serializable
data class CfContestDto(
    val id: Long,
    val name: String,
    val type: String? = null,
    val phase: String,
    val frozen: Boolean? = null,
    val durationSeconds: Long,
    val startTimeSeconds: Long? = null,
    val relativeTimeSeconds: Long? = null,
    val preparedBy: String? = null,
)

@Serializable
data class CfRatingChangeDto(
    val contestId: Long,
    val contestName: String,
    val handle: String,
    val rank: Int,
    val ratingUpdateTimeSeconds: Long,
    val oldRating: Int,
    val newRating: Int,
)

/** result of problemset.problems: two parallel lists, merged by contestId+index. */
@Serializable
data class CfProblemsetDto(
    val problems: List<CfProblemDto> = emptyList(),
    val problemStatistics: List<CfProblemStatisticsDto> = emptyList(),
)
