package com.ojnexus.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.ojnexus.core.database.entity.TrainingSessionEntity
import com.ojnexus.core.database.entity.TrainingSessionProblemEntity
import com.ojnexus.core.database.relation.SessionWithProblemsPojo
import kotlinx.coroutines.flow.Flow

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
