package com.ojnexus.feature.dashboard

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
import com.ojnexus.core.designsystem.component.NexusStatus
import com.ojnexus.core.designsystem.component.NexusTag
import com.ojnexus.core.designsystem.component.NexusTopBar
import com.ojnexus.core.sample.SampleData
import com.ojnexus.core.ui.formatCount
import com.ojnexus.core.ui.formatDelta
import com.ojnexus.core.ui.labelRes
import com.ojnexus.core.ui.tone

// Dashboard layout metrics. Kept as named file constants; shared values come from the
// design system, dashboard-specific column widths are defined here.
private val ActivityTimeColumnWidth = 52.dp
private val TaskCodeColumnWidth = 88.dp
private val MetricSeparatorHeight = 36.dp
private val TrainingLoadHeight = 48.dp
private val LoadBarAlphaMin = 0.25f
private const val LoadBarAlphaStep = 0.1875f

@Composable
fun DashboardScreen(state: DashboardUiState = SampleData.dashboard) {
    val colors = NexusTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        NexusTopBar(
            title = stringResource(R.string.nav_dashboard),
            trailing = {
                NexusTag(
                    text = stringResource(R.string.sync_source_sample),
                    tone = NexusTone.Warning,
                )
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NexusSpacing.screenHorizontal),
        ) {
            Spacer(modifier = Modifier.height(NexusSpacing.md))
            SystemStatusSection(state.sync)
            SectionGap()
            CompetitiveStatusSection(state)
            SectionGap()
            TodaySection(state.todayTasks)
            SectionGap()
            NextContestSection(state.nextContest)
            SectionGap()
            RecentActivitySection(state.recentActivity)
            SectionGap()
            TrainingLoadSection(state.trainingLoad)
            Spacer(modifier = Modifier.height(NexusSpacing.xxl))
        }
    }
}

/** Rhythm between dashboard sections: hairline with even breathing room. */
@Composable
private fun SectionGap() {
    Spacer(modifier = Modifier.height(NexusSpacing.md))
    NexusDivider()
    Spacer(modifier = Modifier.height(NexusSpacing.md))
}

@Composable
private fun SystemStatusSection(sync: List<JudgeSyncUi>) {
    NexusSection(label = stringResource(R.string.dash_section_system)) {
        sync.forEachIndexed { index, line ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(NexusSize.tableRowHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = line.judge.displayName,
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (line.state == SyncStateUi.SYNCED && line.lastSyncText != null) {
                    Text(
                        text = line.lastSyncText,
                        style = NexusTheme.typography.dataSmall,
                        color = NexusTheme.colors.textTertiary,
                        modifier = Modifier.padding(end = NexusSpacing.sm),
                    )
                }
                NexusStatus(
                    label = syncStateLabel(line.state),
                    tone = syncStateTone(line.state),
                )
            }
            if (index != sync.lastIndex) {
                NexusDivider(insetEnd = NexusSpacing.xxs)
            }
        }
    }
}

@Composable
private fun syncStateLabel(state: SyncStateUi): String = when (state) {
    SyncStateUi.SYNCED -> stringResource(R.string.sync_state_synced)
    SyncStateUi.SYNCING -> stringResource(R.string.sync_state_syncing)
    SyncStateUi.NOT_LINKED -> stringResource(R.string.sync_state_not_linked)
    SyncStateUi.ERROR -> stringResource(R.string.sync_state_error)
}

private fun syncStateTone(state: SyncStateUi): NexusTone = when (state) {
    SyncStateUi.SYNCED -> NexusTone.Success
    SyncStateUi.SYNCING -> NexusTone.Accent
    SyncStateUi.NOT_LINKED -> NexusTone.Neutral
    SyncStateUi.ERROR -> NexusTone.Danger
}

@Composable
private fun CompetitiveStatusSection(state: DashboardUiState) {
    NexusSection(label = stringResource(R.string.dash_section_competitive)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            NexusMetric(
                label = stringResource(R.string.metric_rating),
                value = formatCount(state.rating),
                change = formatDelta(state.ratingDelta),
                changeTone = if (state.ratingDelta >= 0) NexusTone.Success else NexusTone.Danger,
                modifier = Modifier.weight(1f),
            )
            MetricSeparator()
            NexusMetric(
                label = stringResource(R.string.metric_weekly_ac),
                value = formatCount(state.weeklyAc),
                modifier = Modifier.weight(1f),
            )
            MetricSeparator()
            NexusMetric(
                label = stringResource(R.string.metric_streak),
                value = stringResource(R.string.format_streak_days, state.streakDays),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MetricSeparator() {
    Box(
        modifier = Modifier
            .width(NexusSize.dividerThickness)
            .height(MetricSeparatorHeight)
            .background(NexusTheme.colors.border),
    )
}

@Composable
private fun TodaySection(tasks: List<TodayTaskUi>) {
    NexusSection(
        label = stringResource(R.string.dash_section_today),
        trailing = {
            Text(
                text = stringResource(R.string.today_header_priority),
                style = NexusTheme.typography.sectionLabel,
                color = NexusTheme.colors.textTertiary,
            )
        },
    ) {
        tasks.forEachIndexed { index, task ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(NexusSize.tableRowHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = task.code,
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.accent,
                    modifier = Modifier.width(TaskCodeColumnWidth),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = NexusTheme.typography.body,
                        color = NexusTheme.colors.textPrimary,
                        maxLines = 1,
                    )
                    Text(
                        text = stringResource(R.string.format_mastery, task.masteryPercent),
                        style = NexusTheme.typography.dataSmall,
                        color = NexusTheme.colors.textTertiary,
                    )
                }
                Text(
                    text = task.priority.toString(),
                    style = NexusTheme.typography.data,
                    color = NexusTheme.colors.accent,
                )
            }
            if (index != tasks.lastIndex) {
                NexusDivider(insetEnd = NexusSpacing.xxs)
            }
        }
    }
}

@Composable
private fun NextContestSection(contest: NextContestUi?) {
    if (contest == null) return
    NexusSection(label = stringResource(R.string.dash_section_next_contest)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NexusTheme.colors.surface, NexusRadius.md)
                .padding(NexusSpacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = contest.name,
                    style = NexusTheme.typography.title,
                    color = NexusTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                NexusTag(text = contest.judge.displayName, tone = NexusTone.Accent)
            }
            Spacer(modifier = Modifier.height(NexusSpacing.sm))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.contest_label_start),
                        style = NexusTheme.typography.sectionLabel,
                        color = NexusTheme.colors.textTertiary,
                    )
                    Text(
                        text = contest.startsText,
                        style = NexusTheme.typography.data,
                        color = NexusTheme.colors.textSecondary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.contest_label_countdown),
                        style = NexusTheme.typography.sectionLabel,
                        color = NexusTheme.colors.textTertiary,
                    )
                    Text(
                        text = contest.countdownText,
                        style = NexusTheme.typography.dataLarge,
                        color = NexusTheme.colors.accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentActivitySection(activity: List<SubmissionRowUi>) {
    NexusSection(label = stringResource(R.string.dash_section_recent)) {
        ActivityHeaderRow()
        NexusDivider()
        activity.forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(NexusSize.tableRowHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.timeText,
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.textTertiary,
                    modifier = Modifier.width(ActivityTimeColumnWidth),
                )
                Text(
                    text = row.problemCode,
                    style = NexusTheme.typography.data,
                    color = NexusTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = row.judge.displayName,
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.textTertiary,
                    modifier = Modifier.padding(end = NexusSpacing.sm),
                )
                NexusTag(
                    text = stringResource(row.verdict.labelRes()),
                    tone = row.verdict.tone(),
                )
            }
            if (index != activity.lastIndex) {
                NexusDivider(insetEnd = NexusSpacing.xxs)
            }
        }
    }
}

@Composable
private fun ActivityHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NexusSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.activity_header_time),
            style = NexusTheme.typography.sectionLabel,
            color = NexusTheme.colors.textTertiary,
            modifier = Modifier.width(ActivityTimeColumnWidth),
        )
        Text(
            text = stringResource(R.string.activity_header_problem),
            style = NexusTheme.typography.sectionLabel,
            color = NexusTheme.colors.textTertiary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.activity_header_verdict),
            style = NexusTheme.typography.sectionLabel,
            color = NexusTheme.colors.textTertiary,
        )
    }
}

@Composable
private fun TrainingLoadSection(load: List<Int>) {
    val colors = NexusTheme.colors
    NexusSection(label = stringResource(R.string.dash_section_load)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TrainingLoadHeight),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xs),
        ) {
            load.forEach { intensity ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(barHeight(intensity))
                        .background(
                            colors.accent.copy(alpha = barAlpha(intensity)),
                            NexusRadius.xs,
                        ),
                )
            }
        }
        Spacer(modifier = Modifier.height(NexusSpacing.xxs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xs),
        ) {
            weekdayLabels().forEach { label ->
                Text(
                    text = label,
                    style = NexusTheme.typography.sectionLabel,
                    color = colors.textTertiary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun weekdayLabels(): List<String> = listOf(
    stringResource(R.string.day_mon),
    stringResource(R.string.day_tue),
    stringResource(R.string.day_wed),
    stringResource(R.string.day_thu),
    stringResource(R.string.day_fri),
    stringResource(R.string.day_sat),
    stringResource(R.string.day_sun),
)

private fun barHeight(intensity: Int) = NexusSpacing.xxxs + NexusSpacing.xs * intensity.coerceIn(0, 4)

private fun barAlpha(intensity: Int): Float = LoadBarAlphaMin + LoadBarAlphaStep * intensity.coerceIn(0, 4)

@Preview(name = "Dashboard")
@Composable
private fun DashboardPreview() {
    NexusTheme {
        DashboardScreen(state = SampleData.dashboard)
    }
}
