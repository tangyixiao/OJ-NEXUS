package com.ojnexus.judge.luogu.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class LuoguProblemDetailResponse(
    val status: Int = 200,
    val template: String? = null,
    val instance: String? = null,
    val data: LuoguProblemDetailData? = null,
)

@Serializable
data class LuoguProblemDetailData(
    val problem: LuoguProblemDetailDto? = null,
)

/** Public problem payload returned by Luogu's `problem.show` content-only page. */
@Serializable
data class LuoguProblemDetailDto(
    val pid: String = "",
    val type: String? = null,
    val name: String = "",
    val difficulty: Int? = null,
    val tags: List<Int> = emptyList(),
    val totalSubmit: Int? = null,
    val totalAccepted: Int? = null,
    val contenu: LuoguProblemContentDto? = null,
    val content: LuoguProblemContentDto? = null,
    val samples: List<String> = emptyList(),
    val limits: LuoguProblemLimitsDto? = null,
)

@Serializable
data class LuoguProblemContentDto(
    val name: String? = null,
    val background: String? = null,
    val description: String? = null,
    val formatI: String? = null,
    val formatO: String? = null,
    val hint: String? = null,
    val locale: String? = null,
)

@Serializable
data class LuoguProblemLimitsDto(
    val time: List<Int> = emptyList(),
    val memory: List<Int> = emptyList(),
)
