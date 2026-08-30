package com.ojnexus.judge.luogu.api.dto

import kotlinx.serialization.Serializable

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
