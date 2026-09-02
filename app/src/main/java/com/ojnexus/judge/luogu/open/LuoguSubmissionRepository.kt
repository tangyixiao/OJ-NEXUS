package com.ojnexus.judge.luogu.open

import androidx.room.withTransaction
import com.ojnexus.core.database.entity.AttemptEntity
import com.ojnexus.core.database.entity.ProblemEntity
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.SubmissionJobEntity
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.Verdict
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import com.ojnexus.judge.luogu.LuoguUrls

enum class SubmissionJobKind { RUN, PROBLEM }

enum class SubmissionJobStatus { PENDING, READY, FAILED }

interface LuoguSubmissionHistory {
    suspend fun latestForProblem(pid: String): SubmissionJobEntity?
}

interface LuoguSubmissionCenter : LuoguSubmissionHistory, LuoguOpenResultSignal {
    fun observeRecentJobs(limit: Int): Flow<List<SubmissionJobEntity>>
    suspend fun refreshResult(requestId: String): LuoguOpenResult
}

/** Adds local-first lifecycle persistence around the official Open Platform gateway. */
class LuoguSubmissionRepository(
    private val database: OjNexusDatabase,
    private val gateway: LuoguOpenGateway,
    private val clock: Clock,
    private val resultScheduler: LuoguResultWorkScheduler? = null,
) : LuoguOpenGateway, LuoguSubmissionCenter {
    private val dao = database.submissionJobDao()

    override val supportsCustomInputRun: Boolean
        get() = gateway.supportsCustomInputRun

    override suspend fun latestForProblem(pid: String): SubmissionJobEntity? =
        dao.findLatestByProblem(JudgeId.LUOGU.id, pid)

    override suspend fun awaitResultSignal(requestId: String, timeoutMillis: Long): Boolean =
        gateway.awaitResultSignal(requestId, timeoutMillis)

    override fun observeRecentJobs(limit: Int): Flow<List<SubmissionJobEntity>> =
        dao.observeRecent(limit)

    override suspend fun refreshResult(requestId: String): LuoguOpenResult =
        fetchResult(requestId)

    override suspend fun submitProblem(request: LuoguProblemJudgeRequest): LuoguOpenSubmission {
        val response = gateway.submitProblem(request)
        persist(
            requestId = response.requestId,
            kind = SubmissionJobKind.PROBLEM,
            pid = request.pid,
            title = request.displayTitle,
            language = request.lang,
        )
        resultScheduler?.enqueue(response.requestId)
        return response
    }

    override suspend fun run(request: LuoguRunRequest): LuoguOpenSubmission {
        val response = gateway.run(request)
        persist(
            requestId = response.requestId,
            kind = SubmissionJobKind.RUN,
            pid = null,
            language = request.lang,
        )
        resultScheduler?.enqueue(response.requestId)
        return response
    }

    override suspend fun fetchResult(requestId: String): LuoguOpenResult {
        val result = try {
            gateway.fetchResult(requestId)
        } catch (error: LuoguOpenApiError) {
            dao.findByRequestId(requestId)?.let { job ->
                dao.update(
                    job.copy(
                        status = if (error.isRetryableResultError()) {
                            SubmissionJobStatus.PENDING.name
                        } else {
                            SubmissionJobStatus.FAILED.name
                        },
                        updatedAt = clock.millis(),
                        lastErrorType = error::class.simpleName,
                    ),
                )
            }
            throw error
        }
        val job = dao.findByRequestId(requestId)
        when (result) {
            LuoguOpenResult.Pending -> job?.let {
                dao.update(it.copy(status = SubmissionJobStatus.PENDING.name, updatedAt = clock.millis()))
            }
            is LuoguOpenResult.InProgress -> job?.let {
                persistEvaluation(it, result.evaluation, SubmissionJobStatus.PENDING)
            }
            is LuoguOpenResult.Ready -> job?.let {
                val finished = result.evaluation.isFinished()
                persistEvaluation(
                    it,
                    result.evaluation,
                    if (finished) SubmissionJobStatus.READY else SubmissionJobStatus.PENDING,
                )
            }
        }
        return result
    }

    private suspend fun persistEvaluation(
        job: SubmissionJobEntity,
        evaluation: LuoguOpenEvaluation,
        status: SubmissionJobStatus,
    ) {
        database.withTransaction {
            dao.update(
                job.copy(
                    status = status.name,
                    judgeStatus = evaluation.status,
                    score = evaluation.score,
                    compileSuccess = evaluation.compileSuccess,
                    compileMessage = evaluation.compileMessage,
                    output = evaluation.output,
                    exitCode = evaluation.exitCode,
                    executionTimeMs = evaluation.timeMs
                        ?.coerceIn(0, Int.MAX_VALUE.toLong())
                        ?.toInt(),
                    memoryKiB = evaluation.memoryKiB
                        ?.coerceIn(0, Int.MAX_VALUE.toLong())
                        ?.toInt(),
                    updatedAt = clock.millis(),
                    lastErrorType = null,
                ),
            )
            if (status == SubmissionJobStatus.READY) materializeAttempt(job, evaluation)
        }
    }

    private suspend fun materializeAttempt(
        job: SubmissionJobEntity,
        evaluation: LuoguOpenEvaluation,
    ) {
        if (job.kind != SubmissionJobKind.PROBLEM.name) return
        val status = evaluation.status ?: return
        if (!LuoguJudgeStatus.isTerminal(status)) return
        val pid = job.pid?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val problemDao = database.problemDao()
        val problem = problemDao.findByKey(JudgeId.LUOGU.id, pid)
        val problemId = problem?.id ?: problemDao.insert(
            ProblemEntity(
                judge = JudgeId.LUOGU.id,
                externalId = pid,
                title = pid,
                difficulty = null,
                createdAt = job.createdAt,
                updatedAt = clock.millis(),
                sourceUrl = LuoguUrls.problem(pid),
            ),
        )
        val timestamp = job.createdAt
        val attempt = AttemptEntity(
            problemId = problemId,
            timestamp = timestamp,
            dayIndex = java.time.Instant.ofEpochMilli(timestamp).atZone(clock.zone).toLocalDate().toEpochDay(),
            verdict = LuoguJudgeStatus.verdict(status).name,
            rawVerdict = status.toString(),
            language = job.language,
            sourceJudge = JudgeId.LUOGU.id,
            externalSubmissionId = job.requestId,
            executionTimeMs = evaluation.timeMs?.coerceIn(0, Int.MAX_VALUE.toLong())?.toInt(),
            memoryBytes = evaluation.memoryKiB?.coerceAtMost(Long.MAX_VALUE / 1024)?.times(1024),
            score = evaluation.score?.toDouble(),
        )
        val attemptDao = database.attemptDao()
        val existing = attemptDao.findByExternalId(JudgeId.LUOGU.id, job.requestId)
        if (existing == null) {
            attemptDao.insert(attempt)
            problemDao.applyAttempt(
                id = problemId,
                timestamp = timestamp,
                solved = attempt.verdict == Verdict.AC.name,
                firstSolvedAt = timestamp.takeIf { attempt.verdict == Verdict.AC.name },
                updatedAt = clock.millis(),
            )
        } else {
            attemptDao.update(existing.copy(
                verdict = attempt.verdict,
                rawVerdict = attempt.rawVerdict,
                executionTimeMs = attempt.executionTimeMs,
                memoryBytes = attempt.memoryBytes,
                score = attempt.score,
            ))
            if (attempt.verdict == Verdict.AC.name) {
                problemDao.promoteSolved(problemId, timestamp, clock.millis())
            }
        }
    }

    private suspend fun persist(
        requestId: String,
        kind: SubmissionJobKind,
        pid: String?,
        title: String? = null,
        language: String,
    ) {
        val now = clock.millis()
        dao.insert(
            SubmissionJobEntity(
                judge = JudgeId.LUOGU.id,
                requestId = requestId,
                kind = kind.name,
                pid = pid,
                title = title?.trim()?.takeIf { it.isNotEmpty() },
                language = language,
                status = SubmissionJobStatus.PENDING.name,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }
}
