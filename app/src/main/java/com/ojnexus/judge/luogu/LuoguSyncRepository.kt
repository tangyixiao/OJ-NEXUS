package com.ojnexus.judge.luogu

import com.ojnexus.core.data.sync.StageOutcome
import com.ojnexus.core.data.sync.SyncPhase
import com.ojnexus.core.data.sync.SyncReport
import com.ojnexus.core.data.sync.SyncStage
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.database.entity.JudgeAccountEntity
import com.ojnexus.core.database.entity.SyncStateEntity
import com.ojnexus.core.model.JudgeId
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

/** Local-first cache writer for Luogu's public content-only endpoints. */
class LuoguSyncRepository(
    private val database: OjNexusDatabase,
    private val adapter: LuoguAdapter,
    private val clock: Clock,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val maxCatalogPages: Int = LuoguPolicies.MAX_CATALOG_PAGES,
) {
    private val syncStateDao = database.syncStateDao()

    fun observeSyncState() = syncStateDao.observeByJudge(JudgeId.LUOGU.id)

    suspend fun findSyncState(): SyncStateEntity? = syncStateDao.findByJudge(JudgeId.LUOGU.id)

    suspend fun syncProfile(account: JudgeAccountEntity, force: Boolean): StageOutcome =
        runStage(account, SyncStage.PROFILE, force, LuoguPolicies.PROFILE_FRESH_MS) {
            val uid = resolveUid(account)
            val profilePage = adapter.fetchUserPage(uid)
            val practicePage = adapter.fetchPracticePage(uid)
            val profile = profilePage.data?.user ?: practicePage.data?.user
                ?: throw parseError("Luogu profile payload has no user")
            val currentRating = profilePage.data?.gu?.rating
                ?: practicePage.data?.gu?.rating
                ?: profile.eloValue
            database.judgeProfileDao().upsert(
                LuoguMappers.toProfileEntity(profile, currentRating, JudgeId.LUOGU, clock.millis()),
            )
            1
        }

    suspend fun syncRating(account: JudgeAccountEntity, force: Boolean): StageOutcome =
        runStage(account, SyncStage.RATING, force, LuoguPolicies.RATING_FRESH_MS) {
            val uid = resolveUid(account)
            val profilePage = adapter.fetchUserPage(uid)
            val practicePage = adapter.fetchPracticePage(uid)
            val entries = practicePage.data?.elo ?: profilePage.data?.elo ?: emptyList()
            database.ratingChangeDao().upsertAll(
                LuoguMappers.toRatingChangeEntities(JudgeId.LUOGU, account.canonicalHandle, entries),
            )
            entries.size
        }

    suspend fun syncContests(account: JudgeAccountEntity, force: Boolean): StageOutcome =
        runStage(account, SyncStage.CONTESTS, force, LuoguPolicies.CONTESTS_FRESH_MS) {
            var pageNumber = 1
            var loaded = 0
            var processed = 0
            while (pageNumber <= maxCatalogPages) {
                val page = try {
                    adapter.fetchContestPage(pageNumber)
                } catch (e: LuoguApiError) {
                    throw PartialStageFailure(e, processed)
                }
                val collection = page.data?.contests
                    ?: throw parseError("Luogu contest payload has no contest collection")
                val rows = collection.result
                val now = clock.millis()
                database.contestDao().upsertAll(
                    rows.map { LuoguMappers.toContestEntity(it, JudgeId.LUOGU, now, now / 1_000) },
                )
                loaded += rows.size
                processed += rows.size
                if (!hasMore(pageNumber, loaded, rows.size, collection.perPage, collection.count)) break
                pageNumber++
            }
            if (pageNumber > maxCatalogPages) {
                throw PartialStageFailure(paginationError("contest"), processed)
            }
            processed
        }

    suspend fun syncProblems(account: JudgeAccountEntity, force: Boolean): StageOutcome =
        runStage(account, SyncStage.PROBLEMS, force, LuoguPolicies.PROBLEMS_FRESH_MS) {
            var pageNumber = 1
            var loaded = 0
            var processed = 0
            while (pageNumber <= maxCatalogPages) {
                val page = try {
                    adapter.fetchProblemPage(pageNumber)
                } catch (e: LuoguApiError) {
                    throw PartialStageFailure(e, processed)
                }
                val collection = page.data?.problems
                    ?: throw parseError("Luogu problem payload has no problem collection")
                val rows = collection.result.filter { it.pid.isNotBlank() }
                database.remoteProblemDao().upsertAll(
                    rows.map { LuoguMappers.toRemoteProblemEntity(it, JudgeId.LUOGU, clock.millis()) },
                )
                loaded += rows.size
                processed += rows.size
                if (!hasMore(pageNumber, loaded, rows.size, collection.perPage, collection.count)) break
                pageNumber++
            }
            if (pageNumber > maxCatalogPages) {
                throw PartialStageFailure(paginationError("problem"), processed)
            }
            processed
        }

    /** Stage 1 deliberately makes no private-data claim; anonymous records are auth-gated. */
    suspend fun syncSubmissions(account: JudgeAccountEntity, force: Boolean): StageOutcome =
        runStage(account, SyncStage.SUBMISSIONS, force, Long.MAX_VALUE) {
            val uid = resolveUid(account)
            adapter.fetchRecordPage(uid, 1)
            throw LuoguApiError.AuthenticationRequired()
        }

    suspend fun finalizeSync(report: SyncReport) {
        val current = syncStateDao.findByJudge(JudgeId.LUOGU.id) ?: return
        val now = clock.millis()
        syncStateDao.upsert(
            current.copy(
                state = report.phase().name,
                finishedAt = now,
                lastSuccessfulSyncAt = if (report.anyOk) now else current.lastSuccessfulSyncAt,
                lastErrorType = report.failures.firstOrNull()?.errorType,
                lastErrorMessage = report.failures.firstOrNull()?.errorMessage,
            ),
        )
    }

    private suspend fun resolveUid(account: JudgeAccountEntity): Long =
        adapter.searchUser(account.canonicalHandle)?.uid?.takeIf { it > 0 }
            ?: throw LuoguApiError.UserNotFound()

    private fun hasMore(
        pageNumber: Int,
        loaded: Int,
        received: Int,
        pageSize: Int,
        reportedCount: Int,
    ): Boolean {
        if (received == 0) return false
        if (reportedCount > 0 && loaded >= reportedCount) return false
        val effectivePageSize = pageSize.takeIf { it > 0 } ?: received
        return received >= effectivePageSize && pageNumber < maxCatalogPages
    }

    private suspend fun runStage(
        account: JudgeAccountEntity,
        stage: SyncStage,
        force: Boolean,
        freshnessMs: Long,
        block: suspend () -> Int,
    ): StageOutcome {
        val state = syncStateDao.findByJudge(JudgeId.LUOGU.id)
        val lastSynced = when (stage) {
            SyncStage.PROFILE -> state?.profileSyncedAt
            SyncStage.RATING -> state?.ratingSyncedAt
            SyncStage.CONTESTS -> state?.contestsSyncedAt
            SyncStage.PROBLEMS -> state?.problemsetSyncedAt
            SyncStage.SUBMISSIONS -> state?.submissionsSyncedAt
            else -> null
        }
        if (!force && lastSynced != null && clock.millis() - lastSynced < freshnessMs) {
            return StageOutcome(stage, ok = true)
        }
        markSyncing(account, stage)
        val outcome = try {
            val count = block()
            stamp(stage)
            StageOutcome(stage, ok = true, itemsProcessed = count)
        } catch (e: PartialStageFailure) {
            StageOutcome(
                stage,
                ok = false,
                errorType = e.error.javaClass.simpleName,
                errorMessage = e.error.message,
                itemsProcessed = e.itemsProcessed,
            )
        } catch (e: LuoguApiError) {
            StageOutcome(stage, ok = false, errorType = e.javaClass.simpleName, errorMessage = e.message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StageOutcome(stage, ok = false, errorType = e.javaClass.simpleName, errorMessage = e.message)
        }
        recordOutcome(outcome)
        return outcome
    }

    private suspend fun markSyncing(account: JudgeAccountEntity, stage: SyncStage) {
        val current = syncStateDao.findByJudge(JudgeId.LUOGU.id) ?:
            SyncStateEntity(judge = JudgeId.LUOGU.id, accountId = account.id)
        syncStateDao.upsert(
            current.copy(
                accountId = account.id,
                state = SyncPhase.SYNCING.name,
                startedAt = clock.millis(),
                finishedAt = null,
                currentStage = stage.name,
            ),
        )
    }

    private suspend fun stamp(stage: SyncStage) {
        val current = syncStateDao.findByJudge(JudgeId.LUOGU.id) ?: return
        val now = clock.millis()
        syncStateDao.upsert(
            when (stage) {
                SyncStage.PROFILE -> current.copy(profileSyncedAt = now)
                SyncStage.RATING -> current.copy(ratingSyncedAt = now)
                SyncStage.CONTESTS -> current.copy(contestsSyncedAt = now)
                SyncStage.PROBLEMS -> current.copy(problemsetSyncedAt = now)
                SyncStage.SUBMISSIONS -> current.copy(submissionsSyncedAt = now)
                else -> current
            },
        )
    }

    private suspend fun recordOutcome(outcome: StageOutcome) {
        val current = syncStateDao.findByJudge(JudgeId.LUOGU.id) ?: return
        syncStateDao.upsert(
            current.copy(
                lastErrorType = outcome.errorType,
                lastErrorMessage = outcome.errorMessage,
                submissionsImported = if (outcome.stage == SyncStage.SUBMISSIONS) {
                    outcome.itemsProcessed
                } else {
                    current.submissionsImported
                },
            ),
        )
    }

    private fun parseError(message: String): LuoguApiError.ParseError =
        LuoguApiError.ParseError(IllegalStateException(message))

    private fun paginationError(kind: String): LuoguApiError.ParseError =
        parseError("Luogu $kind pagination exceeded page budget")

    private class PartialStageFailure(
        val error: LuoguApiError,
        val itemsProcessed: Int,
    ) : Exception(error)
}
