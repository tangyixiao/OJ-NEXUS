package com.ojnexus.core.domain

data class AchievementEvidence(
    val solved: Int = 0,
    val activeDays: Int = 0,
    val currentStreak: Int = 0,
    val maxSolvedDifficulty: Int? = null,
    val ratedContests: Int = 0,
)

enum class AchievementId {
    FIRST_BLOOD,
    TEN_SOLVED,
    IRON_WILL,
    RED_LINE,
    CONTESTANT,
}

data class AchievementState(val id: AchievementId, val unlocked: Boolean)

/** Offline achievement policy; unlock state is always derived from current local evidence. */
object AchievementEngine {
    fun evaluate(evidence: AchievementEvidence): List<AchievementState> = listOf(
        AchievementState(AchievementId.FIRST_BLOOD, evidence.solved >= 1),
        AchievementState(AchievementId.TEN_SOLVED, evidence.solved >= 10),
        AchievementState(
            AchievementId.IRON_WILL,
            evidence.activeDays >= 7 && evidence.currentStreak >= 7,
        ),
        AchievementState(AchievementId.RED_LINE, (evidence.maxSolvedDifficulty ?: 0) >= 1800),
        AchievementState(AchievementId.CONTESTANT, evidence.ratedContests >= 1),
    )
}
