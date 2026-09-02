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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive

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
    val summary: DashboardSummary,
    val judgeConnections: List<JudgeDashboardConnection> = emptyList(),
    // Codeforces rating remains source-native and official.
    val cfAccount: com.ojnexus.core.database.entity.JudgeAccountEntity? = null,
    val cfProfile: com.ojnexus.core.database.entity.JudgeProfileEntity? = null,
    val cfSyncState: com.ojnexus.core.database.entity.SyncStateEntity? = null,
    /** Earliest upcoming contest with an announced start time. */
    val nextContest: com.ojnexus.core.database.entity.ContestEntity? = null,
    val nowSeconds: Long,
)

data class JudgeDashboardConnection(
    val judge: com.ojnexus.core.model.JudgeId,
    val account: com.ojnexus.core.database.entity.JudgeAccountEntity,
    val syncState: com.ojnexus.core.database.entity.SyncStateEntity?,
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
    private val judgeDataRepository: com.ojnexus.core.data.repository.JudgeDataRepository,
) : ViewModel() {

    private val todayEpochDay: Long = clock.instant().atZone(clock.zone).toLocalDate().toEpochDay()

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

    private val clockTicks = flow {
        while (currentCoroutineContext().isActive) {
            emit(clock.instant().epochSecond)
            delay(CLOCK_TICK_INTERVAL_MS)
        }
    }

    val state: StateFlow<Loadable<DashboardUiState>> = combine(
        localSnapshot,
        judgeDataRepository.observeConnections(),
        judgeDataRepository.observeContests(),
        clockTicks,
    ) { local, connections, contests, now ->
        val cfAccount = connections.accounts[com.ojnexus.core.model.JudgeId.CODEFORCES]
        val week = WeekSummary(
            solved = local.week.sumOf { it.solved },
            attempts = local.week.sumOf { it.attempts },
            trainingMs = local.week.sumOf { it.trainingMs },
        )
        val dueReviews = local.queue.filter { it.dueDayIndex <= todayEpochDay }
        val judgeConnections = connections.accounts.mapNotNull { (judge, account) ->
            if (!account.enabled) null else JudgeDashboardConnection(judge, account, connections.syncStates[judge])
        }.sortedBy { it.judge.ordinal }
        val nextContest = contests
            .filter { (it.startTimeSeconds ?: 0L) > now }
            .minByOrNull { it.startTimeSeconds ?: Long.MAX_VALUE }
        Loadable.Ready(
            DashboardUiState(
                todayTasks = local.tasks,
                week = week,
                currentStreak = local.streaks.current,
                longestStreak = local.streaks.longest,
                nextReview = dueReviews.minByOrNull { it.dueDayIndex },
                recent = local.recent,
                loadWeek = local.week.sortedBy { it.dayIndex }.map { ActivityScorer.intensity(it) },
                summary = deriveDashboardSummary(
                    reviews = local.queue,
                    todayEpochDay = todayEpochDay,
                    enabledJudgeCount = judgeConnections.size,
                    solvedThisWeek = week.solved,
                    contests = contests,
                    nowSeconds = now,
                ),
                judgeConnections = judgeConnections,
                cfAccount = cfAccount,
                cfProfile = connections.profiles[com.ojnexus.core.model.JudgeId.CODEFORCES],
                cfSyncState = connections.syncStates[com.ojnexus.core.model.JudgeId.CODEFORCES],
                nextContest = nextContest,
                nowSeconds = now,
            ),
        )
    }
        .catch<Loadable<DashboardUiState>> {
            emit(Loadable.Failed(it.message ?: com.ojnexus.core.ui.localizedString(com.ojnexus.R.string.error_load_failed)))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)
}

private const val CLOCK_TICK_INTERVAL_MS = 60_000L
