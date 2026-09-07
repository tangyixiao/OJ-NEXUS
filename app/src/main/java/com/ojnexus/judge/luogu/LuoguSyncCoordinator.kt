package com.ojnexus.judge.luogu

import com.ojnexus.core.data.repository.JudgeAccountRepository
import com.ojnexus.core.data.sync.StageOutcome
import com.ojnexus.core.data.sync.SyncReport
import com.ojnexus.core.data.sync.SyncRunContext
import com.ojnexus.core.database.entity.JudgeAccountEntity
import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.JudgeSyncCoordinator
import kotlinx.coroutines.CancellationException

/** Orchestrates only the public Luogu stages advertised by the adapter. */
class LuoguSyncCoordinator(
    private val accountRepository: JudgeAccountRepository,
    private val syncRepository: LuoguSyncRepository,
) : JudgeSyncCoordinator {
    override val judgeId = JudgeId.LUOGU

    override suspend fun syncAccount(accountId: Long, force: Boolean): SyncReport? =
        syncAccountInternal(accountId, force, recordModule = null)

    override suspend fun syncAccount(
        accountId: Long,
        force: Boolean,
        context: SyncRunContext,
    ): SyncReport? = syncAccountInternal(accountId, force, context::recordModule)

    private suspend fun syncAccountInternal(
        accountId: Long,
        force: Boolean,
        recordModule: (suspend (StageOutcome) -> Unit)?,
    ): SyncReport? {
        val account = accountRepository.findById(accountId)
            ?.takeIf { it.judge == JudgeId.LUOGU.id && it.enabled }
            ?: return null
        val outcomes = mutableListOf<StageOutcome>()

        val profile = syncRepository.syncProfile(account, force)
        outcomes += profile
        recordModule?.invoke(profile)
        ensureActive(account)
        val rating = syncRepository.syncRating(account, force)
        outcomes += rating
        recordModule?.invoke(rating)
        ensureActive(account)
        val contests = syncRepository.syncContests(account, force)
        outcomes += contests
        recordModule?.invoke(contests)
        ensureActive(account)
        val problems = syncRepository.syncProblems(account, force)
        outcomes += problems
        recordModule?.invoke(problems)

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
