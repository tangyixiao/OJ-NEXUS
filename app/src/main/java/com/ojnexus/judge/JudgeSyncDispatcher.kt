package com.ojnexus.judge

import com.ojnexus.core.data.repository.JudgeAccountRepository
import com.ojnexus.core.data.sync.SyncReport
import com.ojnexus.core.model.JudgeId

/** Validates worker input identity before routing to the registered judge coordinator. */
class JudgeSyncDispatcher(
    private val accountRepository: JudgeAccountRepository,
    private val registry: JudgeRegistry,
) {
    suspend fun sync(judge: JudgeId, accountId: Long, force: Boolean): SyncReport? {
        val account = accountRepository.findById(accountId)
            ?.takeIf { it.enabled && it.judge == judge.id }
            ?: return null
        return registry.syncCoordinator(judge).syncAccount(account.id, force)
    }
}
