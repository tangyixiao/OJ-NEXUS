package com.ojnexus.judge.atcoder

import androidx.room.withTransaction
import com.ojnexus.core.data.sync.StageOutcome
import com.ojnexus.core.data.sync.SyncPhase
import com.ojnexus.core.data.sync.SyncReport
import com.ojnexus.core.data.sync.SyncStage
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.AttemptEntity
import com.ojnexus.core.database.entity.JudgeAccountEntity
import com.ojnexus.core.database.entity.ProblemEntity
import com.ojnexus.core.database.entity.SyncStateEntity
import com.ojnexus.core.model.DifficultySource
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.Verdict
import com.ojnexus.judge.atcoder.api.dto.AtCoderSubmissionDto
import com.ojnexus.judge.atcoder.mapper.AtCoderMappers
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

/** AtCoder-specific timestamp sync and cache writer; page writes are crash-safe transactions. */
class AtCoderSyncRepository(
    private val database: OjNexusDatabase,
    private val adapter: AtCoderAdapter,
    private val clock: Clock,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val syncStateDao = database.syncStateDao()

    suspend fun syncSubmissions(account: JudgeAccountEntity, force: Boolean): StageOutcome {
        val stateBefore = syncStateDao.findByJudge(JudgeId.ATCODER.id)
        if (!force && stateBefore?.submissionsSyncedAt?.let {
                clock.millis() - it < AtCoderSyncPolicy.SUBMISSIONS_FRESH_MS
            } == true
        ) {
            return StageOutcome(SyncStage.SUBMISSIONS, ok = true)
        }
        markSyncing(account, SyncStage.SUBMISSIONS)
        val durableBefore = stateBefore?.latestSubmissionTimeSeconds
        var fromSecond = (durableBefore?.minus(AtCoderSyncPolicy.SUBMISSION_OVERLAP_SECONDS) ?: 0L)
            .coerceAtLeast(0L)
        val seenIds = mutableSetOf<Long>()
        var processed = 0
        var pages = 0
        val outcome = try {
            while (pages++ < MAX_PAGES_PER_RUN) {
                val page = adapter.fetchSubmissions(account.canonicalHandle, fromSecond)
                val externalIds = page.map { it.id.toString() }
                if (externalIds.isNotEmpty()) {
                    seenIds += database.attemptDao()
                        .findExistingExternalIds(JudgeId.ATCODER.id, externalIds)
                        .mapNotNull(String::toLongOrNull)
                }
                val decision = AtCoderSubmissionCursorPlanner.plan(
                    fromSecond,
                    page,
                    AtCoderSyncPolicy.SUBMISSION_PAGE_SIZE,
                    seenIds,
                )
                database.withTransaction {
                    page.forEach { persistSubmission(it) }
                    val current = syncStateDao.findByJudge(JudgeId.ATCODER.id)
                        ?: SyncStateEntity(judge = JudgeId.ATCODER.id, accountId = account.id)
                    val cursor = listOfNotNull(
                        current.latestSubmissionTimeSeconds,
                        decision.durableCursorSecond,
                    ).maxOrNull()
                    syncStateDao.upsert(
                        current.copy(
                            accountId = account.id,
                            latestSubmissionTimeSeconds = cursor,
                            submissionsImported = processed + page.size,
                        ),
                    )
                }
                processed += page.size
                seenIds += page.map { it.id }
                if (decision.stalled) {
                    throw PaginationStalled("AtCoder submission pagination stalled at $fromSecond")
                }
                if (!decision.shouldContinue) break
                fromSecond = requireNotNull(decision.nextFromSecond)
            }
            if (pages > MAX_PAGES_PER_RUN) {
                throw PaginationStalled("AtCoder submission pagination exceeded page budget")
            }
            stamp(SyncStage.SUBMISSIONS)
            StageOutcome(SyncStage.SUBMISSIONS, true, itemsProcessed = processed)
        } catch (e: PaginationStalled) {
            StageOutcome(SyncStage.SUBMISSIONS, false, "PaginationStalled", e.message, processed)
        } catch (e: AtCoderApiError) {
            StageOutcome(SyncStage.SUBMISSIONS, false, e.javaClass.simpleName, e.message, processed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StageOutcome(SyncStage.SUBMISSIONS, false, e.javaClass.simpleName, e.message, processed)
        }
        recordOutcome(outcome)
        return outcome
    }

    suspend fun syncContests(account: JudgeAccountEntity, force: Boolean): StageOutcome =
        runStage(account, SyncStage.CONTESTS, force, AtCoderSyncPolicy.CONTESTS_FRESH_MS) {
            val now = clock.millis()
            val contests = adapter.fetchContests()
            database.contestDao().upsertAll(contests.map { AtCoderMappers.toContest(it, now) })
            contests.size
        }

    suspend fun syncProblems(account: JudgeAccountEntity, force: Boolean): StageOutcome =
        runStage(account, SyncStage.PROBLEMS, force, AtCoderSyncPolicy.PROBLEMS_FRESH_MS) {
            val contests = adapter.fetchContests()
            val problems = adapter.fetchMergedProblems()
            val models = adapter.fetchProblemModels()
            val contestsById = contests.associateBy { it.id }
            val now = clock.millis()
            val entities = problems.map { problem ->
                AtCoderMappers.toRemoteProblem(
                    problem,
                    models[problem.id],
                    contestsById[problem.contestId],
                    now,
                )
            }
            database.withTransaction {
                entities.chunked(CATALOG_CHUNK).forEach { database.remoteProblemDao().upsertAll(it) }
            }
            entities.size
        }

    suspend fun finalizeSync(report: SyncReport) {
        val state = syncStateDao.findByJudge(JudgeId.ATCODER.id) ?: return
        val now = clock.millis()
        syncStateDao.upsert(
            state.copy(
                state = report.phase().name,
                finishedAt = now,
                lastSuccessfulSyncAt = if (report.anyOk) now else state.lastSuccessfulSyncAt,
                lastErrorType = report.failures.firstOrNull()?.errorType,
                lastErrorMessage = report.failures.firstOrNull()?.errorMessage,
            ),
        )
    }

    private suspend fun persistSubmission(dto: AtCoderSubmissionDto) {
        val remote = database.remoteProblemDao().findByKey(JudgeId.ATCODER.id, dto.problemId)
        val existingProblem = database.problemDao().findByKey(JudgeId.ATCODER.id, dto.problemId)
        val title = remote?.name ?: dto.problemId
        val sourceUrl = AtCoderUrls.problem(dto.contestId, dto.problemId)
        val problemId = if (existingProblem == null) {
            database.problemDao().insert(
                ProblemEntity(
                    judge = JudgeId.ATCODER.id,
                    externalId = dto.problemId,
                    title = title,
                    difficulty = remote?.rating,
                    difficultySource = remote?.difficultySource ?: DifficultySource.UNKNOWN.name,
                    createdAt = clock.millis(),
                    updatedAt = clock.millis(),
                    sourceUrl = sourceUrl,
                ),
            )
        } else {
            database.problemDao().applyRemoteMetadata(
                id = existingProblem.id,
                title = title,
                difficulty = remote?.rating ?: existingProblem.difficulty,
                difficultySource = remote?.difficultySource ?: existingProblem.difficultySource,
                sourceUrl = existingProblem.sourceUrl?.takeIf { !it.contains("atcoder.jp") } ?: sourceUrl,
                updatedAt = clock.millis(),
            )
            existingProblem.id
        }
        val mapped = dto.toAttempt(problemId)
        val existingAttempt = database.attemptDao().findByExternalId(
            JudgeId.ATCODER.id,
            dto.id.toString(),
        )
        if (existingAttempt == null) {
            database.attemptDao().insert(mapped)
            database.problemDao().applyAttempt(
                problemId,
                mapped.timestamp,
                mapped.verdict == Verdict.AC.name,
                mapped.timestamp.takeIf { mapped.verdict == Verdict.AC.name },
                clock.millis(),
            )
        } else {
            database.attemptDao().update(
                existingAttempt.copy(
                    verdict = mapped.verdict,
                    rawVerdict = mapped.rawVerdict,
                    language = mapped.language,
                    executionTimeMs = mapped.executionTimeMs,
                    score = mapped.score,
                    codeLengthBytes = mapped.codeLengthBytes,
                ),
            )
            if (mapped.verdict == Verdict.AC.name) {
                database.problemDao().promoteSolved(problemId, mapped.timestamp, clock.millis())
            }
        }
    }

    private fun AtCoderSubmissionDto.toAttempt(problemId: Long): AttemptEntity = AttemptEntity(
        problemId = problemId,
        timestamp = epochSecond * 1_000,
        dayIndex = LocalDate.ofInstant(java.time.Instant.ofEpochSecond(epochSecond), zone).toEpochDay(),
        verdict = AtCoderMappers.verdict(result).name,
        rawVerdict = result,
        language = language,
        sourceJudge = JudgeId.ATCODER.id,
        externalSubmissionId = id.toString(),
        contestId = contestId,
        executionTimeMs = executionTime,
        score = point,
        codeLengthBytes = length,
    )

    private suspend fun runStage(
        account: JudgeAccountEntity,
        stage: SyncStage,
        force: Boolean,
        freshMs: Long,
        block: suspend () -> Int,
    ): StageOutcome {
        val state = syncStateDao.findByJudge(JudgeId.ATCODER.id)
        val last = when (stage) {
            SyncStage.CONTESTS -> state?.contestsSyncedAt
            SyncStage.PROBLEMS -> state?.problemsetSyncedAt
            else -> null
        }
        if (!force && last != null && clock.millis() - last < freshMs) {
            return StageOutcome(stage, true)
        }
        markSyncing(account, stage)
        val outcome = try {
            val count = block()
            stamp(stage)
            StageOutcome(stage, true, itemsProcessed = count)
        } catch (e: AtCoderApiError) {
            StageOutcome(stage, false, e.javaClass.simpleName, e.message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StageOutcome(stage, false, e.javaClass.simpleName, e.message)
        }
        recordOutcome(outcome)
        return outcome
    }

    private suspend fun markSyncing(account: JudgeAccountEntity, stage: SyncStage) {
        val state = syncStateDao.findByJudge(JudgeId.ATCODER.id)
            ?: SyncStateEntity(judge = JudgeId.ATCODER.id, accountId = account.id)
        syncStateDao.upsert(
            state.copy(
                accountId = account.id,
                state = SyncPhase.SYNCING.name,
                startedAt = clock.millis(),
                finishedAt = null,
                currentStage = stage.name,
            ),
        )
    }

    private suspend fun stamp(stage: SyncStage) {
        val state = syncStateDao.findByJudge(JudgeId.ATCODER.id) ?: return
        val now = clock.millis()
        syncStateDao.upsert(
            when (stage) {
                SyncStage.SUBMISSIONS -> state.copy(submissionsSyncedAt = now)
                SyncStage.CONTESTS -> state.copy(contestsSyncedAt = now)
                SyncStage.PROBLEMS -> state.copy(problemsetSyncedAt = now)
                else -> state
            },
        )
    }

    private suspend fun recordOutcome(outcome: StageOutcome) {
        val state = syncStateDao.findByJudge(JudgeId.ATCODER.id) ?: return
        syncStateDao.upsert(
            state.copy(
                lastErrorType = outcome.errorType,
                lastErrorMessage = outcome.errorMessage,
                submissionsImported = if (outcome.stage == SyncStage.SUBMISSIONS) {
                    outcome.itemsProcessed
                } else {
                    state.submissionsImported
                },
            ),
        )
    }

    private class PaginationStalled(message: String) : Exception(message)

    private companion object {
        const val MAX_PAGES_PER_RUN = 10_000
        const val CATALOG_CHUNK = 2_000
    }
}
