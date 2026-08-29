package com.ojnexus.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted sync state per judge — survives process death, so the app never shows a
 * stale SYNCING forever and always knows the last successful sync. Module freshness
 * timestamps live here too (SyncPolicy decides when each module re-syncs).
 */
@Entity(tableName = "sync_states")
data class SyncStateEntity(
    /** [com.ojnexus.core.model.JudgeId] id. */
    @PrimaryKey val judge: String,
    /** [com.ojnexus.core.data.sync.SyncPhase] name: IDLE/SYNCING/SUCCESS/PARTIAL/ERROR. */
    val state: String = "IDLE",
    @ColumnInfo(name = "started_at") val startedAt: Long? = null,
    @ColumnInfo(name = "finished_at") val finishedAt: Long? = null,
    @ColumnInfo(name = "last_successful_sync_at") val lastSuccessfulSyncAt: Long? = null,
    @ColumnInfo(name = "last_error_type") val lastErrorType: String? = null,
    @ColumnInfo(name = "last_error_message") val lastErrorMessage: String? = null,
    /** [com.ojnexus.core.data.sync.SyncStage] name of the stage currently/last running. */
    @ColumnInfo(name = "current_stage") val currentStage: String? = null,
    /** Imported/refreshed submission count for the running or last sync. */
    @ColumnInfo(name = "submissions_imported") val submissionsImported: Int? = null,
    // --- Module freshness ---
    @ColumnInfo(name = "profile_synced_at") val profileSyncedAt: Long? = null,
    @ColumnInfo(name = "rating_synced_at") val ratingSyncedAt: Long? = null,
    @ColumnInfo(name = "submissions_synced_at") val submissionsSyncedAt: Long? = null,
    @ColumnInfo(name = "contests_synced_at") val contestsSyncedAt: Long? = null,
    @ColumnInfo(name = "problemset_synced_at") val problemsetSyncedAt: Long? = null,
    /** Highest synced remote submission id — the incremental sync cursor. */
    @ColumnInfo(name = "latest_external_submission_id") val latestExternalSubmissionId: Long? = null,
)
