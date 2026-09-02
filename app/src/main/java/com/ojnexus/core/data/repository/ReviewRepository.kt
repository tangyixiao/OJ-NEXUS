package com.ojnexus.core.data.repository

import androidx.room.withTransaction
import com.ojnexus.core.data.DataError
import com.ojnexus.core.data.DataResult
import com.ojnexus.core.data.dataResult
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.dao.ReviewDao
import com.ojnexus.core.database.entity.ReviewEntity
import com.ojnexus.core.database.entity.ReviewLogEntity
import com.ojnexus.core.database.mapper.toDomain
import com.ojnexus.core.domain.ReviewScheduler
import com.ojnexus.core.domain.ScheduledReview
import com.ojnexus.core.model.ReviewQueueItem
import com.ojnexus.core.model.ReviewResult
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Review scheduling and queue. All interval logic lives in the pure
 * [ReviewScheduler]; this repository only persists its decisions and writes the
 * completion log (SKIP included, for history — activity aggregation excludes SKIP).
 */
class ReviewRepository(
    private val database: OjNexusDatabase,
    private val clock: Clock,
) {
    private val reviewDao: ReviewDao = database.reviewDao()

    fun observeQueue(): Flow<List<ReviewQueueItem>> =
        reviewDao.observeQueue().map { rows ->
            rows.mapNotNull { row ->
                val problem = row.problem ?: return@mapNotNull null
                ReviewQueueItem(
                    problemId = row.review.problemId,
                    problemTitle = problem.title,
                    judge = com.ojnexus.core.model.JudgeId.fromId(problem.judge)
                        ?: com.ojnexus.core.model.JudgeId.LOCAL,
                    difficulty = problem.difficulty,
                    stage = row.review.stage,
                    dueAt = row.review.dueAt,
                    dueDayIndex = row.review.dueDayIndex,
                    lastResult = row.review.lastResult?.let { raw ->
                        ReviewResult.entries.firstOrNull { it.name == raw }
                    },
                )
            }
        }

    fun observeByProblem(problemId: Long): Flow<com.ojnexus.core.model.ReviewState?> =
        reviewDao.observeByProblem(problemId).map { it?.toDomain() }

    suspend fun findByProblem(problemId: Long): com.ojnexus.core.model.ReviewState? =
        reviewDao.findByProblem(problemId)?.toDomain()

    /** Starts (or restarts) the review ladder for a problem at stage 0, due tomorrow. */
    suspend fun scheduleReview(problemId: Long): DataResult<ScheduledReview> {
        if (database.problemDao().findById(problemId) == null) {
            return DataResult.Failure(DataError.NotFound("problem $problemId"))
        }
        return dataResult {
            val scheduled = ReviewScheduler.initialSchedule(clock.instant(), clock.zone)
            database.withTransaction {
                reviewDao.upsert(
                    ReviewEntity(
                        problemId = problemId,
                        stage = scheduled.stage,
                        dueAt = scheduled.dueAt,
                        dueDayIndex = scheduled.dueDayIndex,
                        createdAt = clock.millis(),
                    ),
                )
            }
            scheduled
        }
    }

    /** Adds several problems to stage 0 without overwriting any existing review rows. */
    suspend fun scheduleReviews(problemIds: List<Long>): DataResult<Int> {
        val ids = problemIds.distinct()
        if (ids.isEmpty()) return DataResult.Success(0)

        val missingId = ids.firstOrNull { database.problemDao().findById(it) == null }
        if (missingId != null) {
            return DataResult.Failure(DataError.NotFound("problem $missingId"))
        }

        return dataResult {
            val scheduled = ReviewScheduler.initialSchedule(clock.instant(), clock.zone)
            val createdAt = clock.millis()
            var insertedCount = 0
            database.withTransaction {
                ids.forEach { problemId ->
                    if (reviewDao.findByProblem(problemId) == null) {
                        reviewDao.upsert(
                            ReviewEntity(
                                problemId = problemId,
                                stage = scheduled.stage,
                                dueAt = scheduled.dueAt,
                                dueDayIndex = scheduled.dueDayIndex,
                                createdAt = createdAt,
                            ),
                        )
                        insertedCount++
                    }
                }
            }
            insertedCount
        }
    }

    /** Removes the problem from the review system entirely. */
    suspend fun cancelReview(problemId: Long) {
        reviewDao.deleteByProblem(problemId)
    }

    /**
     * Applies a review outcome. Returns the scheduler's next decision so callers can show
     * what just happened ("next review in 3 days").
     */
    suspend fun completeReview(problemId: Long, result: ReviewResult): DataResult<ScheduledReview> {
        val current = reviewDao.findByProblem(problemId)
            ?: return DataResult.Failure(DataError.NotFound("review for $problemId"))
        return dataResult {
            val now = clock.instant()
            val scheduled = ReviewScheduler.next(current.stage, result, now, clock.zone)
            database.withTransaction {
                reviewDao.upsert(
                    current.copy(
                        stage = scheduled.stage,
                        dueAt = scheduled.dueAt,
                        dueDayIndex = scheduled.dueDayIndex,
                        lastResult = result.name,
                        lastReviewedAt = clock.millis(),
                    ),
                )
                reviewDao.insertLog(
                    ReviewLogEntity(
                        problemId = problemId,
                        result = result.name,
                        stage = current.stage,
                        completedAt = clock.millis(),
                        dayIndex = clock.dayIndex(),
                    ),
                )
            }
            scheduled
        }
    }
}
