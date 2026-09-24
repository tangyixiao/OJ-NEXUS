package com.ojnexus.judge.codeforces

import androidx.room.withTransaction
import com.ojnexus.core.data.sync.SyncPhase
import com.ojnexus.core.data.sync.SyncReport
import com.ojnexus.core.data.sync.SyncStage
import com.ojnexus.core.data.sync.StageOutcome
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.ContestEntity
import com.ojnexus.core.database.entity.JudgeAccountEntity
import com.ojnexus.core.database.entity.RatingChangeEntity
import com.ojnexus.core.database.entity.RemoteProblemEntity
import com.ojnexus.core.database.entity.SyncStateEntity
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.Verdict
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

/**
 * Owns every database write of the Codeforces sync and drives the stage pipeline.
 *
 * Invariants enforced here (tested in CodeforcesSyncTest):
 *  A. one CF submission id maps to at most one imported attempt (unique index + upsert),
 *  B. re-running a sync changes no row counts (idempotent upserts),
 *  C. rejudge updates the existing attempt's verdict and can promote solved,
 *  D. an AC'd problem is never unsolved by later failures (sticky solved),
 *  E/F. sync never touches notes, reviews, failure entries or user tags,
 *  G. per-page transactions keep the database lock short and progress durable.
 */
class CodeforcesSyncRepository(
    private val database: OjNexusDatabase,
    private val adapter: CodeforcesAdapter,
    private val clock: Clock,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val syncStateDao = database.syncStateDao()

    fun observeSyncState(judge: JudgeId) = syncStateDao.observeByJudge(judge.id)

    suspend fun findSyncState(judge: JudgeId): SyncStateEntity? = syncStateDao.findByJudge(judge.id)

    suspend fun findStateFor(account: JudgeAccountEntity): SyncStateEntity? =
        syncStateDao.findByJudge(account.judge)

    // --- Remote data reads for the UI (kept near their writers) ---

    fun observeProfile(judge: JudgeId) = database.judgeProfileDao().observeByJudge(judge.id)

    fun observeRatingChanges(judge: JudgeId) = database.ratingChangeDao().observeByJudge(judge.id)

    fun observeContests(judge: JudgeId) = database.contestDao().observeByJudge(judge.id)

    fun observeSyncStateFlow(judge: JudgeId) = syncStateDao.observeByJudge(judge.id)

    /** Reads the already-synced remote catalog without touching the network. */
    suspend fun searchRemoteProblems(
        query: String,
        solvedFilter: Int,
        limit: Int,
        offset: Int,
    ): List<RemoteProblemEntity> = database.remoteProblemDao().search(
        judge = JudgeId.CODEFORCES.id,
        query = query.trim(),
        minRating = null,
        maxRating = null,
        solvedFilter = solvedFilter,
        limit = limit,
        offset = offset,
    )

    // --- Stage runners ---

    suspend fun syncProfile(account: JudgeAccountEntity, force: Boolean): StageOutcome =
        runStage(account, SyncStage.PROFILE, force, SyncPolicy.PROFILE_FRESH_MS) {
            val profile = adapter.fetchProfile(account.canonicalHandle)
            database.judgeProfileDao().upsert(
                com.ojnexus.judge.codeforces.mapper.CfMappers.run {
                    profile.toProfileEntity(JudgeId.CODEFORCES, clock.millis())
                },
            )
            // Handle renames: user.rating/historic handles resolve to the new canonical
            // handle — adopt it, keep the connection alive (never treated as invalid).
            if (profile.handle != account.canonicalHandle) {
                database.judgeAccountDao().update(
                    account.copy(canonicalHandle = profile.handle, updatedAt = clock.millis()),
                )
            }
            1
        }

    suspend fun syncRating(account: JudgeAccountEntity, force: Boolean): StageOutcome =
        runStage(account, SyncStage.RATING, force, SyncPolicy.RATING_FRESH_MS) {
            val changes = adapter.fetchRatingHistory(account.canonicalHandle)
            val judge = JudgeId.CODEFORCES
            database.ratingChangeDao().upsertAll(
                changes.map { dto ->
                    com.ojnexus.judge.codeforces.mapper.CfMappers.run {
                        dto.toRatingChangeEntity(judge, account.canonicalHandle)
                    }
                },
            )
            changes.size
        }

    suspend fun syncContests(account: JudgeAccountEntity, force: Boolean): StageOutcome =
        runStage(account, SyncStage.CONTESTS, force, SyncPolicy.CONTESTS_FRESH_MS) {
            val contests = adapter.fetchContests()
            val judge = JudgeId.CODEFORCES
            val now = clock.millis()
            database.contestDao().upsertAll(
                contests.map { dto ->
                    com.ojnexus.judge.codeforces.mapper.CfMappers.run {
                        dto.toContestEntity(judge, now)
                    }
                },
            )
            contests.size
        }

    suspend fun syncProblemset(account: JudgeAccountEntity, force: Boolean): StageOutcome =
        runStage(account, SyncStage.PROBLEMS, force, SyncPolicy.PROBLEMSET_FRESH_MS) {
            val problemset = adapter.fetchProblemset()
            val merged = com.ojnexus.judge.codeforces.mapper.CfMappers.run {
                mergeProblemset(problemset.problems, problemset.problemStatistics)
            }
            val judge = JudgeId.CODEFORCES
            val now = clock.millis()
            val entities = merged.mapNotNull { (problem, statistics) ->
                com.ojnexus.judge.codeforces.mapper.CfMappers.run {
                    problem.toRemoteProblemEntity(judge, statistics, now)
                }
            }
            // One transaction for the whole catalog batch (upsert, tens of thousands of
            // small rows inside a single prepared statement loop).
            database.withTransaction {
                entities.chunked(PROBLEMSET_CHUNK).forEach { chunk ->
                    database.remoteProblemDao().upsertAll(chunk)
                }
            }
            entities.size
        }

    /**
     * Submission sync: initial = page through the whole history; incremental = stop after
     * the first full page that touches the last-synced boundary (that page still upserts —
     * the rejudge overlap window). Every page is one transaction.
     */
    suspend fun syncSubmissions(account: JudgeAccountEntity, force: Boolean): StageOutcome {
        val judge = JudgeId.CODEFORCES
        val startedAt = clock.millis()
        val stateBeforeSync = findStateFor(account)
        val lastSync = stateBeforeSync?.submissionsSyncedAt
        if (!force && lastSync != null && clock.millis() - lastSync < SyncPolicy.SUBMISSIONS_FRESH_MS) {
            return StageOutcome(SyncStage.SUBMISSIONS, ok = true, itemsProcessed = 0)
        }
        markSyncing(judge.id, SyncStage.SUBMISSIONS)

        var from = 1
        var imported = 0
        val syncBoundaryId = stateBeforeSync?.latestExternalSubmissionId
        var newestSeenId = syncBoundaryId
        val outcome = try {
            while (true) {
                val page = adapter.fetchSubmissionsPage(
                    handle = account.canonicalHandle,
                    from = from,
                    count = SyncPolicy.SUBMISSION_PAGE_SIZE,
                )
                database.withTransaction {
                    page.forEach { dto -> persistSubmission(account, dto) }
                }
                imported += page.size
                page.maxOfOrNull { it.id }?.let { maxId ->
                    newestSeenId = maxOf(newestSeenId ?: 0L, maxId)
                    updateCursor(judge.id, newestSeenId, imported)
                }
                val shouldContinue = SubmissionSyncPlanner.shouldContinueAfterPage(
                    pageItems = page.size,
                    pageSize = SyncPolicy.SUBMISSION_PAGE_SIZE,
                    pageSmallestSubmissionId = page.minOfOrNull { it.id },
                    latestKnownSubmissionId = syncBoundaryId,
                )
                if (!shouldContinue) break
                from += SyncPolicy.SUBMISSION_PAGE_SIZE
            }
            StageOutcome(SyncStage.SUBMISSIONS, ok = true, itemsProcessed = imported)
        } catch (e: CodeforcesApiError) {
            StageOutcome(SyncStage.SUBMISSIONS, ok = false, errorType = e.javaClass.simpleName, errorMessage = e.rawComment)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StageOutcome(SyncStage.SUBMISSIONS, ok = false, errorType = e.javaClass.simpleName, errorMessage = e.message)
        }
        if (outcome.ok) {
            stampModule(judge.id, SyncStage.SUBMISSIONS)
        }
        recordStage(judge.id, outcome, startedAt)
        return outcome
    }

    /** Maps + upserts one remote submission inside the caller's page transaction. */
    private suspend fun persistSubmission(account: JudgeAccountEntity, dto: com.ojnexus.judge.codeforces.api.dto.CfSubmissionDto) {
        val problemDto = dto.problem
        val externalProblemId = com.ojnexus.judge.codeforces.mapper.CfMappers.run {
            problemDto.contestId?.let { externalId(it, problemDto.index) }
        } ?: return // problemset-only submissions are not representable in the library

        val existing = database.problemDao().findByKey(JudgeId.CODEFORCES.id, externalProblemId)
        val problemId: Long
        if (existing == null) {
            // Materialize a minimal local problem from remote metadata.
            problemId = database.problemDao().insert(
                com.ojnexus.core.database.entity.ProblemEntity(
                    judge = JudgeId.CODEFORCES.id,
                    externalId = externalProblemId,
                    title = problemDto.name,
                        difficulty = problemDto.rating,
                        difficultySource = com.ojnexus.core.model.DifficultySource.OFFICIAL.name,
                    createdAt = clock.millis(),
                    updatedAt = clock.millis(),
                    sourceUrl = com.ojnexus.judge.codeforces.CodeforcesUrls.problem(
                        requireNotNull(problemDto.contestId),
                        problemDto.index,
                    ),
                ),
            )
        } else {
            // Remote metadata is authoritative for title/difficulty/url; user tags,
            // favorite, notes, review and failure log are never touched.
            val officialUrl = problemDto.contestId?.let {
                com.ojnexus.judge.codeforces.CodeforcesUrls.problem(it, problemDto.index)
            }
            // User's custom non-Codeforces URL wins; null or official CF URLs get refreshed.
            val finalUrl = existing.sourceUrl?.takeIf {
                !it.contains("codeforces.com")
            } ?: officialUrl
            database.problemDao().applyRemoteMetadata(
                id = existing.id,
                title = problemDto.name,
                difficulty = problemDto.rating,
                difficultySource = com.ojnexus.core.model.DifficultySource.OFFICIAL.name,
                sourceUrl = finalUrl,
                updatedAt = clock.millis(),
            )
            problemId = existing.id
        }

        val attempt = com.ojnexus.judge.codeforces.mapper.CfMappers.run {
            dto.toAttemptEntity(problemId, zone)
        }
        val synced = database.attemptDao().findByExternalId(
            sourceJudge = JudgeId.CODEFORCES.id,
            externalId = dto.id.toString(),
        )
        if (synced == null) {
            database.attemptDao().insert(attempt)
            database.problemDao().applyAttempt(
                id = problemId,
                timestamp = attempt.timestamp,
                solved = attempt.verdict == Verdict.AC.name,
                firstSolvedAt = if (attempt.verdict == Verdict.AC.name) attempt.timestamp else null,
                updatedAt = clock.millis(),
            )
        } else {
            // Rejudge / status transition: update the verdict facts in place.
            database.attemptDao().update(
                synced.copy(
                    verdict = attempt.verdict,
                    rawVerdict = attempt.rawVerdict,
                    passedTestCount = attempt.passedTestCount,
                    executionTimeMs = attempt.executionTimeMs,
                    memoryBytes = attempt.memoryBytes,
                    participantType = attempt.participantType,
                    testset = attempt.testset,
                ),
            )
            if (attempt.verdict == Verdict.AC.name) {
                database.problemDao().promoteSolved(
                    id = problemId,
                    solvedAt = attempt.timestamp,
                    updatedAt = clock.millis(),
                )
            }
        }
    }

    // --- Sync state bookkeeping ---

    private suspend fun markSyncing(judgeId: String, stage: SyncStage) {
        val state = syncStateDao.findByJudge(judgeId) ?: SyncStateEntity(judge = judgeId)
        syncStateDao.upsert(
            state.copy(
                state = SyncPhase.SYNCING.name,
                startedAt = clock.millis(),
                finishedAt = null,
                currentStage = stage.name,
            ),
        )
    }

    private suspend fun updateCursor(judgeId: String, latestId: Long?, imported: Int) {
        val state = syncStateDao.findByJudge(judgeId) ?: return
        syncStateDao.upsert(
            state.copy(
                latestExternalSubmissionId = latestId,
                submissionsImported = imported,
                currentStage = SyncStage.SUBMISSIONS.name,
            ),
        )
    }

    private suspend fun recordStage(judgeId: String, outcome: StageOutcome, startedAt: Long) {
        val state = syncStateDao.findByJudge(judgeId) ?: return
        syncStateDao.upsert(
            state.copy(
                submissionsImported = if (outcome.ok) outcome.itemsProcessed else state.submissionsImported,
                lastErrorType = outcome.errorType,
                lastErrorMessage = outcome.errorMessage,
            ),
        )
    }

    suspend fun finalizeSync(judgeId: String, report: SyncReport) {
        val now = clock.millis()
        val state = syncStateDao.findByJudge(judgeId) ?: return
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

    /** Runs one stage unless its freshness window still holds; failures become outcomes. */
    private suspend fun runStage(
        account: JudgeAccountEntity,
        stage: SyncStage,
        force: Boolean,
        freshnessMs: Long,
        block: suspend () -> Int,
    ): StageOutcome {
        val judgeId = account.judge
        markSyncing(judgeId, stage)
        val startedAt = clock.millis()
        val state = findStateFor(account)
        val lastSync = when (stage) {
            SyncStage.PROFILE -> state?.profileSyncedAt
            SyncStage.RATING -> state?.ratingSyncedAt
            SyncStage.CONTESTS -> state?.contestsSyncedAt
            SyncStage.PROBLEMS -> state?.problemsetSyncedAt
            else -> null
        }
        if (!force && lastSync != null && clock.millis() - lastSync < freshnessMs) {
            return StageOutcome(stage, ok = true, itemsProcessed = 0)
        }
        val outcome = try {
            val count = block()
            stampModule(account.judge, stage)
            StageOutcome(stage, ok = true, itemsProcessed = count)
        } catch (e: CodeforcesApiError) {
            StageOutcome(stage, ok = false, errorType = e.javaClass.simpleName, errorMessage = e.rawComment)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StageOutcome(stage, ok = false, errorType = e.javaClass.simpleName, errorMessage = e.message)
        }
        // Persist module timestamp + error fields for this stage.
        val fresh = syncStateDao.findByJudge(judgeId) ?: return outcome
        syncStateDao.upsert(
            fresh.copy(
                lastErrorType = if (outcome.ok) null else outcome.errorType,
                lastErrorMessage = if (outcome.ok) null else outcome.errorMessage,
            ),
        )
        return outcome
    }

    private suspend fun stampModule(judgeId: String, stage: SyncStage) {
        val state = syncStateDao.findByJudge(judgeId) ?: return
        val now = clock.millis()
        syncStateDao.upsert(
            when (stage) {
                SyncStage.PROFILE -> state.copy(profileSyncedAt = now)
                SyncStage.RATING -> state.copy(ratingSyncedAt = now)
                SyncStage.SUBMISSIONS -> state.copy(submissionsSyncedAt = now)
                SyncStage.CONTESTS -> state.copy(contestsSyncedAt = now)
                SyncStage.PROBLEMS -> state.copy(problemsetSyncedAt = now)
                else -> state
            },
        )
    }

    private companion object {
        const val PROBLEMSET_CHUNK = 2_000
    }
}
