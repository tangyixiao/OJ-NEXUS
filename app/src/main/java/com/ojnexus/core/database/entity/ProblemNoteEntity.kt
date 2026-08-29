package com.ojnexus.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** Per-problem structured notes. One row per problem, upserted on save. */
@Entity(
    tableName = "problem_notes",
    foreignKeys = [
        ForeignKey(
            entity = ProblemEntity::class,
            parentColumns = ["id"],
            childColumns = ["problem_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ProblemNoteEntity(
    @PrimaryKey @ColumnInfo(name = "problem_id") val problemId: Long,
    @ColumnInfo(name = "key_insight") val keyInsight: String = "",
    @ColumnInfo(name = "implementation_notes") val implementationNotes: String = "",
    val complexity: String = "",
    val general: String = "",
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
