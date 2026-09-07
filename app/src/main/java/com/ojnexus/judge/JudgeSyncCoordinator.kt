package com.ojnexus.judge

import com.ojnexus.core.data.sync.SyncReport
import com.ojnexus.core.data.sync.SyncRunContext
import com.ojnexus.core.model.JudgeId

interface JudgeSyncCoordinator {
    val judgeId: JudgeId
    suspend fun syncAccount(accountId: Long, force: Boolean): SyncReport?

    /** Optional durable receipt context; legacy coordinators retain the two-argument path. */
    suspend fun syncAccount(
        accountId: Long,
        force: Boolean,
        context: SyncRunContext,
    ): SyncReport? = syncAccount(accountId, force)
}
