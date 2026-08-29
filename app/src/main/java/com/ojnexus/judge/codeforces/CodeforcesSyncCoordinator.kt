package com.ojnexus.judge.codeforces

import com.ojnexus.core.data.sync.SyncReport
import com.ojnexus.core.data.sync.SyncStage
import com.ojnexus.core.data.sync.StageOutcome
import com.ojnexus.core.database.entity.JudgeAccountEntity
import kotlinx.coroutines.CancellationException

/**
 * Orchestrates one sync run across the five stages. The Worker owns lifecycle and
 * WorkManager results; this class owns sync business logic:
 *
 *  - stages run in order (PROFILE → RATING → SUBMISSIONS → CONTESTS → PROBLEMSET);
 *  - a stage failure never aborts the run: already-persisted data stays and the report
 *    marks the run PARTIAL (all-failed → ERROR);
 *  - between stages the account is re-read — a DISCONNECT during a sync stops all further
 *    writes for that account (cancellation propagates, never swallowed);
 *  - `force = false` lets fresh modules no-op (freshness policy); manual sync forces.
 */
class CodeforcesSyncCoordinator(
    private val accountRepository: com.ojnexus.core.data.repository.JudgeAccountRepository,
    private val syncRepository: CodeforcesSyncRepository,
) : com.ojnexus.judge.JudgeSyncCoordinator {

    override val judgeId = com.ojnexus.core.model.JudgeId.CODEFORCES

    override suspend fun syncAccount(accountId: Long, force: Boolean): SyncReport? {
        val account = accountRepository.findById(accountId) ?: return null
        val outcomes = mutableListOf<StageOutcome>()

        outcomes += syncRepository.syncProfile(account, force)
        ensureActive(account)

        outcomes += syncRepository.syncRating(account, force)
        ensureActive(account)

        outcomes += syncRepository.syncSubmissions(account, force)
        ensureActive(account)

        outcomes += syncRepository.syncContests(account, force)
        ensureActive(account)

        outcomes += syncRepository.syncProblemset(account, force)

        val report = SyncReport(outcomes)
        syncRepository.finalizeSync(account.judge, report)
        return report
    }

    /** Stops the pipeline when the user disconnected mid-sync (cancellation-safe). */
    private suspend fun ensureActive(account: JudgeAccountEntity) {
        val current = accountRepository.findById(account.id)
        if (current == null || !current.enabled) {
            throw CancellationException("judge account disconnected during sync")
        }
    }
}
