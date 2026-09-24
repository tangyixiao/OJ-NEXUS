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
