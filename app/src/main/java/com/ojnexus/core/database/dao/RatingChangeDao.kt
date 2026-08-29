package com.ojnexus.core.database.dao

import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Dao
import com.ojnexus.core.database.entity.RatingChangeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RatingChangeDao {

    @Query("SELECT * FROM rating_changes WHERE judge = :judge ORDER BY rating_update_time_seconds ASC")
    fun observeByJudge(judge: String): Flow<List<RatingChangeEntity>>

    @Query("SELECT COUNT(*) FROM rating_changes WHERE judge = :judge")
    fun observeCount(judge: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM rating_changes WHERE judge = :judge")
    suspend fun countByJudge(judge: String): Int

    @Upsert
    suspend fun upsertAll(changes: List<RatingChangeEntity>)

    @Query("DELETE FROM rating_changes WHERE judge = :judge")
    suspend fun deleteByJudge(judge: String)
}
