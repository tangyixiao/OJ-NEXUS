package com.ojnexus.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.RecentAttempt
import com.ojnexus.core.model.ReviewQueueItem
import com.ojnexus.core.model.TaskType
import com.ojnexus.core.model.TrainingTask
import com.ojnexus.core.model.Verdict
import com.ojnexus.core.ui.Loadable
import com.ojnexus.core.ui.formatCount
import com.ojnexus.core.ui.formatDate
import com.ojnexus.core.ui.formatDateTime
import com.ojnexus.core.ui.formatDuration
import com.ojnexus.core.ui.labelRes
import com.ojnexus.core.ui.tone

// Dashboard layout metrics.
private val ActivityTimeColumnWidth = 52.dp
private val TaskCodeColumnWidth = 88.dp
private val MetricSeparatorHeight = 36.dp
private val TrainingLoadHeight = 48.dp
private val LoadBarAlphaMin = 0.25f
private const val LoadBarAlphaStep = 0.1875f

@Composable
fun DashboardScreen(
    onOpenContests: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val container = com.ojnexus.core.ui.LocalAppContainer.current
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<DashboardViewModel>(
        factory = com.ojnexus.core.ui.ContainerViewModelFactory(container) {
            DashboardViewModel(
                trainingRepository = it.trainingRepository,
                reviewRepository = it.reviewRepository,
                analyticsRepository = it.analyticsRepository,
                clock = it.clock,
                judgeDataRepository = it.judgeDataRepository,
            )
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background),
    ) {
        NexusTopBar(title = stringResource(R.string.nav_dashboard))
        when (val s = state) {
            Loadable.Loading -> Box(Modifier.fillMaxSize())
            is Loadable.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = s.message,
                    style = NexusTheme.typography.data,
                    color = NexusTheme.colors.danger,
                )
            }
            is Loadable.Ready -> DashboardContent(
                state = s.value,
                onOpenContests = onOpenContests,
                onOpenSettings = onOpenSettings,
            )
        }
    }
}

@Composable
private fun DashboardContent(
    state: DashboardUiState,
    onOpenContests: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = NexusTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NexusSpacing.screenHorizontal),
    ) {
        Spacer(modifier = Modifier.height(NexusSpacing.md))

        // SYSTEM STATUS — honest connection state; rating only from a real synced profile.
        NexusSection(
            label = stringResource(R.string.dash_section_system),
            trailing = {
                NexusTag(
                    text = if (state.judgeConnections.isNotEmpty()) {
                        stringResource(R.string.sync_state_synced)
                    } else {
                        stringResource(R.string.sync_source_local)
                    },
                    tone = if (state.judgeConnections.isNotEmpty()) NexusTone.Accent else NexusTone.Neutral,
                    selected = true,
                )
            },
        ) {
            if (state.judgeConnections.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(NexusSize.tableRowHeight).clickable { onOpenSettings() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.dash_oj_connection),
                        style = NexusTheme.typography.dataSmall,
                        color = NexusTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    NexusStatus(label = stringResource(R.string.dash_not_connected), tone = NexusTone.Neutral)
                }
            } else {
                state.judgeConnections.forEachIndexed { index, connection ->
                    if (index > 0) NexusDivider(insetEnd = NexusSpacing.xxs)
                    Row(
                        modifier = Modifier.fillMaxWidth().height(NexusSize.tableRowHeight).clickable { onOpenSettings() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = connection.judge.displayName,
                            style = NexusTheme.typography.dataSmall,
                            color = NexusTheme.colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = connection.account.canonicalHandle,
                            style = NexusTheme.typography.dataSmall,
                            color = NexusTheme.colors.accent,
                        )
                        val syncing = connection.syncState?.state == com.ojnexus.core.data.sync.SyncPhase.SYNCING.name
                        NexusStatus(
                            label = stringResource(
                                if (syncing) R.string.settings_state_syncing else R.string.settings_state_connected,
                            ),
                            tone = if (syncing) NexusTone.Accent else NexusTone.Success,
                        )
                    }
                    }
                }
            }
            NexusDivider(insetEnd = NexusSpacing.xxs)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(NexusSize.tableRowHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.dash_local_mode),
                    style = NexusTheme.typography.dataSmall,
                    color = NexusTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                NexusStatus(
                    label = stringResource(R.string.dash_status_ready),
                    tone = NexusTone.Success,
                )
            }
        }

        SectionGap()

        // WEEK + RATING (rating appears only with a real synced profile)
        NexusSection(label = stringResource(R.string.dash_section_week)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                if (state.cfProfile?.rating != null) {
                    NexusMetric(
                        label = stringResource(R.string.metric_rating),
                        value = formatCount(state.cfProfile.rating),
                        modifier = Modifier.weight(1f),
                    )
                    MetricSeparator()
                }
                NexusMetric(
                    label = stringResource(R.string.dash_week_solved),
                    value = com.ojnexus.core.ui.formatCount(state.week.solved),
                    modifier = Modifier.weight(1f),
                )
                MetricSeparator()
                NexusMetric(
                    label = stringResource(R.string.dash_week_attempts),
                    value = com.ojnexus.core.ui.formatCount(state.week.attempts),
                    modifier = Modifier.weight(1f),
                )
                MetricSeparator()
                NexusMetric(
                    label = stringResource(R.string.dash_week_training),
                    value = formatDuration(state.week.trainingMs / 60_000),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(NexusSpacing.sm))
            Row(modifier = Modifier.fillMaxWidth()) {
                NexusMetric(
                    label = stringResource(R.string.dash_streak_current),
                    value = stringResource(R.string.format_streak_days, state.currentStreak),
                    modifier = Modifier.weight(1f),
                )
                MetricSeparator()
                NexusMetric(
                    label = stringResource(R.string.dash_streak_longest),
                    value = stringResource(R.string.format_streak_days, state.longestStreak),
                    modifier = Modifier.weight(1f),
                )
                Box(modifier = Modifier.weight(if (state.cfProfile?.rating != null) 1f else 2f))
            }
        }

        SectionGap()

        // TODAY
        NexusSection(
            label = stringResource(R.string.dash_section_today),
            trailing = {
                Text(
                    text = stringResource(R.string.today_header_priority),
                    style = NexusTheme.typography.sectionLabel,
                    color = colors.textTertiary,
                )
            },
        ) {
            if (state.todayTasks.isEmpty()) {
                Text(
                    text = stringResource(R.string.dash_empty_tasks),
                    style = NexusTheme.typography.dataSmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(vertical = NexusSpacing.xs),
                )
            } else {
                state.todayTasks.forEachIndexed { index, task ->
                    TaskRow(task)
                    if (index != state.todayTasks.lastIndex) NexusDivider(insetEnd = NexusSpacing.xxs)
                }
            }
        }

        SectionGap()

        // NEXT REVIEW
        NexusSection(
            label = stringResource(R.string.dash_section_next_review),
            trailing = {
                Text(
                    text = stringResource(R.string.contest_view_all),
                    style = NexusTheme.typography.sectionLabel,
                    color = colors.accent,
                    modifier = Modifier.clickable { onOpenContests() },
                )
            },
        ) {
            val review = state.nextReview
            if (review == null) {
                Text(
                    text = stringResource(R.string.dash_next_review_none),
                    style = NexusTheme.typography.dataSmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(vertical = NexusSpacing.xs),
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(NexusSize.tableRowHeight),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = review.problemTitle,
                        style = NexusTheme.typography.data,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.review_stage_label, review.stage + 1),
                        style = NexusTheme.typography.dataSmall,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(end = NexusSpacing.sm),
                    )
                    Text(
                        text = formatDate(review.dueAt),
                        style = NexusTheme.typography.dataSmall,
                        color = colors.accent,
                    )
                }
            }
        }

        SectionGap()

        // NEXT CONTEST — real synced contest; links to the Contest Center.
        NexusSection(label = stringResource(R.string.dash_section_next_contest)) {
            val contest = state.nextContest
            if (contest == null) {
                Text(
                    text = stringResource(R.string.dash_next_contest_pending),
                    style = NexusTheme.typography.dataSmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(vertical = NexusSpacing.xs),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NexusTheme.colors.surface, NexusRadius.md)
                        .clickable { onOpenContests() }
                        .padding(NexusSpacing.md),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = contest.name,
                            style = NexusTheme.typography.title,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        NexusTag(
                            text = stringResource(R.string.contest_status_upcoming),
                            tone = NexusTone.Accent,
                            selected = true,
                        )
                    }
                    Spacer(modifier = Modifier.height(NexusSpacing.sm))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.contest_label_start),
                                style = NexusTheme.typography.sectionLabel,
                                color = colors.textTertiary,
                            )
                            Text(
                                text = contest.startTimeSeconds?.let { formatDateTime(it * 1000) }
                                    ?: stringResource(R.string.problems_no_value),
                                style = NexusTheme.typography.data,
                                color = colors.textSecondary,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.contest_label_duration),
                                style = NexusTheme.typography.sectionLabel,
                                color = colors.textTertiary,
                            )
                            Text(
                                text = formatDuration(contest.durationSeconds / 60),
                                style = NexusTheme.typography.data,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            }
        }

        SectionGap()

        // RECENT ACTIVITY
        NexusSection(label = stringResource(R.string.dash_section_recent)) {
            if (state.recent.isEmpty()) {
                Text(
                    text = stringResource(R.string.dash_empty_activity),
                    style = NexusTheme.typography.dataSmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(vertical = NexusSpacing.xs),
                )
            } else {
                ActivityHeaderRow()
                NexusDivider()
                state.recent.forEachIndexed { index, row ->
                    ActivityRow(row)
                    if (index != state.recent.lastIndex) NexusDivider(insetEnd = NexusSpacing.xxs)
                }
            }
        }

        SectionGap()

        // TRAINING LOAD
        NexusSection(label = stringResource(R.string.dash_section_load)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TrainingLoadHeight),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xs),
            ) {
                state.loadWeek.forEach { intensity ->
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
private fun MetricSeparator() {
    Box(
        modifier = Modifier
            .width(NexusSize.dividerThickness)
            .height(MetricSeparatorHeight)
            .background(NexusTheme.colors.border),
    )
}

@Composable
private fun TaskRow(task: TrainingTask) {
    val colors = NexusTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(NexusSize.tableRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(task.type.labelRes()),
            style = NexusTheme.typography.dataSmall,
            color = colors.accent,
            modifier = Modifier.width(TaskCodeColumnWidth),
        )
        Text(
            text = task.problemTitle ?: task.title ?: task.type.name,
            style = NexusTheme.typography.body,
            color = if (task.completed) colors.textTertiary else colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = task.priority.toString(),
            style = NexusTheme.typography.data,
            color = colors.accent,
        )
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
private fun ActivityRow(row: RecentAttempt) {
    val colors = NexusTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(NexusSize.tableRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatDate(row.timestamp),
            style = NexusTheme.typography.dataSmall,
            color = colors.textTertiary,
            modifier = Modifier.width(ActivityTimeColumnWidth),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.problemCode,
                style = NexusTheme.typography.data,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = row.judge.displayName,
            style = NexusTheme.typography.dataSmall,
            color = colors.textTertiary,
            modifier = Modifier.padding(end = NexusSpacing.sm),
        )
        NexusTag(
            text = stringResource(row.verdict.labelRes()),
            tone = row.verdict.tone(),
        )
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
