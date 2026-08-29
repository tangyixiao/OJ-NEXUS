package com.ojnexus.core.domain

/**
 * Explainable activity intensity for the heatmap. Not AC-only — attempts, reviews and
 * training time all contribute. Kept deliberately simple:
 *
 *   score = 3×solved + otherAttempts + 2×reviews + trainingMinutes
 *   intensity 0..4 buckets: 0 | 1–2 | 3–5 | 6–9 | 10+
 */
object ActivityScorer {

    fun score(day: DayActivity): Int {
        val otherAttempts = (day.attempts - day.solved).coerceAtLeast(0)
        val trainingMinutes = day.trainingMs / (60 * 1000)
        return day.solved * 3 + otherAttempts + day.reviewsCompleted * 2 + trainingMinutes.toInt()
    }

    fun intensity(day: DayActivity): Int {
        val s = score(day)
        return when {
            s <= 0 -> 0
            s <= 2 -> 1
            s <= 5 -> 2
            s <= 9 -> 3
            else -> 4
        }
    }
}
