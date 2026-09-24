package com.ojnexus.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/** Cached public judge-owned problem detail; separate from the user's local problem library. */
@Entity(
    tableName = "remote_problem_details",
    primaryKeys = ["judge", "external_id"],
)
data class RemoteProblemDetailEntity(
    val judge: String,
    @ColumnInfo(name = "external_id") val externalId: String,
    val title: String,
    val difficulty: Int?,
    @ColumnInfo(name = "tags_json") val tagsJson: String,
    @ColumnInfo(name = "total_submit") val totalSubmit: Int?,
    @ColumnInfo(name = "total_accepted") val totalAccepted: Int?,
    val background: String,
    val description: String,
    @ColumnInfo(name = "input_format") val inputFormat: String,
    @ColumnInfo(name = "output_format") val outputFormat: String,
    val hint: String,
    @ColumnInfo(name = "samples_json") val samplesJson: String,
    @ColumnInfo(name = "time_limit_ms") val timeLimitMs: Int?,
    @ColumnInfo(name = "memory_limit_mb") val memoryLimitMb: Int?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
