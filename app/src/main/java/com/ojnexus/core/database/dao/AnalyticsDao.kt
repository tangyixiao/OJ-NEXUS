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
data class FirstTryAcRow(val attemptedProblems: Int, val firstTryAc: Int)
data class TagPerformanceRow(
    val tag: String,
    val attempts: Int,
    val acCount: Int,
    val problemCount: Int,
)

@Dao
interface AnalyticsDao {

    @Query(
        "SELECT COUNT(DISTINCT a.problem_id) AS attemptedProblems, " +
            "COUNT(DISTINCT CASE WHEN a.verdict = 'AC' AND a.id = (" +
            "SELECT a2.id FROM attempts a2 WHERE a2.problem_id = a.problem_id " +
            "ORDER BY a2.timestamp ASC, a2.id ASC LIMIT 1) " +
            "THEN a.problem_id END) AS firstTryAc FROM attempts a",
    )
    fun observeFirstTryAc(): Flow<FirstTryAcRow>

    @Query(
        "SELECT t.name AS tag, COUNT(a.id) AS attempts, " +
            "COUNT(CASE WHEN a.verdict = 'AC' THEN 1 END) AS acCount, " +
            "COUNT(DISTINCT p.id) AS problemCount " +
            "FROM problem_tags t " +
            "JOIN problem_tag_cross_ref x ON x.tag_id = t.id " +
            "JOIN problems p ON p.id = x.problem_id " +
            "JOIN attempts a ON a.problem_id = p.id " +
            "GROUP BY t.id, t.name ORDER BY attempts DESC, t.name ASC",
    )
    fun observeTagPerformance(): Flow<List<TagPerformanceRow>>

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
