package com.ojnexus.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.ojnexus.core.database.entity.ReviewEntity
import com.ojnexus.core.database.entity.ReviewLogEntity
import com.ojnexus.core.database.relation.ReviewWithProblemPojo
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewDao {

    @Transaction
    @Query("SELECT * FROM reviews ORDER BY due_day_index ASC")
    fun observeQueue(): Flow<List<ReviewWithProblemPojo>>

    @Query("SELECT * FROM reviews WHERE problem_id = :problemId")
    fun observeByProblem(problemId: Long): Flow<ReviewEntity?>

    @Query("SELECT * FROM reviews WHERE problem_id = :problemId")
    suspend fun findByProblem(problemId: Long): ReviewEntity?

    @Upsert
    suspend fun upsert(review: ReviewEntity)

    @Query("DELETE FROM reviews WHERE problem_id = :problemId")
    suspend fun deleteByProblem(problemId: Long)

    @Insert
    suspend fun insertLog(entry: ReviewLogEntity): Long
}
