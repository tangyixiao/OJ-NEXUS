package com.ojnexus.core.database.relation

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.ojnexus.core.database.entity.AttemptEntity
import com.ojnexus.core.database.entity.FailureEntryEntity
import com.ojnexus.core.database.entity.ProblemEntity
import com.ojnexus.core.database.entity.ProblemNoteEntity
import com.ojnexus.core.database.entity.ProblemTagCrossRef
import com.ojnexus.core.database.entity.ProblemTagEntity
import com.ojnexus.core.database.entity.ReviewEntity
import com.ojnexus.core.database.entity.TrainingSessionEntity
import com.ojnexus.core.database.entity.TrainingSessionProblemEntity
import com.ojnexus.core.database.entity.TrainingTaskEntity

/** Problem + its tag set, for the library list. */
data class ProblemWithTagsPojo(
    @Embedded val problem: ProblemEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ProblemTagCrossRef::class,
            parentColumn = "problem_id",
            entityColumn = "tag_id",
        ),
    )
    val tags: List<ProblemTagEntity>,
)

/** Full problem aggregate for the detail screen. */
data class ProblemDetailPojo(
    @Embedded val problem: ProblemEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ProblemTagCrossRef::class,
            parentColumn = "problem_id",
            entityColumn = "tag_id",
        ),
    )
    val tags: List<ProblemTagEntity>,
    @Relation(parentColumn = "id", entityColumn = "problem_id")
    val note: ProblemNoteEntity?,
    @Relation(parentColumn = "id", entityColumn = "problem_id")
    val attempts: List<AttemptEntity>,
    @Relation(parentColumn = "id", entityColumn = "problem_id")
    val failures: List<FailureEntryEntity>,
    @Relation(parentColumn = "id", entityColumn = "problem_id")
    val review: ReviewEntity?,
)

/** Review queue row with the scheduled problem. */
data class ReviewWithProblemPojo(
    @Embedded val review: ReviewEntity,
    @Relation(entity = ProblemEntity::class, parentColumn = "problem_id", entityColumn = "id")
    val problem: ProblemEntity?,
)

/** Task row with its optional problem. */
data class TaskWithProblemPojo(
    @Embedded val task: TrainingTaskEntity,
    @Relation(entity = ProblemEntity::class, parentColumn = "problem_id", entityColumn = "id")
    val problem: ProblemEntity?,
)

/** Session row with its attached problems. */
data class SessionWithProblemsPojo(
    @Embedded val session: TrainingSessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TrainingSessionProblemEntity::class,
            parentColumn = "session_id",
            entityColumn = "problem_id",
        ),
    )
    val problems: List<ProblemEntity>,
)
