package com.ojnexus.judge.sync

import com.ojnexus.core.database.entity.JudgeAccountEntity
import com.ojnexus.core.model.JudgeId

/** Restores periodic sync work for existing accounts without performing an immediate sync. */
class JudgeSyncBootstrap(
    private val activeAccount: suspend (JudgeId) -> JudgeAccountEntity?,
    private val backgroundJudges: Set<JudgeId>,
    private val enqueuePeriodic: (JudgeId, Long) -> Unit,
) {
    suspend fun reconcile() {
        backgroundJudges
            .sortedBy { it.id }
            .forEach { judge ->
                val account = activeAccount(judge)
                    ?.takeIf { it.enabled }
                    ?: return@forEach
                runCatching { enqueuePeriodic(judge, account.id) }
            }
    }
}
