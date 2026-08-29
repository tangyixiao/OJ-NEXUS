package com.ojnexus.core.domain

/**
 * One day's aggregated activity. `dayIndex` is the local calendar day as epoch day —
 * computed once when the underlying record is written, so streaks and heatmap buckets never
 * depend on query-time timezone handling.
 */
data class DayActivity(
    val dayIndex: Long,
    val solved: Int,
    val attempts: Int,
    val reviewsCompleted: Int,
    val trainingMs: Long,
)

/**
 * Central policy for what counts as an "active day" (streak definition).
 * A day is active when at least one condition holds:
 *  - at least [SOLVED_THRESHOLD] problems solved
 *  - at least [REVIEWS_THRESHOLD] reviews completed
 *  - at least [TRAINING_MIN_MS] of recorded training time
 */
object ActivityPolicy {
    const val SOLVED_THRESHOLD = 1
    const val REVIEWS_THRESHOLD = 1
    const val TRAINING_MIN_MS = 20L * 60 * 1000

    fun isActive(day: DayActivity): Boolean =
        day.solved >= SOLVED_THRESHOLD ||
            day.reviewsCompleted >= REVIEWS_THRESHOLD ||
            day.trainingMs >= TRAINING_MIN_MS
}

/**
 * Deterministic streak computation over per-day aggregates. Pure Kotlin; input days may be
 * sparse (only days with any activity).
 */
object StreakCalculator {

    /** Streak ending today (or yesterday, if today is not yet active). Empty-safe. */
    fun currentStreak(activeDayIndexes: Set<Long>, todayEpochDay: Long): Int {
        var streak = 0
        var cursor = todayEpochDay
        // Today not active yet is allowed — the streak can still be alive from yesterday.
        if (todayEpochDay !in activeDayIndexes) {
            cursor = todayEpochDay - 1
        }
        while (cursor in activeDayIndexes) {
            streak++
            cursor--
        }
        return streak
    }

    fun longestStreak(activeDayIndexes: Set<Long>): Int {
        if (activeDayIndexes.isEmpty()) return 0
        var longest = 0
        var run = 0
        var previous: Long? = null
        for (day in activeDayIndexes.sorted()) {
            run = if (previous != null && day == previous + 1) run + 1 else 1
            if (run > longest) longest = run
            previous = day
        }
        return longest
    }

    fun activeDayIndexes(days: List<DayActivity>): Set<Long> =
        days.filter { ActivityPolicy.isActive(it) }.map { it.dayIndex }.toSet()
}
