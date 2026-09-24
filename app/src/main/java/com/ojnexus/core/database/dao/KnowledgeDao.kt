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
)

@Dao
interface KnowledgeDao {
    @Query("SELECT knowledge_area FROM problem_knowledge WHERE problem_id = :problemId ORDER BY knowledge_area")
    fun observeForProblem(problemId: Long): Flow<List<String>>

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
            "ON k3.problem_id = f.problem_id WHERE k3.knowledge_area = k.knowledge_area) AS failures " +
            "FROM problem_knowledge k JOIN problems p ON p.id = k.problem_id " +
            "WHERE p.attempt_count > 0 GROUP BY k.knowledge_area ORDER BY k.knowledge_area",
    )
    fun observePerformance(): Flow<List<KnowledgePerformanceRow>>
}
