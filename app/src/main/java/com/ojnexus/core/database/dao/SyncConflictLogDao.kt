package com.ojnexus.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ojnexus.core.database.entity.SyncConflictLogEntity

/**
 * Phase 118 (RFC 0001 §3) — every deterministic merge resolution lands here so outcomes are
 * inspectable, never silent.
 */
@Dao
interface SyncConflictLogDao {

    @Insert
    suspend fun insert(conflict: SyncConflictLogEntity): Long

    @Query("SELECT * FROM sync_conflict_log ORDER BY resolvedAtEpochMs DESC LIMIT :limit")
    suspend fun recent(limit: Int = 50): List<SyncConflictLogEntity>

    @Query("SELECT COUNT(*) FROM sync_conflict_log")
    suspend fun count(): Int

    @Query("DELETE FROM sync_conflict_log")
    suspend fun clear()
}
