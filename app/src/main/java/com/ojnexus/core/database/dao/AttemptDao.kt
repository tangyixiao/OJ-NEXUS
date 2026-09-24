package com.ojnexus.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.ojnexus.core.database.entity.AttemptEntity
import com.ojnexus.core.database.entity.ProblemEntity
import kotlinx.coroutines.flow.Flow

/** Attempt row joined with its problem for activity feeds. */
data class AttemptWithProblemPojo(
    @Embedded val attempt: AttemptEntity,
    @Relation(entity = ProblemEntity::class, parentColumn = "problem_id", entityColumn = "id")
    val problem: ProblemEntity?,
)

@Dao
interface AttemptDao {

    @Insert
    suspend fun insert(attempt: AttemptEntity): Long

    @Query("DELETE FROM attempts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM attempts WHERE problem_id = :problemId ORDER BY timestamp DESC")
    fun observeByProblem(problemId: Long): Flow<List<AttemptEntity>>

    @Query("SELECT * FROM attempts WHERE problem_id = :problemId ORDER BY timestamp ASC")
    suspend fun findByProblem(problemId: Long): List<AttemptEntity>

    @Transaction
    @Query("SELECT * FROM attempts ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AttemptWithProblemPojo>>

    /** Lookup by the remote idempotency key; null when this submission was never synced. */
    @Query(
        "SELECT * FROM attempts WHERE source_judge = :sourceJudge AND external_submission_id = :externalId LIMIT 1",
    )
    suspend fun findByExternalId(sourceJudge: String, externalId: String): AttemptEntity?

    @Query(
        "SELECT external_submission_id FROM attempts WHERE source_judge = :sourceJudge " +
            "AND external_submission_id IN (:externalIds)",
    )
    suspend fun findExistingExternalIds(sourceJudge: String, externalIds: List<String>): List<String>

    @Update
    suspend fun update(attempt: AttemptEntity)
}
