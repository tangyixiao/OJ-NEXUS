package com.ojnexus.core.domain

import com.ojnexus.core.model.ReviewResult
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewSchedulerTest {

    private val zone = ZoneId.of("UTC")
    private val now: Instant = Instant.parse("2026-08-29T12:00:00Z")

    private fun days(n: Long) = n * 24 * 60 * 60

    private fun intervalOf(scheduled: ScheduledReview): Long =
        (scheduled.dueAt - now.toEpochMilli()) / 1000 / days(1)

    @Test
    fun `initial schedule starts at stage 0 due in 1 day`() {
        val scheduled = ReviewScheduler.initialSchedule(now, zone)
        assertEquals(0, scheduled.stage)
        assertEquals(1L, intervalOf(scheduled))
    }

    @Test
    fun `pass advances through the full ladder`() {
        var stage = 0
        val expectedIntervals = listOf(3L, 7L, 21L, 45L, 90L, 90L)
        for (expected in expectedIntervals) {
            val scheduled = ReviewScheduler.next(stage, ReviewResult.PASS, now, zone)
            assertEquals(expected, intervalOf(scheduled))
            stage = scheduled.stage
        }
        // Ladder caps at the last stage and repeats its interval.
        assertEquals(ReviewScheduler.MAX_STAGE, stage)
    }

    @Test
    fun `hard stays on stage with half the interval`() {
        // Stage 1 = 3 days -> hard = 1.5 -> coerced to minimum 1 day.
        val scheduled = ReviewScheduler.next(1, ReviewResult.HARD, now, zone)
        assertEquals(1, scheduled.stage)
        assertEquals(1L, intervalOf(scheduled))

        // Stage 3 = 21 days -> hard = 10.5 -> floors to 10 whole days.
        val later = ReviewScheduler.next(3, ReviewResult.HARD, now, zone)
        assertEquals(3, later.stage)
        assertEquals(10L, intervalOf(later))
    }

    @Test
    fun `fail drops one stage and re-tests the next day`() {
        val scheduled = ReviewScheduler.next(3, ReviewResult.FAIL, now, zone)
        assertEquals(2, scheduled.stage)
        assertEquals(1L, intervalOf(scheduled))
    }

    @Test
    fun `fail at stage 0 stays at stage 0`() {
        val scheduled = ReviewScheduler.next(0, ReviewResult.FAIL, now, zone)
        assertEquals(0, scheduled.stage)
        assertEquals(1L, intervalOf(scheduled))
    }

    @Test
    fun `skip keeps the stage and schedules the same interval`() {
        val scheduled = ReviewScheduler.next(2, ReviewResult.SKIP, now, zone)
        assertEquals(2, scheduled.stage)
        assertEquals(7L, intervalOf(scheduled))
    }

    @Test
    fun `reset returns to stage 0`() {
        val scheduled = ReviewScheduler.reset(now, zone)
        assertEquals(0, scheduled.stage)
        assertEquals(1L, intervalOf(scheduled))
    }

    @Test
    fun `due day index uses the caller zone`() {
        // 2026-08-29T12:00Z + 1 day = 2026-08-30T12:00Z -> epoch day of 2026-08-30 in UTC.
        val scheduled = ReviewScheduler.initialSchedule(now, zone)
        assertEquals(java.time.LocalDate.of(2026, 8, 30).toEpochDay(), scheduled.dueDayIndex)
    }

    @Test
    fun `due day index crosses the local date in eastern zones`() {
        // 2026-08-29T20:00Z + 1 day = 2026-08-30T20:00Z = 2026-08-31 06:00 in UTC+10.
        val brisbane = ZoneId.of("Australia/Brisbane")
        val evening: Instant = Instant.parse("2026-08-29T20:00:00Z")
        val scheduled = ReviewScheduler.initialSchedule(evening, brisbane)
        assertEquals(java.time.LocalDate.of(2026, 8, 31).toEpochDay(), scheduled.dueDayIndex)
        // The same instant in UTC is still the 30th.
        val utc = ReviewScheduler.initialSchedule(evening, zone)
        assertEquals(java.time.LocalDate.of(2026, 8, 30).toEpochDay(), utc.dueDayIndex)
    }

    @Test
    fun `out of range stage input is coerced`() {
        val scheduled = ReviewScheduler.next(99, ReviewResult.PASS, now, zone)
        assertEquals(ReviewScheduler.MAX_STAGE, scheduled.stage)
    }
}
