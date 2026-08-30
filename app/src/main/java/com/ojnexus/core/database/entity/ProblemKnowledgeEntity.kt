package com.ojnexus.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Explicit problem-to-knowledge relation; tags remain judge metadata, not mastery evidence. */
@Entity(
    tableName = "problem_knowledge",
    primaryKeys = ["problem_id", "knowledge_area"],
    foreignKeys = [
        ForeignKey(
            entity = ProblemEntity::class,
            parentColumns = ["id"],
            childColumns = ["problem_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["knowledge_area"])],
)
data class ProblemKnowledgeEntity(
    @ColumnInfo(name = "problem_id") val problemId: Long,
    @ColumnInfo(name = "knowledge_area") val knowledgeArea: String,
)
