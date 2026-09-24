package com.ojnexus.feature.dashboard

import com.ojnexus.core.model.JudgeId

internal fun shouldShowLuoguSetup(connectedJudges: Set<JudgeId>): Boolean =
    JudgeId.LUOGU !in connectedJudges
