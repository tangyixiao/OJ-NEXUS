package com.ojnexus.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

/** Local lifecycle metadata for an Open Platform request; source code and input are excluded. */
@Entity(
    tableName = "submission_jobs",
    indices = [
        Index(value = ["request_id"], unique = true),
        Index(value = ["status"]),
        Index(value = ["updated_at"]),
    ],
)
data class SubmissionJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val judge: String,
    @ColumnInfo(name = "request_id") val requestId: String,
    @ColumnInfo(name = "track_id") val trackId: String? = null,
    val kind: String,
    val pid: String? = null,
    /** Public problem title captured locally for history display; PID remains the identity. */
    val title: String? = null,
    val language: String,
    val status: String,
    @ColumnInfo(name = "judge_status") val judgeStatus: Int? = null,
    val score: Int? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "last_error_type") val lastErrorType: String? = null,
    @ColumnInfo(name = "compile_success") val compileSuccess: Boolean? = null,
    @ColumnInfo(name = "compile_message") val compileMessage: String? = null,
    val output: String? = null,
    @ColumnInfo(name = "exit_code") val exitCode: Int? = null,
    @ColumnInfo(name = "execution_time_ms") val executionTimeMs: Int? = null,
    @ColumnInfo(name = "memory_kib") val memoryKiB: Int? = null,
)
