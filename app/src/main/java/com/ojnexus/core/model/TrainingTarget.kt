package com.ojnexus.core.model

/** A user-authored difficulty center and tolerance; null center means no target is configured. */
data class TrainingTarget(
    val judge: JudgeId?,
    val center: Int?,
    val tolerance: Int,
)

object TrainingTargetPolicy {
    const val DEFAULT_TOLERANCE = 200
    const val MIN_TOLERANCE = 25
    const val MAX_TOLERANCE = 1_000
    const val MIN_CENTER = 0
    const val MAX_CENTER = 4_000

    fun isValid(target: TrainingTarget): Boolean =
        target.tolerance in MIN_TOLERANCE..MAX_TOLERANCE &&
            (target.center == null || target.center in MIN_CENTER..MAX_CENTER)
}

/** Selects a judge-specific target first, then the explicit default target. */
fun selectTrainingTarget(targets: List<TrainingTarget>, judge: JudgeId): TrainingTarget? =
    targets.firstOrNull { it.judge == judge } ?: targets.firstOrNull { it.judge == null }
