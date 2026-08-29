package com.ojnexus.judge

import com.ojnexus.core.data.sync.SyncReport
import com.ojnexus.core.model.JudgeId

interface JudgeSyncCoordinator {
    val judgeId: JudgeId
    suspend fun syncAccount(accountId: Long, force: Boolean): SyncReport?
}
