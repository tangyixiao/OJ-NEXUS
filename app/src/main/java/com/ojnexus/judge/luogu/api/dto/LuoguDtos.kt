package com.ojnexus.judge.luogu.api.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class LuoguUserSearchResponse(
    val users: List<LuoguUserSummary?> = emptyList(),
)

/** Public fields returned by Luogu's user search endpoint. */
@Serializable
data class LuoguUserSummary(
    val uid: Long,
    val name: String,
    val avatar: String? = null,
    val slogan: String? = null,
    val badge: String? = null,
    val isAdmin: Boolean = false,
    val isBanned: Boolean = false,
    val color: String? = null,
    val ccfLevel: Int? = null,
    val xcpcLevel: Int? = null,
    val background: String? = null,
    val isRoot: Boolean = false,
)

@Serializable
data class LuoguUserPageResponse(
    val status: Int = 200,
    val template: String? = null,
    val instance: String? = null,
    val locale: String? = null,
    val data: LuoguUserPageData? = null,
)

@Serializable
data class LuoguUserPageData(
    val user: LuoguPublicUserDto? = null,
    val gu: LuoguRatingSummaryDto? = null,
    val elo: List<LuoguEloEntryDto>? = null,
)

/** Public user fields shared by Luogu's profile and user-info payloads. */
@Serializable
data class LuoguPublicUserDto(
    val uid: Long = 0,
    val name: String = "",
    val introduction: String? = null,
    val registerTime: Long? = null,
    val followingCount: Int? = null,
    val followerCount: Int? = null,
    val ranking: Int? = null,
    val eloValue: Int? = null,
    val blogAddress: String? = null,
    val passedProblemCount: Int? = null,
    val submittedProblemCount: Int? = null,
    val avatar: String? = null,
    val slogan: String? = null,
    val badge: String? = null,
    val color: String? = null,
    val ccfLevel: Int? = null,
    val xcpcLevel: Int? = null,
    val background: String? = null,
)

@Serializable
data class LuoguRatingSummaryDto(
    val rating: Int? = null,
    val time: Long? = null,
    val scores: Map<String, Int> = emptyMap(),
)

@Serializable
data class LuoguEloEntryDto(
    val rating: Int? = null,
    val time: Long? = null,
    val latest: Boolean = false,
    val contest: LuoguRatingContestDto? = null,
    val userCount: Int? = null,
    val prevDiff: Int? = null,
    val previous: LuoguPreviousRatingDto? = null,
)

@Serializable
data class LuoguPreviousRatingDto(
    val rating: Int? = null,
)

@Serializable
data class LuoguRatingContestDto(
    val id: Long? = null,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val name: String = "",
)

@Serializable
data class LuoguProblemListResponse(
    val status: Int = 200,
    val template: String? = null,
    val instance: String? = null,
    val data: LuoguProblemListData? = null,
)

@Serializable
data class LuoguProblemListData(
    val problems: LuoguProblemPageDto? = null,
)

@Serializable
data class LuoguProblemPageDto(
    val perPage: Int = 0,
    val count: Int = 0,
    val result: List<LuoguProblemDto> = emptyList(),
)

@Serializable
data class LuoguProblemDto(
    val pid: String = "",
    val type: String? = null,
    val name: String = "",
    val difficulty: Int? = null,
    val tags: List<String> = emptyList(),
    val totalSubmit: Int? = null,
    val totalAccepted: Int? = null,
)

@Serializable
data class LuoguContestListResponse(
    val status: Int = 200,
    val template: String? = null,
    val instance: String? = null,
    val data: LuoguContestListData? = null,
)

@Serializable
data class LuoguContestListData(
    val contests: LuoguContestPageDto? = null,
)

@Serializable
data class LuoguContestPageDto(
    val perPage: Int = 0,
    val count: Int = 0,
    val result: List<LuoguContestDto> = emptyList(),
)

@Serializable
data class LuoguContestDto(
    val id: Long = 0,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val name: String = "",
    val method: Int? = null,
    val rated: Int? = null,
    val problemCount: Int? = null,
    val host: LuoguContestHostDto? = null,
)

@Serializable
data class LuoguContestHostDto(
    val id: Long? = null,
    val name: String? = null,
)

@Serializable
data class LuoguRecordPageResponse(
    val status: Int = 200,
    val template: String? = null,
    val instance: String? = null,
    val data: JsonObject? = null,
)
