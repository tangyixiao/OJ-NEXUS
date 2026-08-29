package com.ojnexus.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojnexus.core.data.repository.AnalyticsRepository
import com.ojnexus.core.data.repository.ProblemRepository
import com.ojnexus.core.data.repository.ReviewRepository
import com.ojnexus.core.data.repository.TrainingRepository
import com.ojnexus.core.domain.ActivityScorer
import com.ojnexus.core.model.ReviewQueueItem
import com.ojnexus.core.model.TrainingTask
import com.ojnexus.core.ui.Loadable
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class WeekSummary(
    val solved: Int,
    val attempts: Int,
    val trainingMs: Long,
)

data class DashboardUiState(
    val todayTasks: List<TrainingTask>,
    val week: WeekSummary,
    val currentStreak: Int,
    val longestStreak: Int,
    /** Earliest due review (overdue or due today); null when nothing is pending. */
    val nextReview: ReviewQueueItem?,
    val recent: List<com.ojnexus.core.model.RecentAttempt>,
    /** Heatmap-style intensities (0–4) for the last 7 days, oldest first. */
    val loadWeek: List<Int>,
    // --- Codeforces connection (null until connected) ---
    val cfAccount: com.ojnexus.core.database.entity.JudgeAccountEntity? = null,
    val cfProfile: com.ojnexus.core.database.entity.JudgeProfileEntity? = null,
    val cfSyncState: com.ojnexus.core.database.entity.SyncStateEntity? = null,
    /** Earliest upcoming contest with an announced start time. */
    val nextContest: com.ojnexus.core.database.entity.ContestEntity? = null,
    val nowSeconds: Long,
)

/**
 * Dashboard over local data only. There is deliberately no rating: no OJ is connected in
 * Phase 1, and fake numbers are forbidden. OJ CONNECTION renders NOT CONNECTED.
 */
class DashboardViewModel(
    trainingRepository: TrainingRepository,
    reviewRepository: ReviewRepository,
    analyticsRepository: AnalyticsRepository,
    private val clock: Clock,
    private val syncRepository: com.ojnexus.judge.codeforces.CodeforcesSyncRepository,
    private val accountRepository: com.ojnexus.core.data.repository.JudgeAccountRepository,
) : ViewModel() {

    private val todayEpochDay: Long = java.time.LocalDate.ofInstant(clock.instant(), clock.zone).toEpochDay()

    /** Local-only snapshot feeding the outer combine. */
    private data class LocalSnapshot(
        val tasks: List<TrainingTask>,
        val week: List<com.ojnexus.core.domain.DayActivity>,
        val streaks: com.ojnexus.core.data.repository.Streaks,
        val queue: List<com.ojnexus.core.model.ReviewQueueItem>,
        val recent: List<com.ojnexus.core.model.RecentAttempt>,
    )

    private val localSnapshot = combine(
        trainingRepository.observeTasks(todayEpochDay),
        analyticsRepository.observeDailyActivity(7),
        analyticsRepository.observeStreaks(days = 365),
        reviewRepository.observeQueue(),
        analyticsRepository.observeRecentAttempts(limit = 6),
    ) { tasks, week, streaks, queue, recent ->
        LocalSnapshot(tasks, week, streaks, queue, recent)
    }

    val state: StateFlow<Loadable<DashboardUiState>> = combine(
        localSnapshot,
        accountRepository.observeActive(com.ojnexus.core.model.JudgeId.CODEFORCES),
        syncRepository.observeProfile(com.ojnexus.core.model.JudgeId.CODEFORCES),
        syncRepository.observeSyncStateFlow(com.ojnexus.core.model.JudgeId.CODEFORCES),
        syncRepository.observeContests(com.ojnexus.core.model.JudgeId.CODEFORCES),
    ) { local, account, profile, syncState, contests ->
        val now = clock.instant().epochSecond
        Loadable.Ready(
            DashboardUiState(
                todayTasks = local.tasks,
                week = WeekSummary(
                    solved = local.week.sumOf { it.solved },
                    attempts = local.week.sumOf { it.attempts },
                    trainingMs = local.week.sumOf { it.trainingMs },
                ),
                currentStreak = local.streaks.current,
                longestStreak = local.streaks.longest,
                nextReview = local.queue.filter { it.dueDayIndex <= todayEpochDay }
                    .minByOrNull { it.dueDayIndex },
                recent = local.recent,
                loadWeek = local.week.sortedBy { it.dayIndex }.map { ActivityScorer.intensity(it) },
                cfAccount = account,
                cfProfile = profile,
                cfSyncState = syncState,
                nextContest = contests
                    .filter { (it.startTimeSeconds ?: 0L) > now }
                    .minByOrNull { it.startTimeSeconds ?: Long.MAX_VALUE },
                nowSeconds = now,
            ),
        )
    }
        .catch<Loadable<DashboardUiState>> { emit(Loadable.Failed(it.message ?: "Load failed")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)
}
