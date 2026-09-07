package com.ojnexus.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One committed module outcome belonging to a sync operation. */
@Entity(
    tableName = "sync_operation_modules",
    foreignKeys = [
        ForeignKey(
            entity = SyncOperationEntity::class,
            parentColumns = ["id"],
            childColumns = ["operation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["operation_id"]),
        Index(value = ["operation_id", "stage"], unique = true),
    ],
)
data class SyncOperationModuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "operation_id") val operationId: Long,
    val stage: String,
    val status: String,
    @ColumnInfo(name = "attempted_count") val attemptedCount: Int = 0,
    @ColumnInfo(name = "imported_count") val importedCount: Int = 0,
    @ColumnInfo(name = "updated_count") val updatedCount: Int = 0,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    @ColumnInfo(name = "failure_type") val failureType: String? = null,
)
