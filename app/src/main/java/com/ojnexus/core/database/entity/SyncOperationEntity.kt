package com.ojnexus.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Stable stored status values for one explicit judge sync run. */
enum class SyncOperationStatus {
    RUNNING,
    SUCCESS,
    PARTIAL,
    ERROR,
    CANCELLED,
    STALE_GENERATION,
}

/** Stable stored status values for one committed module outcome. */
enum class SyncModuleStatus {
    SUCCESS,
    PARTIAL,
    ERROR,
    CANCELLED,
    SKIPPED,
}

/** Bounded history row for one explicit sync invocation. */
@Entity(
    tableName = "sync_operations",
    indices = [
        Index(value = ["judge", "started_at"]),
        Index(value = ["judge", "status", "started_at"]),
    ],
)
data class SyncOperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val judge: String,
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "data_generation") val dataGeneration: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "finished_at") val finishedAt: Long? = null,
    val status: String = SyncOperationStatus.RUNNING.name,
    @ColumnInfo(name = "current_stage") val currentStage: String? = null,
    @ColumnInfo(name = "last_error_type") val lastErrorType: String? = null,
)
