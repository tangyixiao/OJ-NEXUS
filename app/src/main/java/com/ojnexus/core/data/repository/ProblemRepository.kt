package com.ojnexus.core.data.repository

import androidx.room.withTransaction
import com.ojnexus.core.data.DataError
import com.ojnexus.core.data.DataResult
import com.ojnexus.core.data.dataResult
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.dao.AttemptDao
import com.ojnexus.core.database.dao.FailureDao
import com.ojnexus.core.database.dao.NoteDao
import com.ojnexus.core.database.dao.ProblemDao
import com.ojnexus.core.database.dao.ReviewDao
import com.ojnexus.core.database.entity.AttemptEntity
import com.ojnexus.core.database.entity.FailureEntryEntity
import com.ojnexus.core.database.entity.ProblemEntity
import com.ojnexus.core.database.entity.ProblemNoteEntity
import com.ojnexus.core.database.entity.ProblemTagCrossRef
import com.ojnexus.core.database.entity.ProblemTagEntity
import com.ojnexus.core.database.mapper.toDetail
import com.ojnexus.core.database.mapper.toDomain
import com.ojnexus.core.model.Attempt
import com.ojnexus.core.model.FailureCategory
import com.ojnexus.core.model.FailureEntry
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.Problem
import com.ojnexus.core.model.ProblemDetail
import com.ojnexus.core.model.ProblemKey
import com.ojnexus.core.model.ProblemNotes
import com.ojnexus.core.model.Verdict
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Local problem library: problems, tags, attempts, failure entries and notes.
 * Every mutation that touches derived problem fields (attempt count, solved, timestamps)
 * runs inside one transaction so rows never disagree.
 */
class ProblemRepository(
    private val database: OjNexusDatabase,
    private val clock: Clock,
) {
    private val problemDao: ProblemDao = database.problemDao()
    private val attemptDao: AttemptDao = database.attemptDao()
    private val failureDao: FailureDao = database.failureDao()
    private val noteDao: NoteDao = database.noteDao()
    private val reviewDao: ReviewDao = database.reviewDao()

    // --- Library ---

    fun observeLibrary(): Flow<List<Problem>> =
        problemDao.observeLibrary().map { rows -> rows.map { it.toDomain() } }

    fun observeTags(): Flow<List<String>> =
        problemDao.observeTags().map { tags -> tags.map { it.name } }

    fun observeDetail(problemId: Long): Flow<ProblemDetail?> =
        problemDao.observeDetail(problemId).map { it?.toDetail() }

    suspend fun findProblem(problemId: Long): Problem? =
        problemDao.findDetail(problemId)?.toDomain()

    suspend fun deleteProblem(problemId: Long) {
        val problem = problemDao.findById(problemId) ?: return
        database.withTransaction {
            // Deleting the problem cascades to attempts, failures, notes, reviews, tasks
            // and session links (declared foreign keys). A live review row goes with it.
            reviewDao.deleteByProblem(problemId)
            problemDao.delete(problem)
        }
    }

    suspend fun setFavorite(problemId: Long, favorite: Boolean) {
        problemDao.setFavorite(problemId, favorite)
    }

    // --- Create / edit ---

    data class ProblemInput(
        val key: ProblemKey,
        val title: String,
        val difficulty: Int?,
        val tags: List<String>,
        val sourceUrl: String?,
    )

    suspend fun keyExists(key: ProblemKey): Boolean =
        problemDao.findByKey(key.judge.id, key.externalId) != null

    suspend fun findProblemByKey(key: ProblemKey): Problem? =
        problemDao.findByKey(key.judge.id, key.externalId)?.toDomain()

    suspend fun addProblem(input: ProblemInput): DataResult<Long> {
        val duplicate = problemDao.findByKey(input.key.judge.id, input.key.externalId)
        if (duplicate != null) {
            return DataResult.Failure(DataError.DuplicateProblem(input.key.toString()))
        }
        return dataResult {
            val now = clock.millis()
            database.withTransaction {
                val problemId = problemDao.insert(
                    ProblemEntity(
                        judge = input.key.judge.id,
                        externalId = input.key.externalId,
                        title = input.title,
                        difficulty = input.difficulty,
                        createdAt = now,
                        updatedAt = now,
                        sourceUrl = input.sourceUrl?.trim()?.takeIf { it.isNotEmpty() },
                    ),
                )
                replaceTags(problemId, input.tags)
                problemId
            }
        }
    }

    suspend fun updateProblem(problemId: Long, input: ProblemInput): DataResult<Unit> {
        val existing = problemDao.findById(problemId)
            ?: return DataResult.Failure(DataError.NotFound("problem $problemId"))
        val clash = problemDao.findByKey(input.key.judge.id, input.key.externalId)
        if (clash != null && clash.id != problemId) {
            return DataResult.Failure(DataError.DuplicateProblem(input.key.toString()))
        }
        return dataResult {
            database.withTransaction {
                problemDao.update(
                    existing.copy(
                        judge = input.key.judge.id,
                        externalId = input.key.externalId,
                        title = input.title,
                        difficulty = input.difficulty,
                        sourceUrl = input.sourceUrl?.trim()?.takeIf { it.isNotEmpty() },
                        updatedAt = clock.millis(),
                    ),
                )
                replaceTags(problemId, input.tags)
            }
        }
    }

    /** LOCAL problems get a stable app-side external id so keys stay unique without a judge. */
    fun newLocalExternalId(): String = "local-" + UUID.randomUUID().toString().substring(0, 8)

    // --- Attempts ---

    suspend fun addAttempt(
        problemId: Long,
        verdict: Verdict,
        rawVerdict: String? = null,
        durationMinutes: Int? = null,
        language: String? = null,
        note: String? = null,
    ): DataResult<Long> {
        if (problemDao.findById(problemId) == null) {
            return DataResult.Failure(DataError.NotFound("problem $problemId"))
        }
        return dataResult {
            val now = clock.millis()
            database.withTransaction {
                val attemptId = attemptDao.insert(
                    AttemptEntity(
                        problemId = problemId,
                        timestamp = now,
                        dayIndex = clock.dayIndex(),
                        verdict = verdict.name,
                        rawVerdict = rawVerdict,
                        durationMinutes = durationMinutes,
                        language = language,
                        note = note,
                    ),
                )
                problemDao.applyAttempt(
                    id = problemId,
                    timestamp = now,
                    // Solved stays true once achieved — later failures never unsolve a problem.
                    solved = verdict.isAccepted,
                    firstSolvedAt = if (verdict.isAccepted) now else null,
                    updatedAt = now,
                )
                attemptId
            }
        }
    }

    // --- Failure log ---

    suspend fun addFailureEntry(
        problemId: Long,
        attemptId: Long?,
        category: FailureCategory,
        description: String,
    ): DataResult<Long> = dataResult {
        database.withTransaction {
            failureDao.insert(
                FailureEntryEntity(
                    problemId = problemId,
                    attemptId = attemptId,
                    category = category.name,
                    description = description,
                    createdAt = clock.millis(),
                    dayIndex = clock.dayIndex(),
                ),
            )
        }
    }

    suspend fun updateFailureEntry(entry: FailureEntry): DataResult<Unit> {
        val existing = failureDao.findById(entry.id)
            ?: return DataResult.Failure(DataError.NotFound("failure ${entry.id}"))
        return dataResult {
            failureDao.update(
                existing.copy(
                    category = entry.category.name,
                    description = entry.description,
                ),
            )
        }
    }

    suspend fun deleteFailureEntry(entryId: Long) {
        failureDao.delete(entryId)
    }

    // --- Notes ---

    suspend fun saveNotes(notes: ProblemNotes): DataResult<Unit> {
        if (problemDao.findById(notes.problemId) == null) {
            return DataResult.Failure(DataError.NotFound("problem ${notes.problemId}"))
        }
        return dataResult {
            database.withTransaction {
                noteDao.upsert(
                    ProblemNoteEntity(
                        problemId = notes.problemId,
                        keyInsight = notes.keyInsight,
                        implementationNotes = notes.implementationNotes,
                        complexity = notes.complexity,
                        general = notes.general,
                        updatedAt = clock.millis(),
                    ),
                )
                problemDao.touch(notes.problemId, clock.millis())
            }
        }
    }

    // --- Internal ---

    /** Replaces the tag set: resolves existing tag rows, creates missing ones, drops removed. */
    private suspend fun replaceTags(problemId: Long, tags: List<String>) {
        val existing = problemDao.tagIdsFor(problemId).toSet()
        val target = linkedMapOf<Long, Unit>()
        for (rawName in tags) {
            val name = normalizeTag(rawName) ?: continue
            val tagId = problemDao.findTagByName(name)?.id
                ?: problemDao.insertTag(ProblemTagEntity(name = name))
            target[tagId] = Unit
        }
        for (oldId in existing - target.keys) {
            problemDao.deleteTagLink(problemId, oldId)
        }
        for (newId in target.keys - existing) {
            problemDao.insertTagCrossRef(ProblemTagCrossRef(problemId, newId))
        }
    }

    companion object {
        /** Tag storage form: trimmed, single-spaced, lowercase. Display uses the stored form. */
        fun normalizeTag(raw: String): String? =
            raw.trim().replace(Regex("\\s+"), " ").lowercase().takeIf { it.isNotEmpty() }
    }
}

/** Local calendar day (epoch day) of "now" under this clock's zone — the day-bucketing key. */
fun Clock.dayIndex(): Long = java.time.LocalDate.ofInstant(instant(), zone).toEpochDay()
