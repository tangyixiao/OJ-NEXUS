package com.ojnexus.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/** Local-only per-contest problem marker; remote sync never changes it. */
@Entity(
    tableName = "contest_problem_markers",
    primaryKeys = ["judge", "contest_id", "problem_external_id"],
    indices = [Index(value = ["judge", "contest_id"])],
)
data class ContestProblemMarkerEntity(
    val judge: String,
    @ColumnInfo(name = "contest_id") val contestId: String,
    @ColumnInfo(name = "problem_external_id") val problemExternalId: String,
    val marker: String = "NONE",
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
