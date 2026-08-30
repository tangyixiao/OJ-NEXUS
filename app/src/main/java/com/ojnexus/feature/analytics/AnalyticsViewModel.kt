package com.ojnexus.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojnexus.core.data.repository.AnalyticsRepository
import com.ojnexus.core.data.repository.FirstTryAc
import com.ojnexus.core.data.repository.TagPerformance
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
    val cfConnected: Boolean,
    val ratingHistory: List<com.ojnexus.core.database.entity.RatingChangeEntity>,
    val judgeAttemptCounts: List<Pair<com.ojnexus.core.model.JudgeId, Int>>,
    val difficultyByJudge: Map<com.ojnexus.core.model.JudgeId, List<Pair<Int?, Int>>>,
    val firstTryAc: FirstTryAc,
    val tagPerformance: List<TagPerformance>,
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

    private val baseData = combine(
        analyticsRepository.observeDailyActivity(heatmapWindowDays),
        analyticsRepository.observeVerdictCounts(),
        analyticsRepository.observeDifficultyCounts(),
        analyticsRepository.observeTotals(),
        analyticsRepository.observeStreaks(days = heatmapWindowDays),
    ) { daily, verdicts, difficulties, totals, streaks ->
        LocalAnalytics(daily, verdicts, difficulties, totals, streaks)
    }

    private data class LocalAnalytics(
        val daily: List<DayActivity>,
        val verdictCounts: List<Pair<Verdict, Int>>,
        val difficultyCounts: List<Pair<Int?, Int>>,
        val totals: com.ojnexus.core.data.repository.Totals,
        val streaks: com.ojnexus.core.data.repository.Streaks,
        val firstTryAc: FirstTryAc = FirstTryAc(0, 0),
        val tagPerformance: List<TagPerformance> = emptyList(),
    )

    private val localData = combine(
        baseData,
        analyticsRepository.observeFirstTryAc(),
        analyticsRepository.observeTagPerformance(),
    ) { base, firstTryAc, tagPerformance ->
        base.copy(firstTryAc = firstTryAc, tagPerformance = tagPerformance)
    }

    private val judgeBreakdown = combine(
        analyticsRepository.observeJudgeAttemptCounts(),
        analyticsRepository.observeDifficultyCountsByJudge(),
    ) { attempts, difficulties -> attempts to difficulties }

    val state: StateFlow<Loadable<AnalyticsUiState>> = kotlinx.coroutines.flow.combine(
        localData,
        analyticsRepository.observeJudgeAccounts(),
        analyticsRepository.observeRatingChanges(com.ojnexus.core.model.JudgeId.CODEFORCES.id),
        judgeBreakdown,
    ) { local, accounts, ratingHistory, breakdown ->
        Loadable.Ready(
            AnalyticsUiState(
                heatmapDays = local.daily,
                gridStartEpochDay = gridStart(local.daily),
                solveTrend = local.daily.takeLast(trendWindowDays),
                verdictCounts = local.verdictCounts,
                difficultyCounts = local.difficultyCounts,
                totals = local.totals,
                trainingMsTotal = local.daily.sumOf { it.trainingMs },
                dailyTrainingMs = local.daily.takeLast(trendWindowDays),
                currentStreak = local.streaks.current,
                longestStreak = local.streaks.longest,
                cfConnected = accounts.any {
                    it.judge == com.ojnexus.core.model.JudgeId.CODEFORCES.id && it.enabled
                },
                ratingHistory = ratingHistory,
                judgeAttemptCounts = breakdown.first,
                difficultyByJudge = breakdown.second,
                firstTryAc = local.firstTryAc,
                tagPerformance = local.tagPerformance,
            ),
        )
    }
        .catch<Loadable<AnalyticsUiState>> { emit(Loadable.Failed(it.message ?: "Load failed")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    /** Monday of the week containing the first heatmap day. */
    private fun gridStart(days: List<DayActivity>): Long {
        val first = days.firstOrNull()?.dayIndex ?: return clock.instant().atZone(clock.zone).toLocalDate().toEpochDay()
        val dayOfWeek = LocalDate.ofEpochDay(first).dayOfWeek.value // Mon=1..Sun=7
        return first - (dayOfWeek - 1)
    }
}
