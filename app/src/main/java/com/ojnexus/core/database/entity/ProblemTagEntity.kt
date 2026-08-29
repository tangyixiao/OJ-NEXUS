package com.ojnexus.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Normalized tag row. Tags are shared across problems; the name is the natural key. */
@Entity(
    tableName = "problem_tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class ProblemTagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

/** Many-to-many between problems and tags. */
@Entity(
    tableName = "problem_tag_cross_ref",
    primaryKeys = ["problem_id", "tag_id"],
    indices = [Index(value = ["tag_id"]), Index(value = ["problem_id"])],
)
data class ProblemTagCrossRef(
    @ColumnInfo(name = "problem_id") val problemId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
)
