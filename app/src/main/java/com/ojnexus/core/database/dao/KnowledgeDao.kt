package com.ojnexus.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ojnexus.core.database.entity.ProblemKnowledgeEntity
import kotlinx.coroutines.flow.Flow

data class KnowledgePerformanceRow(
    val area: String,
    val attemptedProblems: Int,
    val solvedProblems: Int,
    val attempts: Int,
    val failures: Int,
    /**
     * Local days since the newest solved problem in this area, or null when no linked problem has
     * been solved. Decay reads this; the mastery policy never touches a clock.
     */
    val daysSinceLastSolved: Int?,
)

@Dao
interface KnowledgeDao {
    @Query("SELECT knowledge_area FROM problem_knowledge WHERE problem_id = :problemId ORDER BY knowledge_area")
    fun observeForProblem(problemId: Long): Flow<List<String>>

    @Query("SELECT knowledge_area FROM problem_knowledge WHERE problem_id = :problemId ORDER BY knowledge_area")
    suspend fun findForProblem(problemId: Long): List<String>

    @Upsert
    suspend fun upsert(relation: ProblemKnowledgeEntity)

    @Upsert
    suspend fun upsertAll(relations: List<ProblemKnowledgeEntity>)

    @Query(
        "DELETE FROM problem_knowledge WHERE problem_id = :problemId AND knowledge_area = :area",
    )
    suspend fun delete(problemId: Long, area: String)

    @Query(
        "SELECT k.knowledge_area AS area, COUNT(DISTINCT p.id) AS attemptedProblems, " +
            "COUNT(DISTINCT CASE WHEN p.solved = 1 THEN p.id END) AS solvedProblems, " +
            "(SELECT COUNT(*) FROM attempts a JOIN problem_knowledge k2 " +
            "ON k2.problem_id = a.problem_id WHERE k2.knowledge_area = k.knowledge_area) AS attempts, " +
            "(SELECT COUNT(*) FROM failure_entries f JOIN problem_knowledge k3 " +
            "ON k3.problem_id = f.problem_id WHERE k3.knowledge_area = k.knowledge_area) AS failures, " +
            "(SELECT MAX(CAST(:todayEpochDay - p2.first_solved_at / 86400000 AS INTEGER)) " +
            "FROM problem_knowledge k4 JOIN problems p2 ON p2.id = k4.problem_id " +
            "WHERE k4.knowledge_area = k.knowledge_area AND p2.solved = 1) AS daysSinceLastSolved " +
            "FROM problem_knowledge k JOIN problems p ON p.id = k.problem_id " +
            "WHERE p.attempt_count > 0 GROUP BY k.knowledge_area ORDER BY k.knowledge_area",
    )
    fun observePerformance(todayEpochDay: Long): Flow<List<KnowledgePerformanceRow>>
}
