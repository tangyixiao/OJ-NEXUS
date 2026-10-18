package com.ojnexus.judge.luogu.api.dto

import kotlinx.serialization.Serializable

/**
 * Shapes of the `lentille-context` JSON payload embedded in Luogu's public
 * `/training/list` and `/training/{id}` HTML pages (probed 2026-10-18, no auth).
 */

@Serializable
data class LuoguTrainingListResponse(
    val template: String? = null,
    val status: Int = 200,
    val data: LuoguTrainingListData? = null,
)

@Serializable
data class LuoguTrainingListData(
    val trainings: LuoguTrainingPage? = null,
)

@Serializable
data class LuoguTrainingPage(
    val perPage: Int = 0,
    val count: Int = 0,
    val result: List<LuoguTrainingSummaryDto> = emptyList(),
)

@Serializable
data class LuoguTrainingSummaryDto(
    val id: Long = 0,
    val name: String = "",
    val type: Int? = null,
    val provider: LuoguTrainingProviderDto? = null,
    val createTime: Long? = null,
    val deadline: Long? = null,
    val problemCount: Int = 0,
    val markCount: Int = 0,
)

@Serializable
data class LuoguTrainingProviderDto(
    val uid: Long = 0,
    val name: String = "",
    val color: String? = null,
)

@Serializable
data class LuoguTrainingDetailResponse(
    val template: String? = null,
    val status: Int = 200,
    val data: LuoguTrainingDetailData? = null,
)

@Serializable
data class LuoguTrainingDetailData(
    val training: LuoguTrainingDetailDto? = null,
)

@Serializable
data class LuoguTrainingDetailDto(
    val id: Long = 0,
    val name: String = "",
    val type: Int? = null,
    val provider: LuoguTrainingProviderDto? = null,
    val createTime: Long? = null,
    val deadline: Long? = null,
    val problemCount: Int = 0,
    val markCount: Int = 0,
    val description: String? = null,
    val problems: List<LuoguTrainingProblemDto> = emptyList(),
)

@Serializable
data class LuoguTrainingProblemDto(
    val pid: String = "",
    val type: String? = null,
    val name: String = "",
    val difficulty: Int? = null,
    val fullScore: Int? = null,
    val totalSubmit: Long? = null,
    val totalAccepted: Long? = null,
)
