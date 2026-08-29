package com.ojnexus.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ojnexus.core.database.entity.SyncStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncStateDao {

    @Query("SELECT * FROM sync_states WHERE judge = :judge")
    fun observeByJudge(judge: String): Flow<SyncStateEntity?>

    @Query("SELECT * FROM sync_states WHERE judge = :judge")
    suspend fun findByJudge(judge: String): SyncStateEntity?

    @Upsert
    suspend fun upsert(state: SyncStateEntity)

    @Query("DELETE FROM sync_states WHERE judge = :judge")
    suspend fun deleteByJudge(judge: String)
}
