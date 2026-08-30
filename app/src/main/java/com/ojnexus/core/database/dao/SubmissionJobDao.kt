package com.ojnexus.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ojnexus.core.database.entity.SubmissionJobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubmissionJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: SubmissionJobEntity): Long

    @Update
    suspend fun update(job: SubmissionJobEntity)

    @Query("SELECT * FROM submission_jobs WHERE request_id = :requestId LIMIT 1")
    suspend fun findByRequestId(requestId: String): SubmissionJobEntity?

    @Query("SELECT * FROM submission_jobs WHERE judge = :judge AND pid = :pid ORDER BY updated_at DESC LIMIT 1")
    suspend fun findLatestByProblem(judge: String, pid: String): SubmissionJobEntity?

    @Query("SELECT * FROM submission_jobs ORDER BY updated_at DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SubmissionJobEntity>>
}
