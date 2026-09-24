package com.ojnexus.core.database.dao

import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.ojnexus.core.database.entity.TrainingSessionEntity
import com.ojnexus.core.database.entity.TrainingSessionProblemEntity
import com.ojnexus.core.database.relation.SessionWithProblemsPojo
import kotlinx.coroutines.flow.Flow

/** Aggregated attempt progress for one problem inside a persisted training-session window. */
data class SessionProblemProgressRow(
    @ColumnInfo(name = "problem_id") val problemId: Long,
    val judge: String,
    @ColumnInfo(name = "external_id") val externalId: String,
    val title: String,
    val difficulty: Int?,
    val attempts: Int,
    val solved: Boolean,
    @ColumnInfo(name = "latest_verdict") val latestVerdict: String?,
    @ColumnInfo(name = "in_review") val inReview: Boolean,
)

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: TrainingSessionEntity): Long

    @Update
    suspend fun update(session: TrainingSessionEntity)

    @Query("SELECT * FROM training_sessions WHERE state IN ('RUNNING', 'PAUSED')")
    fun observeActive(): Flow<List<TrainingSessionEntity>>

    @Query("SELECT * FROM training_sessions WHERE id = :id")
    fun observeById(id: Long): Flow<TrainingSessionEntity?>

    @Query("SELECT * FROM training_sessions WHERE id = :id")
    suspend fun findById(id: Long): TrainingSessionEntity?

    @Transaction
    @Query(
        "SELECT * FROM training_sessions WHERE state IN ('FINISHED', 'CANCELLED') " +
            "ORDER BY started_at DESC LIMIT :limit",
    )
    fun observeHistory(limit: Int): Flow<List<SessionWithProblemsPojo>>

    @Query("SELECT COUNT(*) FROM training_sessions WHERE state IN ('RUNNING', 'PAUSED')")
    suspend fun countActive(): Int

    @Query("SELECT COUNT(*) FROM training_sessions")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM training_session_problems WHERE session_id = :sessionId")
    fun observeSessionProblemCount(sessionId: Long): Flow<Int>

    @Query(
        """
        SELECT p.id AS problem_id,
               p.judge AS judge,
               p.external_id AS external_id,
               p.title AS title,
               p.difficulty AS difficulty,
               COUNT(a.id) AS attempts,
               CASE WHEN MAX(CASE WHEN a.verdict = 'AC' THEN 1 ELSE 0 END) = 1
                    THEN 1 ELSE 0 END AS solved,
               (
                   SELECT a2.verdict
                   FROM attempts a2
                   WHERE a2.problem_id = p.id
                     AND a2.timestamp >= session.started_at
                     AND (session.finished_at IS NULL OR a2.timestamp <= session.finished_at)
                   ORDER BY a2.timestamp DESC, a2.id DESC
                   LIMIT 1
               ) AS latest_verdict,
               EXISTS (
                   SELECT 1 FROM reviews r WHERE r.problem_id = p.id
               ) AS in_review
        FROM training_session_problems link
        JOIN training_sessions session ON session.id = link.session_id
        JOIN problems p ON p.id = link.problem_id
        LEFT JOIN attempts a ON a.problem_id = p.id
            AND a.timestamp >= session.started_at
            AND (session.finished_at IS NULL OR a.timestamp <= session.finished_at)
        WHERE link.session_id = :sessionId
        GROUP BY p.id, p.judge, p.external_id, p.title, p.difficulty
        ORDER BY link.problem_id ASC
        """,
    )
    fun observeSessionProblemProgress(sessionId: Long): Flow<List<SessionProblemProgressRow>>

    @Insert
    suspend fun insertSessionProblem(link: TrainingSessionProblemEntity)

    @Update
    suspend fun updateSessionProblem(link: TrainingSessionProblemEntity)

    @Query(
        "SELECT * FROM training_session_problems WHERE session_id = :sessionId",
    )
    suspend fun sessionProblems(sessionId: Long): List<TrainingSessionProblemEntity>

    @Query("DELETE FROM training_session_problems WHERE session_id = :sessionId AND problem_id = :problemId")
    suspend fun deleteSessionProblem(sessionId: Long, problemId: Long)

    @Transaction
    @Query("SELECT * FROM training_sessions WHERE id = :id")
    fun observeSessionWithProblems(id: Long): Flow<SessionWithProblemsPojo?>
}
