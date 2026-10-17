package com.ojnexus.core.domain

import com.ojnexus.core.model.KnowledgeArea
import kotlin.math.roundToInt

/** Evidence aggregated from local problem/attempt/failure rows for one knowledge area. */
data class KnowledgeEvidence(
    val attemptedProblems: Int,
    val solvedProblems: Int,
    val attempts: Int,
    val failures: Int,
    /**
     * Local days since the newest AC among this area's linked problems. `null` means no linked
     * problem has been solved, so the area has nothing to decay.
     */
    val daysSinceLastSolved: Int? = null,
)

enum class MasteryReason {
    NO_EVIDENCE,
    LOW_AC_RATE,
    FAILURE_LOG,
    DECAY,
}

data class MasteryResult(
    val area: KnowledgeArea,
    val score: Int,
    val reasons: Set<MasteryReason>,
    /** Points removed by [MasteryReason.DECAY]; 0 when nothing decayed. */
    val decay: Int = 0,
)

/**
 * Explainable, deterministic mastery policy. Solving coverage contributes 70 points, first-try
 * efficiency contributes 30, and each recorded failure entry removes five points. Time since the
 * last solved problem in the area then removes
 * `floor(score * daysSinceLastSolved / DECAY_FULL_DAYS)` points, capped at [MAX_DECAY]. No
 * network or generated advice is involved; the reason codes are rendered by the feature layer.
 */
object MasteryEngine {
    fun evaluate(area: KnowledgeArea, evidence: KnowledgeEvidence): MasteryResult {
        val attempted = evidence.attemptedProblems.coerceAtLeast(0)
        val solved = evidence.solvedProblems.coerceIn(0, attempted)
        val attempts = evidence.attempts.coerceAtLeast(0)
        val failures = evidence.failures.coerceAtLeast(0)
        if (attempted == 0) {
            return MasteryResult(area, score = 0, reasons = setOf(MasteryReason.NO_EVIDENCE))
        }

        val coverage = solved.toDouble() / attempted
        val efficiency = if (attempts == 0) 0.0 else (solved.toDouble() / attempts).coerceAtMost(1.0)
        val base = (coverage * 70 + efficiency * 30 - failures * 5)
            .roundToInt()
            .coerceIn(0, 100)
        val days = evidence.daysSinceLastSolved?.coerceAtLeast(0)
        val decay = if (days == null) {
            0
        } else {
            (base * days / DECAY_FULL_DAYS.toDouble()).toInt().coerceIn(0, MAX_DECAY)
        }
        val score = (base - decay).coerceIn(0, 100)
        val reasons = buildSet {
            if (coverage < LOW_AC_RATE_THRESHOLD) add(MasteryReason.LOW_AC_RATE)
            if (failures > 0) add(MasteryReason.FAILURE_LOG)
            if (decay > 0) add(MasteryReason.DECAY)
        }
        return MasteryResult(area, score, reasons, decay)
    }

    /** Days of silence after which the full [MAX_DECAY] is applied. */
    const val DECAY_FULL_DAYS: Int = 30

    /** Largest number of points time-since-last-AC can remove. */
    const val MAX_DECAY: Int = 30

    private const val LOW_AC_RATE_THRESHOLD = 0.6
}
