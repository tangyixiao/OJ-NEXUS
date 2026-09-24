package com.ojnexus.core.database.dao

import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Dao
import com.ojnexus.core.database.entity.SyncOperationEntity
import com.ojnexus.core.database.entity.SyncOperationModuleEntity
import kotlinx.coroutines.flow.Flow

const val SYNC_OPERATION_HISTORY_LIMIT = 20

data class SyncOperationWithModules(
    @Embedded val operation: SyncOperationEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "operation_id",
    )
    val modules: List<SyncOperationModuleEntity>,
)

@Dao
interface SyncOperationDao {
    @Insert
    suspend fun openOperation(operation: SyncOperationEntity): Long

    /** Replaces the same operation/stage row so a retried local write remains idempotent. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun appendCommittedModule(module: SyncOperationModuleEntity): Long

    @Query(
        "UPDATE sync_operations SET status = :status, finished_at = :finishedAt, " +
            "last_error_type = :errorType, current_stage = NULL WHERE id = :operationId",
    )
    suspend fun closeOperation(
        operationId: Long,
        status: String,
        finishedAt: Long,
        errorType: String? = null,
    )

    @Query("UPDATE sync_operations SET current_stage = :stage WHERE id = :operationId")
    suspend fun markCurrentStage(operationId: Long, stage: String?)

    @Transaction
    @Query(
        "SELECT * FROM sync_operations WHERE judge = :judge " +
            "ORDER BY started_at DESC, id DESC LIMIT :limit",
    )
    fun observeRecentByJudge(judge: String, limit: Int): Flow<List<SyncOperationWithModules>>

    @Transaction
    @Query("SELECT * FROM sync_operations WHERE id = :operationId")
    suspend fun findById(operationId: Long): SyncOperationWithModules?

    @Query("DELETE FROM sync_operations WHERE id = :operationId")
    suspend fun deleteById(operationId: Long)

    /** Keep only the newest completed rows for this judge; running rows are never removed. */
    @Query(
        "DELETE FROM sync_operations " +
            "WHERE judge = :judge " +
            "AND status IN ('SUCCESS', 'PARTIAL', 'ERROR', 'CANCELLED', 'STALE_GENERATION') " +
            "AND id NOT IN (SELECT id FROM sync_operations WHERE judge = :judge " +
            "AND status IN ('SUCCESS', 'PARTIAL', 'ERROR', 'CANCELLED', 'STALE_GENERATION') " +
            "ORDER BY started_at DESC, id DESC LIMIT :keepCount)",
    )
    suspend fun pruneCompletedHistory(
        judge: String,
        keepCount: Int = SYNC_OPERATION_HISTORY_LIMIT,
    )
}
