package com.ojnexus.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ojnexus.core.database.entity.ContestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContestDao {

    @Upsert
    suspend fun upsertAll(contests: List<ContestEntity>)

    @Query(
        "SELECT * FROM contests WHERE judge = :judge AND start_time_seconds IS NOT NULL " +
            "ORDER BY start_time_seconds ASC",
    )
    fun observeByJudge(judge: String): Flow<List<ContestEntity>>

    @Query("SELECT COUNT(*) FROM contests WHERE judge = :judge")
    suspend fun countByJudge(judge: String): Int

    @Query("DELETE FROM contests WHERE judge = :judge")
    suspend fun deleteByJudge(judge: String)
}
