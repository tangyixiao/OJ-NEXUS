package com.ojnexus.core.domain

import com.ojnexus.core.model.KnowledgeArea
import kotlin.math.roundToInt

/** Evidence aggregated from local problem/attempt/failure rows for one knowledge area. */
data class KnowledgeEvidence(
    val attemptedProblems: Int,
    val solvedProblems: Int,
    val attempts: Int,
    val failures: Int,
)

enum class MasteryReason {
    NO_EVIDENCE,
    LOW_AC_RATE,
    FAILURE_LOG,
}

data class MasteryResult(
    val area: KnowledgeArea,
    val score: Int,
    val reasons: Set<MasteryReason>,
)

/**
 * Explainable, deterministic mastery policy. Solving coverage contributes 70 points, first-try
 * efficiency contributes 30, and each recorded failure entry removes five points. No network or
 * generated advice is involved; the reason codes are rendered by the feature layer.
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
        val score = (coverage * 70 + efficiency * 30 - failures * 5)
            .roundToInt()
            .coerceIn(0, 100)
        val reasons = buildSet {
            if (coverage < LOW_AC_RATE_THRESHOLD) add(MasteryReason.LOW_AC_RATE)
            if (failures > 0) add(MasteryReason.FAILURE_LOG)
        }
        return MasteryResult(area, score, reasons)
    }

    private const val LOW_AC_RATE_THRESHOLD = 0.6
}
