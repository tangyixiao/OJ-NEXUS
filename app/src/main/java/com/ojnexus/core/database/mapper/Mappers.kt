package com.ojnexus.core.database.mapper

import com.ojnexus.core.database.entity.AttemptEntity
import com.ojnexus.core.database.entity.FailureEntryEntity
import com.ojnexus.core.database.entity.ProblemEntity
import com.ojnexus.core.database.entity.ProblemNoteEntity
import com.ojnexus.core.database.entity.ReviewEntity
import com.ojnexus.core.database.entity.TrainingSessionEntity
import com.ojnexus.core.database.entity.TrainingTaskEntity
import com.ojnexus.core.database.relation.ProblemDetailPojo
import com.ojnexus.core.database.relation.ProblemWithTagsPojo
import com.ojnexus.core.model.Attempt
import com.ojnexus.core.model.DifficultySource
import com.ojnexus.core.model.FailureCategory
import com.ojnexus.core.model.FailureEntry
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.Problem
import com.ojnexus.core.model.ProblemKey
import com.ojnexus.core.model.ProblemNotes
import com.ojnexus.core.model.ReviewResult
import com.ojnexus.core.model.ReviewState
import com.ojnexus.core.model.SessionState
import com.ojnexus.core.model.TrainingSession
import com.ojnexus.core.model.TrainingTask
import com.ojnexus.core.model.TrainingType
import com.ojnexus.core.model.TaskType
import com.ojnexus.core.model.Verdict

/**
 * Entity ↔ domain mapping. Unknown enum names degrade instead of throwing — a schema drift
 * or corrupt row must never crash the app.
 */

fun ProblemWithTagsPojo.toDomain(): Problem = problem.toDomain(
    tags = tags.map { it.name },
    inReview = inReview,
)

/** Full detail aggregate: problem + attempts (newest first) + failures + notes + review. */
fun ProblemDetailPojo.toDetail(): com.ojnexus.core.model.ProblemDetail =
    com.ojnexus.core.model.ProblemDetail(
        problem = toDomain(),
        attempts = attempts.sortedByDescending { it.timestamp }.map { it.toDomain() },
        failures = failures.sortedBy { it.createdAt }.map { it.toDomain() },
        notes = note?.toDomain(),
        review = review?.toDomain(),
    )

fun ProblemDetailPojo.toDomain(): Problem = problem.toDomain(
    tags = tags.map { it.name },
)

fun ProblemEntity.toDomain(tags: List<String> = emptyList(), inReview: Boolean = false): Problem = Problem(
    id = id,
    key = ProblemKey(
        judge = JudgeId.fromId(judge) ?: JudgeId.LOCAL,
        externalId = externalId,
    ),
    title = title,
    difficulty = difficulty,
    difficultySource = DifficultySource.entries.firstOrNull { it.name == difficultySource }
        ?: DifficultySource.UNKNOWN,
    createdAt = createdAt,
    updatedAt = updatedAt,
    firstSolvedAt = firstSolvedAt,
    lastAttemptAt = lastAttemptAt,
    attemptCount = attemptCount,
    solved = solved,
    favorite = favorite,
    sourceUrl = sourceUrl,
    tags = tags,
    inReview = inReview,
)

fun AttemptEntity.toDomain(): Attempt = Attempt(
    id = id,
    problemId = problemId,
    timestamp = timestamp,
    verdict = Verdict.fromRaw(verdict),
    rawVerdict = rawVerdict,
    durationMinutes = durationMinutes,
    language = language,
    note = note,
)

fun FailureEntryEntity.toDomain(): FailureEntry = FailureEntry(
    id = id,
    problemId = problemId,
    attemptId = attemptId,
    category = FailureCategory.entries.firstOrNull { it.name == category } ?: FailureCategory.OTHER,
    description = description,
    createdAt = createdAt,
)

fun ProblemNoteEntity.toDomain(): ProblemNotes = ProblemNotes(
    problemId = problemId,
    keyInsight = keyInsight,
    implementationNotes = implementationNotes,
    complexity = complexity,
    general = general,
    updatedAt = updatedAt,
)

fun ReviewEntity.toDomain(): ReviewState = ReviewState(
    problemId = problemId,
    stage = stage,
    dueAt = dueAt,
    dueDayIndex = dueDayIndex,
    lastResult = lastResult?.let { r -> ReviewResult.entries.firstOrNull { it.name == r } },
    lastReviewedAt = lastReviewedAt,
    createdAt = createdAt,
)

fun TrainingTaskEntity.toDomain(problemTitle: String?): TrainingTask = TrainingTask(
    id = id,
    dateEpochDay = dateEpochDay,
    type = TaskType.entries.firstOrNull { it.name == type } ?: TaskType.SOLVE,
    problemId = problemId,
    problemTitle = problemTitle,
    title = title,
    completed = completed,
    priority = priority,
    sortOrder = sortOrder,
    createdAt = createdAt,
)

fun TrainingSessionEntity.toDomain(): TrainingSession = TrainingSession(
    id = id,
    type = TrainingType.entries.firstOrNull { it.name == type } ?: TrainingType.PRACTICE,
    state = SessionState.entries.firstOrNull { it.name == state } ?: SessionState.FINISHED,
    startedAt = startedAt,
    pausedAt = pausedAt,
    totalPausedMs = totalPausedMs,
    finishedAt = finishedAt,
    targetDurationMin = targetDurationMin,
    targetTag = targetTag,
    note = note,
)
