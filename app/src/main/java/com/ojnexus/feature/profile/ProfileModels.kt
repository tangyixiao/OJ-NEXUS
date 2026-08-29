package com.ojnexus.feature.profile

import com.ojnexus.core.model.JudgeId

data class JudgeCardUi(
    val judge: JudgeId,
    val linked: Boolean,
    /** Preformatted rating text; null when unlinked. */
    val ratingText: String? = null,
    val rankText: String? = null,
    val solvedCount: Int? = null,
    val contestCount: Int? = null,
)

data class GlobalStatsUi(
    val solved: Int,
    val submissions: Int,
    val activeDays: Int,
    val streakDays: Int,
    val maxDifficulty: Int,
)

data class ProfileUiState(
    val handle: String,
    val judgeCards: List<JudgeCardUi>,
    val global: GlobalStatsUi,
)
