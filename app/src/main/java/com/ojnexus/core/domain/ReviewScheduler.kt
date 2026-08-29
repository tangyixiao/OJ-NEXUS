package com.ojnexus.core.domain

import com.ojnexus.core.model.ReviewResult
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * One scheduled review decision produced by [ReviewScheduler].
 *
 * `dueAt` is UTC epoch millis (stable storage form); `dueDayIndex` is the local calendar day
 * the review is due (epoch day), precomputed with the caller's zone so day bucketing never
 * depends on where/when the query runs.
 */
data class ScheduledReview(
    val stage: Int,
    val dueAt: Long,
    val dueDayIndex: Long,
    val intervalDays: Long,
)

/**
 * Deterministic spaced-review ladder. Pure Kotlin, no Android dependencies.
 *
 * Intervals (days): 1, 3, 7, 21, 45, 90.
 * - PASS  → advance one stage (capped at the last interval, which then repeats).
 * - HARD  → stay on the stage, use half the interval (min 1 day).
 * - FAIL  → drop one stage (min 0) and re-test the next day.
 * - SKIP  → stay on the stage, use the full interval; not counted as a completion.
 * - RESET → back to stage 0.
 */
object ReviewScheduler {

    val INTERVAL_DAYS = longArrayOf(1, 3, 7, 21, 45, 90)

    val MAX_STAGE: Int = INTERVAL_DAYS.lastIndex

    fun initialSchedule(now: Instant, zone: ZoneId): ScheduledReview =
        schedule(stage = 0, intervalDays = INTERVAL_DAYS[0], now = now, zone = zone)

    fun next(
        stage: Int,
        result: ReviewResult,
        now: Instant,
        zone: ZoneId,
    ): ScheduledReview {
        val currentStage = stage.coerceIn(0, MAX_STAGE)
        return when (result) {
            ReviewResult.PASS -> {
                val nextStage = (currentStage + 1).coerceAtMost(MAX_STAGE)
                schedule(nextStage, INTERVAL_DAYS[nextStage], now, zone)
            }

            ReviewResult.HARD -> {
                val halfInterval = (INTERVAL_DAYS[currentStage] / 2).coerceAtLeast(1)
                schedule(currentStage, halfInterval, now, zone)
            }

            ReviewResult.FAIL -> {
                val nextStage = (currentStage - 1).coerceAtLeast(0)
                // A failed recall is re-tested the next day regardless of the new stage.
                schedule(nextStage, 1, now, zone)
            }

            ReviewResult.SKIP ->
                schedule(currentStage, INTERVAL_DAYS[currentStage], now, zone)
        }
    }

    fun reset(now: Instant, zone: ZoneId): ScheduledReview =
        schedule(stage = 0, intervalDays = INTERVAL_DAYS[0], now = now, zone = zone)

    private fun schedule(
        stage: Int,
        intervalDays: Long,
        now: Instant,
        zone: ZoneId,
    ): ScheduledReview {
        // Calendar-day arithmetic in the user's zone: a "day" is the local calendar day,
        // not a fixed 86 400 s block. Around DST transitions a local day is 23 h or 25 h;
        // plusDays() keeps the local time-of-day stable and resolves gaps per java.time.
        val due: ZonedDateTime = now.atZone(zone).plusDays(intervalDays)
        return ScheduledReview(
            stage = stage,
            dueAt = due.toInstant().toEpochMilli(),
            dueDayIndex = due.toLocalDate().toEpochDay(),
            intervalDays = intervalDays,
        )
    }
}
