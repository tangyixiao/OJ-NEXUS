package com.ojnexus.judge.luogu.open

import com.ojnexus.core.database.dao.SubmissionJobDao

internal const val MAX_PENDING_BOOTSTRAP = 50

class LuoguResultWorkBootstrap(
    private val submissionJobDao: SubmissionJobDao,
    private val scheduler: LuoguResultWorkScheduler,
) {
    suspend fun reconcilePending(limit: Int = MAX_PENDING_BOOTSTRAP) {
        val safeLimit = limit.coerceIn(0, MAX_PENDING_BOOTSTRAP)
        submissionJobDao.findPendingForBackground(safeLimit).forEach { job ->
            runCatching { scheduler.enqueue(job.requestId) }
        }
    }
}
