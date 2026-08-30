package com.ojnexus.judge.luogu

import com.ojnexus.core.data.repository.JudgeAccountRepository
import com.ojnexus.core.data.sync.StageOutcome
import com.ojnexus.core.data.sync.SyncReport
import com.ojnexus.core.database.entity.JudgeAccountEntity
import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.JudgeSyncCoordinator
import kotlinx.coroutines.CancellationException

/** Orchestrates public Luogu stages and records the private-data limitation as PARTIAL. */
class LuoguSyncCoordinator(
    private val accountRepository: JudgeAccountRepository,
    private val syncRepository: LuoguSyncRepository,
) : JudgeSyncCoordinator {
    override val judgeId = JudgeId.LUOGU

    override suspend fun syncAccount(accountId: Long, force: Boolean): SyncReport? {
        val account = accountRepository.findById(accountId)
            ?.takeIf { it.judge == JudgeId.LUOGU.id && it.enabled }
            ?: return null
        val outcomes = mutableListOf<StageOutcome>()

        outcomes += syncRepository.syncProfile(account, force)
        ensureActive(account)
        outcomes += syncRepository.syncRating(account, force)
        ensureActive(account)
        outcomes += syncRepository.syncContests(account, force)
        ensureActive(account)
        outcomes += syncRepository.syncProblems(account, force)
        ensureActive(account)
        outcomes += syncRepository.syncSubmissions(account, force)

        val report = SyncReport(outcomes)
        syncRepository.finalizeSync(report)
        return report
    }

    private suspend fun ensureActive(account: JudgeAccountEntity) {
        val current = accountRepository.findById(account.id)
        if (current == null || !current.enabled) {
            throw CancellationException("judge account disconnected during sync")
        }
    }
}
