package com.ojnexus.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ojnexus.core.database.entity.FailureEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FailureDao {

    @Insert
    suspend fun insert(failure: FailureEntryEntity): Long

    @Update
    suspend fun update(failure: FailureEntryEntity)

    @Query("DELETE FROM failure_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM failure_entries WHERE id = :id")
    suspend fun findById(id: Long): FailureEntryEntity?

    @Query("SELECT COUNT(*) FROM failure_entries")
    suspend fun count(): Int

    @Query("SELECT * FROM failure_entries WHERE problem_id = :problemId ORDER BY created_at ASC")
    fun observeByProblem(problemId: Long): Flow<List<FailureEntryEntity>>
}
