package com.ojnexus.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ojnexus.R
import com.ojnexus.core.designsystem.NexusRadius
import com.ojnexus.core.designsystem.NexusSize
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.NexusTone
import com.ojnexus.core.designsystem.component.NexusDivider
import com.ojnexus.core.designsystem.component.NexusMetric
import com.ojnexus.core.designsystem.component.NexusSection
import com.ojnexus.core.designsystem.component.NexusTag
import com.ojnexus.core.designsystem.component.NexusTopBar
import com.ojnexus.core.sample.SampleData
import com.ojnexus.core.ui.formatCount
import com.ojnexus.core.ui.formatDays

@Composable
fun ProfileScreen(state: ProfileUiState = SampleData.profile) {
    val colors = NexusTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        NexusTopBar(title = stringResource(R.string.nav_profile))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NexusSpacing.screenHorizontal),
        ) {
            Spacer(modifier = Modifier.height(NexusSpacing.md))
            PlayerCard(state)
            SectionGap()
            GlobalSection(state.global)
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
private fun PlayerCard(state: ProfileUiState) {
    val colors = NexusTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, NexusRadius.md)
            .padding(NexusSpacing.md),
    ) {
        Text(
            text = state.handle,
            style = NexusTheme.typography.displayData,
            color = colors.textPrimary,
        )
        Text(
            text = stringResource(R.string.profile_role),
            style = NexusTheme.typography.sectionLabel,
            color = colors.textTertiary,
        )
        Spacer(modifier = Modifier.height(NexusSpacing.sm))
        NexusDivider()
        Spacer(modifier = Modifier.height(NexusSpacing.xxs))
        state.judgeCards.forEachIndexed { index, card ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(NexusSize.tableRowHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = card.judge.displayName,
                    style = NexusTheme.typography.dataSmall,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (card.linked) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = card.ratingText ?: "—",
                            style = NexusTheme.typography.data,
                            color = colors.textPrimary,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            card.solvedCount?.let { solved ->
                                Text(
                                    text = stringResource(R.string.profile_judge_solved, solved),
                                    style = NexusTheme.typography.dataSmall,
                                    color = colors.textTertiary,
                                    modifier = Modifier.padding(end = NexusSpacing.xs),
                                )
                            }
                            card.rankText?.let { rank ->
                                NexusTag(text = rank, tone = NexusTone.Accent)
                            }
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.judge_not_linked),
                        style = NexusTheme.typography.dataSmall,
                        color = colors.textTertiary,
                    )
                }
            }
            if (index != state.judgeCards.lastIndex) {
                NexusDivider(insetEnd = NexusSpacing.xxs)
            }
        }
    }
}

@Composable
private fun GlobalSection(global: GlobalStatsUi) {
    NexusSection(label = stringResource(R.string.profile_section_global)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            NexusMetric(
                label = stringResource(R.string.profile_stat_solved),
                value = formatCount(global.solved),
                modifier = Modifier.weight(1f),
            )
            MetricSeparator()
            NexusMetric(
                label = stringResource(R.string.profile_stat_submissions),
                value = formatCount(global.submissions),
                modifier = Modifier.weight(1f),
            )
            MetricSeparator()
            NexusMetric(
                label = stringResource(R.string.profile_stat_active_days),
                value = formatCount(global.activeDays),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(NexusSpacing.sm))
        Row(modifier = Modifier.fillMaxWidth()) {
            NexusMetric(
                label = stringResource(R.string.profile_stat_streak),
                value = formatDays(global.streakDays),
                modifier = Modifier.weight(1f),
            )
            MetricSeparator()
            NexusMetric(
                label = stringResource(R.string.profile_stat_max_diff),
                value = formatCount(global.maxDifficulty),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MetricSeparator() {
    Box(
        modifier = Modifier
            .width(ProfileMetricSeparatorWidth)
            .height(MetricSeparatorHeight)
            .background(NexusTheme.colors.border),
    )
}

private val MetricSeparatorHeight = 36.dp
private val ProfileMetricSeparatorWidth = 1.dp

@Preview(name = "Profile")
@Composable
private fun ProfilePreview() {
    NexusTheme {
        ProfileScreen(state = SampleData.profile)
    }
}
