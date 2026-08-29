package com.ojnexus.feature.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ojnexus.R
import com.ojnexus.core.designsystem.NexusRadius
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.component.NexusDivider
import com.ojnexus.core.designsystem.component.NexusSection
import com.ojnexus.core.designsystem.component.NexusTopBar
import com.ojnexus.core.designsystem.component.foregroundColor
import com.ojnexus.core.sample.SampleData
import com.ojnexus.core.ui.formatCount
import com.ojnexus.core.ui.labelRes
import com.ojnexus.core.ui.tone

// Analytics layout metrics.
private val HeatmapCellSize = 8.dp
private val HeatmapCellSpacing = 2.dp
private val RatingChartHeight = 72.dp
private val DistributionBarHeight = 8.dp
private val DistributionLabelWidth = 48.dp
private val DistributionCountWidth = 48.dp

@Composable
fun AnalyticsScreen(state: AnalyticsUiState = SampleData.analytics) {
    val colors = NexusTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        NexusTopBar(title = stringResource(R.string.nav_analytics))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NexusSpacing.screenHorizontal),
        ) {
            Spacer(modifier = Modifier.height(NexusSpacing.md))
            HeatmapSection(state.heatmap)
            SectionGap()
            RatingTrendSection(state.ratingTrend)
            SectionGap()
            VerdictSection(state.verdictCounts)
            SectionGap()
            WeakTagSection(state.weakTags)
            Spacer(modifier = Modifier.height(NexusSpacing.md))
            Text(
                text = stringResource(R.string.analytics_footer_sample),
                style = NexusTheme.typography.sectionLabel,
                color = NexusTheme.colors.textTertiary,
            )
            Spacer(modifier = Modifier.height(NexusSpacing.xxl))
        }
    }
}

@Composable
private fun SectionGap() {
    Spacer(modifier = Modifier.height(NexusSpacing.md))
    NexusDivider()
    Spacer(modifier = Modifier.height(NexusSpacing.md))
}

@Composable
private fun HeatmapSection(heatmap: HeatmapUi) {
    val colors = NexusTheme.colors
    val description = stringResource(R.string.analytics_heatmap_cd)
    NexusSection(label = stringResource(R.string.analytics_section_heatmap)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(HeatmapCellSpacing),
        ) {
            heatmap.weeks.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(HeatmapCellSpacing)) {
                    week.forEach { intensity ->
                        Box(
                            modifier = Modifier
                                .size(HeatmapCellSize)
                                .background(cellColor(intensity), NexusRadius.xs),
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
    }
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
private fun RatingTrendSection(trend: List<Int>) {
    if (trend.isEmpty()) return
    val colors = NexusTheme.colors
    NexusSection(
        label = stringResource(R.string.analytics_section_rating),
        trailing = {
            Text(
                text = formatCount(trend.last()),
                style = NexusTheme.typography.dataLarge,
                color = NexusTheme.colors.textPrimary,
            )
        },
    ) {
        val pathColor = colors.accent
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(RatingChartHeight),
        ) {
            if (trend.size < 2) return@Canvas
            val min = trend.min()
            val max = trend.max()
            val range = (max - min).coerceAtLeast(1)
            val stepX = size.width / (trend.size - 1)
            val path = Path()
            trend.forEachIndexed { index, value ->
                val x = stepX * index
                val y = size.height * (1f - (value - min).toFloat() / range)
                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            drawPath(
                path = path,
                color = pathColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun VerdictSection(counts: List<VerdictCountUi>) {
    val colors = NexusTheme.colors
    NexusSection(label = stringResource(R.string.analytics_section_verdict)) {
        val maxCount = counts.maxOfOrNull { it.count } ?: 0
        counts.forEachIndexed { index, entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = NexusSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(entry.verdict.labelRes()),
                    style = NexusTheme.typography.dataSmall,
                    color = entry.verdict.tone().foregroundColor(colors),
                    modifier = Modifier.width(DistributionLabelWidth),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(DistributionBarHeight)
                        .background(colors.surfaceElevated, NexusRadius.xs),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(barFraction(entry.count, maxCount))
                            .height(DistributionBarHeight)
                            .background(entry.verdict.tone().foregroundColor(colors), NexusRadius.xs),
                    )
                }
                Text(
                    text = formatCount(entry.count),
                    style = NexusTheme.typography.dataSmall,
                    color = colors.textSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier
                        .padding(start = NexusSpacing.sm)
                        .width(DistributionCountWidth),
                )
            }
            if (index != counts.lastIndex) {
                Spacer(modifier = Modifier.height(NexusSpacing.xxxs))
            }
        }
    }
}

private fun barFraction(count: Int, max: Int): Float =
    if (max <= 0) 0f else (count.toFloat() / max).coerceIn(0.02f, 1f)

@Composable
private fun WeakTagSection(tags: List<TagCountUi>) {
    val colors = NexusTheme.colors
    NexusSection(label = stringResource(R.string.analytics_section_weak)) {
        tags.forEachIndexed { index, tag ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = NexusSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tag.tag,
                    style = NexusTheme.typography.data,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatCount(tag.count),
                    style = NexusTheme.typography.dataSmall,
                    color = colors.textSecondary,
                )
            }
            if (index != tags.lastIndex) {
                NexusDivider(insetEnd = NexusSpacing.xxs)
            }
        }
    }
}

@Preview(name = "Analytics")
@Composable
private fun AnalyticsPreview() {
    NexusTheme {
        AnalyticsScreen(state = SampleData.analytics)
    }
}
