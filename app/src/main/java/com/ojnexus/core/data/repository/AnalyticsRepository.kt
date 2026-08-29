package com.ojnexus.core.data.repository

import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.dao.DailyAttemptRow
import com.ojnexus.core.database.dao.DailyReviewRow
import com.ojnexus.core.database.dao.DailyTrainingRow
import com.ojnexus.core.database.dao.VerdictCountRow
import com.ojnexus.core.domain.DayActivity
import com.ojnexus.core.domain.StreakCalculator
import com.ojnexus.core.model.Verdict
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Aggregated analytics read models. All heavy grouping happens in SQL, never in memory. */
class AnalyticsRepository(
    private val database: OjNexusDatabase,
    private val clock: Clock,
) {
    private val analyticsDao = database.analyticsDao()

    /** Daily activity for the last [days] local days (missing days come back as zero rows). */
    fun observeDailyActivity(days: Int): Flow<List<DayActivity>> {
        val fromDay = clock.dayIndex() - (days - 1)
        return combine(
            analyticsDao.observeDailyAttempts(fromDay),
            analyticsDao.observeDailyReviews(fromDay),
            analyticsDao.observeDailyTraining(fromDay),
        ) { attempts, reviews, training ->
            mergeDailyActivity(days, attempts, reviews, training)
        }
    }

    fun observeVerdictCounts(): Flow<List<Pair<Verdict, Int>>> =
        analyticsDao.observeVerdictCounts().map { rows -> rows.map { it.toPair() } }

    fun observeDifficultyCounts(): Flow<List<Pair<Int?, Int>>> =
        analyticsDao.observeSolvedDifficultyCounts().map { rows -> rows.map { it.difficulty to it.count } }

    fun observeTotals(): Flow<Totals> = combine(
        analyticsDao.observeAttemptTotal(),
        analyticsDao.observeAcTotal(),
        analyticsDao.observeProblemTotal(),
        analyticsDao.observeSolvedTotal(),
    ) { attempts, ac, problems, solved ->
        Totals(attempts = attempts, ac = ac, problems = problems, solved = solved)
    }

    /** Current and longest streak over the combined activity window. */
    fun observeStreaks(days: Int = 365): Flow<Streaks> =
        observeDailyActivity(days).map { daily ->
            val active = StreakCalculator.activeDayIndexes(daily)
            Streaks(
                current = StreakCalculator.currentStreak(active, clock.dayIndex()),
                longest = StreakCalculator.longestStreak(active),
            )
        }

    private fun mergeDailyActivity(
        days: Int,
        attempts: List<DailyAttemptRow>,
        reviews: List<DailyReviewRow>,
        training: List<DailyTrainingRow>,
    ): List<DayActivity> {
        val today = clock.dayIndex()
        val attemptByDay = attempts.associateBy { it.dayIndex }
        val reviewByDay = reviews.associateBy { it.dayIndex }
        val trainingByDay = training.associateBy { it.dayIndex }
        return (0 until days).map { offset ->
            val day = today - offset
            val a = attemptByDay[day]
            val r = reviewByDay[day]
            val t = trainingByDay[day]
            DayActivity(
                dayIndex = day,
                solved = a?.acCount ?: 0,
                attempts = a?.attempts ?: 0,
                reviewsCompleted = r?.count ?: 0,
                trainingMs = t?.totalMs ?: 0L,
            )
        }.sortedBy { it.dayIndex }
    }
}

private fun VerdictCountRow.toPair(): Pair<Verdict, Int> =
    (Verdict.entries.firstOrNull { it.name == verdict } ?: Verdict.OTHER) to count

data class Totals(
    val attempts: Int,
    val ac: Int,
    val problems: Int,
    val solved: Int,
) {
    val attemptAcRatio: Float get() = if (attempts == 0) 0f else ac.toFloat() / attempts
}

data class Streaks(val current: Int, val longest: Int)
