package com.ojnexus.core.data.repository

import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.dao.DailyAttemptRow
import com.ojnexus.core.database.dao.DailyReviewRow
import com.ojnexus.core.database.dao.DailyTrainingRow
import com.ojnexus.core.database.dao.VerdictCountRow
import com.ojnexus.core.domain.DayActivity
import com.ojnexus.core.domain.StreakCalculator
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.RecentAttempt
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

    fun observeJudgeAttemptCounts(): Flow<List<Pair<JudgeId, Int>>> =
        analyticsDao.observeAttemptCountsByJudge().map { rows ->
            rows.mapNotNull { row -> JudgeId.fromId(row.judge)?.let { it to row.count } }
        }

    fun observeDifficultyCountsByJudge(): Flow<Map<JudgeId, List<Pair<Int?, Int>>>> =
        analyticsDao.observeDifficultyCountsByJudge().map { rows ->
            rows.mapNotNull { row ->
                JudgeId.fromId(row.judge)?.let { Triple(it, row.difficulty, row.count) }
            }.groupBy({ it.first }, { it.second to it.third })
        }

    fun observeTotals(): Flow<Totals> = combine(
        analyticsDao.observeAttemptTotal(),
        analyticsDao.observeAcTotal(),
        analyticsDao.observeProblemTotal(),
        analyticsDao.observeSolvedTotal(),
    ) { attempts, ac, problems, solved ->
        Totals(attempts = attempts, ac = ac, problems = problems, solved = solved)
    }

    /** Current/longest streak over the combined activity window, plus active-day count. */
    fun observeStreaks(days: Int = 365): Flow<Streaks> =
        observeDailyActivity(days).map { daily ->
            val active = StreakCalculator.activeDayIndexes(daily)
            Streaks(
                current = StreakCalculator.currentStreak(active, clock.dayIndex()),
                longest = StreakCalculator.longestStreak(active),
                activeDays = active.size,
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

    /** Recent attempts across all problems, newest first. */
    fun observeRecentAttempts(limit: Int): Flow<List<RecentAttempt>> =
        database.attemptDao().observeRecent(limit).map { rows ->
            rows.mapNotNull { row ->
                val problem = row.problem ?: return@mapNotNull null
                RecentAttempt(
                    problemId = problem.id,
                    judge = JudgeId.fromId(problem.judge) ?: JudgeId.LOCAL,
                    problemCode = problem.externalId,
                    problemTitle = problem.title,
                    verdict = Verdict.fromRaw(row.attempt.verdict),
                    timestamp = row.attempt.timestamp,
                )
            }
        }

    // --- Judge connection data (Phase 2) ---

    fun observeJudgeAccounts(): Flow<List<com.ojnexus.core.database.entity.JudgeAccountEntity>> =
        database.judgeAccountDao().observeAll()

    fun observeJudgeProfile(judgeId: String) = database.judgeProfileDao().observeByJudge(judgeId)

    fun observeRatingChanges(judgeId: String) = database.ratingChangeDao().observeByJudge(judgeId)
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

data class Streaks(val current: Int, val longest: Int, val activeDays: Int)
