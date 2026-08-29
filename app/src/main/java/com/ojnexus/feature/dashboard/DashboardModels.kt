package com.ojnexus.feature.dashboard

import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.Verdict

/** Per-judge connection line shown in SYSTEM STATUS. */
data class JudgeSyncUi(
    val judge: JudgeId,
    val state: SyncStateUi,
    /** Preformatted last-sync text; null when the judge has never synced. */
    val lastSyncText: String? = null,
)

enum class SyncStateUi {
    SYNCED,
    SYNCING,
    NOT_LINKED,
    ERROR,
}

data class TodayTaskUi(
    val code: String,
    val title: String,
    val priority: Int,
    val masteryPercent: Int,
)

data class NextContestUi(
    val name: String,
    val judge: JudgeId,
    /** Preformatted start text (Phase 0); the real countdown is computed by the contest engine. */
    val startsText: String,
    val countdownText: String,
)

data class SubmissionRowUi(
    val timeText: String,
    val judge: JudgeId,
    val problemCode: String,
    val verdict: Verdict,
)

data class DashboardUiState(
    val sync: List<JudgeSyncUi>,
    val rating: Int,
    val ratingDelta: Int,
    val weeklyAc: Int,
    val streakDays: Int,
    val todayTasks: List<TodayTaskUi>,
    val nextContest: NextContestUi?,
    val recentActivity: List<SubmissionRowUi>,
    /** One intensity (0–4) per weekday, Monday first. */
    val trainingLoad: List<Int>,
)
