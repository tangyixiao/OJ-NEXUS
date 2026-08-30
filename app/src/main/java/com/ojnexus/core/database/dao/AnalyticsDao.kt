package com.ojnexus.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Aggregation rows for analytics. Day keys are local epoch days, written at record time. */
data class VerdictCountRow(val verdict: String, val count: Int)

data class DailyAttemptRow(
    val dayIndex: Long,
    val attempts: Int,
    val acCount: Int,
)

data class DailyTrainingRow(val dayIndex: Long, val totalMs: Long)

data class DailyReviewRow(val dayIndex: Long, val count: Int)

data class DifficultyCountRow(val difficulty: Int?, val count: Int)
data class JudgeAttemptCountRow(val judge: String, val count: Int)
data class JudgeDifficultyCountRow(val judge: String, val difficulty: Int?, val count: Int)

@Dao
interface AnalyticsDao {

    @Query(
        "SELECT p.judge AS judge, COUNT(*) AS count FROM attempts a " +
            "JOIN problems p ON p.id = a.problem_id GROUP BY p.judge",
    )
    fun observeAttemptCountsByJudge(): Flow<List<JudgeAttemptCountRow>>

    @Query(
        "SELECT judge, difficulty, COUNT(*) AS count FROM problems " +
            "WHERE solved = 1 GROUP BY judge, difficulty",
    )
    fun observeDifficultyCountsByJudge(): Flow<List<JudgeDifficultyCountRow>>

    @Query("SELECT verdict, COUNT(*) AS count FROM attempts GROUP BY verdict")
    fun observeVerdictCounts(): Flow<List<VerdictCountRow>>

    @Query(
        "SELECT day_index AS dayIndex, COUNT(*) AS attempts, " +
            "SUM(CASE WHEN verdict = 'AC' THEN 1 ELSE 0 END) AS acCount " +
            "FROM attempts WHERE day_index >= :fromDay GROUP BY day_index",
    )
    fun observeDailyAttempts(fromDay: Long): Flow<List<DailyAttemptRow>>

    @Query(
        "SELECT day_index AS dayIndex, SUM(finished_at - started_at - total_paused_ms) AS totalMs " +
            "FROM training_sessions WHERE state = 'FINISHED' AND day_index >= :fromDay " +
            "GROUP BY day_index",
    )
    fun observeDailyTraining(fromDay: Long): Flow<List<DailyTrainingRow>>

    @Query(
        "SELECT day_index AS dayIndex, COUNT(*) AS count FROM review_log " +
            // SKIP is explicitly not a completion (see ReviewScheduler) — exclude from activity.
            "WHERE day_index >= :fromDay AND result != 'SKIP' GROUP BY day_index",
    )
    fun observeDailyReviews(fromDay: Long): Flow<List<DailyReviewRow>>

    @Query("SELECT COUNT(*) FROM attempts")
    fun observeAttemptTotal(): Flow<Int>

    @Query("SELECT COUNT(*) FROM attempts WHERE verdict = 'AC'")
    fun observeAcTotal(): Flow<Int>

    @Query("SELECT COUNT(*) FROM problems")
    fun observeProblemTotal(): Flow<Int>

    @Query("SELECT COUNT(*) FROM problems WHERE solved = 1")
    fun observeSolvedTotal(): Flow<Int>

    @Query(
        "SELECT difficulty, COUNT(*) AS count FROM problems WHERE solved = 1 GROUP BY difficulty",
    )
    fun observeSolvedDifficultyCounts(): Flow<List<DifficultyCountRow>>

    @Query(
        "SELECT difficulty, COUNT(*) AS count FROM problems GROUP BY difficulty",
    )
    fun observeAllDifficultyCounts(): Flow<List<DifficultyCountRow>>
}
