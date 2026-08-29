package com.ojnexus.feature.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ojnexus.R
import com.ojnexus.core.designsystem.NexusRadius
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.component.NexusDivider
import com.ojnexus.core.designsystem.component.NexusMetric
import com.ojnexus.core.designsystem.component.NexusSection
import com.ojnexus.core.designsystem.component.NexusTag
import com.ojnexus.core.designsystem.component.NexusTopBar
import com.ojnexus.core.designsystem.component.foregroundColor
import com.ojnexus.core.designsystem.NexusTone
import com.ojnexus.core.domain.ActivityScorer
import com.ojnexus.core.domain.DayActivity
import com.ojnexus.core.ui.ContainerViewModelFactory
import com.ojnexus.core.ui.LocalAppContainer
import com.ojnexus.core.ui.Loadable
import com.ojnexus.core.ui.formatCount
import com.ojnexus.core.ui.formatDate
import com.ojnexus.core.ui.formatDuration
import com.ojnexus.core.ui.formatDateTime
import com.ojnexus.core.ui.labelRes
import com.ojnexus.core.ui.tone
import java.time.LocalDate

// Analytics layout metrics.
private val HeatmapCellSize = 9.dp
private val HeatmapCellSpacing = 2.dp
private val TrendBarHeight = 48.dp
private val DistributionBarHeight = 8.dp
private val DistributionLabelWidth = 56.dp
private val DistributionCountWidth = 48.dp
private val RatingChartHeight = 140.dp

@Composable
fun AnalyticsScreen() {
    val container = LocalAppContainer.current
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<AnalyticsViewModel>(
        factory = ContainerViewModelFactory(container) {
            AnalyticsViewModel(
                analyticsRepository = it.analyticsRepository,
                clock = it.clock,
            )
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background),
    ) {
        NexusTopBar(title = stringResource(R.string.nav_analytics))
        when (val s = state) {
            Loadable.Loading -> Box(Modifier.fillMaxSize())
            is Loadable.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = s.message, style = NexusTheme.typography.data, color = NexusTheme.colors.danger)
            }
            is Loadable.Ready -> AnalyticsContent(s.value)
        }
    }
}

@Composable
private fun AnalyticsContent(state: AnalyticsUiState) {
    if (state.isEmpty) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(NexusSpacing.screenHorizontal),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.analytics_empty_title),
                style = NexusTheme.typography.dataLarge,
                color = NexusTheme.colors.textTertiary,
            )
            Spacer(modifier = Modifier.height(NexusSpacing.xs))
            Text(
                text = stringResource(R.string.analytics_empty_hint),
                style = NexusTheme.typography.dataSmall,
                color = NexusTheme.colors.textTertiary,
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NexusSpacing.screenHorizontal),
    ) {
            Spacer(modifier = Modifier.height(NexusSpacing.md))
            HeatmapSection(state)
            SectionGap()
            if (state.cfConnected && state.ratingHistory.isNotEmpty()) {
                RatingSection(state.ratingHistory)
                SectionGap()
            }
            TotalsSection(state)
        SectionGap()
        TrendSection(state)
        SectionGap()
        VerdictSection(state.verdictCounts)
        SectionGap()
        DifficultySection(state.difficultyCounts)
        SectionGap()
        TrainingTimeSection(state)
        Spacer(modifier = Modifier.height(NexusSpacing.xxl))
    }
}

@Composable
private fun SectionGap() {
    Spacer(modifier = Modifier.height(NexusSpacing.md))
    NexusDivider()
    Spacer(modifier = Modifier.height(NexusSpacing.md))
}

@Composable
private fun HeatmapSection(state: AnalyticsUiState) {
    val colors = NexusTheme.colors
    val description = stringResource(R.string.analytics_heatmap_cd)
    var selectedDay by remember { mutableStateOf<DayActivity?>(null) }

    NexusSection(label = stringResource(R.string.analytics_section_heatmap)) {
        val weeks = state.heatmapDays.groupByWeeks(state.gridStartEpochDay)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .semantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(HeatmapCellSpacing),
        ) {
            weeks.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(HeatmapCellSpacing)) {
                    week.forEach { day ->
                        val intensity = day?.let { ActivityScorer.intensity(it) } ?: -1
                        val label = stringResource(
                            R.string.analytics_heatmap_cell_cd,
                            intensity.coerceAtLeast(0),
                            day?.solved ?: 0,
                        )
                        Box(
                            modifier = Modifier
                                .size(HeatmapCellSize)
                                .background(cellColor(intensity), NexusRadius.xs)
                                .then(
                                    if (day != null) {
                                        Modifier.clickable { selectedDay = day }
                                    } else {
                                        Modifier
                                    },
                                )
                                .semantics { contentDescription = label },
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(NexusSpacing.xs))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HeatmapCellSpacing),
        ) {
            Text(
                text = stringResource(R.string.heatmap_legend_less),
                style = NexusTheme.typography.sectionLabel,
                color = colors.textTertiary,
            )
            for (intensity in 0..4) {
                Box(
                    modifier = Modifier
                        .size(HeatmapCellSize)
                        .background(cellColor(intensity), NexusRadius.xs),
                )
            }
            Text(
                text = stringResource(R.string.heatmap_legend_more),
                style = NexusTheme.typography.sectionLabel,
                color = colors.textTertiary,
            )
        }
        selectedDay?.let { day ->
            Spacer(modifier = Modifier.height(NexusSpacing.sm))
            NexusDivider()
            Spacer(modifier = Modifier.height(NexusSpacing.xxs))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatDate(LocalDate.ofEpochDay(day.dayIndex).toEpochDay() * 24L * 60 * 60 * 1000),
                        style = NexusTheme.typography.dataSmall,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = if (ActivityScorer.score(day) == 0) {
                            stringResource(R.string.analytics_day_zero)
                        } else {
                            "SCORE ${ActivityScorer.score(day)} · ${ActivityScorer.intensity(day)}/4"
                        },
                        style = NexusTheme.typography.dataSmall,
                        color = colors.textTertiary,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${stringResource(R.string.analytics_day_solved)} ${day.solved} · " +
                            "${stringResource(R.string.analytics_day_attempts)} ${day.attempts}",
                        style = NexusTheme.typography.dataSmall,
                        color = colors.textSecondary,
                    )
                    Text(
                        text = "${stringResource(R.string.analytics_day_reviews)} ${day.reviewsCompleted} · " +
                            "${stringResource(R.string.analytics_day_active)} ${formatDuration(day.trainingMs / 60_000)}",
                        style = NexusTheme.typography.dataSmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

/**
 * Buckets the ordered day list into columns of 7 aligned on Monday, with leading/trailing
 * nulls for grid padding.
 */
private fun List<DayActivity>.groupByWeeks(gridStart: Long): List<List<DayActivity?>> {
    if (isEmpty()) return emptyList()
    val byDay = associateBy { it.dayIndex }
    val last = last().dayIndex
    val columns = mutableListOf<List<DayActivity?>>()
    var weekStart = gridStart
    while (weekStart <= last) {
        columns.add(
            (0 until 7).map { offset ->
                byDay[weekStart + offset]
            },
        )
        weekStart += 7
    }
    return columns
}

@Composable
private fun cellColor(intensity: Int) = with(NexusTheme.colors) {
    if (intensity <= 0) {
        surfaceElevated
    } else {
        accent.copy(alpha = 0.25f + 0.1625f * intensity.coerceIn(1, 4))
    }
}

@Composable
private fun TotalsSection(state: AnalyticsUiState) {
    NexusSection(label = stringResource(R.string.analytics_section_totals)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            NexusMetric(
                label = stringResource(R.string.analytics_total_problems),
                value = formatCount(state.totals.problems),
                modifier = Modifier.weight(1f),
            )
            MetricSeparator()
            NexusMetric(
                label = stringResource(R.string.analytics_total_solved),
                value = formatCount(state.totals.solved),
                modifier = Modifier.weight(1f),
            )
            MetricSeparator()
            NexusMetric(
                label = stringResource(R.string.analytics_total_attempts),
                value = formatCount(state.totals.attempts),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(NexusSpacing.sm))
        Row(modifier = Modifier.fillMaxWidth()) {
            NexusMetric(
                label = stringResource(R.string.analytics_total_ac),
                value = formatCount(state.totals.ac),
                modifier = Modifier.weight(1f),
            )
            MetricSeparator()
            NexusMetric(
                label = stringResource(R.string.analytics_ratio),
                value = String.format(
                    java.util.Locale.getDefault(),
                    "%.2f",
                    state.totals.attemptAcRatio,
                ),
                modifier = Modifier.weight(1f),
            )
            MetricSeparator()
            NexusMetric(
                label = stringResource(R.string.metric_streak),
                value = stringResource(R.string.format_streak_days, state.currentStreak),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TrendSection(state: AnalyticsUiState) {
    val colors = NexusTheme.colors
    NexusSection(
        label = stringResource(R.string.analytics_section_trend),
        trailing = {
            Text(
                text = formatCount(state.solveTrend.sumOf { it.solved }),
                style = NexusTheme.typography.dataLarge,
                color = colors.textPrimary,
            )
        },
    ) {
        val maxSolved = state.solveTrend.maxOfOrNull { it.solved } ?: 0
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TrendBarHeight),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs),
        ) {
            state.solveTrend.forEach { day ->
                val fraction = if (maxSolved <= 0) 0f else day.solved.toFloat() / maxSolved
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height((TrendBarHeight * fraction).coerceAtLeast(2.dp))
                        .background(colors.accent.copy(alpha = 0.7f), NexusRadius.xs),
                )
            }
        }
        Spacer(modifier = Modifier.height(NexusSpacing.xxs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs),
        ) {
            state.solveTrend.forEach { day ->
                Text(
                    text = formatDate(day.dayIndex * 24L * 60 * 60 * 1000),
                    style = NexusTheme.typography.sectionLabel,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun VerdictSection(counts: List<Pair<com.ojnexus.core.model.Verdict, Int>>) {
    val colors = NexusTheme.colors
    NexusSection(label = stringResource(R.string.analytics_section_verdict)) {
        val maxCount = counts.maxOfOrNull { it.second } ?: 0
        counts.forEach { (verdict, count) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = NexusSpacing.xxxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(verdict.labelRes()),
                    style = NexusTheme.typography.dataSmall,
                    color = verdict.tone().foregroundColor(colors),
                    modifier = Modifier.width(DistributionLabelWidth),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(DistributionBarHeight)
                        .background(colors.surfaceElevated, NexusRadius.xs),
                ) {
                    val fraction = if (maxCount <= 0) 0f else (count.toFloat() / maxCount).coerceIn(0.02f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(DistributionBarHeight)
                            .background(verdict.tone().foregroundColor(colors), NexusRadius.xs),
                    )
                }
                Text(
                    text = formatCount(count),
                    style = NexusTheme.typography.dataSmall,
                    color = colors.textSecondary,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .padding(start = NexusSpacing.sm)
                        .width(DistributionCountWidth),
                )
            }
        }
    }
}

@Composable
private fun DifficultySection(counts: List<Pair<Int?, Int>>) {
    val colors = NexusTheme.colors
    NexusSection(label = stringResource(R.string.analytics_section_difficulty)) {
        val maxCount = counts.maxOfOrNull { it.second } ?: 0
        if (counts.isEmpty()) {
            Text(
                text = stringResource(R.string.analytics_day_zero),
                style = NexusTheme.typography.dataSmall,
                color = colors.textTertiary,
            )
            return@NexusSection
        }
        counts.sortedBy { it.first ?: Int.MIN_VALUE }.forEach { (difficulty, count) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = NexusSpacing.xxxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = difficulty?.toString()
                        ?: stringResource(R.string.analytics_difficulty_unknown),
                    style = NexusTheme.typography.dataSmall,
                    color = colors.textSecondary,
                    modifier = Modifier.width(DistributionLabelWidth),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(DistributionBarHeight)
                        .background(colors.surfaceElevated, NexusRadius.xs),
                ) {
                    val fraction = if (maxCount <= 0) 0f else (count.toFloat() / maxCount).coerceIn(0.02f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(DistributionBarHeight)
                            .background(colors.accent.copy(alpha = 0.65f), NexusRadius.xs),
                    )
                }
                Text(
                    text = formatCount(count),
                    style = NexusTheme.typography.dataSmall,
                    color = colors.textSecondary,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .padding(start = NexusSpacing.sm)
                        .width(DistributionCountWidth),
                )
            }
        }
    }
}

@Composable
private fun TrainingTimeSection(state: AnalyticsUiState) {
    val colors = NexusTheme.colors
    NexusSection(
        label = stringResource(R.string.analytics_section_training),
        trailing = {
            Text(
                text = formatDuration(state.trainingMsTotal / 60_000),
                style = NexusTheme.typography.dataLarge,
                color = colors.textPrimary,
            )
        },
    ) {
        val maxMs = state.dailyTrainingMs.maxOfOrNull { it.trainingMs } ?: 0L
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TrendBarHeight),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs),
        ) {
            state.dailyTrainingMs.forEach { day ->
                val fraction = if (maxMs <= 0) 0f else day.trainingMs.toFloat() / maxMs
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height((TrendBarHeight * fraction).coerceAtLeast(2.dp))
                        .background(colors.success.copy(alpha = 0.6f), NexusRadius.xs),
                )
            }
        }
    }
}

@Composable
private fun RatingSection(history: List<com.ojnexus.core.database.entity.RatingChangeEntity>) {
    val colors = NexusTheme.colors
    var selected by remember { mutableStateOf<Int?>(null) }
    NexusSection(
        label = stringResource(R.string.rating_section),
        trailing = {
            Text(
                text = history.last().newRating.toString(),
                style = NexusTheme.typography.dataLarge,
                color = colors.textPrimary,
            )
        },
    ) {
        val chartDescription = stringResource(R.string.rating_chart_cd)
        val peak = history.maxOf { it.newRating }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(RatingChartHeight)
                .semantics { contentDescription = chartDescription },
        ) {
            val min = history.minOf { minOf(it.oldRating, it.newRating) }
            val max = maxOf(peak, history.maxOf { it.newRating })
            val range = (max - min).coerceAtLeast(1)
            fun yOf(rating: Int): Float = size.height * (1f - (rating - min).toFloat() / range)
            val stepX = size.width / (history.size - 1).coerceAtLeast(1)
            val path = Path()
            history.forEachIndexed { index, point ->
                val x = stepX * index
                val y = yOf(point.newRating)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = colors.accent,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
            // Node markers; selected node highlighted.
            history.forEachIndexed { index, point ->
                val x = stepX * index
                val y = yOf(point.newRating)
                drawCircle(
                    color = if (selected == index) colors.textPrimary else colors.accent,
                    radius = if (selected == index) 6.dp.toPx() else 3.dp.toPx(),
                    center = Offset(x, y),
                )
            }
        }
        // Node selection rows (tap targets below the canvas, keyboard/screen-reader safe).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs),
        ) {
            history.indices.forEach { index ->
                NexusTag(
                    text = (index + 1).toString(),
                    tone = NexusTone.Accent,
                    selected = selected == index,
                    modifier = Modifier.clickable(role = Role.Button) {
                        selected = if (selected == index) null else index
                    },
                )
            }
        }
        selected?.let { index ->
            val point = history.getOrNull(index) ?: return@let
            Spacer(modifier = Modifier.height(NexusSpacing.sm))
            NexusDivider()
            Spacer(modifier = Modifier.height(NexusSpacing.xxs))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = point.contestName,
                        style = NexusTheme.typography.dataSmall,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatDateTime(point.ratingUpdateTimeSeconds * 1000),
                        style = NexusTheme.typography.dataSmall,
                        color = colors.textTertiary,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${point.oldRating} → ${point.newRating}",
                        style = NexusTheme.typography.dataSmall,
                        color = colors.textSecondary,
                    )
                    Text(
                        text = "${stringResource(R.string.rating_rank_label)} ${point.rank}",
                        style = NexusTheme.typography.dataSmall,
                        color = colors.textTertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricSeparator() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(36.dp)
            .background(NexusTheme.colors.border),
    )
}
