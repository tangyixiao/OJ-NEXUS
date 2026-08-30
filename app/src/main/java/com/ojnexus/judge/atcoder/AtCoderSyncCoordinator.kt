package com.ojnexus.judge.atcoder

import com.ojnexus.core.data.repository.JudgeAccountRepository
import com.ojnexus.core.data.sync.SyncReport
import com.ojnexus.core.database.entity.JudgeAccountEntity
import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.JudgeSyncCoordinator
import kotlinx.coroutines.CancellationException

class AtCoderSyncCoordinator(
    private val accountRepository: JudgeAccountRepository,
    private val syncRepository: AtCoderSyncRepository,
) : JudgeSyncCoordinator {
    override val judgeId = JudgeId.ATCODER

    override suspend fun syncAccount(accountId: Long, force: Boolean): SyncReport? {
        val account = accountRepository.findById(accountId)
            ?.takeIf { it.judge == JudgeId.ATCODER.id && it.enabled }
            ?: return null
        val outcomes = mutableListOf<com.ojnexus.core.data.sync.StageOutcome>()
        outcomes += syncRepository.syncSubmissions(account, force)
        ensureActive(account)
        outcomes += syncRepository.syncContests(account, force)
        ensureActive(account)
        outcomes += syncRepository.syncProblems(account, force)
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
