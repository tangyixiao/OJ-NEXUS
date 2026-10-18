package com.ojnexus.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ojnexus.core.database.entity.SyncLogOperationEntity
import kotlinx.coroutines.flow.Flow

/**
 * Phase 118 (RFC 0001 §3) — device-local sync op-log ledger. Never carries credentials; the
 * tables have no reference to judge_accounts by design.
 */
@Dao
interface SyncLogOperationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(operation: SyncLogOperationEntity)

    @Query("SELECT * FROM sync_log_operations WHERE opId = :opId")
    suspend fun findById(opId: String): SyncLogOperationEntity?

    @Query("SELECT * FROM sync_log_operations ORDER BY lamport DESC, deviceId DESC LIMIT :limit")
    suspend fun recent(limit: Int = 50): List<SyncLogOperationEntity>

    @Query("SELECT * FROM sync_log_operations WHERE status = :status ORDER BY lamport ASC")
    suspend fun findByStatus(status: String): List<SyncLogOperationEntity>

    @Query("SELECT * FROM sync_log_operations ORDER BY lamport DESC, deviceId DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<SyncLogOperationEntity>>

    @Query("UPDATE sync_log_operations SET status = :status WHERE opId = :opId")
    suspend fun updateStatus(opId: String, status: String)

    @Query("SELECT MAX(lamport) FROM sync_log_operations")
    suspend fun maxLamport(): Long?

    @Query("DELETE FROM sync_log_operations WHERE opId = :opId")
    suspend fun deleteById(opId: String)
}
