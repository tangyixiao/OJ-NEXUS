package com.ojnexus.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "workspace_drafts",
    primaryKeys = ["judge", "pid"],
)
data class WorkspaceDraftEntity(
    val judge: String,
    val pid: String,
    val code: String,
    val input: String,
    val language: String,
    val o2: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
