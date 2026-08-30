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

interface LuoguSubmissionCenter : LuoguSubmissionHistory {
    fun observeRecentJobs(limit: Int): Flow<List<SubmissionJobEntity>>
    suspend fun refreshResult(requestId: String): LuoguOpenResult
}

/** Adds local-first lifecycle persistence around the official Open Platform gateway. */
class LuoguSubmissionRepository(
    private val database: OjNexusDatabase,
    private val gateway: LuoguOpenGateway,
    private val clock: Clock,
) : LuoguOpenGateway, LuoguSubmissionCenter {
    private val dao = database.submissionJobDao()

    override suspend fun latestForProblem(pid: String): SubmissionJobEntity? =
        dao.findLatestByProblem(JudgeId.LUOGU.id, pid)

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
            language = request.lang,
        )
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
        return response
    }

    override suspend fun fetchResult(requestId: String): LuoguOpenResult {
        val result = try {
            gateway.fetchResult(requestId)
        } catch (error: LuoguOpenApiError) {
            dao.findByRequestId(requestId)?.let { job ->
                dao.update(job.copy(status = SubmissionJobStatus.FAILED.name, updatedAt = clock.millis(), lastErrorType = error::class.simpleName))
            }
            throw error
        }
        val job = dao.findByRequestId(requestId)
        when (result) {
            LuoguOpenResult.Pending -> job?.let {
                dao.update(it.copy(status = SubmissionJobStatus.PENDING.name, updatedAt = clock.millis()))
            }
            is LuoguOpenResult.Ready -> job?.let {
                val finished = result.evaluation.isFinished()
                database.withTransaction {
                    dao.update(
                        it.copy(
                            status = if (finished) SubmissionJobStatus.READY.name else SubmissionJobStatus.PENDING.name,
                            judgeStatus = result.evaluation.status,
                            score = result.evaluation.score,
                            updatedAt = clock.millis(),
                            lastErrorType = null,
                        ),
                    )
                    if (finished) materializeAttempt(it, result.evaluation)
                }
            }
        }
        return result
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

    private fun LuoguOpenEvaluation.isFinished(): Boolean =
        status?.let(LuoguJudgeStatus::isTerminal) ?: (exitCode != null || compileSuccess != null)

    private suspend fun persist(
        requestId: String,
        kind: SubmissionJobKind,
        pid: String?,
        language: String,
    ) {
        val now = clock.millis()
        dao.insert(
            SubmissionJobEntity(
                judge = JudgeId.LUOGU.id,
                requestId = requestId,
                kind = kind.name,
                pid = pid,
                language = language,
                status = SubmissionJobStatus.PENDING.name,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }
}

private object LuoguJudgeStatus {
    fun isTerminal(status: Int): Boolean = status !in setOf(0, 1)

    fun verdict(status: Int): Verdict = when (status) {
        2 -> Verdict.CE
        3 -> Verdict.OTHER
        4 -> Verdict.MLE
        5 -> Verdict.TLE
        6, 14 -> Verdict.WA
        7 -> Verdict.RE
        12 -> Verdict.AC
        else -> Verdict.OTHER
    }
}
