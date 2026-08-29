package com.ojnexus.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ojnexus.core.database.entity.AttemptEntity
import kotlinx.coroutines.flow.Flow

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
}
