package com.ojnexus.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojnexus.core.data.repository.AnalyticsRepository
import com.ojnexus.core.data.repository.ProblemRepository
import com.ojnexus.core.domain.DayActivity
import com.ojnexus.core.model.Verdict
import com.ojnexus.core.ui.Loadable
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AnalyticsUiState(
    /** Full activity days (oldest first) for the heatmap window. */
    val heatmapDays: List<DayActivity>,
    /** Epoch day of the first heatmap cell's week (Monday) for grid alignment. */
    val gridStartEpochDay: Long,
    /** Daily solved counts, oldest first, last N days. */
    val solveTrend: List<DayActivity>,
    val verdictCounts: List<Pair<Verdict, Int>>,
    /** Difficulty -> solved count; null key = unknown difficulty. */
    val difficultyCounts: List<Pair<Int?, Int>>,
    val totals: com.ojnexus.core.data.repository.Totals,
    /** Total finished training time in ms within the trend window. */
    val trainingMsTotal: Long,
    val dailyTrainingMs: List<DayActivity>,
    val currentStreak: Int,
    val longestStreak: Int,
) {
    /** True when there is nothing to show yet — drives the empty state. */
    val isEmpty: Boolean
        get() = totals.attempts == 0 && totals.problems == 0
}

class AnalyticsViewModel(
    analyticsRepository: AnalyticsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val heatmapWindowDays = 365
    private val trendWindowDays = 14

    val state: StateFlow<Loadable<AnalyticsUiState>> = combine(
        analyticsRepository.observeDailyActivity(heatmapWindowDays),
        analyticsRepository.observeVerdictCounts(),
        analyticsRepository.observeDifficultyCounts(),
        analyticsRepository.observeTotals(),
        analyticsRepository.observeStreaks(days = heatmapWindowDays),
    ) { daily, verdicts, difficulties, totals, streaks ->
        Loadable.Ready(
            AnalyticsUiState(
                heatmapDays = daily,
                gridStartEpochDay = gridStart(daily),
                solveTrend = daily.takeLast(trendWindowDays),
                verdictCounts = verdicts,
                difficultyCounts = difficulties,
                totals = totals,
                trainingMsTotal = daily.sumOf { it.trainingMs },
                dailyTrainingMs = daily.takeLast(trendWindowDays),
                currentStreak = streaks.current,
                longestStreak = streaks.longest,
            ),
        )
    }
        .catch<Loadable<AnalyticsUiState>> { emit(Loadable.Failed(it.message ?: "Load failed")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    /** Monday of the week containing the first heatmap day. */
    private fun gridStart(days: List<DayActivity>): Long {
        val first = days.firstOrNull()?.dayIndex ?: return LocalDate.ofInstant(clock.instant(), clock.zone).toEpochDay()
        val dayOfWeek = LocalDate.ofEpochDay(first).dayOfWeek.value // Mon=1..Sun=7
        return first - (dayOfWeek - 1)
    }
}
