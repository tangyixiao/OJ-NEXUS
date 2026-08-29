package com.ojnexus.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StreakCalculatorTest {

    private fun day(epochDay: Long, solved: Int = 0, attempts: Int = 0, reviews: Int = 0, trainingMs: Long = 0) =
        DayActivity(epochDay, solved, attempts, reviews, trainingMs)

    private fun activeSet(vararg days: Long) = days.toSet()

    @Test
    fun `empty data has zero streaks`() {
        assertEquals(0, StreakCalculator.currentStreak(emptySet(), todayEpochDay = 100))
        assertEquals(0, StreakCalculator.longestStreak(emptySet()))
    }

    @Test
    fun `today active counts today`() {
        assertEquals(3, StreakCalculator.currentStreak(activeSet(98, 99, 100), todayEpochDay = 100))
    }

    @Test
    fun `today inactive but yesterday active keeps streak alive`() {
        assertEquals(2, StreakCalculator.currentStreak(activeSet(98, 99), todayEpochDay = 100))
    }

    @Test
    fun `gap of one day breaks the streak`() {
        // 98, 99 active; 100 missing; today = 101 -> yesterday inactive -> streak 0.
        assertEquals(0, StreakCalculator.currentStreak(activeSet(98, 99), todayEpochDay = 101))
    }

    @Test
    fun `month and year boundaries are just consecutive epoch days`() {
        // 2026 is not a leap year: 02-27, 02-28, 03-01 are consecutive epoch days.
        val feb27 = java.time.LocalDate.of(2026, 2, 27).toEpochDay()
        val mar1 = java.time.LocalDate.of(2026, 3, 1).toEpochDay()
        val days = setOf(feb27, feb27 + 1, mar1)
        assertEquals(3, StreakCalculator.currentStreak(days, todayEpochDay = mar1))
        assertEquals(3, StreakCalculator.longestStreak(days))

        val dec31 = java.time.LocalDate.of(2025, 12, 31).toEpochDay()
        val jan1 = java.time.LocalDate.of(2026, 1, 1).toEpochDay()
        assertEquals(2, StreakCalculator.currentStreak(setOf(dec31, jan1), todayEpochDay = jan1))
    }

    @Test
    fun `longest streak ignores gaps in the middle`() {
        val days = activeSet(1, 2, 3, 10, 11, 12, 13)
        assertEquals(4, StreakCalculator.longestStreak(days))
    }

    @Test
    fun `unsorted input is handled`() {
        assertEquals(3, StreakCalculator.longestStreak(activeSet(5, 4, 3)))
    }

    @Test
    fun `activity policy thresholds define active days`() {
        assertEquals(true, ActivityPolicy.isActive(day(1, solved = 1)))
        assertEquals(true, ActivityPolicy.isActive(day(1, reviews = 1)))
        assertEquals(true, ActivityPolicy.isActive(day(1, trainingMs = ActivityPolicy.TRAINING_MIN_MS)))
        assertEquals(false, ActivityPolicy.isActive(day(1, attempts = 5, trainingMs = ActivityPolicy.TRAINING_MIN_MS - 1)))
        assertEquals(false, ActivityPolicy.isActive(day(1)))
    }

    @Test
    fun `activeDayIndexes applies the policy`() {
        val days = listOf(
            day(10, solved = 1),
            day(11, attempts = 3), // attempts alone are not active
            day(12, reviews = 2),
        )
        assertEquals(setOf(10L, 12L), StreakCalculator.activeDayIndexes(days))
    }
}
