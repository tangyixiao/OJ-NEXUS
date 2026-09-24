package com.ojnexus.judge.sync

import com.ojnexus.core.model.JudgeId

object JudgeWorkNames {
    fun manual(judge: JudgeId, accountId: Long) = "judge-sync-manual-${judge.id}-$accountId"
    fun periodic(judge: JudgeId, accountId: Long) = "judge-sync-periodic-${judge.id}-$accountId"
}
