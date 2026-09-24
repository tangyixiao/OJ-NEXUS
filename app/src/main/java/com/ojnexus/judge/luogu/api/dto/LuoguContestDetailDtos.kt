package com.ojnexus.judge.luogu.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class LuoguContestDetailResponse(
    val status: Int = 200,
    val template: String? = null,
    val instance: String? = null,
    val data: LuoguContestDetailData? = null,
)

@Serializable
data class LuoguContestDetailData(
    val contest: LuoguContestDetailDto? = null,
    val contestProblems: List<LuoguContestProblemDto> = emptyList(),
)

@Serializable
data class LuoguContestDetailDto(
    val id: Long = 0,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val name: String = "",
    val method: Int? = null,
    val rated: Int? = null,
    val problemCount: Int? = null,
    val description: String? = null,
    val totalParticipants: Int? = null,
)

@Serializable
data class LuoguContestProblemDto(
    val score: Int? = null,
    val problem: LuoguContestProblemRefDto? = null,
    val no: String? = null,
)

@Serializable
data class LuoguContestProblemRefDto(
    val pid: String = "",
    val type: String? = null,
    val name: String = "",
    val difficulty: Int? = null,
)
