package com.ojnexus.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ojnexus.core.database.entity.SyncLogOpEntity

/** Row-level operations belonging to one [SyncLogOperationEntity] op batch (Phase 118). */
@Dao
interface SyncLogOpDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(ops: List<SyncLogOpEntity>)

    @Query("SELECT * FROM sync_log_ops WHERE opId = :opId ORDER BY id ASC")
    suspend fun findByOpId(opId: String): List<SyncLogOpEntity>

    @Query("DELETE FROM sync_log_ops WHERE opId = :opId")
    suspend fun deleteByOpId(opId: String)
}
