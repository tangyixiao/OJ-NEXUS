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
) : ViewModel() {

    private val todayEpochDay: Long = LocalDate.ofInstant(clock.instant(), clock.zone).toEpochDay()

    val state: StateFlow<Loadable<DashboardUiState>> = combine(
        trainingRepository.observeTasks(todayEpochDay),
        analyticsRepository.observeDailyActivity(7),
        analyticsRepository.observeStreaks(days = 365),
        reviewRepository.observeQueue(),
        analyticsRepository.observeRecentAttempts(limit = 6),
    ) { tasks, week, streaks, queue, recent ->
        Loadable.Ready(
            DashboardUiState(
                todayTasks = tasks,
                week = WeekSummary(
                    solved = week.sumOf { it.solved },
                    attempts = week.sumOf { it.attempts },
                    trainingMs = week.sumOf { it.trainingMs },
                ),
                currentStreak = streaks.current,
                longestStreak = streaks.longest,
                nextReview = queue.filter { it.dueDayIndex <= todayEpochDay }
                    .minByOrNull { it.dueDayIndex },
                recent = recent,
                loadWeek = week.sortedBy { it.dayIndex }.map { ActivityScorer.intensity(it) },
            ),
        )
    }
        .catch<Loadable<DashboardUiState>> { emit(Loadable.Failed(it.message ?: "Load failed")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)
}
